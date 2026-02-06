package dev.pulsemc.pulse.api.events;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Triggered when Pulse detects massive block updates in a single chunk
 * and attempts to replace them with a single ChunkData packet.
 * <p>
 * If cancelled, the server will send the original individual block update packets.
 */
public class PulseChunkOptimizationEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final Chunk chunk;
    private final int packetCount;
    private boolean cancelled;

    public PulseChunkOptimizationEvent(Player player, Chunk chunk, int packetCount) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.player = player;
        this.chunk = chunk;
        this.packetCount = packetCount;
    }

    /**
     * Gets the player receiving the updates.
     * @return the player
     */
    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * Gets the chunk where the updates are happening.
     * @return the chunk
     */
    public @NotNull Chunk getChunk() {
        return chunk;
    }

    /**
     * Gets the number of individual block update packets that were queued.
     * @return packet count
     */
    public int getPacketCount() {
        return packetCount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
