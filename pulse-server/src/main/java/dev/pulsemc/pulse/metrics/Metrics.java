package dev.pulsemc.pulse.metrics;

import dev.pulsemc.pulse.ConfigManager;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Metrics {
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    public static final AtomicLong logicalCounter = new AtomicLong(0);
    public static final AtomicLong physicalCounter = new AtomicLong(0);

    private static long lastLogical = 0;
    private static long lastPhysical = 0;
    private static long lastBytes = 0;

    public static final AtomicLong optimizedChunks = new AtomicLong(0);
    public static final AtomicLong totalBytesSent = new AtomicLong(0);

    public static double ppsLogical = 0;
    public static double ppsPhysical = 0;
    public static double cpuUsage = 0;
    public static double vanillaCpuEst = 0;
    public static long savedAllocationsBytes = 0;
    public static long totalSavedSyscalls = 0;
    public static double networkSpeedKbs = 0;

    public static final AtomicLong flushReasonLimit = new AtomicLong(0);
    public static final AtomicLong flushReasonTick = new AtomicLong(0);
    public static final AtomicLong flushReasonInstant = new AtomicLong(0);

    private static ScheduledFuture<?> currentTask;


    public static void start() {
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel(false);
        }

        int interval = Math.max(1, ConfigManager.metricsUpdateInterval);

        scheduler.scheduleAtFixedRate(() -> {
            if (!ConfigManager.metricsEnabled) return;

            long currentLogical = logicalCounter.get();
            long currentPhysical = physicalCounter.get();
            long currentBytes = totalBytesSent.get();

            // Calculating PPS
            ppsLogical = (double) (currentLogical - lastLogical) / interval;
            ppsPhysical = (double) (currentPhysical - lastPhysical) / interval;
            double bytesDiff = currentBytes - lastBytes;
            networkSpeedKbs = (bytesDiff / 1024.0) / interval;

            lastLogical = currentLogical;
            lastPhysical = currentPhysical;
            lastBytes = currentBytes;

            cpuUsage = osBean.getProcessCpuLoad() * 100.0 * Runtime.getRuntime().availableProcessors();

            double savedPerSec = ppsLogical - ppsPhysical;
            if (savedPerSec > 0) {
                totalSavedSyscalls += (long) (savedPerSec * interval);
                // 8.5mcs = 0.00085% on 1 PPS
                double cpuRecovered = (savedPerSec * 8.5) / 10000.0;
                vanillaCpuEst = cpuUsage + cpuRecovered;
            } else {
                vanillaCpuEst = cpuUsage;
            }

            savedAllocationsBytes += (long) (savedPerSec * 512 * interval);

            // API Event
            try {
                if (dev.pulsemc.pulse.api.events.PulseMetricUpdateEvent.getHandlerList().getRegisteredListeners().length > 0) {
                    double savings = Math.max(0, vanillaCpuEst - cpuUsage);
                    org.bukkit.Bukkit.getPluginManager().callEvent(
                        new dev.pulsemc.pulse.api.events.PulseMetricUpdateEvent(ppsLogical, ppsPhysical, networkSpeedKbs, savings)
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }, 1, interval, TimeUnit.SECONDS);
    }

    public static void reload() {
        start();
    }
}
