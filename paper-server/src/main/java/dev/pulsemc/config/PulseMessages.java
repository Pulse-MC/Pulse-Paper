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
}
