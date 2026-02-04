package dev.pulsemc.network;

import dev.pulsemc.config.ConfigManager;
import dev.pulsemc.api.events.PulsePacketSendEvent;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PulseBuffer {
    private final ServerCommonPacketListenerImpl listener;
    private final AtomicInteger bufferedCount = new AtomicInteger(0);
    private final AtomicInteger currentBatchBytes = new AtomicInteger(0);

    // Hardcode packets bundle size limit
    private static final int HARD_CAP_COUNT = 4096;

    public PulseBuffer(ServerCommonPacketListenerImpl listener) {
        this.listener = listener;
    }

    private record PacketEntry(Packet<?> packet, @Nullable PacketSendListener listener) {}
    private final List<PacketEntry> blockQueue = new ArrayList<>();

    public void add(Packet<?> packet, @Nullable PacketSendListener sendListener){
        if (packet == null) return;
        String packetName = packet.getClass().getSimpleName();

        // Ignored Packets
        if (ConfigManager.ignoredPackets.contains(packetName)) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        // Chat Safety
        if (!Bukkit.isPrimaryThread() || isChatPacket(packet) || !ConfigManager.enabled) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        // Join Safety
        boolean isIngame = (listener instanceof ServerGamePacketListenerImpl);
        if (!isIngame) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        PulseMetrics.logicalCounter.incrementAndGet();

        // compatibility.emulate-events
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

        // Instant Packets
        if (isCritical(packet) || ConfigManager.instantPackets.contains(packetName)) {
            flush();
            listener.connection.send(packet, sendListener, true);
            PulseMetrics.physicalCounter.incrementAndGet();
            return;
        }

        // Flushing the buffer when it is full.
        if (getPendingBytes() > (ConfigManager.maxBatchBytes - ConfigManager.safetyMargin)) {
            flush();
        }
        // Batching
        listener.connection.send(packet, sendListener, false);

        // Count limit
        if (bufferedCount.incrementAndGet() >= HARD_CAP_COUNT) {
            flush();
        }
    }

    /**
     * Sends all packets accumulated in the buffer to the client.
     */
    public void flush() {
        if (!blockQueue.isEmpty()) {
            processBlockQueue();
        }

        long pending = getPendingBytes();

        if (bufferedCount.get() == 0 && pending == 0) return;

        PulseMetrics.totalBytesSent.addAndGet(pending);

        listener.connection.flushChannel();

        bufferedCount.set(0);
        currentBatchBytes.set(0);
        PulseMetrics.physicalCounter.incrementAndGet();
    }

    private boolean isChatPacket(Packet<?> packet) {
        String name = packet.getClass().getSimpleName();
        return name.contains("Chat") || name.contains("Message") || name.contains("Disguised") || name.contains("SystemChat");
    }


    private boolean isCritical(Packet<?> packet) {
        String name = packet.getClass().getSimpleName();
        return name.contains("KeepAlive") || name.contains("Disconnect") ||
            name.contains("Login") || name.contains("Handshake") ||
            name.contains("Status");
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

    private void handleBlockUpdate(Packet<?> packet, @Nullable PacketSendListener listener) {
        blockQueue.add(new PacketEntry(packet, listener));
    }

    private void processBlockQueue() {
        if (blockQueue.isEmpty()) return;

        List<PacketEntry> processingQueue = new ArrayList<>(blockQueue);
        blockQueue.clear();

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
                int x = ChunkPos.getX(chunkKey);
                int z = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = gameListener.player.level().getChunkIfLoaded(x, z);

                if (chunk != null) {
                    PulseMetrics.optimizedChunks.incrementAndGet();

                    ClientboundLevelChunkWithLightPacket chunkPacket = new ClientboundLevelChunkWithLightPacket(chunk, gameListener.player.level().getLightEngine(), null, null);
                    // send ONE chunk packet, without listener
                    queuePacketToNetty(chunkPacket, null);
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
            packet instanceof ClientboundSectionBlocksUpdatePacket;
    }

    private void queuePacketToNetty(Packet<?> packet, @Nullable PacketSendListener listenerCb) {
        listener.connection.send(packet, listenerCb, false);

        if (getPendingBytes() > (ConfigManager.maxBatchBytes - ConfigManager.safetyMargin)) {
            flush();
        } else if (bufferedCount.incrementAndGet() >= HARD_CAP_COUNT) {
            flush();
        }
    }

    private long getChunkKey(Packet<?> packet) {
        if (packet instanceof ClientboundBlockUpdatePacket blockPacket) {
            return ChunkPos.asLong(blockPacket.getPos());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
            return sectionPacket.sectionPos.chunk().toLong();
        }
        return 0;
    }
}
