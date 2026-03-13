package dev.pulsemc.pulse.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.pulsemc.pulse.ConfigManager;
import dev.pulsemc.pulse.metrics.extensions.MetricsBar;
import dev.pulsemc.pulse.metrics.Metrics;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.bukkit.command.CommandSender;

import java.util.concurrent.CompletableFuture;

public class PulseCommands {
    private static final String PREFIX = "<bold><gradient:#FF005D:#FF0048>Pulse</gradient></bold> <dark_gray>| ";
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("pulse")
                        .requires(stack -> stack.getBukkitSender().hasPermission("pulse.admin"))

                        .then(Commands.literal("reload")
                                .executes(PulseCommands::reload)
                        )

                        .then(Commands.literal("bar")
                                .executes(PulseCommands::toggleBar)
                        )

                        .then(Commands.literal("stats")
                                .executes(ctx -> sendStats(ctx, "all"))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(PulseCommands::suggestStatsTypes)
                                        .executes(ctx -> sendStats(ctx, StringArgumentType.getString(ctx, "type")))
                                )
                        )
        );
    }

    private static CompletableFuture<Suggestions> suggestStatsTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        builder.suggest("network", Component.literal("Network PPS and Bandwidth metrics"));
        builder.suggest("cpu", Component.literal("CPU optimization and Syscalls analysis"));
        builder.suggest("ram", Component.literal("Memory impact and GC pressure relief"));
        builder.suggest("all", Component.literal("Show everything at once"));
        return builder.buildFuture();
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getBukkitSender();

        boolean hasIssues = ConfigManager.load();

        MetricsBar.reload();
        dev.pulsemc.pulse.network.PulseBuffer.reload();

        if (!hasIssues) {
            sender.sendMessage(mm.deserialize(PREFIX + "<green>Configuration reloaded successfully!"));
        } else {
            sender.sendMessage(mm.deserialize(PREFIX + "<red>Configuration reloaded with ISSUES:"));
            sender.sendMessage(" ");

            for (net.kyori.adventure.text.Component line : ConfigManager.lastLoadReport) {
                sender.sendMessage(line);
            }

            sender.sendMessage(mm.deserialize("<grey>Safe default values were used for invalid settings."));
        }
        return 1;
    }

    private static int toggleBar(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer nmsPlayer) {
            MetricsBar.toggle(nmsPlayer.getBukkitEntity());
        } else {
            CommandSender sender = ctx.getSource().getBukkitSender();
            sender.sendMessage(mm.deserialize(PREFIX + "<red>Only for players!"));
        }
        return 1;
    }

    private static int sendStats(CommandContext<CommandSourceStack> ctx, String type) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        type = type.toLowerCase();

        if (type.equals("network") || type.equals("all")) {
            double efficiency = 100 - (Metrics.ppsPhysical / Math.max(1, Metrics.ppsLogical) * 100);
            sender.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse Network</white> ] ---"));
            sender.sendMessage(mm.deserialize(String.format("<grey>PPS (Logical):  <white>%d pkt/s <grey>(Vanilla)", (int) Metrics.ppsLogical)));
            sender.sendMessage(mm.deserialize(String.format("<grey>PPS (Physical): <white>%d pkt/s <#ff2929>(Pulse)", (int) Metrics.ppsPhysical)));
            sender.sendMessage(mm.deserialize(String.format("<grey>Calls Saved:    <white>%d/s <grey>(+%.1f%%)", (int) (Metrics.logicalCounter.get() - Metrics.physicalCounter.get()), efficiency)));
            sender.sendMessage(" ");
            sender.sendMessage(mm.deserialize(String.format("<grey>Bandwidth:      <green>%.2f<white> kB/s", Metrics.networkSpeedKbs)));
            sender.sendMessage(mm.deserialize(String.format("<grey>Optimized Chunks: <gold>%d <grey>(mass updates prevented)", Metrics.optimizedChunks.get())));
            sender.sendMessage(" ");
        }

        if (type.equals("cpu") || type.equals("all")) {
            double diff = Metrics.vanillaCpuEst - Metrics.cpuUsage;
            if (diff < 0) diff = 0;
            sender.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse CPU Analyzer</white> ] ---"));
            sender.sendMessage(mm.deserialize(String.format("<grey>Current Usage: %.2f%%", Metrics.cpuUsage)));
            sender.sendMessage(mm.deserialize(String.format("<grey>Vanilla Est:   %.2f%%", Metrics.vanillaCpuEst)));
            sender.sendMessage(" ");
            sender.sendMessage(mm.deserialize(String.format("<white>Pulse Efficiency: <#ff2929>-%.3f%% Total Load", diff)));
            sender.sendMessage(" ");
        }

        if (type.equals("ram") || type.equals("all")) {
            sender.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse Memory</white> ] ---"));
            sender.sendMessage(mm.deserialize(String.format("<grey>Saved Allocations: <white>%d MB <grey>(Total)", (Metrics.savedAllocationsBytes / 1024 / 1024))));
            sender.sendMessage(" ");
        }

        return 1;
    }

}