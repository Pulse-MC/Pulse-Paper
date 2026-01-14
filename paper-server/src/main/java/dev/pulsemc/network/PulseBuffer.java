package dev.pulsemc.network;

import dev.pulsemc.config.ConfigManager;
import dev.pulsemc.api.events.PulsePacketSendEvent;
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
import io.netty.channel.ChannelFutureListener;
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

    private List<Packet<?>> blockQueue = new ArrayList<>();

    public void add(Packet<?> packet, @Nullable ChannelFutureListener sendListener) {
        if (packet == null) return;
        if (ConfigManager.optExplosions && isBlockUpdate(packet)) {
            handleBlockUpdate(packet);
            return;
        }
        String packetName = packet.getClass().getSimpleName();

        // Ignored Packets
        // If packet in ignore list - send instant
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
        // If disabled in config - skipping this block
        if (ConfigManager.emulateEvents) {
            org.bukkit.entity.Player bukkitPlayer = ((ServerGamePacketListenerImpl) listener).getCraftPlayer();
            if (bukkitPlayer != null) {
                PulsePacketSendEvent event = new PulsePacketSendEvent(bukkitPlayer, packet);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) return;
                packet = event.getPacket();
            }
        }

        // Instant Packets
        if (isCritical(packet) || ConfigManager.instantPackets.contains(packetName)) {
            flush();
            listener.connection.send(packet, sendListener, true);
            PulseMetrics.physicalCounter.incrementAndGet();
            return;
        }

        // Flushing the buffer when it is full.
        if (getPendingBytes() > (ConfigManager.maxBatchBytes - ConfigManager.safety_margin)) {
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

    private int estimatePacketSize(Packet<?> packet) {
        String name = packet.getClass().getSimpleName();
        if (name.contains("EntityVelocity")) return 12;
        if (name.contains("MoveEntity")) return 10;
        if (name.contains("Teleport")) return 32;
        if (name.contains("SetEntityData")) return 64;
        if (name.contains("Chunk")) return 2048;
        return 24;
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

    private void handleBlockUpdate(Packet<?> packet) {
        long chunkKey = 0;
        int count = 1;

        if (packet instanceof ClientboundBlockUpdatePacket blockPacket) {
            chunkKey = ChunkPos.asLong(blockPacket.getPos());
        }
        else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
            chunkKey = sectionPacket.sectionPos.chunk().toLong();
            count = sectionPacket.positions.length;
        }

        blockQueue.add(packet);
    }

    private void processBlockQueue() {
        if (blockQueue.isEmpty()) return;

        List<Packet<?>> processingQueue = new ArrayList<>(blockQueue);
        blockQueue.clear();

        if (!(listener instanceof ServerGamePacketListenerImpl gameListener) || gameListener.player == null) {
            processingQueue.forEach(p -> queuePacketToNetty(p, null));
            return;
        }

        Map<Long, List<Packet<?>>> batchMap = new HashMap<>();
        // Добавляем карту для подсчета блоков
        Map<Long, Integer> chunkBlockCounts = new HashMap<>();

        for (Packet<?> packet : processingQueue) {
            long key = getChunkKey(packet);
            batchMap.computeIfAbsent(key, k -> new ArrayList<>()).add(packet);

            // Считаем блоки
            int count = 1;
            if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
                count = sectionPacket.positions.length; // Массив изменений
            }
            chunkBlockCounts.merge(key, count, Integer::sum);
        }

        for (Map.Entry<Long, List<Packet<?>>> entry : batchMap.entrySet()) {
            long chunkKey = entry.getKey();
            List<Packet<?>> packets = entry.getValue();

            // Берем реальное число изменений
            int totalChanges = chunkBlockCounts.getOrDefault(chunkKey, 0);

            // Проверяем по числу БЛОКОВ, а не пакетов
            if (totalChanges >= ConfigManager.optExplosionThreshold) {
                int x = ChunkPos.getX(chunkKey);
                int z = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = gameListener.player.level().getChunkIfLoaded(x, z);

                if (chunk != null) {
                    PulseMetrics.optimizedChunks.incrementAndGet();

                    ClientboundLevelChunkWithLightPacket chunkPacket = new ClientboundLevelChunkWithLightPacket(chunk, gameListener.player.level().getLightEngine(), null, null);
                    queuePacketToNetty(chunkPacket, null);
                } else {
                    packets.forEach(p -> queuePacketToNetty(p, null));
                }
            } else {
                packets.forEach(p -> queuePacketToNetty(p, null));
            }
        }
    }

    private boolean isBlockUpdate(Packet<?> packet) {
        return packet instanceof ClientboundBlockUpdatePacket ||
            packet instanceof ClientboundSectionBlocksUpdatePacket;
    }

    private void queuePacketToNetty(Packet<?> packet, ChannelFutureListener listenerCb) {
        listener.connection.send(packet, listenerCb, false);

        if (getPendingBytes() > (ConfigManager.maxBatchBytes - ConfigManager.safety_margin)) {
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
