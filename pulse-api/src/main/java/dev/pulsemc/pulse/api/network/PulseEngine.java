package dev.pulsemc.pulse.api.network;

import org.bukkit.entity.Player;
import java.util.function.Function;

/**
 * The internal bridge and registry for the Pulse network engine services.
 * <p>
 * This class serves as the link between the Pulse API and the server implementation.
 * It is automatically initialized by the server core during the bootstrap process.
 * <b>Note:</b> Methods in this class are mostly for internal use.
 * Developers should prefer using {@link PulsePlayer#from(Player)}.
 */
public class PulseEngine {

    /**
     * Internal provider function set by the server core to handle player wrapping.
     */
    public static Function<Player, PulsePlayer> provider;

    /**
     * Retrieves the {@link PulsePlayer} instance for a given Bukkit player.
     *
     * @param player the player to look up
     * @return the specialized PulsePlayer instance, or null if the engine is not initialized
     */
    public static PulsePlayer getPulsePlayer(Player player) {
        return (provider != null) ? provider.apply(player) : null;
    }
}