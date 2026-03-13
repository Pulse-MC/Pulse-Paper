package dev.pulsemc.pulse.network.impl;

import dev.pulsemc.pulse.api.network.PulsePlayer;
import dev.pulsemc.pulse.api.virtual.block.VirtualBlockManager;
import dev.pulsemc.pulse.network.PulseBuffer;
import dev.pulsemc.pulse.api.virtual.entity.VirtualEntityManager;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.NotNull;

public class PulsePlayerImpl implements PulsePlayer {
    private final Player player;
    private final PulseBuffer buffer;
    private final VirtualBlockManager blockManager;
    private final VirtualEntityManager virtualEntityManager;

    public PulsePlayerImpl(Player player) {
        this.player = player;
        this.buffer = ((CraftPlayer) player).getHandle().connection.pulseBuffer;
        this.blockManager = new VirtualBlockManagerImpl(buffer, player);
        this.virtualEntityManager = new VirtualEntityManagerImpl(player);
    }

    public @NotNull VirtualBlockManager getVirtualBlockManager() {
        return blockManager;
    }

    public @NotNull PulseBuffer getBuffer() {
        return buffer;
    }

    @Override public @NotNull Player getBukkitPlayer() { return player; }

    @Override
    public @NotNull VirtualEntityManager getVirtualEntityManager() {
        return virtualEntityManager;
    }
}