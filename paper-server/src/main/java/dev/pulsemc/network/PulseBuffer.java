package dev.pulsemc.network;

import dev.pulsemc.config.ConfigManager;
import dev.pulsemc.api.events.PulsePacketSendEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import io.netty.channel.ChannelFutureListener;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PulseBuffer {
    private final ServerCommonPacketListenerImpl listener;
    private final AtomicInteger bufferedCount = new AtomicInteger(0);

    private static final int HARD_CAP_COUNT = 4096;

    private static final Set<PulseBuffer> activeBuffers = Collections.newSetFromMap(new WeakHashMap<>());
    private static final ScheduledExecutorService intervalScheduler = Executors.newSingleThreadScheduledExecutor();
    private static boolean schedulerStarted = false;
    private long lastFlushTime = System.currentTimeMillis();

    private record PacketEntry(Packet<?> packet, @Nullable ChannelFutureListener listener) {}
    private final List<PacketEntry> blockQueue = new ArrayList<>();

    public PulseBuffer(ServerCommonPacketListenerImpl listener) {
        this.listener = listener;

        synchronized (activeBuffers) {
            activeBuffers.add(this);
        }
        startSchedulerIfNeeded();
    }

    private static void startSchedulerIfNeeded() {
        if (schedulerStarted) return;
        schedulerStarted = true;

        intervalScheduler.scheduleAtFixedRate(() -> {
            try {
                if (ConfigManager.batchingMode == ConfigManager.BatchingMode.SMART_EXECUTION) return;

                long now = System.currentTimeMillis();

                long targetDelay;
                if (ConfigManager.batchingMode == ConfigManager.BatchingMode.STRICT_TICK) {
                    targetDelay = 50;
                } else {
                    targetDelay = ConfigManager.flushInterval;
                }

                synchronized (activeBuffers) {
                    for (PulseBuffer buffer : activeBuffers) {
                        if (now - buffer.lastFlushTime >= targetDelay) {
                            if (buffer.listener.connection.channel.eventLoop().inEventLoop()) {
                                buffer.flush("Timer");
                            } else {
                                buffer.listener.connection.channel.eventLoop().execute(() -> buffer.flush("Timer"));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 5, 5, TimeUnit.MILLISECONDS);
    }

    public void add(Packet<?> packet, @Nullable ChannelFutureListener sendListener) {
        if (packet == null) return;

        String packetName = packet.getClass().getSimpleName();

        if (ConfigManager.ignoredPackets.contains(packetName)) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        if (!Bukkit.isPrimaryThread() || isChatPacket(packet) || !ConfigManager.enabled) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        boolean isIngame = (listener instanceof ServerGamePacketListenerImpl);
        if (!isIngame) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        PulseMetrics.logicalCounter.incrementAndGet();

        if (ConfigManager.emulateEvents) {
            org.bukkit.entity.Player bukkitPlayer = ((ServerGamePacketListenerImpl) listener).getCraftPlayer();
            if (bukkitPlayer != null) {
                PulsePacketSendEvent event = new PulsePacketSendEvent(bukkitPlayer, packet);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) return;
                packet = event.getPacket();
            }
        }

        if (ConfigManager.optExplosions && isBlockUpdate(packet)) {
            handleBlockUpdate(packet, sendListener);
            return;
        }

        if (isCritical(packet) || ConfigManager.instantPackets.contains(packetName)) {
            flush("Instant");
            listener.connection.send(packet, sendListener, true);
            PulseMetrics.physicalCounter.incrementAndGet();
            return;
        }

        if (getPendingBytes() > (ConfigManager.maxBatchBytes - ConfigManager.safety_margin)) {
            flush("LimitBytes");
        }

        listener.connection.send(packet, sendListener, false);

        int count = bufferedCount.incrementAndGet();

        if (ConfigManager.batchingMode == ConfigManager.BatchingMode.SMART_EXECUTION) {
                flush("LimitCount");
        }

        if (count >= HARD_CAP_COUNT) {
            flush("HardCap");
        }
    }

    public void tick() {
        if (ConfigManager.batchingMode == ConfigManager.BatchingMode.SMART_EXECUTION) {
            flush("SmartTick");
        }
    }

    public void flush(String reason) {
        if (!blockQueue.isEmpty()) {
            processBlockQueue();
        }

        long pending = getPendingBytes();
        if (bufferedCount.get() == 0 && pending == 0) return;

        PulseMetrics.totalBytesSent.addAndGet(pending);

        if (reason.startsWith("Limit") || reason.equals("HardCap")) PulseMetrics.flushReasonLimit.incrementAndGet();
        else if (reason.equals("SmartTick")) PulseMetrics.flushReasonTick.incrementAndGet();
        else if (reason.equals("Timer")) PulseMetrics.flushReasonTick.incrementAndGet();
        else if (reason.equals("Instant")) PulseMetrics.flushReasonInstant.incrementAndGet();

        listener.connection.flushChannel();

        lastFlushTime = System.currentTimeMillis();

        bufferedCount.set(0);
        PulseMetrics.physicalCounter.incrementAndGet();
    }

    public void flush() {
        flush("Unknown");
    }


    private void handleBlockUpdate(Packet<?> packet, ChannelFutureListener listener) {
        blockQueue.add(new PacketEntry(packet, listener));
    }

    private void processBlockQueue() {
        if (blockQueue.isEmpty()) return;
        List<PacketEntry> processingQueue = new ArrayList<>(blockQueue);
        blockQueue.clear();

        if (!(listener instanceof ServerGamePacketListenerImpl gameListener) || gameListener.player == null) {
            processingQueue.forEach(e -> queuePacketToNetty(e.packet, e.listener));
            return;
        }

        Map<Long, List<PacketEntry>> batchMap = new HashMap<>();
        Map<Long, Integer> chunkBlockCounts = new HashMap<>();

        for (PacketEntry entry : processingQueue) {
            long key = getChunkKey(entry.packet);
            batchMap.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
            int count = 1;
            if (entry.packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) count = sectionPacket.positions.length;
            chunkBlockCounts.merge(key, count, Integer::sum);
        }

        for (Map.Entry<Long, List<PacketEntry>> entry : batchMap.entrySet()) {
            long chunkKey = entry.getKey();
            List<PacketEntry> entries = entry.getValue();
            int totalChanges = chunkBlockCounts.getOrDefault(chunkKey, 0);

            if (totalChanges >= ConfigManager.optExplosionThreshold) {
                int x = ChunkPos.getX(chunkKey);
                int z = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = gameListener.player.level().getChunkIfLoaded(x, z);
                if (chunk != null) {
                    PulseMetrics.optimizedChunks.incrementAndGet();
                    ClientboundLevelChunkWithLightPacket chunkPacket = new ClientboundLevelChunkWithLightPacket(chunk, gameListener.player.level().getLightEngine(), null, null);
                    queuePacketToNetty(chunkPacket, null);
                } else {
                    entries.forEach(e -> queuePacketToNetty(e.packet, e.listener));
                }
            } else {
                entries.forEach(e -> queuePacketToNetty(e.packet, e.listener));
            }
        }
    }

    private void queuePacketToNetty(Packet<?> packet, ChannelFutureListener listenerCb) {
        listener.connection.send(packet, listenerCb, false);
        if (getPendingBytes() > (ConfigManager.maxBatchBytes - ConfigManager.safety_margin)) {
            flush("LimitBytes");
        } else if (bufferedCount.incrementAndGet() >= HARD_CAP_COUNT) {
            flush("LimitCount");
        }
    }

    private long getChunkKey(Packet<?> packet) {
        if (packet instanceof ClientboundBlockUpdatePacket blockPacket) return ChunkPos.asLong(blockPacket.getPos());
        else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) return sectionPacket.sectionPos.chunk().toLong();
        return 0;
    }

    public long getPendingBytes() {
        var channel = listener.connection.channel;
        if (channel != null && channel.unsafe() != null && channel.unsafe().outboundBuffer() != null) {
            return channel.unsafe().outboundBuffer().totalPendingWriteBytes();
        }
        return 0;
    }

    private boolean isChatPacket(Packet<?> packet) {
        String name = packet.getClass().getSimpleName();
        return name.contains("Chat") || name.contains("Message") || name.contains("Disguised") || name.contains("SystemChat");
    }

    private boolean isBlockUpdate(Packet<?> packet) {
        return packet instanceof ClientboundBlockUpdatePacket || packet instanceof ClientboundSectionBlocksUpdatePacket;
    }

    private boolean isCritical(Packet<?> packet) {
        String name = packet.getClass().getSimpleName();
        return name.contains("KeepAlive") || name.contains("Disconnect") ||
            name.contains("Login") || name.contains("Handshake") ||
            name.contains("Status");
    }

    private int estimatePacketSize(Packet<?> packet) { return 24; }
}
