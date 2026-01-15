package dev.pulsemc.command;

import dev.pulsemc.config.ConfigManager;
import dev.pulsemc.network.PulseMetrics;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class PulseCommand implements CommandExecutor, TabCompleter {
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("pulse.admin")) {
            sender.sendMessage(mm.deserialize("<#ff2929>You don't have permission!</#ff2929>"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(mm.deserialize("<#ff2929>Pulse <grey>v1.0 <grey>| <white>Usage: <aqua>/pulse <reload|stats>"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            String err = ConfigManager.load();
            sender.sendMessage(err == null ? mm.deserialize("<green>Pulse: Configuration reloaded successfully!") : mm.deserialize("<#ff2929>Error: " + err));
            return true;
        }

        if (sub.equals("bar")) {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage(mm.deserialize("<red>Only for players!"));
                return true;
            }
            dev.pulsemc.network.PulseBar.toggle(player);
            return true;
        }

        if (sub.equals("stats")) {
            if (args.length == 1) {
                sendNetworkStats(sender);
                sendCpuStats(sender);
                sendRamStats(sender);
                return true;
            }

            String type = args[1].toLowerCase();
            switch (type) {
                case "network" -> sendNetworkStats(sender);
                case "cpu" -> sendCpuStats(sender);
                case "ram" -> sendRamStats(sender);
                default -> sender.sendMessage(mm.deserialize("<#ff2929>Unknown metrics type! <white>(network, cpu, ram)"));
            }
            return true;
        }

        return true;
    }
    // TODO: Redesign message
    private void sendNetworkStats(CommandSender s) {
        double efficiency = 100 - (PulseMetrics.ppsPhysical / Math.max(1, PulseMetrics.ppsLogical) * 100);
        s.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse Network</white> ] ---"));
        s.sendMessage(mm.deserialize("<grey>PPS (Logical):  " + makeBar(PulseMetrics.ppsLogical / 2000) + " <white>" + (int)PulseMetrics.ppsLogical + " pkt/s <grey>(Vanilla)"));
        s.sendMessage(mm.deserialize("<grey>PPS (Physical): " + makeBar(PulseMetrics.ppsPhysical / 2000) + " <white>" + (int)PulseMetrics.ppsPhysical + " pkt/s <#ff2929>(Pulse)"));
        s.sendMessage(mm.deserialize("<grey>---------------------------"));
        s.sendMessage(mm.deserialize("<grey>Calls Saved: <white>" + (int)(PulseMetrics.ppsLogical - PulseMetrics.ppsPhysical) + "/s"));
        s.sendMessage(mm.deserialize("<grey>Efficiency: <green>+" + String.format("%.1f", efficiency) + "%"));
        s.sendMessage(mm.deserialize("<grey>---------------------------"));
        s.sendMessage(mm.deserialize("<grey>Bundles Size: <green>" + PulseMetrics.networkSpeedKbs + "<white> kB/s"));
        s.sendMessage(mm.deserialize("<grey>Chunk Optimizations: <gold>" + PulseMetrics.optimizedChunks.get() + " <grey>(mass updates prevented)"));
        s.sendMessage(" ");
        // s.sendMessage(mm.deserialize("<grey>Flush reasons:"));
        // s.sendMessage(mm.deserialize("<grey> - Limits (MTU/Count): <white>" + PulseMetrics.flushReasonLimit.get()));
        // s.sendMessage(mm.deserialize("<grey> - End of tick (50ms): <white>" + PulseMetrics.flushReasonTick.get()));
        // s.sendMessage(mm.deserialize("<grey> - Instant / Listeners: <#ff2929>" + PulseMetrics.flushReasonInstant.get()));
    }
    // TODO: Redesign message
    private void sendCpuStats(CommandSender s) {
        double diff = PulseMetrics.vanillaCpuEst - PulseMetrics.cpuUsage;
        if (diff < 0) diff = 0;
        s.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse CPU Analyzer</white> ] ---"));
        s.sendMessage(mm.deserialize("<grey>Current Usage: " + makeBar(PulseMetrics.cpuUsage / 100) + " <white>" + String.format("%.2f", PulseMetrics.cpuUsage) + "%"));
        s.sendMessage(mm.deserialize("<grey>Vanilla Est:   " + makeBar(PulseMetrics.vanillaCpuEst / 100) + " <white>" + String.format("%.2f", PulseMetrics.vanillaCpuEst) + "%"));
        s.sendMessage(" ");
        s.sendMessage(mm.deserialize("<white>Pulse Efficiency: <#ff2929>-" + String.format("%.3f", diff) + "% Total Load"));
        String statusText = (diff > 0.01) ? "CPU load reduced by " + String.format("%.1f", (diff/Math.max(0.1, PulseMetrics.vanillaCpuEst)*100)) + "% легче" : "Optimization idle";
        s.sendMessage(mm.deserialize("<grey>Status: <italic>" + statusText));
        s.sendMessage(" ");
    }
    // TODO: Redesign message
    private void sendRamStats(CommandSender s) {
        s.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse Memory</white> ] ---"));
        s.sendMessage(mm.deserialize("<grey>Saved Allocations: <white>" + (PulseMetrics.savedAllocationsBytes / 1024 / 1024) + " MB <grey>(Total)"));
        s.sendMessage(mm.deserialize("<grey>GC Pressure Relief: <green>HIGH"));
        s.sendMessage(" ");
    }

    private String makeBar(double percent) {
        int size = 10;
        int filled = (int) (percent * size);
        filled = Math.min(size, Math.max(0, filled));
        return "<#ff2929>[" + "|".repeat(filled) + "<grey>" + ".".repeat(size - filled) + "<#ff2929>]</#ff2929>";
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if(!sender.isOp()) return null;
        if (args.length == 1) {
            completions.add("reload");
            completions.add("stats");
            completions.add("bar");
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            completions.add("network");
            completions.add("cpu");
            completions.add("ram");
        }

        return completions;
    }
}
