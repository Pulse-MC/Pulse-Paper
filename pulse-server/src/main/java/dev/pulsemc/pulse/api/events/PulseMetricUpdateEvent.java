package dev.pulsemc.pulse.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Triggered periodically (default: 1s) when PulseMetrics updates its statistics.
 * Useful for plugins that want to display server performance data (e.g., TabList or Scoreboard).
 */
public class PulseMetricUpdateEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final double logicalPPS;
    private final double physicalPPS;
    private final double bandwidthKB;
    private final double cpuSavings;

    public PulseMetricUpdateEvent(double logicalPPS, double physicalPPS, double bandwidthKB, double cpuSavings) {
        super(true);
        this.logicalPPS = logicalPPS;
        this.physicalPPS = physicalPPS;
        this.bandwidthKB = bandwidthKB;
        this.cpuSavings = cpuSavings;
    }

    public double getLogicalPPS() { return logicalPPS; }
    public double getPhysicalPPS() { return physicalPPS; }
    public double getBandwidthKB() { return bandwidthKB; }

    /**
     * Gets the estimated CPU load saved by Pulse.
     * @return savings in percentage (e.g. 5.4)
     */
    public double getCpuSavings() { return cpuSavings; }

    @Override
    public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
