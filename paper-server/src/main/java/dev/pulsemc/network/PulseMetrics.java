package dev.pulsemc.network;

import dev.pulsemc.config.ConfigManager;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class PulseMetrics {
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    public static final AtomicLong logicalCounter = new AtomicLong(0);
    public static final AtomicLong physicalCounter = new AtomicLong(0);

    // Snapshots for PPS
    private static long lastLogical = 0;
    private static long lastPhysical = 0;
    private static long lastBytes = 0;

    public static final AtomicLong optimizedChunks = new AtomicLong(0);
    public static final AtomicLong totalBytesSent = new AtomicLong(0);

    // Results
    public static double ppsLogical = 0;
    public static double ppsPhysical = 0;
    public static double cpuUsage = 0;
    public static double vanillaCpuEst = 0;
    public static long savedAllocationsBytes = 0;
    public static long totalSavedSyscalls = 0;
    public static double networkSpeedKbs = 0; // Скорость в КБ/с

    public static final AtomicLong flushReasonLimit = new AtomicLong(0);
    public static final AtomicLong flushReasonTick = new AtomicLong(0);
    public static final AtomicLong flushReasonInstant = new AtomicLong(0);


    public static void start() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!ConfigManager.metricsEnabled) return;

            long currentLogical = logicalCounter.get();
            long currentPhysical = physicalCounter.get();
            int interval = ConfigManager.metricsUpdateInterval;
            long currentBytes = totalBytesSent.get();

            // Calculating PPS
            ppsLogical = (double) (currentLogical - lastLogical) / interval;
            ppsPhysical = (double) (currentPhysical - lastPhysical) / interval;
            double bytesDiff = currentBytes - lastBytes;
            networkSpeedKbs = (bytesDiff / 1024.0) / interval;

            lastLogical = currentLogical;
            lastPhysical = currentPhysical;
            lastBytes = currentBytes;

            // Calculating CPU
            cpuUsage = osBean.getProcessCpuLoad() * 100.0 * Runtime.getRuntime().availableProcessors();

            // Overhead model - 8.5 microseconds
            double savedPerSec = ppsLogical - ppsPhysical;
            if (savedPerSec > 0) {
                totalSavedSyscalls += (long) (savedPerSec * interval);
                // 8.5mcs = 0.00085% on 1 PPS
                double cpuRecovered = (savedPerSec * 8.5) / 10000.0;
                vanillaCpuEst = cpuUsage + cpuRecovered;
            } else {
                vanillaCpuEst = cpuUsage;
            }

            // RAM Impact
            savedAllocationsBytes += (long) (savedPerSec * 512 * interval);

        }, 1, ConfigManager.metricsUpdateInterval, TimeUnit.SECONDS);
    }
}
