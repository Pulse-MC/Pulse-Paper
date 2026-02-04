package dev.pulsemc.network;

import dev.pulsemc.config.ConfigManager;
import dev.pulsemc.api.events.PulsePacketSendEvent;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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

    private static volatile java.util.Set<Class<?>> instantPacketClasses = java.util.Collections.emptySet();
    private static volatile java.util.Set<Class<?>> ignoredPacketClasses = java.util.Collections.emptySet();
    private static volatile boolean classesInitialized = false;

    public PulseBuffer(ServerCommonPacketListenerImpl listener) {
        this.listener = listener;
        if (!classesInitialized) {
            initializeClasses();
        }
    }

    public static void reload() {
        classesInitialized = false;
    }

    private synchronized static void initializeClasses() {
        if (classesInitialized) return;
        
        java.util.Set<Class<?>> instant = new java.util.HashSet<>();
        java.util.Set<Class<?>> ignored = new java.util.HashSet<>();
        
        // We will try to resolve classes from ConfigManager names
        resolveClasses(ConfigManager.instantPackets, instant);
        resolveClasses(ConfigManager.ignoredPackets, ignored);
        
        instantPacketClasses = java.util.Collections.unmodifiableSet(instant);
        ignoredPacketClasses = java.util.Collections.unmodifiableSet(ignored);
        classesInitialized = true;
    }

    private static void resolveClasses(List<String> names, java.util.Set<Class<?>> into) {
        for (String name : names) {
            try {
                // This is a bit hacky as we don't know the full package, but most packets are in these packages
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
                    // Try without package if it's already full
                    into.add(Class.forName(name));
                }
            } catch (Exception e) {
                // Ignore if not found
            }
        }
    }

    private record PacketEntry(Packet<?> packet, @Nullable PacketSendListener listener) {}
    private final List<PacketEntry> blockQueue = new ArrayList<>();

    public void add(Packet<?> packet, @Nullable PacketSendListener sendListener){
        if (packet == null) return;
        
        if (!ConfigManager.enabled) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        Class<?> packetClass = packet.getClass();

        // Ignored Packets
        if (ignoredPacketClasses.contains(packetClass)) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        // Chat Safety & Thread Safety
        if (!Bukkit.isPrimaryThread() || isChatPacket(packet)) {
            listener.connection.send(packet, sendListener, true);
            return;
        }

        // Join Safety
        if (!(listener instanceof ServerGamePacketListenerImpl)) {
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
        if (isCritical(packet) || instantPacketClasses.contains(packetClass)) {
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
        if (bufferedCount.incrementAndGet() >= ConfigManager.maxBatchSize) {
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
        return packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerChatPacket ||
               packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket ||
               packet instanceof net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket ||
               packet instanceof net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket; // Not chat but usually needs to be fast
    }


    private boolean isCritical(Packet<?> packet) {
        // Use instanceof for speed
        return packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket ||
               packet instanceof net.minecraft.network.protocol.common.ClientboundDisconnectPacket ||
               packet instanceof net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket ||
               packet instanceof net.minecraft.network.protocol.status.ClientboundStatusResponsePacket ||
               packet instanceof net.minecraft.network.protocol.common.ClientboundPingPacket;
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
            for (PacketEntry e : processingQueue) queuePacketToNetty(e.packet(), e.listener());
            return;
        }

        Long2ObjectOpenHashMap<List<PacketEntry>> batchMap = new Long2ObjectOpenHashMap<>();
        Long2IntOpenHashMap chunkBlockCounts = new Long2IntOpenHashMap();

        for (PacketEntry entry : processingQueue) {
            long key = getChunkKey(entry.packet());
            batchMap.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);

            int count = 1;
            if (entry.packet() instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
                count = sectionPacket.positions.length;
            }
            chunkBlockCounts.merge(key, count, Integer::sum);
        }

        for (Long2ObjectOpenHashMap.Entry<List<PacketEntry>> entry : batchMap.long2ObjectEntrySet()) {
            long chunkKey = entry.getLongKey();
            List<PacketEntry> entries = entry.getValue();
            int totalChanges = chunkBlockCounts.get(chunkKey);

            if (totalChanges >= ConfigManager.optExplosionThreshold) {
                int x = ChunkPos.getX(chunkKey);
                int z = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = gameListener.player.level().getChunkIfLoaded(x, z);

                if (chunk != null) {
                    PulseMetrics.optimizedChunks.incrementAndGet();

                    ClientboundLevelChunkWithLightPacket chunkPacket = new ClientboundLevelChunkWithLightPacket(chunk, gameListener.player.level().getLightEngine(), null, null);
                    queuePacketToNetty(chunkPacket, null);
                } else {
                    for (PacketEntry e : entries) queuePacketToNetty(e.packet(), e.listener());
                }
            } else {
                for (PacketEntry e : entries) queuePacketToNetty(e.packet(), e.listener());
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
        } else if (bufferedCount.incrementAndGet() >= ConfigManager.maxBatchSize) {
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
