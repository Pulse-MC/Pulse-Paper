package dev.pulsemc.pulse.api.virtual.block;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages virtual (client-side only) blocks for a specific player.
 * <p>
 * Unlike standard fake blocks, blocks managed here are "persistent" at the network layer.
 * Pulse tracks these blocks and will automatically re-send them to the client
 * if the server performs a full chunk refresh (e.g., during explosions or lighting updates).
 */
public interface VirtualBlockManager {

    /**
     * Sets a persistent virtual block at the specified location.
     * <p>
     * This block is stored in the Pulse session and will be restored
     * automatically if the underlying chunk is re-sent to the player.
     *
     * @param location the coordinates where the block should appear
     * @param data the block state data to be displayed
     */
    void setBlock(@NotNull Location location, @NotNull BlockData data);

    /**
     * Removes a virtual block and restores the actual server-side state.
     * <p>
     * Pulse will stop tracking this location and send a packet to the client
     * with the real block data currently existing on the server.
     *
     * @param location the coordinates to restore
     */
    void removeBlock(@NotNull Location location);

    /**
     * Retrieves the data of a virtual block if it is currently tracked.
     *
     * @param location the coordinates to check
     * @return the {@link BlockData} of the virtual block, or null if the player sees the real world state
     */
    @Nullable BlockData getBlock(@NotNull Location location);

    /**
     * Clears all virtual blocks tracked for this player.
     * <p>
     * Note: This does not automatically refresh the client's view.
     * The player will see real blocks only after a chunk reload or manual update.
     */
    void clearAll();

    /**
     * Enables manual virtual context mode.
     * <p>
     * While active, all block update packets sent to the player (via any plugin or NMS)
     * are automatically intercepted and registered as persistent virtual blocks.
     * Use this for sending large schematics or client-side only structures.
     */
    void beginContext();

    /**
     * Disables manual virtual context mode and returns to normal network behavior.
     */
    void endContext();
}