package dev.pulsemc.pulse.network;

import dev.pulsemc.pulse.ConfigManager;
import dev.pulsemc.pulse.api.events.PulsePacketSendEvent;
import dev.pulsemc.pulse.metrics.Metrics;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import dev.pulsemc.pulse.api.enums.FlushReason;
import dev.pulsemc.pulse.api.events.PulseBufferFlushEvent;
import dev.pulsemc.pulse.api.events.PulseChunkOptimizationEvent;

public class PulseBuffer {
    private final ServerCommonPacketListenerImpl listener;
    private final AtomicInteger bufferedCount = new AtomicInteger(0);
    private final AtomicInteger currentBatchBytes = new AtomicInteger(0);

    private final PulseVirtualView virtualView = new PulseVirtualView();
    private boolean manualFakeMode = false;


    public PulseBuffer(ServerCommonPacketListenerImpl listener) {
        this.listener = listener;
    }

    private record PacketEntry(Packet<?> packet, @Nullable ChannelFutureListener listener) {}
    private final List<PacketEntry> blockQueue = new ArrayList<>();


    public void add(Packet<?> packet, @Nullable ChannelFutureListener sendListener) {
        if (packet == null) return;
        String packetName = packet.getClass().getSimpleName();

        // Restore fake blocks
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket p) {
            listener.connection.send(packet, sendListener, true);
            restoreVirtualBlocks(p.getX(), p.getZ());
            return;
        }

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

        Metrics.logicalCounter.incrementAndGet();

        // compatibility.emulate-events
        boolean canOptimize = true;

        if (ConfigManager.emulateEvents) {
            org.bukkit.entity.Player bukkitPlayer = ((ServerGamePacketListenerImpl) listener).getCraftPlayer();
            if (bukkitPlayer != null) {
                PulsePacketSendEvent event = new PulsePacketSendEvent(bukkitPlayer, packet);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) return;

                packet = event.getPacket();
                canOptimize = event.canBeOptimized();

                if (event.isForceSendImmediately()) {
                    flush(FlushReason.INSTANT);
                    listener.connection.send(packet, sendListener, true);
                    Metrics.physicalCounter.incrementAndGet();
                    Metrics.recordSavedPacket();
                    return;
                }
            }
        }

        // Explosion Optimization
        if (ConfigManager.optExplosions && isBlockUpdate(packet) && canOptimize) {
            handleBlockUpdate(packet, sendListener);
            return;
        }

        // Instant Packets
        if (isCritical(packet) || ConfigManager.instantPackets.contains(packetName)) {
            flush(FlushReason.INSTANT);
            listener.connection.send(packet, sendListener, true);
            Metrics.physicalCounter.incrementAndGet();
            Metrics.recordSavedPacket();
            return;
        }

        // Flushing the buffer when it is full.
        if (getPendingBytes() > (ConfigManager.maxBatchBytes - ConfigManager.safetyMargin)) {
            flush(FlushReason.LIMIT_BYTES); // Pulse - API Integration
        }

        // Batching
        listener.connection.send(packet, sendListener, false);

        // Count limit
        if (bufferedCount.incrementAndGet() >= ConfigManager.maxBatchSize) {
            flush(FlushReason.LIMIT_COUNT); // Pulse - API Integration
        }


    }

    /**
     * Sends all packets accumulated in the buffer to the client.
     * @param reason The reason for the flush (API/Network/Tick)
     */
    public void flush(FlushReason reason) {
        if (!blockQueue.isEmpty()) {
            processBlockQueue();
        }

        long pending = getPendingBytes();
        int count = bufferedCount.get();

        if (count == 0 && pending == 0) return;

        // API Event
        if (listener instanceof ServerGamePacketListenerImpl gameListener && gameListener.player != null) {
            if (PulseBufferFlushEvent.getHandlerList().getRegisteredListeners().length > 0) {
                Bukkit.getPluginManager().callEvent(new PulseBufferFlushEvent(
                    gameListener.getCraftPlayer(), reason, count, pending
                ));
            }
        }

        Metrics.totalBytesSent.addAndGet(pending);
        listener.connection.flushChannel();

        bufferedCount.set(0);
        currentBatchBytes.set(0);
        Metrics.physicalCounter.incrementAndGet();
        Metrics.recordSavedPacket();
    }

    public void flush() {
        flush(FlushReason.MANUAL);
    }

    private boolean isChatPacket(Packet<?> packet) {
        String name = packet.getClass().getSimpleName();
        return name.contains("Chat") || name.contains("Message") || name.contains("Disguised");
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

    /**
     * Automatically detects if a block update is "fake" by comparing it with the actual world state.
     * This keeps plugins like Denizen working out-of-the-box.
     */
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

    private void queuePacketToNetty(Packet<?> packet, ChannelFutureListener listenerCb) {
        listener.connection.send(packet, listenerCb, false);

        if (getPendingBytes() > (ConfigManager.maxBatchBytes - ConfigManager.safetyMargin)
            || bufferedCount.incrementAndGet() >= ConfigManager.maxBatchSize) {
            flush();
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
        Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> blocks = virtualView.getBlocksInChunk(chunkX, chunkZ);
        if (blocks == null) return;

        blocks.forEach((pos, state) -> {
            net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket repair =
                new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(pos, state);
            listener.connection.send(repair, null, true);
        });
    }

    public PulseVirtualView getVirtualView() {
        return virtualView;
    }
}
