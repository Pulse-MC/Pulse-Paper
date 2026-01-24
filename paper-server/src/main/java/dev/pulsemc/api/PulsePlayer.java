package dev.pulsemc.api;

import dev.pulsemc.network.PulseBuffer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * A wrapper class for players to interact with the Pulse network engine.
 */
public class PulsePlayer {
    private final PulseBuffer buffer;

    private PulsePlayer(PulseBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Internal method to get the buffer.
     */
    public PulseBuffer getBuffer() {
        return buffer;
    }

    /**
     * Gets a PulsePlayer instance for a Bukkit player.
     * @param player the Bukkit player
     * @return PulsePlayer or null if not available
     */
    public static PulsePlayer of(Player player) {
        if (player instanceof CraftPlayer cp) {
            if (cp.getHandle().connection instanceof ServerGamePacketListenerImpl listener) {
                if (listener.pulseBuffer != null) {
                    return new PulsePlayer(listener.pulseBuffer);
                }
            }
        }
        return null;
    }
}
