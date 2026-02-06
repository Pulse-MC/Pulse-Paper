package dev.pulsemc.pulse.api.virtual.block;

import dev.pulsemc.pulse.api.network.PulseNetwork;
import dev.pulsemc.pulse.api.network.PulsePlayer;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * API for managing persistent virtual blocks.
 */
public class PulseVirtualBlock {

    /**
     * Begins a "Fake Block Context". While active, all block updates are marked as fake.
     */
    public static void beginContext(Player player) {
        PulsePlayer pp = PulseNetwork.getPlayer(player);
        if (pp != null) pp.getBuffer().setManualFakeMode(true);
    }

    /**
     * Ends the fake block context.
     */
    public static void endContext(Player player) {
        PulsePlayer pp = PulseNetwork.getPlayer(player);
        if (pp != null) pp.getBuffer().setManualFakeMode(false);
    }

    /**
     * Sets a persistent virtual block for the player.
     */
    public static void setBlock(Player player, Location loc, BlockData data) {
        PulsePlayer pp = PulseNetwork.getPlayer(player);
        if (pp == null) return;

        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        net.minecraft.world.level.block.state.BlockState state = ((org.bukkit.craftbukkit.block.data.CraftBlockData) data).getState();

        pp.getVirtualView().registerBlock(pos, state);
        player.sendBlockChange(loc, data);
    }

    /**
     * Removes a virtual block and restores the actual server-side block for the player.
     */
    public static void removeBlock(Player player, Location loc) {
        PulsePlayer pp = PulseNetwork.getPlayer(player);
        if (pp == null) return;

        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        pp.getVirtualView().unregisterBlock(pos);

        player.sendBlockChange(loc, loc.getBlock().getBlockData());
    }

    /**
     * Gets the virtual block data a player is currently seeing at a location.
     */
    @Nullable
    public static BlockData getBlock(Player player, Location loc) {
        PulsePlayer pp = PulseNetwork.getPlayer(player);
        if (pp == null) return null;

        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        net.minecraft.world.level.block.state.BlockState state = pp.getVirtualView().getBlock(pos);

        return state != null ? org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(state) : null;
    }

    /**
     * Clears all virtual blocks for the player.
     */
    public static void clearAll(Player player) {
        PulsePlayer pp = PulseNetwork.getPlayer(player);
        if (pp == null) return;

        pp.getVirtualView().clear();
    }
}
