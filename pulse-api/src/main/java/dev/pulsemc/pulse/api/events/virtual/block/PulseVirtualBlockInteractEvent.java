package dev.pulsemc.pulse.api.events.virtual.block;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.jetbrains.annotations.NotNull;

/**
 * Triggered when a player interacts (Left/Right clicks) with a virtual block.
 * <p>
 * Cancelling this event will prevent the interaction from proceeding and will
 * ensure the virtual block remains visible to the player.
 */
public class PulseVirtualBlockInteractEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final Location location;
    private final BlockData virtualBlock;
    private final Action action;
    private boolean cancelled;

    private boolean cancelVanillaInteraction = true;

    public PulseVirtualBlockInteractEvent(@NotNull Player player, @NotNull Location location, @NotNull BlockData virtualBlock, @NotNull Action action) {
        this.player = player;
        this.location = location;
        this.virtualBlock = virtualBlock;
        this.action = action;
    }

    @NotNull public Player getPlayer() { return player; }
    @NotNull public Location getLocation() { return location; }
    @NotNull public BlockData getVirtualBlock() { return virtualBlock; }
    @NotNull public Action getAction() { return action; }

    /**
     * Should the vanilla server interaction be cancelled?
     * By default, this is true to prevent accidental block placements on fake blocks.
     */
    public boolean isVanillaInteractionCancelled() { return cancelVanillaInteraction; }
    public void setCancelVanillaInteraction(boolean cancel) { this.cancelVanillaInteraction = cancel; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}