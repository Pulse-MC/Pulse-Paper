package dev.pulsemc.pulse.api.enums;

/**
 * Represents the reason why the packet buffer was flushed to the network.
 */
public enum FlushReason {
    /**
     * The buffer exceeded the maximum byte size.
     */
    LIMIT_BYTES,

    /**
     * The buffer exceeded the maximum packet count.
     */
    LIMIT_COUNT,

    /**
     * Flushed at the end of the server tick.
     */
    TICK,

    /**
     * Flushed because a critical packet (e.g., KeepAlive) or a packet with a listener was sent.
     */
    INSTANT,

    /**
     * Flushed by the interval scheduler (Interval Mode).
     */
    INTERVAL,

    /**
     * Flushed manually via the API or command.
     */
    MANUAL
}
