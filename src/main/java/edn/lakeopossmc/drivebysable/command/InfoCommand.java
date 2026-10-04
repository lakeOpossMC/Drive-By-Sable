package edn.lakeopossmc.drivebysable.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager.IncomingConnection;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// --- /dbs info <target> <@mod[name]> [summarize | getChannel[name] | getLevel | getType] --- //
// * A @target[transceiver] is handed to TransceiverCommands, which lists what it is linked to
public final class InfoCommand {

    private static final DynamicCommandExceptionType NO_SUCH_CHANNEL = new DynamicCommandExceptionType(
            channel -> Component.translatableEscape("commands.drivebysable.info.no_such_channel", channel));
    private static final SimpleCommandExceptionType MODULES_NOT_ALLOWED = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.info.no_modules_allowed"));

    private InfoCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        final LiteralArgumentBuilder<CommandSourceStack> info = Commands.literal("info")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS));

        info.then(views(Commands.argument("target", TargetArgument.target()), false));
        info.then(Commands.argument("target", TargetArgument.target())
                .then(views(Commands.argument("module", ModuleArgument.module()), true)));
        return info;
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T views(final T node, final boolean modules) {
        node.executes(context -> summarize(context, modules));
        node.then(Commands.literal("summarize").executes(context -> summarize(context, modules)));
        node.then(Commands.argument("channel", ChannelArgument.channel())
                .executes(context -> getChannel(context, modules)));
        node.then(Commands.literal("getLevel").executes(context -> getLevel(context, modules)));
        node.then(Commands.literal("getType").executes(context -> getType(context, modules)));
        return node;
    }

    private static CableTargets.Resolved resolve(
            final CommandContext<CommandSourceStack> context,
            final boolean modules
    ) throws CommandSyntaxException {
        return CableTargets.resolve(
                context.getSource(),
                TargetArgument.getTarget(context, "target"),
                modules ? ModuleArgument.getModule(context, "module") : null);
    }

    //#region // --- SUMMARIZE --- //
    private static int summarize(
            final CommandContext<CommandSourceStack> context,
            final boolean modules
    ) throws CommandSyntaxException {
        final CableTarget target = TargetArgument.getTarget(context, "target");
        if (target.side().transceivers()) {
            return TransceiverCommands.summarize(context, target, modules);
        }

        final CableTargets.Resolved resolved = resolve(context, modules);
        final List<CableEndpoint> endpoints = resolved.endpoints();

        if (endpoints.size() == 1) {
            return send(context, single(context.getSource(), resolved, endpoints.get(0)));
        }
        return send(context, many(context.getSource(), resolved));
    }

    private static MutableComponent single(
            final CommandSourceStack source,
            final CableTargets.Resolved resolved,
            final CableEndpoint endpoint
    ) {
        final ServerLevel level = resolved.level();
        final CableNetworkManager manager = resolved.manager();
        final boolean sending = endpoint.source();

        final MutableComponent message = endpoint.hasModule()
                ? Component.translatable(
                sending
                        ? "commands.drivebysable.info.module.header.source"
                        : "commands.drivebysable.info.module.header.output",
                SourceText.moduleName(endpoint),
                SourceText.coordinates(level, endpoint.pos()),
                SourceText.id(level, endpoint.pos(), sending))
                : Component.translatable(
                "commands.drivebysable.info.header",
                SourceText.id(level, endpoint.pos(), sending),
                SourceText.coordinates(level, endpoint.pos()));
        message.withStyle(ChatFormatting.GRAY);

        if (sending) {
            final Map<String, List<CableNetworkSink>> channels = CableChannels.sending(level, manager, endpoint);
            message.append("\n").append(Component.translatable(
                    "commands.drivebysable.info.counts.source",
                    SourceText.sourceNumber(channels.size()),
                    SourceText.outputNumber(CableChannels.outputCount(channels))
            ).withStyle(ChatFormatting.GRAY));

            final List<Component> lines = new ArrayList<>();
            for (final Map.Entry<String, List<CableNetworkSink>> entry : channels.entrySet()) {
                lines.add(Component.translatable(
                        "commands.drivebysable.info.channel.line.source",
                        channelLink(level, endpoint, entry.getKey(), true),
                        SourceText.outputNumber(distinctSinks(entry.getValue()))));
            }
            CableLists.append(source, message, lines);
            return message;
        }

        final Map<String, List<IncomingConnection>> receiving = CableChannels.receiving(level, manager, endpoint);
        message.append("\n").append(Component.translatable(
                "commands.drivebysable.info.counts.output",
                SourceText.outputNumber(receiving.size()),
                SourceText.sourceNumber(CableChannels.sourceCount(receiving))
        ).withStyle(ChatFormatting.GRAY));

        final List<Component> lines = new ArrayList<>();
        for (final Map.Entry<String, List<IncomingConnection>> entry : receiving.entrySet()) {
            lines.add(Component.translatable(
                    "commands.drivebysable.info.channel.line.output",
                    channelLink(level, endpoint, entry.getKey(), false),
                    SourceText.sourceNumber(CableChannels.sourceCount(entry.getValue()))));
        }
        CableLists.append(source, message, lines);
        return message;
    }

    private static MutableComponent many(final CommandSourceStack source, final CableTargets.Resolved resolved) {
        final ServerLevel level = resolved.level();
        final CableNetworkManager manager = resolved.manager();
        final List<CableEndpoint> endpoints = resolved.endpoints();

        boolean anySource = false;
        boolean anyOutput = false;
        for (final CableEndpoint endpoint : endpoints) {
            anySource |= endpoint.source();
            anyOutput |= !endpoint.source();
        }
        final boolean mixed = anySource && anyOutput;

        final MutableComponent message = Component.translatable(
                resolved.modules()
                        ? "commands.drivebysable.info.found.modules"
                        : (mixed
                        ? "commands.drivebysable.info.found.objects"
                        : (anySource
                        ? "commands.drivebysable.info.found.sources"
                        : "commands.drivebysable.info.found.outputs")),
                SourceText.number(endpoints.size()),
                SourceText.dimension(level)
        ).withStyle(ChatFormatting.GRAY);

        if (resolved.modules()) {
            message.append("\n").append(Component.translatable(
                    "commands.drivebysable.info.click.module").withStyle(ChatFormatting.GRAY));
        }
        message.append("\n").append(Component.translatable(
                mixed
                        ? "commands.drivebysable.info.click.object"
                        : (anySource
                        ? "commands.drivebysable.info.click.source"
                        : "commands.drivebysable.info.click.output")).withStyle(ChatFormatting.GRAY));

        final List<Component> lines = new ArrayList<>();
        for (int i = 0; i < endpoints.size(); i++) {
            final CableEndpoint endpoint = endpoints.get(i);
            final Component line = resolved.modules()
                    ? Component.translatable(
                    endpoint.source()
                            ? "commands.drivebysable.info.entry.module.source"
                            : "commands.drivebysable.info.entry.module.output",
                    SourceText.moduleLink(level, endpoint),
                    SourceText.idLink(level, endpoint.pos(), endpoint.source()))
                    : entryFor(level, manager, endpoint);

            lines.add(line);
        }
        CableLists.append(source, message, lines);
        return message;
    }

    private static Component entryFor(
            final ServerLevel level,
            final CableNetworkManager manager,
            final CableEndpoint endpoint
    ) {
        if (endpoint.source()) {
            final Map<String, List<CableNetworkSink>> sending = CableChannels.sending(level, manager, endpoint);
            return Component.translatable(
                    "commands.drivebysable.info.entry.source",
                    SourceText.idLink(level, endpoint.pos(), true),
                    SourceText.sourceNumber(sending.size()),
                    SourceText.outputNumber(CableChannels.outputCount(sending)));
        }

        final Map<String, List<IncomingConnection>> receiving = CableChannels.receiving(level, manager, endpoint);
        return Component.translatable(
                "commands.drivebysable.info.entry.output",
                SourceText.idLink(level, endpoint.pos(), false),
                SourceText.outputNumber(receiving.size()),
                SourceText.sourceNumber(CableChannels.sourceCount(receiving)));
    }
    //#endregion

    //#region // --- GET CHANNEL --- //
    private static int getChannel(
            final CommandContext<CommandSourceStack> context,
            final boolean modules
    ) throws CommandSyntaxException {
        final CableTarget target = TargetArgument.getTarget(context, "target");
        if (target.side().transceivers()) {
            return TransceiverCommands.getChannel();
        }
        CableTargets.requireSingle(target);

        final CableTargets.Resolved resolved = resolve(context, modules);
        final CableEndpoint endpoint = resolved.endpoints().get(0);
        final String name = ChannelArgument.getChannel(context, "channel");
        final ServerLevel level = resolved.level();
        final CableNetworkManager manager = resolved.manager();

        if (endpoint.source()) {
            final List<CableNetworkSink> sinks = CableChannels.sending(level, manager, endpoint).get(name);
            if (sinks == null) {
                throw NO_SUCH_CHANNEL.create(name);
            }
            return send(context, sourceChannel(context.getSource(), level, manager, endpoint, name, sinks));
        }

        final List<IncomingConnection> incoming = CableChannels.receiving(level, manager, endpoint).get(name);
        if (incoming == null) {
            throw NO_SUCH_CHANNEL.create(name);
        }
        return send(context, outputChannel(context.getSource(), level, manager, endpoint, name, incoming));
    }

    private static MutableComponent sourceChannel(
            final CommandSourceStack source,
            final ServerLevel level,
            final CableNetworkManager manager,
            final CableEndpoint endpoint,
            final String name,
            final List<CableNetworkSink> sinks
    ) {
        final MutableComponent message = (endpoint.hasModule()
                ? Component.translatable(
                "commands.drivebysable.info.channel.header.module.source",
                SourceText.channel(name),
                SourceText.moduleName(endpoint),
                SourceText.idLink(level, endpoint.pos(), true))
                : Component.translatable(
                "commands.drivebysable.info.channel.header.source",
                SourceText.channel(name),
                SourceText.idLink(level, endpoint.pos(), true))
        ).withStyle(ChatFormatting.GRAY);

        message.append("\n").append(Component.translatable(
                "commands.drivebysable.info.channel.counts.source",
                SourceText.outputNumber(distinctSinks(sinks)),
                SourceText.signal(manager.getSourceSignals(endpoint.pos()).getOrDefault(name, 0))
        ).withStyle(ChatFormatting.GRAY));

        final List<Component> lines = new ArrayList<>();
        for (int i = 0; i < sinks.size(); i++) {
            final CableNetworkSink sink = sinks.get(i);
            final BlockPos pos = sink.blockPos();

            lines.add(sink.isModule()
                    // * A face connection has no channel to name
                    ? Component.translatable(
                    "commands.drivebysable.info.channel.output.module",
                    SourceText.idLink(level, pos, false),
                    SourceText.coordinates(level, pos),
                    SourceText.outputChannel(sink.sinkChannel()))
                    : Component.translatable(
                    "commands.drivebysable.info.channel.output.face",
                    SourceText.idLink(level, pos, false),
                    SourceText.coordinates(level, pos),
                    SourceText.side(Direction.from3DDataValue(sink.direction()))));
        }
        CableLists.append(source, message, lines);
        return message;
    }

    private static MutableComponent outputChannel(
            final CommandSourceStack source,
            final ServerLevel level,
            final CableNetworkManager manager,
            final CableEndpoint endpoint,
            final String name,
            final List<IncomingConnection> incoming
    ) {
        final MutableComponent message = (endpoint.hasModule()
                ? Component.translatable(
                "commands.drivebysable.info.channel.header.module.output",
                SourceText.outputChannel(name),
                SourceText.moduleName(endpoint),
                SourceText.idLink(level, endpoint.pos(), false))
                : Component.translatable(
                "commands.drivebysable.info.channel.header.output",
                SourceText.outputChannel(name),
                SourceText.idLink(level, endpoint.pos(), false))
        ).withStyle(ChatFormatting.GRAY);

        final int signal = incoming.get(0).isModule()
                ? manager.getModuleSinkSignal(endpoint.pos(), name)
                : manager.getSignalAt(endpoint.pos(), incoming.get(0).direction());

        message.append("\n").append(Component.translatable(
                "commands.drivebysable.info.channel.counts.output",
                SourceText.sourceNumber(CableChannels.sourceCount(incoming)),
                SourceText.signal(signal)
        ).withStyle(ChatFormatting.GRAY));

        final List<Component> lines = new ArrayList<>();
        for (final IncomingConnection connection : incoming) {
            lines.add(Component.translatable(
                    "commands.drivebysable.info.channel.source.line",
                    SourceText.idLink(level, connection.source(), true),
                    SourceText.coordinates(level, connection.source()),
                    SourceText.signal(manager.getSourceSignals(connection.source())
                            .getOrDefault(connection.channel(), 0)),
                    SourceText.channel(connection.channel())));
        }
        CableLists.append(source, message, lines);
        return message;
    }
    //#endregion

    //#region // --- GET LEVEL AND TYPE --- //
    private static int getLevel(
            final CommandContext<CommandSourceStack> context,
            final boolean modules
    ) throws CommandSyntaxException {
        final CableTarget target = TargetArgument.getTarget(context, "target");
        if (target.side().transceivers()) {
            return TransceiverCommands.getLevel(context, target, modules);
        }

        CableTargets.requireSingle(target);
        final CableTargets.Resolved resolved = resolve(context, modules);
        final CableEndpoint endpoint = resolved.endpoints().get(0);
        final ServerLevel level = resolved.level();

        final Component message = endpoint.hasModule()
                ? Component.translatable(
                endpoint.source()
                        ? "commands.drivebysable.info.level.module.source"
                        : "commands.drivebysable.info.level.module.output",
                SourceText.moduleName(endpoint),
                SourceText.idLink(level, endpoint.pos(), endpoint.source()),
                SourceText.levelLabel(level, endpoint.pos()),
                SourceText.coordinates(level, endpoint.pos()),
                SourceText.dimension(level))
                : Component.translatable(
                "commands.drivebysable.info.level",
                SourceText.idLink(level, endpoint.pos(), endpoint.source()),
                SourceText.levelLabel(level, endpoint.pos()),
                SourceText.coordinates(level, endpoint.pos()),
                SourceText.dimension(level));

        return send(context, message.copy().withStyle(ChatFormatting.GRAY));
    }

    private static int getType(
            final CommandContext<CommandSourceStack> context,
            final boolean modules
    ) throws CommandSyntaxException {
        if (modules) {
            throw MODULES_NOT_ALLOWED.create();
        }

        final CableTarget target = TargetArgument.getTarget(context, "target");
        if (target.side().transceivers()) {
            return TransceiverCommands.getType(context, target, false);
        }

        CableTargets.requireSingle(target);
        final CableTargets.Resolved resolved = resolve(context, false);
        final CableEndpoint endpoint = resolved.endpoints().get(0);
        final ServerLevel level = resolved.level();

        return send(context, Component.translatable(
                "commands.drivebysable.info.type",
                SourceText.idLink(level, endpoint.pos(), endpoint.source()),
                SourceText.coordinates(level, endpoint.pos()),
                SourceText.dimension(level),
                SourceText.sourceType(level, endpoint.pos())
        ).withStyle(ChatFormatting.GRAY));
    }
    //#endregion

    private static Component channelLink(
            final ServerLevel level,
            final CableEndpoint endpoint,
            final String channel,
            final boolean source
    ) {
        return SourceText.channelLink(level, endpoint, channel, source);
    }

    private static int distinctSinks(final List<CableNetworkSink> sinks) {
        return (int) sinks.stream().mapToLong(CableNetworkSink::position).distinct().count();
    }

    private static int send(final CommandContext<CommandSourceStack> context, final MutableComponent message) {
        final Component feedback = SourceText.message(message);
        context.getSource().sendSuccess(() -> feedback, false);
        return 1;
    }
}