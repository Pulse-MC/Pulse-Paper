package dev.pulsemc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.pulsemc.config.ConfigManager;
import dev.pulsemc.network.PulseBar;
import dev.pulsemc.network.PulseMetrics;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.bukkit.command.CommandSender;

public class PulseCommands {
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pulse")
                .requires(stack -> stack.getBukkitSender().isOp())

                .then(Commands.literal("reload")
                    .executes(PulseCommands::reload)
                )

                .then(Commands.literal("bar")
                    .executes(PulseCommands::toggleBar)
                )

                .then(Commands.literal("stats")
                    .executes(ctx -> sendStats(ctx, "all"))

                    .then(Commands.literal("network")
                        .executes(ctx -> sendStats(ctx, "network"))
                    )
                    .then(Commands.literal("cpu")
                        .executes(ctx -> sendStats(ctx, "cpu"))
                    )
                    .then(Commands.literal("ram")
                        .executes(ctx -> sendStats(ctx, "ram"))
                    )
                )
        );
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        String err = ConfigManager.load();
        CommandSender sender = ctx.getSource().getBukkitSender();

        if (err != null) {
            sender.sendMessage(mm.deserialize("<#ff2929>Error: " + err));
        } else {
            sender.sendMessage(mm.deserialize("<green>PulseMC: Configuration reloaded!"));
        }
        return 1;
    }

    private static int toggleBar(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer nmsPlayer) {
            PulseBar.toggle(nmsPlayer.getBukkitEntity());
        } else {
            ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Only for players!"));
        }
        return 1;
    }

    private static int sendStats(CommandContext<CommandSourceStack> ctx, String type) {
        CommandSender sender = ctx.getSource().getBukkitSender();

        // --- NETWORK SECTION ---
        if (type.equals("network") || type.equals("all")) {
            double efficiency = 100 - (PulseMetrics.ppsPhysical / Math.max(1, PulseMetrics.ppsLogical) * 100);
            sender.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse Network</white> ] ---"));
            sender.sendMessage(mm.deserialize("<grey>PPS (Logical):  " + makeBar(PulseMetrics.ppsLogical / 2000) + " <white>" + (int)PulseMetrics.ppsLogical + " pkt/s <grey>(Vanilla)"));
            sender.sendMessage(mm.deserialize("<grey>PPS (Physical): " + makeBar(PulseMetrics.ppsPhysical / 2000) + " <white>" + (int)PulseMetrics.ppsPhysical + " pkt/s <#ff2929>(Pulse)"));
            sender.sendMessage(mm.deserialize("<grey>---------------------------"));
            sender.sendMessage(mm.deserialize("<grey>Calls Saved: <white>" + (int)(PulseMetrics.logicalCounter.get() - PulseMetrics.physicalCounter.get()) + "/s"));
            sender.sendMessage(mm.deserialize("<grey>Efficiency: <green>+" + String.format("%.1f", efficiency) + "%"));
            sender.sendMessage(mm.deserialize("<grey>---------------------------"));
            sender.sendMessage(mm.deserialize("<grey>Bundles Size: <green>" + String.format("%.2f", PulseMetrics.networkSpeedKbs) + "<white> kB/s"));
            sender.sendMessage(mm.deserialize("<grey>Chunk Optimizations: <gold>" + PulseMetrics.optimizedChunks.get() + " <grey>(mass updates prevented)"));
            sender.sendMessage(" ");
        }

        // --- CPU SECTION ---
        if (type.equals("cpu") || type.equals("all")) {
            double diff = PulseMetrics.vanillaCpuEst - PulseMetrics.cpuUsage;
            if (diff < 0) diff = 0;
            sender.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse CPU Analyzer</white> ] ---"));
            sender.sendMessage(mm.deserialize("<grey>Current Usage: " + makeBar(PulseMetrics.cpuUsage / 100) + " <white>" + String.format("%.2f", PulseMetrics.cpuUsage) + "%"));
            sender.sendMessage(mm.deserialize("<grey>Vanilla Est:   " + makeBar(PulseMetrics.vanillaCpuEst / 100) + " <white>" + String.format("%.2f", PulseMetrics.vanillaCpuEst) + "%"));
            sender.sendMessage(" ");
            sender.sendMessage(mm.deserialize("<white>Pulse Efficiency: <#ff2929>-" + String.format("%.3f", diff) + "% Total Load"));
            String statusText = (diff > 0.01) ? "CPU load reduced by " + String.format("%.1f", (diff/Math.max(0.1, PulseMetrics.vanillaCpuEst)*100)) + "%" : "Optimization idle";
            sender.sendMessage(mm.deserialize("<grey>Status: <italic>" + statusText));
            sender.sendMessage(" ");
        }

        // --- RAM SECTION ---
        if (type.equals("ram") || type.equals("all")) {
            sender.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse Memory</white> ] ---"));
            sender.sendMessage(mm.deserialize("<grey>Saved Allocations: <white>" + (PulseMetrics.savedAllocationsBytes / 1024 / 1024) + " MB <grey>(Total)"));
            sender.sendMessage(mm.deserialize("<grey>GC Pressure Relief: <green>HIGH"));
            sender.sendMessage(" ");
        }

        return 1;
    }

    private static String makeBar(double percent) {
        int size = 10;
        int filled = (int) (percent * size);
        filled = Math.min(size, Math.max(0, filled));
        return "<#ff2929>[" + "|".repeat(filled) + "<grey>" + ".".repeat(size - filled) + "<#ff2929>]</#ff2929>";
    }
}
