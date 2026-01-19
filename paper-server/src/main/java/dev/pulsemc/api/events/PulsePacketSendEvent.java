package dev.pulsemc.api.events;

import net.minecraft.network.protocol.Packet;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Triggers when
 * Это "Виртуальная отправка". Физически пакет уйдет позже в бандле.
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

    public Player getPlayer() {
        return player;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public void setPacket(Packet<?> packet) {
        this.packet = packet;
    }

    public void setForceSendImmediately(boolean force) {
        this.forceSendImmediately = force;
    }

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
