package dev.pulsemc.pulse.network.impl;

import dev.pulsemc.pulse.api.events.virtual.block.PulseVirtualBlockBreakEvent;
import dev.pulsemc.pulse.api.events.virtual.block.PulseVirtualBlockInteractEvent;
import dev.pulsemc.pulse.api.network.PulsePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.Action;

public class VirtualActionDispatcher {

    /**
     * Calls from NMS than player clicks block
     * @return true if break cancelled
     */
    public static boolean handleInteract(ServerPlayer nmsPlayer, BlockPos pos, Action action) {
        PulsePlayer pp = PulsePlayer.from(nmsPlayer.getBukkitEntity());
        if (pp == null) return false;

        Location loc = new Location(nmsPlayer.getBukkitEntity().getWorld(), pos.getX(), pos.getY(), pos.getZ());
        BlockData virtualBlock = pp.getVirtualBlockManager().getBlock(loc);
        if (virtualBlock == null) return false;

        PulseVirtualBlockInteractEvent event = new PulseVirtualBlockInteractEvent(nmsPlayer.getBukkitEntity(), loc, virtualBlock, action);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            nmsPlayer.getBukkitEntity().sendBlockChange(loc, virtualBlock);
            return true;
        }
        return event.isVanillaInteractionCancelled();
    }

    /**
     * Calls from NMS than player breaks block
     * @return true if break cancelled
     */
    public static boolean handleBreak(ServerPlayer nmsPlayer, BlockPos pos) {
        PulsePlayer pp = PulsePlayer.from(nmsPlayer.getBukkitEntity());
        if (pp == null) return false;

        Location loc = new Location(nmsPlayer.getBukkitEntity().getWorld(), pos.getX(), pos.getY(), pos.getZ());
        BlockData virtualBlock = pp.getVirtualBlockManager().getBlock(loc);
        if (virtualBlock == null) return false;

        PulseVirtualBlockBreakEvent event = new PulseVirtualBlockBreakEvent(nmsPlayer.getBukkitEntity(), loc, virtualBlock);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            nmsPlayer.getBukkitEntity().sendBlockChange(loc, virtualBlock);
            return true;
        } else {
            pp.getVirtualBlockManager().removeBlock(loc);
            return false;
        }
    }
}