package dev.pulsemc.pulse.network.impl;

import dev.pulsemc.pulse.api.virtual.block.VirtualBlockManager;
import dev.pulsemc.pulse.network.PulseBuffer;
import dev.pulsemc.pulse.network.VirtualBlockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.jspecify.annotations.NonNull;

public class VirtualBlockManagerImpl implements VirtualBlockManager {
    private final PulseBuffer buffer;
    private final VirtualBlockTracker tracker;
    private final org.bukkit.entity.Player player;

    public VirtualBlockManagerImpl(PulseBuffer buffer, org.bukkit.entity.Player player) {
        this.buffer = buffer;
        this.tracker = buffer.getVirtualBlockTracker();
        this.player = player;
    }

    @Override
    public void setBlock(Location location, BlockData data) {
        BlockPos pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        BlockState state = ((CraftBlockData) data).getState();

        tracker.registerBlock(pos, state);

        player.sendBlockChange(location, data);
    }

    @Override
    public void removeBlock(Location location) {
        BlockPos pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());

        tracker.unregisterBlock(pos);

        player.sendBlockChange(location, location.getBlock().getBlockData());
    }

    @Override
    public BlockData getBlock(@NonNull Location location) {
        BlockPos pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        BlockState state = tracker.getBlock(pos);
        return state != null ? CraftBlockData.fromData(state) : null;
    }

    @Override
    public void clearAll() {
        java.util.Map<BlockPos, BlockState> blocks = tracker.getAllBlocks();

        tracker.clear();

        for (BlockPos pos : blocks.keySet()) {
            Location loc = new Location(player.getWorld(), pos.getX(), pos.getY(), pos.getZ());
            player.sendBlockChange(loc, loc.getBlock().getBlockData());
        }
    }

    @Override public void beginContext() { buffer.setManualFakeMode(true); }
    @Override public void endContext() { buffer.setManualFakeMode(false); }


}