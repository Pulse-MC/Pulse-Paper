package dev.pulsemc.pulse.api.network;

/**
 * Manages the outbound packet buffer for a player's network connection.
 * <p>
 * Pulse uses a batching system to reduce system call overhead and optimize
 * network performance. This interface provides direct control over the
 * buffering process, allowing plugins to monitor the current buffer size
 * and force-send packets when latency is a priority.
 */
public interface NetworkBuffer {
    /**
     * Flushes the packet buffer for the player.
     * All currently queued packets will be sent to the network immediately.
     * Use this if your plugin sends latency-sensitive data that should not wait for the next tick.
     */
    void flush();

    /**
     * Gets the total number of bytes currently waiting in the outbound buffer.
     * This value represents the raw serialized size of all queued packets.
     *
     * @return the amount of pending data in bytes
     */
    long getPendingBytes();
}