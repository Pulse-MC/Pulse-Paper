package dev.pulsemc.pulse.api.events;

import dev.pulsemc.pulse.api.enums.FlushReason;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Triggered when the PulseBuffer flushes accumulated packets to the network.
 * <p>
 * This event is not cancellable as the flush process has already started at the network layer.
 */
public class PulseBufferFlushEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final FlushReason reason;
    private final int packetCount;
    private final long totalBytes;

    public PulseBufferFlushEvent(Player player, FlushReason reason, int packetCount, long totalBytes) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.player = player;
        this.reason = reason;
        this.packetCount = packetCount;
        this.totalBytes = totalBytes;
    }

    /**
     * Gets the player whose buffer was flushed.
     * @return the player
     */
    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * Gets the reason for the flush.
     * @return flush reason
     */
    public @NotNull FlushReason getReason() {
        return reason;
    }

    /**
     * Gets the number of packets in this batch.
     * @return packet count
     */
    public int getPacketCount() {
        return packetCount;
    }

    /**
     * Gets the size of the batch in bytes.
     * @return size in bytes
     */
    public long getTotalBytes() {
        return totalBytes;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
