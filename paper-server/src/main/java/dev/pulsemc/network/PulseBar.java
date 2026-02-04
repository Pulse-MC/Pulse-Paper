package dev.pulsemc.network;

import dev.pulsemc.config.ConfigManager;
import dev.pulsemc.config.PulseMessages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PulseBar {
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final BossBar bossBar = BossBar.bossBar(
        Component.empty(),
        1.0f,
        BossBar.Color.GREEN,
        BossBar.Overlay.NOTCHED_20
    );

    private static final Set<UUID> viewers = new HashSet<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static ScheduledFuture<?> currentTask;

    public static void toggle(Player player) {
        if (viewers.contains(player.getUniqueId())) {
            viewers.remove(player.getUniqueId());
            player.hideBossBar(bossBar);
            player.sendMessage(PulseMessages.get(PulseMessages.barDisabled));
        } else {
            viewers.add(player.getUniqueId());
            player.showBossBar(bossBar);
            player.sendMessage(PulseMessages.get(PulseMessages.barEnabled));
        }
    }

    public static void reload() {
        start();
    }

    public static void start() {
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel(false);
        }

        int interval = Math.max(1, ConfigManager.metricsUpdateInterval);

        currentTask = scheduler.scheduleAtFixedRate(() -> {
            if (viewers.isEmpty()) return;
            try {
                updateBar();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1, interval, TimeUnit.SECONDS);
    }

    private static void updateBar() {
        double logical = Math.max(1, PulseMetrics.ppsLogical);
        double efficiency = (logical - PulseMetrics.ppsPhysical) / logical;

        if (efficiency < 0) efficiency = 0;
        if (efficiency > 1) efficiency = 1;

        BossBar.Color color;
        if (efficiency > 0.75) color = BossBar.Color.GREEN;
        else if (efficiency > 0.40) color = BossBar.Color.YELLOW;
        else color = BossBar.Color.RED;

        double speed = PulseMetrics.networkSpeedKbs;
        String speedStr;
        if (speed > 1024) {
            speedStr = String.format("%.2f MB/s", speed / 1024.0);
        } else {
            speedStr = String.format("%.1f KB/s", speed);
        }

        String title = String.format(
            "<bold><gradient:#FF005D:#FF0048>Pulse</gradient></bold> <dark_gray>| <white>Eff: <color:%s>%d%%</color> <dark_gray>| <white>Vanilla: <aqua>%d p/s <dark_gray>| <white>Out: <aqua>%d p/s</aqua> <gray>(%s)",
            (efficiency > 0.75 ? "#55FF55" : (efficiency > 0.4 ? "#FFFF55" : "#FF5555")),
            (int)(efficiency * 100),
            (int) PulseMetrics.ppsLogical,
            (int) PulseMetrics.ppsPhysical,
            speedStr
        );

        bossBar.name(mm.deserialize(title));
        bossBar.progress((float) efficiency);
        bossBar.color(color);
    }
}
