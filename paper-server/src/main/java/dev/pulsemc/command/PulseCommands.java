package dev.pulsemc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.pulsemc.config.ConfigManager;
import dev.pulsemc.config.PulseMessages;
import dev.pulsemc.network.PulseBar;
import dev.pulsemc.network.PulseMetrics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.bukkit.command.CommandSender;

import java.util.concurrent.CompletableFuture;

public class PulseCommands {

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
            sender.sendMessage(PulseMessages.get(PulseMessages.reloadSuccess));
        } else {
            sender.sendMessage(PulseMessages.get(PulseMessages.reloadError));
            sender.sendMessage(" ");

            for (net.kyori.adventure.text.Component line : ConfigManager.lastLoadReport) {
                sender.sendMessage(line);
            }

            sender.sendMessage(PulseMessages.getRaw(PulseMessages.reloadSafeDefault));
        }
        return 1;
    }

    private static int toggleBar(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer nmsPlayer) {
            PulseBar.toggle(nmsPlayer.getBukkitEntity());
        } else {
            CommandSender sender = ctx.getSource().getBukkitSender();
            sender.sendMessage(PulseMessages.get(PulseMessages.onlyPlayers));
        }
        return 1;
    }

    private static int sendStats(CommandContext<CommandSourceStack> ctx, String type) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        type = type.toLowerCase();

        if (type.equals("network") || type.equals("all")) {
            double efficiency = 100 - (PulseMetrics.ppsPhysical / Math.max(1, PulseMetrics.ppsLogical) * 100);
            sender.sendMessage(PulseMessages.getRaw(PulseMessages.statsNetworkHeader));
            sender.sendMessage(PulseMessages.getRawFormatted(PulseMessages.statsNetworkPpsLogical, (int) PulseMetrics.ppsLogical));
            sender.sendMessage(PulseMessages.getRawFormatted(PulseMessages.statsNetworkPpsPhysical, (int) PulseMetrics.ppsPhysical));
            sender.sendMessage(PulseMessages.getRawFormatted(PulseMessages.statsNetworkCallsSaved, (int) (PulseMetrics.logicalCounter.get() - PulseMetrics.physicalCounter.get()), efficiency));
            sender.sendMessage(" ");
            sender.sendMessage(PulseMessages.getRawFormatted(PulseMessages.statsNetworkBandwidth, PulseMetrics.networkSpeedKbs));
            sender.sendMessage(PulseMessages.getRawFormatted(PulseMessages.statsNetworkOptimizedChunks, PulseMetrics.optimizedChunks.get()));
            sender.sendMessage(" ");
        }

        if (type.equals("cpu") || type.equals("all")) {
            double diff = PulseMetrics.vanillaCpuEst - PulseMetrics.cpuUsage;
            if (diff < 0) diff = 0;
            sender.sendMessage(PulseMessages.getRaw(PulseMessages.statsCpuHeader));
            sender.sendMessage(PulseMessages.getRawFormatted(PulseMessages.statsCpuUsage, PulseMetrics.cpuUsage));
            sender.sendMessage(PulseMessages.getRawFormatted(PulseMessages.statsCpuVanillaEst, PulseMetrics.vanillaCpuEst));
            sender.sendMessage(" ");
            sender.sendMessage(PulseMessages.getRawFormatted(PulseMessages.statsCpuEfficiency, diff));
            sender.sendMessage(" ");
        }

        if (type.equals("ram") || type.equals("all")) {
            sender.sendMessage(PulseMessages.getRaw(PulseMessages.statsRamHeader));
            sender.sendMessage(PulseMessages.getRawFormatted(PulseMessages.statsRamSavedAllocations, (PulseMetrics.savedAllocationsBytes / 1024 / 1024)));
            sender.sendMessage(" ");
        }

        return 1;
    }

}
