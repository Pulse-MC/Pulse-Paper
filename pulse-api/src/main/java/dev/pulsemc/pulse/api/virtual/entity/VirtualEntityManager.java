package dev.pulsemc.pulse.api.virtual.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Manages virtual (client-side only) entities that possess server-side AI.
 * <p>
 * Virtual entities in Pulse are unique because they are backed by real NMS entities.
 * This means they support:
 * <ul>
 *     <li>Pathfinding and Navigation</li>
 *     <li>Physics and Gravity</li>
 *     <li>Standard Bukkit Events (Damage, Interact)</li>
 *     <li>Metadata updates (Equipment, Names)</li>
 * </ul>
 * However, they are modified to be visible <b>only</b> to the target player
 * and are strictly excluded from world saving logic.
 */
public interface VirtualEntityManager {

    /**
     * Spawns a virtual entity with native AI.
     * <p>
     * The returned entity is a standard Bukkit entity proxy. You can use standard
     * Bukkit API methods (e.g., {@code entity.setCustomName()}) to modify it.
     * <p>
     * <b>Note:</b> This entity will NOT be saved to the disk. It will vanish on server restart
     * or when the player disconnects.
     *
     * @param location The spawn location.
     * @param clazz The class of the entity to spawn (e.g. {@code Zombie.class}).
     * @param <T> The entity type.
     * @return The spawned Bukkit entity, visible only to this player.
     */
    @NotNull <T extends Entity> T spawnEntity(@NotNull Location location, @NotNull Class<T> clazz);

    /**
     * Checks if the specified Bukkit entity is a virtual entity managed by Pulse.
     * <p>
     * Use this in EventHandlers to distinguish between real mobs and per-player phantoms.
     *
     * @param entity The entity to check.
     * @return true if the entity is virtual and owned by this player.
     */
    boolean isVirtualEntity(@NotNull Entity entity);

    /**
     * Gets a collection of all active virtual entities for this player.
     *
     * @return A collection of entities.
     */
    @NotNull Collection<Entity> getEntities();

    /**
     * Removes all virtual entities belonging to this player.
     * <p>
     * This is automatically called when the player quits to prevent memory leaks.
     */
    void clearAll();

    /**
     * Adds players to the viewers list of this virtual entity.
     * They will instantly start seeing the entity and colliding with it.
     */
    void addViewers(@NotNull Entity entity, @NotNull Player... players);

    /**
     * Removes players from the viewers list.
     * <p>
     * <b>Auto-Cleanup:</b> If the viewers list becomes empty after removal,
     * the virtual entity is automatically destroyed and removed from the world.
     */
    void removeViewers(@NotNull Entity entity, @NotNull Player... players);

    /**
     * Replaces the current viewers list with a new one.
     */
    void setViewers(@NotNull Entity entity, @NotNull Player... players);

    /**
     * Gets a list of UUIDs of all players currently seeing this entity.
     */
    @NotNull Set<UUID> getViewers(@NotNull Entity entity);
}