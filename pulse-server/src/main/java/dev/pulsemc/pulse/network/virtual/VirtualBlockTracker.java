package dev.pulsemc.pulse.network.virtual;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualBlockTracker {
    private final ServerCommonPacketListenerImpl listener;
    private final Map<Long, Map<BlockPos, BlockState>> virtualBlocks = new ConcurrentHashMap<>();
    private final Map<Long, Long> chunkTimestamps = new ConcurrentHashMap<>();
    private static final long VIRTUAL_TTL = 30_000L;

    public VirtualBlockTracker(ServerCommonPacketListenerImpl listener) {
        this.listener = listener;
    }

    public void registerBlock(BlockPos pos, BlockState state) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        virtualBlocks.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(pos, state);
        chunkTimestamps.put(key, System.currentTimeMillis());
    }

    public boolean isChunkProtected(long chunkKey) {
        Long lastUpdate = chunkTimestamps.get(chunkKey);
        return lastUpdate != null && (System.currentTimeMillis() - lastUpdate < VIRTUAL_TTL);
    }

    public boolean isVirtualBlock(Packet<?> packet) {
        if (!(listener instanceof ServerGamePacketListenerImpl gameListener)) return false;
        try {
            if (packet instanceof ClientboundBlockUpdatePacket p) {
                return !p.getBlockState().equals(gameListener.player.level().getBlockState(p.getPos()));
            }
            if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
                if (p.positions.length > 0) {
                    var pos = p.sectionPos.relativeToBlockPos(p.positions[0]);
                    return !p.states[0].equals(gameListener.player.level().getBlockState(pos));
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void restoreVirtualBlocks(int chunkX, int chunkZ) {
        Map<BlockPos, BlockState> blocks = virtualBlocks.get(ChunkPos.asLong(chunkX, chunkZ));
        if (blocks == null || blocks.isEmpty()) return;

        blocks.forEach((pos, state) -> {
            ClientboundBlockUpdatePacket repair = new ClientboundBlockUpdatePacket(pos, state);
            listener.connection.send(repair, null, true);
        });
    }
}