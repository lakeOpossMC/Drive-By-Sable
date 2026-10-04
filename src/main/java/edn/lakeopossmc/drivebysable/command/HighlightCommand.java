package edn.lakeopossmc.drivebysable.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager.IncomingConnection;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import edn.lakeopossmc.drivebysable.network.SourceHighlightPacket;
import edn.lakeopossmc.drivebysable.network.SourceHighlightPacket.HighlightEndpoint;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// --- /dbs highlight <target> <@mod[name]> [seconds | infinite] [connected] --- //
public final class HighlightCommand {

    private static final int DEFAULT_SECONDS = 10;
    private static final int MAX_SECONDS = 300;
    static final int INFINITE = -1;

    static final int MAX_ENDPOINTS = 512;
    static final int MAX_LINKS = 4096;

    private HighlightCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        final LiteralArgumentBuilder<CommandSourceStack> highlight = Commands.literal("highlight")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS));

        highlight.then(Commands.literal("clear").executes(HighlightCommand::clear));

        highlight.then(times(Commands.argument("target", TargetArgument.target()), false));
        highlight.then(Commands.argument("target", TargetArgument.target())
                .then(times(Commands.argument("module", ModuleArgument.module()), true)));
        return highlight;
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T times(final T node, final boolean modules) {
        node.executes(context -> run(context, modules, DEFAULT_SECONDS, false));
        node.then(connected(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_SECONDS)),
                modules, context -> IntegerArgumentType.getInteger(context, "seconds")));
        node.then(connected(Commands.literal("infinite"), modules, context -> INFINITE));
        return node;
    }

    @FunctionalInterface
    private interface Seconds {
        int of(CommandContext<CommandSourceStack> context);
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T connected(
            final T node,
            final boolean modules,
            final Seconds seconds
    ) {
        node.executes(context -> run(context, modules, seconds.of(context), false));
        node.then(Commands.argument("connected", BoolArgumentType.bool())
                .executes(context -> run(
                        context, modules, seconds.of(context), BoolArgumentType.getBool(context, "connected"))));
        return node;
    }

    private static int run(
            final CommandContext<CommandSourceStack> context,
            final boolean modules,
            final int seconds,
            final boolean withConnected
    ) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();

        // * Transceiver networks are their own target type
        final CableTarget target = TargetArgument.getTarget(context, "target");
        if (target.side().transceivers()) {
            return TransceiverCommands.highlight(context, target, modules, seconds, withConnected);
        }

        final ServerPlayer player = source.getPlayerOrException();

        final CableTargets.Resolved resolved = CableTargets.resolve(
                source,
                target,
                modules ? ModuleArgument.getModule(context, "module") : null);

        final ServerLevel level = resolved.level();
        final CableNetworkManager manager = resolved.manager();
        final List<CableEndpoint> endpoints = panelsAsModules(level, manager, resolved.endpoints());

        final List<CableEndpoint> targets = endpoints.size() > MAX_ENDPOINTS
                ? endpoints.subList(0, MAX_ENDPOINTS)
                : endpoints;

        final List<HighlightEndpoint> connected = new ArrayList<>();
        final List<Integer> owners = new ArrayList<>();
        if (withConnected) {
            collectConnected(level, manager, targets, connected, owners);
        }

        PacketDistributor.sendToPlayer(player, new SourceHighlightPacket(
                forPacket(targets),
                connected,
                owners,
                seconds == INFINITE ? SourceHighlightPacket.INFINITE : seconds * 20
        ));

        final Component feedback = feedback(source, level, targets, seconds, withConnected);
        source.sendSuccess(() -> feedback, false);
        return targets.size();
    }

    private static List<CableEndpoint> panelsAsModules(
            final ServerLevel level,
            final CableNetworkManager manager,
            final List<CableEndpoint> endpoints
    ) {
        final List<CableEndpoint> expanded = new ArrayList<>(endpoints.size());
        for (final CableEndpoint endpoint : endpoints) {
            if (endpoint.hasModule() || SourceKind.of(level, endpoint.pos()) != SourceKind.PANEL) {
                expanded.add(endpoint);
                continue;
            }

            for (final CableEndpoint module : CableEndpoint.at(level, manager, endpoint.pos(), endpoint.source())) {
                if (module.hasModule()) {
                    expanded.add(module);
                }
            }
        }
        return expanded;
    }

    // * Whatever each target is wired to
    private static void collectConnected(
            final ServerLevel level,
            final CableNetworkManager manager,
            final List<CableEndpoint> targets,
            final List<HighlightEndpoint> connected,
            final List<Integer> owners
    ) {
        final Map<CableEndpoint, Integer> seen = new HashMap<>();

        for (int index = 0; index < targets.size(); index++) {
            final CableEndpoint target = targets.get(index);
            seen.put(target, index);

            if (target.source()) {
                for (final List<CableNetworkSink> sinks : CableChannels.sending(level, manager, target).values()) {
                    for (final CableNetworkSink sink : sinks) {
                        if (connected.size() >= MAX_LINKS) {
                            return;
                        }
                        connected.add(new HighlightEndpoint(sink.blockPos(), sink.sinkChannel(), false));
                        owners.add(index);
                    }
                }
                continue;
            }

            for (final List<IncomingConnection> incoming : CableChannels.receiving(level, manager, target).values()) {
                for (final IncomingConnection connection : incoming) {
                    if (connected.size() >= MAX_LINKS) {
                        return;
                    }
                    final String module = SourceModules.moduleForChannel(
                            level, connection.source(), connection.channel());
                    connected.add(new HighlightEndpoint(connection.source(), module, true));
                    owners.add(index);
                }
            }
        }
    }

    private static List<HighlightEndpoint> forPacket(final List<CableEndpoint> endpoints) {
        final List<HighlightEndpoint> sent = new ArrayList<>(endpoints.size());
        for (final CableEndpoint endpoint : endpoints) {
            sent.add(new HighlightEndpoint(endpoint.pos(), endpoint.module(), endpoint.source()));
        }
        return sent;
    }

    private static int clear(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(), SourceHighlightPacket.clear());
        context.getSource().sendSuccess(
                () -> SourceText.message(Component
                        .translatable("commands.drivebysable.highlight.cleared")
                        .withStyle(ChatFormatting.GRAY)),
                false
        );
        return 1;
    }

    //#region // --- WHAT IT SAYS --- //
    private static Component feedback(
            final CommandSourceStack source,
            final ServerLevel level,
            final List<CableEndpoint> targets,
            final int seconds,
            final boolean withConnected
    ) {
        final MutableComponent message = targets.size() == 1
                ? single(level, targets.get(0), seconds, withConnected)
                : many(source, level, targets, seconds, withConnected);

        message.append("\n").append(Component.translatable(
                "commands.drivebysable.highlight.disable",
                SourceText.clickable(
                        Component.translatable("commands.drivebysable.here"),
                        "/dbs highlight clear",
                        Component.translatable("commands.drivebysable.highlight.here.hover"))
        ).withStyle(ChatFormatting.GRAY));

        return SourceText.message(message);
    }

    private static MutableComponent single(
            final ServerLevel level,
            final CableEndpoint endpoint,
            final int seconds,
            final boolean withConnected
    ) {
        final String key = "commands.drivebysable.highlight.single"
                + (endpoint.hasModule() ? ".module" : "")
                + (withConnected ? ".connected" : "")
                + (seconds == INFINITE ? ".infinite" : "");

        if (endpoint.hasModule()) {
            return Component.translatable(
                    key,
                    SourceText.moduleName(endpoint),
                    SourceText.coordinates(level, endpoint.pos()),
                    sideWord(endpoint),
                    SourceText.idLink(level, endpoint.pos(), endpoint.source()),
                    SourceText.number(seconds)
            ).withStyle(ChatFormatting.GRAY);
        }

        return Component.translatable(
                key,
                SourceText.idLink(level, endpoint.pos(), endpoint.source()),
                SourceText.coordinates(level, endpoint.pos()),
                SourceText.number(seconds)
        ).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent many(
            final CommandSourceStack source,
            final ServerLevel level,
            final List<CableEndpoint> targets,
            final int seconds,
            final boolean withConnected
    ) {
        final boolean modules = targets.get(0).hasModule();

        boolean anySource = false;
        boolean anyOutput = false;
        for (final CableEndpoint endpoint : targets) {
            anySource |= endpoint.source();
            anyOutput |= !endpoint.source();
        }

        final String kind = modules
                ? "modules"
                : (anySource && anyOutput ? "objects" : (anySource ? "sources" : "outputs"));
        final String key = "commands.drivebysable.highlight.many." + kind
                + (withConnected ? ".connected" : "")
                + (seconds == INFINITE ? ".infinite" : "");

        final MutableComponent message = Component.translatable(
                key,
                SourceText.number(targets.size()),
                SourceText.number(seconds)
        ).withStyle(ChatFormatting.GRAY);

        final List<Component> lines = new ArrayList<>();
        for (final CableEndpoint endpoint : targets) {
            final Component line = endpoint.hasModule()
                    ? Component.translatable(
                    "commands.drivebysable.highlight.entry.module",
                    SourceText.moduleLink(level, endpoint),
                    SourceText.coordinates(level, endpoint.pos()),
                    sideWord(endpoint),
                    SourceText.idLink(level, endpoint.pos(), endpoint.source()))
                    : Component.translatable(
                    "commands.drivebysable.highlight.entry",
                    SourceText.idLink(level, endpoint.pos(), endpoint.source()),
                    SourceText.coordinates(level, endpoint.pos()));

            lines.add(line);
        }
        CableLists.append(source, message, lines);
        return message;
    }

    private static Component sideWord(final CableEndpoint endpoint) {
        return Component.translatable(endpoint.source()
                ? "commands.drivebysable.word.source"
                : "commands.drivebysable.word.output");
    }
    //#endregion
}