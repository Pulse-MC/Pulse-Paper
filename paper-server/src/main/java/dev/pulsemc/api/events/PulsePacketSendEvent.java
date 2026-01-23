package dev.pulsemc.api.events;

import net.minecraft.network.protocol.Packet;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Triggered when a packet is about to be added to the Pulse buffer.
 * <p>
 * This event is called synchronously on the main thread before any optimization logic.
 * You can use this event to modify the packet, cancel it, or force immediate sending.
 */
public class PulsePacketSendEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private Packet<?> packet;
    private boolean cancelled;
    private boolean forceSendImmediately;

    public PulsePacketSendEvent(Player player, Packet<?> packet) {
        super();
        this.player = player;
        this.packet = packet;
    }

    /**
     * Gets the player receiving this packet.
     * @return the target player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the raw packet being sent.
     * @return the packet
     */
    public Packet<?> getPacket() {
        return packet;
    }

    /**
     * Replaces the packet with a modified one.
     * @param packet the new packet
     */
    public void setPacket(Packet<?> packet) {
        this.packet = packet;
    }

    /**
     * Sets whether this packet should bypass the buffer and be sent immediately.
     * If true, Pulse will flush the current buffer and send this packet instantly.
     * Use this for latency-sensitive packets.
     * @param force true to send immediately
     */
    public void setForceSendImmediately(boolean force) {
        this.forceSendImmediately = force;
    }

    /**
     * Checks if immediate sending is requested.
     * @return true if forced
     */
    public boolean isForceSendImmediately() {
        return forceSendImmediately;
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
