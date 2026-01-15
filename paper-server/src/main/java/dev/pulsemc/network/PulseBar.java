package dev.pulsemc.network;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PulseBar {
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final BossBar bossBar = BossBar.bossBar(
        Component.empty(),
        1.0f,
        BossBar.Color.GREEN,
        BossBar.Overlay.PROGRESS
    );

    private static final Set<UUID> viewers = new HashSet<>();
    // Native Java Timer
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void toggle(Player player) {
        if (viewers.contains(player.getUniqueId())) {
            viewers.remove(player.getUniqueId());
            player.hideBossBar(bossBar);
            player.sendMessage(mm.deserialize("<#ff2929>PulseMC: <grey>Metrics bar disabled."));
        } else {
            viewers.add(player.getUniqueId());
            player.showBossBar(bossBar);
            player.sendMessage(mm.deserialize("<green>PulseMC: <grey>Metrics bar enabled."));
        }
    }

    public static void start() {
        scheduler.scheduleAtFixedRate(() -> {
            if (viewers.isEmpty()) return;
            try {
                updateBar();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.SECONDS);
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
            "<bold><gradient:#FF005D:#FF0048>Pulse</gradient></bold> <dark_gray>| <white>Eff: <color:%s>%d%%</color> <dark_gray>| <white>Out: <aqua>%d p/s</aqua> <gray>(%s)",
            (efficiency > 0.75 ? "#55FF55" : (efficiency > 0.4 ? "#FFFF55" : "#FF5555")),
            (int)(efficiency * 100),
            (int) PulseMetrics.ppsPhysical,
            speedStr
        );

        // Update bar
        bossBar.name(mm.deserialize(title));
        bossBar.progress((float) efficiency);
        bossBar.color(color);
    }
}
