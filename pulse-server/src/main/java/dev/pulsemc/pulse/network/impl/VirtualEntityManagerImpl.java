package dev.pulsemc.pulse.network.impl;

import dev.pulsemc.pulse.api.virtual.entity.VirtualEntityManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class VirtualEntityManagerImpl implements VirtualEntityManager {
    private final Player player;
    private final Map<Integer, Entity> entities = new ConcurrentHashMap<>();

    public VirtualEntityManagerImpl(Player player) {
        this.player = player;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <T extends Entity> T spawnEntity(@NotNull Location location, @NotNull Class<T> clazz) {
        org.bukkit.craftbukkit.CraftWorld bukkitWorld = (org.bukkit.craftbukkit.CraftWorld) location.getWorld();

        T bukkitEntity = bukkitWorld.createEntity(location, clazz);

        net.minecraft.world.entity.Entity nmsEntity = ((org.bukkit.craftbukkit.entity.CraftEntity) bukkitEntity).getHandle();

        nmsEntity.pulseVirtualViewers.add(player.getUniqueId());

        bukkitWorld.getHandle().addFreshEntity(nmsEntity);

        entities.put(nmsEntity.getId(), bukkitEntity);
        return bukkitEntity;
    }

    @Override
    public boolean isVirtualEntity(@NotNull Entity entity) {
        return entities.containsKey(entity.getEntityId());
    }

    @Override
    public @NotNull Collection<Entity> getEntities() {
        entities.values().removeIf(e -> !e.isValid());
        return entities.values();
    }

    @Override
    public void clearAll() {
        for (Entity entity : entities.values()) {
            entity.remove();
        }
        entities.clear();
    }

    @Override
    public void addViewers(@NotNull Entity entity, @NotNull Player... players) {
        if (!isVirtualEntity(entity)) return;

        net.minecraft.world.entity.Entity nmsEntity = ((org.bukkit.craftbukkit.entity.CraftEntity) entity).getHandle();
        if (nmsEntity.pulseVirtualViewers == null) return;

        net.minecraft.server.level.ChunkMap.TrackedEntity tracked = ((org.bukkit.craftbukkit.CraftWorld) entity.getWorld()).getHandle().getChunkSource().chunkMap.entityMap.get(nmsEntity.getId());

        for (Player p : players) {
            if (nmsEntity.pulseVirtualViewers.add(p.getUniqueId())) {
                if (tracked != null) {
                    tracked.updatePlayer(((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle());
                }
            }
        }
    }

    @Override
    public void removeViewers(@NotNull Entity entity, @NotNull Player... players) {
        if (!isVirtualEntity(entity)) return;

        net.minecraft.world.entity.Entity nmsEntity = ((org.bukkit.craftbukkit.entity.CraftEntity) entity).getHandle();
        if (nmsEntity.pulseVirtualViewers == null) return;

        for (Player p : players) {
            if (nmsEntity.pulseVirtualViewers.remove(p.getUniqueId())) {
                ((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle().connection.send(
                        new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(nmsEntity.getId())
                );
            }
        }

        if (nmsEntity.pulseVirtualViewers.isEmpty()) {
            entity.remove();
            entities.remove(entity.getEntityId());
        }
    }

    @Override
    public void setViewers(@NotNull Entity entity, @NotNull Player... players) {
        if (!isVirtualEntity(entity)) return;

        Set<UUID> current = getViewers(entity);
        for (UUID uuid : current) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null) removeViewers(entity, p);
        }

        addViewers(entity, players);
    }

    @Override
    public @NotNull Set<UUID> getViewers(@NotNull Entity entity) {
        if (!isVirtualEntity(entity)) return java.util.Collections.emptySet();

        net.minecraft.world.entity.Entity nmsEntity = ((org.bukkit.craftbukkit.entity.CraftEntity) entity).getHandle();
        return nmsEntity.pulseVirtualViewers != null ?
                java.util.Collections.unmodifiableSet(nmsEntity.pulseVirtualViewers) :
                java.util.Collections.emptySet();
    }
}