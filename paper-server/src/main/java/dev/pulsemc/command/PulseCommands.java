package dev.pulsemc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.pulsemc.config.ConfigManager;
import dev.pulsemc.network.PulseBar;
import dev.pulsemc.network.PulseMetrics;
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

        PulseBar.reload();
        dev.pulsemc.network.PulseBuffer.reload();

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
            PulseBar.toggle(nmsPlayer.getBukkitEntity());
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
            double efficiency = 100 - (PulseMetrics.ppsPhysical / Math.max(1, PulseMetrics.ppsLogical) * 100);
            sender.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse Network</white> ] ---"));
            sender.sendMessage(mm.deserialize(String.format("<grey>PPS (Logical):  <white>%d pkt/s <grey>(Vanilla)", (int) PulseMetrics.ppsLogical)));
            sender.sendMessage(mm.deserialize(String.format("<grey>PPS (Physical): <white>%d pkt/s <#ff2929>(Pulse)", (int) PulseMetrics.ppsPhysical)));
            sender.sendMessage(mm.deserialize(String.format("<grey>Calls Saved:    <white>%d/s <grey>(+%.1f%%)", (int) (PulseMetrics.logicalCounter.get() - PulseMetrics.physicalCounter.get()), efficiency)));
            sender.sendMessage(" ");
            sender.sendMessage(mm.deserialize(String.format("<grey>Bandwidth:      <green>%.2f<white> kB/s", PulseMetrics.networkSpeedKbs)));
            sender.sendMessage(mm.deserialize(String.format("<grey>Optimized Chunks: <gold>%d <grey>(mass updates prevented)", PulseMetrics.optimizedChunks.get())));
            sender.sendMessage(" ");
        }

        if (type.equals("cpu") || type.equals("all")) {
            double diff = PulseMetrics.vanillaCpuEst - PulseMetrics.cpuUsage;
            if (diff < 0) diff = 0;
            sender.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse CPU Analyzer</white> ] ---"));
            sender.sendMessage(mm.deserialize(String.format("<grey>Current Usage: %.2f%%", PulseMetrics.cpuUsage)));
            sender.sendMessage(mm.deserialize(String.format("<grey>Vanilla Est:   %.2f%%", PulseMetrics.vanillaCpuEst)));
            sender.sendMessage(" ");
            sender.sendMessage(mm.deserialize(String.format("<white>Pulse Efficiency: <#ff2929>-%.3f%% Total Load", diff)));
            sender.sendMessage(" ");
        }

        if (type.equals("ram") || type.equals("all")) {
            sender.sendMessage(mm.deserialize("<grey>--- [ <white>Pulse Memory</white> ] ---"));
            sender.sendMessage(mm.deserialize(String.format("<grey>Saved Allocations: <white>%d MB <grey>(Total)", (PulseMetrics.savedAllocationsBytes / 1024 / 1024))));
            sender.sendMessage(" ");
        }

        return 1;
    }

}
