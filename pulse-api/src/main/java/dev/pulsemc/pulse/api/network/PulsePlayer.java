package dev.pulsemc.pulse.api.network;

import dev.pulsemc.pulse.api.virtual.block.VirtualBlockManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a specialized wrapper for a {@link Player} within the Pulse network engine.
 * <p>
 * This interface provides a gateway to advanced network-level controls and
 * virtual world management (phantom blocks/entities) that are not available
 * in the standard Bukkit or Paper APIs.
 */
public interface PulsePlayer {

    /**
     * Obtains a {@link PulsePlayer} instance for a standard Bukkit player.
     * <p>
     * This is the primary way to interact with Pulse features for a specific player.
     *
     * @param player the Bukkit player instance to wrap
     * @return a PulsePlayer instance, or null if the player's connection is not managed by Pulse
     */
    static @Nullable PulsePlayer from(@NotNull Player player) {
        return PulseEngine.getPulsePlayer(player);
    }

    /**
     * Accesses the player's outbound network buffer.
     * Used to manage batching, low-latency modes, and manual flushing.
     *
     * @return the {@link NetworkBuffer} for this player
     */
    @NotNull NetworkBuffer getBuffer();

    /**
     * Accesses the manager for persistent virtual (client-side only) blocks.
     * Used to create blocks that stay visible even after chunk updates.
     *
     * @return the {@link VirtualBlockManager} for this player
     */
    @NotNull VirtualBlockManager getVirtualBlockManager();

    /**
     * Accesses the manager for Virtual Entities (AI-driven per-player mobs).
     * <p>
     * Allows spawning entities that run real server logic/AI but are only
     * sent to this specific player.
     *
     * @return the {@link dev.pulsemc.pulse.api.virtual.entity.VirtualEntityManager}
     */
    @NotNull dev.pulsemc.pulse.api.virtual.entity.VirtualEntityManager getVirtualEntityManager();

    /**
     * Gets the original Bukkit player instance associated with this PulsePlayer.
     *
     * @return the underlying {@link Player}
     */
    @NotNull Player getBukkitPlayer();
}