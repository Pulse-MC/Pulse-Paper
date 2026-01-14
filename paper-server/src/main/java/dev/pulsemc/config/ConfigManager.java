package dev.pulsemc.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;

public class ConfigManager {
    private static final File FILE = new File("pulse.yml");

    public enum BatchingMode { SMART_EXECUTION, STRICT_TICK, INTERVAL }

    // --- Core ---
    public static boolean enabled = true;

    // --- Batching ---
    public static BatchingMode batchingMode = BatchingMode.SMART_EXECUTION;
    public static int flushInterval = 25;
    public static int maxBatchBytes = 1460; // MTU Safety
    public static List<String> instantPackets = new ArrayList<>();
    public static int safety_margin = 256;

    // --- Optimization ---
    public static boolean optOffsets = true;
    public static boolean optExplosions = true;
    public static int optExplosionThreshold = 512;

    // --- Compression ---
    public static int compressionLevel = 4;
    public static int compressionThreshold = 256;

    // --- Compatibility ---
    public static boolean hybridMode = true;
    public static boolean emulateEvents = true;
    public static List<String> ignoredPackets = new ArrayList<>();

    // --- Mod ---
    public static boolean modHandshake = true;
    public static boolean modRequire = false;
    public static String modKickMessage = "<red>To play on this server, the <bold>Pulse Fabric</bold> mod is required!";

    // --- Metrics ---
    public static boolean metricsEnabled = true;
    public static int metricsUpdateInterval = 5;
    public static boolean metricNetwork = true;
    public static boolean metricCpu = true;
    public static boolean metricRam = true;

    public static String load() {
        try {
            if (!FILE.exists()) {
                createDefaultConfig();
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(FILE);

            // Core
            enabled = config.getBoolean("core.enabled", true);

            // Batching
            try {
                batchingMode = BatchingMode.valueOf(config.getString("batching.mode", "SMART_EXECUTION"));
            } catch (IllegalArgumentException e) {
                batchingMode = BatchingMode.SMART_EXECUTION;
            }
            flushInterval = config.getInt("batching.interval", 25);
            maxBatchBytes = config.getInt("batching.max-buffer-size-bytes", 1460);
            instantPackets = config.getStringList("batching.instant-packets");
            safety_margin = config.getInt("batching.safety-margin");

            // Optimization
            optOffsets = config.getBoolean("optimization.enable-offsets", true);
            optExplosions = config.getBoolean("optimization.explosions.enabled", true);
            optExplosionThreshold = config.getInt("optimization.explosions.block-change-threshold", 512);

            // Compression
            compressionLevel = config.getInt("compression.level", 4);
            compressionThreshold = config.getInt("compression.threshold", 256);

            // Compatibility
            hybridMode = config.getBoolean("compatibility.enable-hybrid-mode", true);
            emulateEvents = config.getBoolean("compatibility.emulate-events", true);
            ignoredPackets = config.getStringList("compatibility.ignored-packets");

            // Mod
            modHandshake = config.getBoolean("mod.allow-handshake", true);
            modRequire = config.getBoolean("mod.require-mod", false);
            modKickMessage = config.getString("mod.kick-message", modKickMessage);

            // Metrics
            metricsEnabled = config.getBoolean("metrics.enabled", true);
            metricsUpdateInterval = config.getInt("metrics.update-interval", 5);
            metricNetwork = config.getBoolean("metrics.modules.network", true);
            metricCpu = config.getBoolean("metrics.modules.cpu-estimation", true);
            metricRam = config.getBoolean("metrics.modules.memory-impact", true);

            return null;
        } catch (Exception e) {
            return "Pulse Config Error: " + e.getMessage();
        }
    }

    private static void createDefaultConfig() throws java.io.IOException {
        // Java Text Block - чисто и красиво
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
            
            # Packet batching (core Pulse feature)
            batching:
              # Flush mode:
              # SMART_EXECUTION – flush after plugin/script execution (recommended)
              # STRICT_TICK     – flush at end of each server tick (50ms)
              # INTERVAL        – flush every N milliseconds (Not fully implemented yet)
              mode: SMART_EXECUTION
            
              # Flush interval (ms), only for INTERVAL mode
              interval: 25
            
              # MTU safety limit.
              # Buffer is flushed immediately if this size is exceeded.
              max-buffer-size-bytes: 1460
            
              # Packets that bypass batching (critical for PvP)
              instant-packets:
                - ClientboundHurtAnimationPacket
                - ClientboundDamageEventPacket
                - ClientboundEntityEventPacket
                - ClientboundSetEntityMotionPacket
            
              # Pulse will flush the buffer BEFORE adding a new packet if remaining space is less than this.
              # Prevents MTU overflow without expensive per-packet size calculation.
              safety-margin: 256
            
            # Protocol-level optimizations
            optimization:
              # Delta compression for positions (Pulse clients only)
              enable-offsets: true
            
              # Smart explosion handling
              explosions:
                enabled: true
                # Re-send whole chunk if block changes exceed this value
                block-change-threshold: 512
            
            # Compression settings
            compression:
              # Zlib compression level (1 = fast, 9 = max compression)
              level: 4
            
              # Minimum batch size to apply compression
              threshold: 256
            
            # Compatibility & behavior
            compatibility:
              # Hybrid mode:
              # - Pulse mod → optimized Pulse packets
              # - Vanilla 1.19.4+ → Bundle packets
              # - Older versions → legacy packets
              enable-hybrid-mode: true
            
              # Emulate Bukkit/Paper packet events.
              # Required for ProtocolLib, AntiCheat, Denizen.
              emulate-events: true
            
              # Packets that Pulse will never batch or modify
              ignored-packets: []
            #    - ClientboundMapItemDataPacket
            
            # Client mod interaction
            mod:
              # Allow client-server Pulse handshake
              allow-handshake: true
            
              # Require Pulse mod to join
              require-mod: false
            
              kick-message: "<red>To play on this server, the <bold>Pulse Fabric</bold> mod is required!"
            
            metrics:
              enabled: true
              update-interval: 5
              modules:
                network: true
                cpu-estimation: true
                memory-impact: true
            """;
        Files.writeString(FILE.toPath(), template);
    }
}
