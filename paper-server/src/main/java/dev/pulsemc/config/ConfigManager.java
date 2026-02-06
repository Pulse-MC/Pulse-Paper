package dev.pulsemc.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ConfigManager {
    private static final File FILE = new File("pulse.yml");
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static final List<Component> lastLoadReport = new ArrayList<>();

    public static boolean enabled = true;

    public static String serverBrandName = "Pulse";

    // Native Transport
    public static boolean useIoUring = false;

    // Batching
    public static BatchingMode batchingMode = BatchingMode.SMART_EXECUTION;
    public static int flushInterval = 25;
    public static int maxBatchSize = 4096;
    public static int maxBatchBytes = 1460;
    public static int safetyMargin = 64;
    public static List<String> instantPackets = new ArrayList<>();

    // Optimization
    public static boolean optOffsets = true;
    public static boolean optExplosions = true;
    public static int optExplosionThreshold = 512;

    // Compatibility
    public static boolean emulateEvents = true;
    public static List<String> ignoredPackets = new ArrayList<>();

    // Metrics
    public static boolean metricsEnabled = true;
    public static int metricsUpdateInterval = 1;
    public static boolean moduleNetwork = true;
    public static boolean moduleCPU = true;
    public static boolean moduleRAM = true;

    public enum BatchingMode { SMART_EXECUTION, STRICT_TICK, INTERVAL }

    /**
     * Loads config and generates error messages
     * @return true if config has errors or warnings
     */
    public static boolean load() {
        lastLoadReport.clear();
        PulseMessages.load();
        try {
            if (!FILE.exists()) createDefaultConfig();

            YamlConfiguration config = YamlConfiguration.loadConfiguration(FILE);


            // Core
            enabled = new Setting<>(config, "core.enabled", true)
                .validateType(Boolean.class)
                .get();

            // F3 Brand
            String rawBrand = config.getString("core.server-brand-name", "Pulse");
            serverBrandName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(rawBrand)
            ).replace("&", "§");

            // Native Transport
            useIoUring = false;
            /*useIoUring = new Setting<>(config, "core.use-io-uring", false)
                .validateType(Boolean.class)
                .get();*/

            // Batching
            batchingMode = new Setting<>(config, "batching.mode", BatchingMode.SMART_EXECUTION)
                .parser(val -> {
                    try {
                        return BatchingMode.valueOf(val.toString().toUpperCase());
                    } catch (Exception e) {
                        return BatchingMode.SMART_EXECUTION;
                    }
                })
                .get();

            flushInterval = new Setting<>(config, "batching.flush-interval", 25)
                .validateType(Integer.class)
                .validate(val -> val >= 1, "Flush interval must be at least 1ms!")
                .get();

            maxBatchSize = new Setting<>(config, "batching.max-batch-size", 4096)
                .validateType(Integer.class)
                .validate(val -> val > 0, "Batch size must be > 0!")
                .warn(val -> val <= 4096, "Values > 4096 may cause packet loss/disconnects!")
                .get();

            maxBatchBytes = new Setting<>(config, "batching.max-batch-bytes", 32000)
                .validateType(Integer.class)
                .validate(val -> val >= 512, "MTU limit too low (<512)! Network may stall.")
                .warn(val -> val <= 64000, "> 64000 bytes is dangerous due to packet fragmentation risks")
                .get();

            safetyMargin = new Setting<>(config, "batching.safety-margin-bytes", 64)
                .validateType(Integer.class)
                .get();

            instantPackets = config.getStringList("batching.instant-packets");
            if (instantPackets.isEmpty()) instantPackets = List.of("ClientboundHurtAnimationPacket", "ClientboundDamageEventPacket");

            // Optimizations
            optExplosions = new Setting<>(config, "optimization.explosions.enabled", true)
                .validateType(Boolean.class)
                .get();
            optExplosionThreshold = new Setting<>(config, "optimization.explosions.block-change-threshold", 512)
                .validateType(Integer.class)
                .get();

            // Metrics
            metricsEnabled = new Setting<>(config, "metrics.enabled", true)
                .validateType(Boolean.class)
                .get();
            metricsUpdateInterval = new Setting<>(config, "metrics.update-interval", 1)
                .validateType(Integer.class)
                .validate(val -> val >= 1, "Interval must be at least 1 second! Provided: %s")
                .get();

            // Compatibility
            emulateEvents = new Setting<>(config, "compatibility.emulate-events", true)
                .validateType(Boolean.class)
                .get();
            ignoredPackets = config.getStringList("compatibility.ignored-packets");

            return !lastLoadReport.isEmpty();

        } catch (Exception e) {
            lastLoadReport.add(mm.deserialize("<red><bold>CRITICAL ERROR:</bold> Config file is broken! " + e.getMessage()));
            e.printStackTrace();
            return true;
        }
    }

    private static class Setting<T> {
        private final YamlConfiguration config;
        private final String path;
        private final T def;
        private T value;
        private boolean hasError = false;

        public Setting(YamlConfiguration config, String path, T def) {
            this.config = config;
            this.path = path;
            this.def = def;
            this.value = (T) config.get(path, def);
        }

        public Setting<T> validateType(Class<?> expected) {
            Object raw = config.get(path);
            if (raw != null && !expected.isAssignableFrom(raw.getClass())) {
                if (Number.class.isAssignableFrom(expected) && raw instanceof Number) {
                    return this;
                }

                reportError(raw, "Must be " + expected.getSimpleName() + "! Provided: " + raw.getClass().getSimpleName());
                this.value = def;
                this.hasError = true;
            }
            return this;
        }

        public Setting<T> validate(Predicate<T> condition, String errorMsg) {
            if (hasError) return this;
            if (!condition.test(value)) {
                reportError(value, String.format(errorMsg, value));
                this.value = def;
                this.hasError = true;
            }
            return this;
        }

        public Setting<T> warn(Predicate<T> condition, String warnMsg) {
            if (hasError) return this;
            if (!condition.test(value)) {
                reportWarning(value, warnMsg);
            }
            return this;
        }

        public Setting<T> parser(java.util.function.Function<Object, T> parser) {
            try {
                Object raw = config.get(path);
                if (raw != null) {
                    this.value = parser.apply(raw);
                }
            } catch (Exception e) {
                this.hasError = true;
            }
            return this;
        }

        public Setting<T> onError(String msg) {
            if (hasError) {
                reportError(config.get(path), msg);
                this.value = def;
            }
            return this;
        }

        public T get() {
            return value;
        }

        private void reportError(Object wrongValue, String msg) {
            lastLoadReport.add(mm.deserialize("  <grey>on <yellow>" + path + "<grey>:"));
            lastLoadReport.add(mm.deserialize("    Value: <red>" + wrongValue + "<red><italic><--[HERE]"));
            lastLoadReport.add(mm.deserialize("    <red>ERROR: <grey>" + msg + " <grey>(Reset to: <green>" + def + "<grey>)"));
            lastLoadReport.add(Component.text(" "));
        }

        private void reportWarning(Object riskyValue, String msg) {
            lastLoadReport.add(mm.deserialize("  <grey>on <yellow>" + path + "<grey>:"));
            lastLoadReport.add(mm.deserialize("    Value: <gold>" + riskyValue + "<red><italic><--[HERE]"));
            lastLoadReport.add(mm.deserialize("    <gold>WARNING: <grey>" + msg));
            lastLoadReport.add(Component.text(" "));
        }
    }

    private static void createDefaultConfig() throws java.io.IOException {
        String template = """
            # +-------------------------------------------------------------------+
            # |                       Pulse Software Config                       |
            # |                    High-Performance Networking                    |
            # +-------------------------------------------------------------------+
            
            # Core settings
            core:
              # Enable Pulse system.
              # If false, server behaves like vanilla Paper.
              enabled: true
            
              # Name in F3 Brand
              server-brand-name: "Pulse"
            
            # Packet batching (core Pulse feature)
            batching:
              # Mode of batching:
              # SMART_EXECUTION - default, flushes when necessary or on tick end.
              # STRICT_TICK     - flushes ONLY on tick end (max throughput, highest latency).
              # INTERVAL        - flushes every X milliseconds (defined below).
              mode: SMART_EXECUTION
            
              # Flush interval for INTERVAL mode (in milliseconds)
              flush-interval: 25
            
              # MTU safety limit.
              # Buffer is flushed immediately if this size is exceeded.
              max-batch-bytes: 32000
            
              # Number of packets in batch limit
              max-batch-size: 4096
            
              # Packets that bypass batching (critical for PvP)
              instant-packets:
                - ClientboundHurtAnimationPacket
                - ClientboundDamageEventPacket
            
              # Pulse will flush the buffer BEFORE adding a new packet if remaining space is less than this.
              # Prevents MTU overflow without expensive per-packet size calculation.
              safety-margin-bytes: 64
            
            # Protocol-level optimizations
            optimization:
            
              # Smart explosion handling
              explosions:
                enabled: true
                # Re-send whole chunk if block changes exceed this value
                block-change-threshold: 512
            
            
            # Compatibility & behavior
            compatibility:
            
              # Emulate Bukkit/Paper packet events.
              # Required for ProtocolLib, AntiCheat, Denizen.
              emulate-events: true
            
              # Packets that Pulse will never batch or modify
              ignored-packets: []
            #    - ClientboundMapItemDataPacket
            
            
            metrics:
              # Enable internal Pulse metrics collection.
              # Used for "/pulse stats".
              enabled: true
            
              # Metrics update interval (in seconds).
              update-interval: 1
            
              # Metric modules to collect
              modules:
                # Network-level statistics:
                # logical vs physical packets, batching efficiency, saved calls.
                network: true
            
                # CPU usage estimation:
                # compares Pulse execution cost against vanilla packet sending.
                cpu-estimation: true
            
                # Memory & GC impact analysis:
                # tracks reduced allocations
                memory-impact: true
            
            """;
        Files.writeString(FILE.toPath(), template);
    }

}
