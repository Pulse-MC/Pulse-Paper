package dev.pulsemc.pulse.network;

import dev.pulsemc.pulse.ConfigManager;
import dev.pulsemc.pulse.api.enums.FlushReason;
import dev.pulsemc.pulse.api.events.optimization.PulseChunkOptimizationEvent;
import dev.pulsemc.pulse.api.events.network.PulsePacketSendEvent;
import dev.pulsemc.pulse.api.network.NetworkBuffer;
import dev.pulsemc.pulse.metrics.Metrics;
import io.netty.channel.ChannelFutureListener;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PulseBuffer implements NetworkBuffer {
    private final ServerCommonPacketListenerImpl listener;
    private final AtomicInteger bufferedCount = new AtomicInteger(0);
    private final AtomicInteger currentBatchBytes = new AtomicInteger(0);

    private static final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
            new com.google.common.util.concurrent.ThreadFactoryBuilder().setNameFormat("pulse-interval-flusher").setDaemon(true).build()
    );
    private java.util.concurrent.ScheduledFuture<?> intervalTask;

    private static volatile java.util.Set<Class<?>> instantPacketClasses = java.util.Collections.emptySet();
    private static volatile java.util.Set<Class<?>> ignoredPacketClasses = java.util.Collections.emptySet();
    private static volatile java.util.Set<Class<?>> chatPacketClasses = java.util.Collections.emptySet();
    private static volatile java.util.Set<Class<?>> criticalPacketClasses = java.util.Collections.emptySet();
    private static volatile boolean classesInitialized = false;

    private final VirtualBlockTracker virtualView = new VirtualBlockTracker();
    private boolean manualFakeMode = false;


    public PulseBuffer(ServerCommonPacketListenerImpl listener) {
        this.listener = listener;
        if (!classesInitialized) {
            initializeClasses();
        }
        setupIntervalTask();
    }

    private void setupIntervalTask() {
        if (ConfigManager.batchingMode == ConfigManager.BatchingMode.INTERVAL) {
            intervalTask = scheduler.scheduleAtFixedRate(() -> flush(FlushReason.INTERVAL),
                    ConfigManager.flushInterval, ConfigManager.flushInterval, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    public static void reload() {
        classesInitialized = false;
    }

    public void stop() {
        if (intervalTask != null) {
            intervalTask.cancel(false);
        }
    }

    private synchronized static void initializeClasses() {
        if (classesInitialized) return;

        java.util.Set<Class<?>> instant = new ReferenceOpenHashSet<>();
        java.util.Set<Class<?>> ignored = new ReferenceOpenHashSet<>();
        java.util.Set<Class<?>> chat = new ReferenceOpenHashSet<>();
        java.util.Set<Class<?>> critical = new ReferenceOpenHashSet<>();

        // We will try to resolve classes from ConfigManager names
        resolveClasses(ConfigManager.instantPackets, instant);
        resolveClasses(ConfigManager.ignoredPackets, ignored);

        // Pre-fill chat packets
        chat.add(net.minecraft.network.protocol.game.ClientboundPlayerChatPacket.class);
        chat.add(net.minecraft.network.protocol.game.ClientboundSystemChatPacket.class);
        chat.add(net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket.class);
        chat.add(net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket.class);

        // Pre-fill critical packets
        critical.add(net.minecraft.network.protocol.common.ClientboundKeepAlivePacket.class);
        critical.add(net.minecraft.network.protocol.common.ClientboundDisconnectPacket.class);
        critical.add(net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket.class);
        critical.add(net.minecraft.network.protocol.status.ClientboundStatusResponsePacket.class);
        critical.add(net.minecraft.network.protocol.common.ClientboundPingPacket.class);

        instantPacketClasses = instant;
        ignoredPacketClasses = ignored;
        chatPacketClasses = chat;
        criticalPacketClasses = critical;
        classesInitialized = true;
    }

    private static void resolveClasses(List<String> names, java.util.Set<Class<?>> into) {
        for (String name : names) {
            try {
                String[] packages = {
                        "net.minecraft.network.protocol.game.",
                        "net.minecraft.network.protocol.common.",
                        "net.minecraft.network.protocol.login.",
                        "net.minecraft.network.protocol.status.",
                        "net.minecraft.network.protocol.handshake."
                };
                boolean found = false;
                for (String pkg : packages) {
                    try {
                        into.add(Class.forName(pkg + name));
                        found = true;
                        break;
                    } catch (ClassNotFoundException ignored) {}
                }
                if (!found) {
                    into.add(Class.forName(name));
                }
            } catch (Exception ignored) {}
        }
    }

    private record PacketEntry(Packet<?> packet, @Nullable ChannelFutureListener listener) {}
    private final java.util.concurrent.ConcurrentLinkedQueue<PacketEntry> blockQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public void add(Packet<?> packet, @Nullable ChannelFutureListener sendListener){
        if (packet == null) return;

        if (packet instanceof ClientboundBlockUpdatePacket p) {
            BlockState fakeState = virtualView.getBlock(p.getPos());
            if (fakeState != null) {
                packet = new ClientboundBlockUpdatePacket(p.getPos(), fakeState);
            }
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            flush(FlushReason.INSTANT);
            listener.connection.send(packet, sendListener, true);
            restoreVirtualBlocks(p.sectionPos.x(), p.sectionPos.z());
            return;
        }

        if (!ConfigManager.enabled) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        if (packet instanceof ClientboundLevelChunkWithLightPacket p) {
            flush(FlushReason.INSTANT);
            listener.connection.send(packet, sendListener, true);
            restoreVirtualBlocks(p.getX(), p.getZ());
            return;
        }

        Class<?> packetClass = packet.getClass();

        // Ignored Packets
        if (ignoredPacketClasses.contains(packetClass)) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        // Chat Safety & Thread Safety
        if (!Bukkit.isPrimaryThread() || chatPacketClasses.contains(packetClass)) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        // Join Safety
        if (!(listener instanceof ServerGamePacketListenerImpl)) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        Metrics.logicalCounter.incrementAndGet();

        // compatibility.emulate-events
        if (ConfigManager.emulateEvents) {
            if (PulsePacketSendEvent.getHandlerList().getRegisteredListeners().length > 0) {
                org.bukkit.entity.Player bukkitPlayer = ((ServerGamePacketListenerImpl) listener).getCraftPlayer();
                if (bukkitPlayer != null) {
                    PulsePacketSendEvent event = new PulsePacketSendEvent(bukkitPlayer, packet);
                    Bukkit.getPluginManager().callEvent(event);
                    if (event.isCancelled()) return;
                    packet = (Packet<?>) event.getPacket();
                    packetClass = packet.getClass();
                }
            }
        }

        if (ConfigManager.optExplosions && isBlockUpdate(packet)) {
            handleBlockUpdate(packet, sendListener);
            return;
        }

        // Instant Packets
        if (criticalPacketClasses.contains(packetClass) || instantPacketClasses.contains(packetClass)) {
            flush(FlushReason.INSTANT);
            listener.connection.send(packet, sendListener, true);
            Metrics.physicalCounter.incrementAndGet();
            return;
        }

        queuePacketToNetty(packet, sendListener);
    }

    /**
     * Sends all packets accumulated in the buffer to the client.
     */
    public synchronized void flush(FlushReason reason) {
        if (!blockQueue.isEmpty()) {
            processBlockQueue();
        }

        long pending = getPendingBytes();

        if (bufferedCount.get() == 0 && pending == 0) return;

        Metrics.totalBytesSent.addAndGet(pending);

        listener.connection.flushChannel();

        bufferedCount.set(0);
        currentBatchBytes.set(0);
        Metrics.physicalCounter.incrementAndGet();
    }

    private boolean isChatPacket(Packet<?> packet) {
        return chatPacketClasses.contains(packet.getClass());
    }


    private boolean isCritical(Packet<?> packet) {
        return criticalPacketClasses.contains(packet.getClass());
    }

    /**
     * @return The bytes that are stored in the buffer.
     */
    public long getPendingBytes() {
        var channel = listener.connection.channel;
        if (channel != null && channel.unsafe() != null && channel.unsafe().outboundBuffer() != null) {
            return channel.unsafe().outboundBuffer().totalPendingWriteBytes();
        }
        return 0;
    }

    private void handleBlockUpdate(Packet<?> packet, @Nullable ChannelFutureListener listener) {
        if (isFakeBlock(packet) || manualFakeMode) {
            long key = getChunkKey(packet);

            if (packet instanceof net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket p) {
                virtualView.registerBlock(p.getPos(), p.getBlockState());
            }

            queuePacketToNetty(packet, listener);
            return;
        }
        blockQueue.add(new PacketEntry(packet, listener));
    }

    private void processBlockQueue() {
        if (blockQueue.isEmpty()) return;

        List<PacketEntry> processingQueue = new ArrayList<>();
        PacketEntry packetEntry;
        while ((packetEntry = blockQueue.poll()) != null) {
            processingQueue.add(packetEntry);
        }

        if (processingQueue.isEmpty()) return;

        if (!(listener instanceof ServerGamePacketListenerImpl gameListener) || gameListener.player == null) {
            processingQueue.forEach(e -> queuePacketToNetty(e.packet(), e.listener()));
            return;
        }

        Map<Long, List<PacketEntry>> batchMap = new HashMap<>();
        Map<Long, Integer> chunkBlockCounts = new HashMap<>();

        for (PacketEntry entry : processingQueue) {
            long key = getChunkKey(entry.packet());
            batchMap.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);

            int count = 1;
            if (entry.packet() instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
                count = sectionPacket.positions.length;
            }
            chunkBlockCounts.merge(key, count, Integer::sum);
        }

        for (Map.Entry<Long, List<PacketEntry>> entry : batchMap.entrySet()) {
            long chunkKey = entry.getKey();
            List<PacketEntry> entries = entry.getValue();
            int totalChanges = chunkBlockCounts.getOrDefault(chunkKey, 0);

            if (totalChanges >= ConfigManager.optExplosionThreshold) {
                if (virtualView.isChunkProtected(chunkKey)) {
                    entries.forEach(e -> queuePacketToNetty(e.packet(), e.listener()));
                    continue;
                }

                int x = ChunkPos.getX(chunkKey);
                int z = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = gameListener.player.level().getChunkIfLoaded(x, z);

                if (chunk != null) {

                    // API Event
                    boolean shouldOptimize = true;
                    if (PulseChunkOptimizationEvent.getHandlerList().getRegisteredListeners().length > 0) {
                        PulseChunkOptimizationEvent event = new PulseChunkOptimizationEvent(
                                gameListener.getCraftPlayer(),
                                gameListener.getCraftPlayer().getWorld().getChunkAt(x, z),
                                totalChanges
                        );
                        Bukkit.getPluginManager().callEvent(event);
                        if (event.isCancelled()) shouldOptimize = false;
                    }

                    if (shouldOptimize) {
                        Metrics.optimizedChunks.incrementAndGet();
                        ClientboundLevelChunkWithLightPacket chunkPacket = new ClientboundLevelChunkWithLightPacket(chunk, gameListener.player.level().getLightEngine(), null, null);
                        queuePacketToNetty(chunkPacket, null);
                    } else {
                        entries.forEach(e -> queuePacketToNetty(e.packet(), e.listener()));
                    }
                } else {
                    entries.forEach(e -> queuePacketToNetty(e.packet(), e.listener()));
                }
            } else {
                entries.forEach(e -> queuePacketToNetty(e.packet(), e.listener()));
            }
        }
    }

    private boolean isBlockUpdate(Packet<?> packet) {
        return packet instanceof ClientboundBlockUpdatePacket ||
                packet instanceof ClientboundSectionBlocksUpdatePacket ||
                packet instanceof ClientboundBlockEntityDataPacket;
    }

    public void queuePacketToNetty(Packet<?> packet, @Nullable ChannelFutureListener listenerCb) {
        listener.connection.send(packet, listenerCb, false);
        int count = bufferedCount.incrementAndGet();

        if (getPendingBytes() > (ConfigManager.maxBatchBytes - ConfigManager.safetyMargin)) {
            flush(FlushReason.LIMIT_BYTES);
            return;
        }

        if (count >= ConfigManager.maxBatchSize) {
            flush(FlushReason.LIMIT_COUNT);
        }
    }

    private long getChunkKey(Packet<?> packet) {
        if (packet instanceof ClientboundBlockUpdatePacket blockPacket) {
            return ChunkPos.asLong(blockPacket.getPos());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
            return sectionPacket.sectionPos.chunk().toLong();
        } else if (packet instanceof ClientboundBlockEntityDataPacket dataPacket) {
            return ChunkPos.asLong(dataPacket.getPos());
        }
        return 0;
    }

    private void restoreVirtualBlocks(int chunkX, int chunkZ) {
        Map<BlockPos, BlockState> blocks = virtualView.getBlocksInChunk(chunkX, chunkZ);
        if (blocks == null) return;

        blocks.forEach((pos, state) -> {
            net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket repair =
                    new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(pos, state);
            listener.connection.send(repair, null, true);
        });
    }

    public VirtualBlockTracker getVirtualBlockTracker() {
        return virtualView;
    }

    private boolean isFakeBlock(Packet<?> packet) {
        if (!(this.listener instanceof ServerGamePacketListenerImpl gameListener)) return false;
        try {
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket p) {
                return !p.getBlockState().equals(gameListener.player.level().getBlockState(p.getPos()));
            }
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket p) {
                if (p.positions.length > 0) {
                    var pos = p.sectionPos.relativeToBlockPos(p.positions[0]);
                    return !p.states[0].equals(gameListener.player.level().getBlockState(pos));
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void setManualFakeMode(boolean state) {
        this.manualFakeMode = state;
    }

    @Override
    public void flush() {
        this.flush(FlushReason.MANUAL);
    }
}