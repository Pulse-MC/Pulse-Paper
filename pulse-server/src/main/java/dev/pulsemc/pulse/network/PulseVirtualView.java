package dev.pulsemc.pulse.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PulseVirtualView {
    private final Map<Long, Map<BlockPos, BlockState>> virtualBlocks = new ConcurrentHashMap<>();
    private final Map<Long, Long> chunkTimestamps = new ConcurrentHashMap<>();
    private static final long FAKE_TTL = 30_000L;

    public void registerBlock(BlockPos pos, BlockState state) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        virtualBlocks.computeIfAbsent(key, k -> new HashMap<>()).put(pos, state);
        chunkTimestamps.put(key, System.currentTimeMillis());
    }

    public void unregisterBlock(BlockPos pos) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Map<BlockPos, BlockState> chunkMap = virtualBlocks.get(key);
        if (chunkMap != null) {
            chunkMap.remove(pos);
            if (chunkMap.isEmpty()) virtualBlocks.remove(key);
        }
    }

    public Map<BlockPos, BlockState> getBlocksInChunk(int x, int z) {
        return virtualBlocks.get(ChunkPos.asLong(x, z));
    }

    public boolean isChunkProtected(long chunkKey) {
        Long lastUpdate = chunkTimestamps.get(chunkKey);
        return lastUpdate != null && (System.currentTimeMillis() - lastUpdate < FAKE_TTL);
    }

    public void clear() {
        virtualBlocks.clear();
        chunkTimestamps.clear();
    }

    /**
     * Gets the specific block state at position.
     */
    public BlockState getBlock(BlockPos pos) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Map<BlockPos, BlockState> chunkMap = virtualBlocks.get(key);
        return chunkMap != null ? chunkMap.get(pos) : null;
    }
}
