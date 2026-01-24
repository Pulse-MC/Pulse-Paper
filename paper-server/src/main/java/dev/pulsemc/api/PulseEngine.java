package dev.pulsemc.api;

import dev.pulsemc.network.PulseBuffer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The main API entry point for interacting with the PulseMC network engine.
 */
public class PulseEngine {

    /**
     * Gets the PulsePlayer instance.
     */
    public static PulsePlayer getPlayer(Player player) {
        return PulsePlayer.of(player);
    }

    /**
     * Flushes the packet buffer for a specific player immediately.
     * Use this if your plugin sends latency-sensitive data.
     *
     * @param player the target player
     */
    public static void flushPlayer(Player player) {
        PulseBuffer buffer = getBuffer(player);
        if (buffer != null) {
            buffer.flush();
        }
    }

    /**
     * Begins a "Fake Block Context" for the player.
     * While active, all block updates are marked as fake and protected from chunk optimization.
     * <p>
     * Use this for sending client-side only blocks (schematics, visual effects).
     *
     * @param player the target player
     */
    public static void beginFakeContext(org.bukkit.entity.Player player) {
        PulsePlayer pp = getPlayer(player);
        if (pp != null) pp.getBuffer().setManualFakeMode(true);
    }

    /**
     * Ends the fake block context and returns to normal batching behavior.
     *
     * @param player the target player
     */
    public static void endFakeContext(org.bukkit.entity.Player player) {
        PulsePlayer pp = getPlayer(player);
        if (pp != null) pp.getBuffer().setManualFakeMode(false);
    }

    /**
     * Checks if Pulse optimizations are active for the player.
     *
     * @param player the target player
     * @return true if active, false otherwise
     */
    public static boolean isPulseActive(Player player) {
        return getBuffer(player) != null;
    }

    /**
     * Gets the current pending bytes in the player's buffer.
     *
     * @param player the target player
     * @return pending bytes or 0
     */
    public static long getPendingBytes(Player player) {
        PulseBuffer buffer = getBuffer(player);
        return buffer != null ? buffer.getPendingBytes() : 0;
    }

    @Nullable
    private static PulseBuffer getBuffer(Player player) {
        if (player instanceof CraftPlayer cp && cp.getHandle().connection instanceof ServerGamePacketListenerImpl listener) {
            return listener.pulseBuffer;
        }
        return null;
    }
}
