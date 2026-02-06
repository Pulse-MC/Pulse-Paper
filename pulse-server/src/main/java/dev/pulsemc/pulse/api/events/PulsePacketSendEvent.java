package dev.pulsemc.pulse.api.events;

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
     * Whether this packet is allowed to be optimized (e.g. replaced by a full chunk update).
     * Default is true. Set to false for fake blocks or packets that must remain individual.
     */
    private boolean canBeOptimized = true;

    /**
     * Sets whether this packet can be optimized by the engine.
     * @param can true to allow optimization, false to force individual sending.
     */
    public void setCanBeOptimized(boolean can) {
        this.canBeOptimized = can;
    }

    /**
     * Checks if this packet is allowed to be optimized.
     * @return true if optimization is allowed.
     */
    public boolean canBeOptimized() {
        return canBeOptimized;
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
