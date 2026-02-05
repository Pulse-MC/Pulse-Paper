package dev.pulsemc.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class PulseMessages {
    private static final File FILE = new File("pulse_messages.yml");
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static String prefix = "<bold><gradient:#FF005D:#FF0048>Pulse</gradient></bold> <dark_gray>| ";
    public static String reloadSuccess = "<green>Configuration reloaded successfully!";
    public static String reloadError = "<red>Configuration reloaded with ISSUES:";
    public static String reloadSafeDefault = "<grey>Safe default values were used for invalid settings.";
    public static String onlyPlayers = "<red>Only for players!";
    public static String barEnabled = "<grey>Metrics bar <green>enabled<grey>.";
    public static String barDisabled = "<grey>Metrics bar <red>disabled<grey>.";
    public static String bossBarTitle = "<bold><gradient:#FF005D:#FF0048>Pulse</gradient></bold> <dark_gray>| <white>Eff: <color:%s>%d%%</color> <dark_gray>| <white>Vanilla: <aqua>%d p/s <dark_gray>| <white>Out: <aqua>%d p/s</aqua> <gray>(%s)";

    // Stats
    public static String statsNetworkHeader = "<grey>--- [ <white>Pulse Network</white> ] ---";
    public static String statsNetworkPpsLogical = "<grey>PPS (Logical):  <white>%d pkt/s <grey>(Vanilla)";
    public static String statsNetworkPpsPhysical = "<grey>PPS (Physical): <white>%d pkt/s <#ff2929>(Pulse)";
    public static String statsNetworkCallsSaved = "<grey>Calls Saved:    <white>%d/s <grey>(+%.1f%%)";
    public static String statsNetworkBandwidth = "<grey>Bandwidth:      <green>%.2f<white> kB/s";
    public static String statsNetworkOptimizedChunks = "<grey>Optimized Chunks: <gold>%d <grey>(mass updates prevented)";

    public static String statsCpuHeader = "<grey>--- [ <white>Pulse CPU Analyzer</white> ] ---";
    public static String statsCpuUsage = "<grey>Current Usage: %.2f%%";
    public static String statsCpuVanillaEst = "<grey>Vanilla Est:   %.2f%%";
    public static String statsCpuEfficiency = "<white>Pulse Efficiency: <#ff2929>-%.3f%% Total Load";

    public static String statsRamHeader = "<grey>--- [ <white>Pulse Memory</white> ] ---";
    public static String statsRamSavedAllocations = "<grey>Saved Allocations: <white>%d MB <grey>(Total)";

    public static void load() {
        if (!FILE.exists()) {
            try {
                createDefault();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(FILE);
        prefix = config.getString("prefix", prefix);
        reloadSuccess = config.getString("commands.reload.success", reloadSuccess);
        reloadError = config.getString("commands.reload.error", reloadError);
        reloadSafeDefault = config.getString("commands.reload.safe-default", reloadSafeDefault);
        onlyPlayers = config.getString("commands.generic.only-players", onlyPlayers);
        barEnabled = config.getString("commands.bar.enabled", barEnabled);
        barDisabled = config.getString("commands.bar.disabled", barDisabled);
        bossBarTitle = config.getString("visuals.bossbar.title", bossBarTitle);

        statsNetworkHeader = config.getString("commands.stats.network.header", statsNetworkHeader);
        statsNetworkPpsLogical = config.getString("commands.stats.network.pps-logical", statsNetworkPpsLogical);
        statsNetworkPpsPhysical = config.getString("commands.stats.network.pps-physical", statsNetworkPpsPhysical);
        statsNetworkCallsSaved = config.getString("commands.stats.network.calls-saved", statsNetworkCallsSaved);
        statsNetworkBandwidth = config.getString("commands.stats.network.bandwidth", statsNetworkBandwidth);
        statsNetworkOptimizedChunks = config.getString("commands.stats.network.optimized-chunks", statsNetworkOptimizedChunks);

        statsCpuHeader = config.getString("commands.stats.cpu.header", statsCpuHeader);
        statsCpuUsage = config.getString("commands.stats.cpu.usage", statsCpuUsage);
        statsCpuVanillaEst = config.getString("commands.stats.cpu.vanilla-est", statsCpuVanillaEst);
        statsCpuEfficiency = config.getString("commands.stats.cpu.efficiency", statsCpuEfficiency);

        statsRamHeader = config.getString("commands.stats.ram.header", statsRamHeader);
        statsRamSavedAllocations = config.getString("commands.stats.ram.saved-allocations", statsRamSavedAllocations);
    }

    private static void createDefault() throws IOException {
        String template = """
            prefix: "<bold><gradient:#FF005D:#FF0048>Pulse</gradient></bold> <dark_gray>| "
            
            commands:
              reload:
                success: "<green>Configuration reloaded successfully!"
                error: "<red>Configuration reloaded with ISSUES:"
                safe-default: "<grey>Safe default values were used for invalid settings."
              generic:
                only-players: "<red>Only for players!"
              bar:
                enabled: "<grey>Metrics bar <green>enabled<grey>."
                disabled: "<grey>Metrics bar <red>disabled<grey>."
              stats:
                network:
                  header: "<grey>--- [ <white>Pulse Network</white> ] ---"
                  pps-logical: "<grey>PPS (Logical):  <white>%d pkt/s <grey>(Vanilla)"
                  pps-physical: "<grey>PPS (Physical): <white>%d pkt/s <#ff2929>(Pulse)"
                  calls-saved: "<grey>Calls Saved:    <white>%d/s <grey>(+%.1f%%)"
                  bandwidth: "<grey>Bandwidth:      <green>%.2f<white> kB/s"
                  optimized-chunks: "<grey>Optimized Chunks: <gold>%d <grey>(mass updates prevented)"
                cpu:
                  header: "<grey>--- [ <white>Pulse CPU Analyzer</white> ] ---"
                  usage: "<grey>Current Usage: %.2f%%"
                  vanilla-est: "<grey>Vanilla Est:   %.2f%%"
                  efficiency: "<white>Pulse Efficiency: <#ff2929>-%.3f%% Total Load"
                ram:
                  header: "<grey>--- [ <white>Pulse Memory</white> ] ---"
                  saved-allocations: "<grey>Saved Allocations: <white>%d MB <grey>(Total)"
                
            visuals:
              bossbar:
                title: "<bold><gradient:#FF005D:#FF0048>Pulse</gradient></bold> <dark_gray>| <white>Eff: <color:%s>%d%%</color> <dark_gray>| <white>Vanilla: <aqua>%d p/s <dark_gray>| <white>Out: <aqua>%d p/s</aqua> <gray>(%s)"
            """;
        Files.writeString(FILE.toPath(), template);
    }

    public static Component get(String message) {
        return mm.deserialize(prefix + message);
    }

    public static Component getRaw(String message) {
        return mm.deserialize(message);
    }

    public static Component getFormatted(String message, Object... args) {
        return mm.deserialize(prefix + String.format(message, args));
    }

    public static Component getRawFormatted(String message, Object... args) {
        return mm.deserialize(String.format(message, args));
    }

}
