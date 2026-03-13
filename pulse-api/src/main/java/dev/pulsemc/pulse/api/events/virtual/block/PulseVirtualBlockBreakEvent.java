package dev.pulsemc.pulse.api.events.virtual.block;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Triggered when a player successfully breaks a virtual block.
 * <p>
 * If cancelled, the block will NOT be removed and will be resent to the client.
 * If not cancelled, the virtual block is removed and the real server block is revealed.
 */
public class PulseVirtualBlockBreakEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final Location location;
    private final BlockData virtualBlock;
    private boolean cancelled;

    public PulseVirtualBlockBreakEvent(@NotNull Player player, @NotNull Location location, @NotNull BlockData virtualBlock) {
        this.player = player;
        this.location = location;
        this.virtualBlock = virtualBlock;
    }

    @NotNull public Player getPlayer() { return player; }
    @NotNull public Location getLocation() { return location; }
    @NotNull public BlockData getVirtualBlock() { return virtualBlock; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}