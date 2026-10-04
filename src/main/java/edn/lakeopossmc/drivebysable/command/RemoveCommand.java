package edn.lakeopossmc.drivebysable.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager.IncomingConnection;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// --- /dbs remove <target> <@mod[name]> [proceed without confirm] [connected] --- //
public final class RemoveCommand {

    private static final SimpleCommandExceptionType SIDE_REQUIRED = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.remove.side_required"));
    private static final SimpleCommandExceptionType CONNECTED_TRANSCEIVER_ONLY = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.remove.connected_transceiver_only"));

    private RemoveCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        final LiteralArgumentBuilder<CommandSourceStack> remove = Commands.literal("remove")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS));

        remove.then(confirmation(Commands.argument("target", TargetArgument.target()), false));
        remove.then(Commands.argument("target", TargetArgument.target())
                .then(confirmation(Commands.argument("module", ModuleArgument.module()), true)));
        return remove;
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T confirmation(
            final T node,
            final boolean modules
    ) {
        // * Without the flag the removal is only described
        node.executes(context -> run(context, modules, false, false));
        node.then(Commands.argument("proceed", BoolArgumentType.bool())
                .executes(context -> run(context, modules, BoolArgumentType.getBool(context, "proceed"), false))
                .then(Commands.argument("connected", BoolArgumentType.bool())
                        .executes(context -> run(
                                context,
                                modules,
                                BoolArgumentType.getBool(context, "proceed"),
                                BoolArgumentType.getBool(context, "connected")))));
        return node;
    }

    private static int run(
            final CommandContext<CommandSourceStack> context,
            final boolean modules,
            final boolean proceed,
            final boolean connected
    ) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final CableTarget target = TargetArgument.getTarget(context, "target");
        if (target.side() == CableTarget.Side.BOTH) {
            throw SIDE_REQUIRED.create();
        }

        if (target.side().transceivers()) {
            return TransceiverCommands.remove(context, target, modules, proceed, connected);
        }
        if (connected) {
            throw CONNECTED_TRANSCEIVER_ONLY.create();
        }

        final String module = modules ? ModuleArgument.getModule(context, "module") : null;

        final CableTargets.Resolved resolved = CableTargets.resolve(source, target, module);
        final ServerLevel level = resolved.level();
        final CableNetworkManager manager = resolved.manager();
        final List<CableEndpoint> endpoints = resolved.endpoints();

        final List<Integer> counts = new ArrayList<>(endpoints.size());
        final Set<BlockPos> touched = new LinkedHashSet<>();
        for (final CableEndpoint endpoint : endpoints) {
            final Set<BlockPos> others = otherEnds(level, manager, endpoint);
            counts.add(others.size());
            touched.addAll(others);
        }

        if (!proceed) {
            final Component warning = warning(level, endpoints, counts, touched.size(), target, module);
            source.sendSuccess(() -> warning, false);
            return 0;
        }

        for (final CableEndpoint endpoint : endpoints) {
            disconnect(level, manager, endpoint);
        }

        final Component feedback = feedback(source, level, endpoints, counts, touched.size());
        source.sendSuccess(() -> feedback, true);
        return endpoints.size();
    }

    //#region // --- THE REMOVAL ITSELF --- //
    private static void disconnect(
            final ServerLevel level,
            final CableNetworkManager manager,
            final CableEndpoint endpoint
    ) {
        // * Linked Gearbox links are never touched
        final boolean keepLinks = CableChannels.hasGearboxLinks(manager, endpoint.pos());

        if (endpoint.source()) {
            if (!endpoint.hasModule() && !keepLinks) {
                manager.removeAllFromSourceInternal(null, level, endpoint.pos());
                return;
            }
            for (final String channel : CableChannels.sending(level, manager, endpoint).keySet()) {
                manager.removeAllFromSourceChannelInternal(level, endpoint.pos(), channel);
            }
            return;
        }

        if (!endpoint.hasModule()) {
            if (!keepLinks) {
                manager.removeAllToSinkInternal(level, endpoint.pos());
                return;
            }
            for (final IncomingConnection incoming : CableChannels.incoming(manager, endpoint.pos())) {
                manager.removeConnectionInternal(level, incoming.source(), endpoint.pos(), incoming.direction(),
                        incoming.channel(), incoming.sinkChannel());
            }
            return;
        }
        for (final Map.Entry<String, List<IncomingConnection>> entry
                : CableChannels.receiving(level, manager, endpoint).entrySet()) {
            if (entry.getValue().get(0).isModule()) {
                manager.removeAllToModuleSinkInternal(level, endpoint.pos(), entry.getKey());
            }
        }
    }

    private static Set<BlockPos> otherEnds(
            final ServerLevel level,
            final CableNetworkManager manager,
            final CableEndpoint endpoint
    ) {
        final Set<BlockPos> others = new LinkedHashSet<>();

        if (endpoint.source()) {
            for (final List<CableNetworkSink> sinks : CableChannels.sending(level, manager, endpoint).values()) {
                for (final CableNetworkSink sink : sinks) {
                    others.add(sink.blockPos());
                }
            }
            return others;
        }

        for (final List<IncomingConnection> incoming : CableChannels.receiving(level, manager, endpoint).values()) {
            for (final IncomingConnection connection : incoming) {
                others.add(connection.source());
            }
        }
        return others;
    }
    //#endregion

    //#region // --- WHAT IT SAYS --- //
    private static Component feedback(
            final CommandSourceStack stack,
            final ServerLevel level,
            final List<CableEndpoint> endpoints,
            final List<Integer> counts,
            final int touched
    ) {
        final CableEndpoint first = endpoints.get(0);
        final boolean source = first.source();

        if (endpoints.size() == 1) {
            return SourceText.message((first.hasModule()
                    ? Component.translatable(
                    "commands.drivebysable.remove.single.module." + word(source),
                    SourceText.moduleName(first),
                    SourceText.coordinates(level, first.pos()),
                    SourceText.id(level, first.pos(), source),
                    counted(counts.get(0), source))
                    : Component.translatable(
                    "commands.drivebysable.remove.single." + word(source),
                    SourceText.id(level, first.pos(), source),
                    SourceText.coordinates(level, first.pos()),
                    counted(counts.get(0), source))
            ).withStyle(ChatFormatting.GRAY));
        }

        final MutableComponent message = Component.translatable(
                "commands.drivebysable.remove.many." + kind(first) + "." + word(source),
                counted(endpoints.size(), !source),
                counted(touched, source)
        ).withStyle(ChatFormatting.GRAY);

        final List<Component> lines = new ArrayList<>();
        for (int i = 0; i < endpoints.size(); i++) {
            final CableEndpoint endpoint = endpoints.get(i);
            final Component line = endpoint.hasModule()
                    ? Component.translatable(
                    "commands.drivebysable.remove.entry.module." + word(source),
                    SourceText.moduleName(endpoint),
                    SourceText.coordinates(level, endpoint.pos()),
                    SourceText.id(level, endpoint.pos(), source),
                    counted(counts.get(i), source))
                    : Component.translatable(
                    "commands.drivebysable.remove.entry." + word(source),
                    SourceText.id(level, endpoint.pos(), source),
                    SourceText.coordinates(level, endpoint.pos()),
                    counted(counts.get(i), source));

            lines.add(line);
        }
        CableLists.append(stack, message, lines);
        return SourceText.message(message);
    }

    private static Component warning(
            final ServerLevel level,
            final List<CableEndpoint> endpoints,
            final List<Integer> counts,
            final int touched,
            final CableTarget target,
            @Nullable final String module
    ) {
        final CableEndpoint first = endpoints.get(0);
        final boolean source = first.source();

        final MutableComponent warning = Component.empty().append(Component
                .translatable("commands.drivebysable.remove.warning")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        final Component confirm = SourceText.clickable(
                Component.translatable("commands.drivebysable.here"),
                "/dbs remove " + target.asCommand()
                        + (module == null ? "" : " @mod[" + module + "]") + " true",
                Component.translatable("commands.drivebysable.remove.confirm.hover"));

        final MutableComponent body;
        if (endpoints.size() == 1) {
            body = first.hasModule()
                    ? Component.translatable(
                    "commands.drivebysable.remove.warn.single.module." + word(source),
                    SourceText.moduleName(first),
                    SourceText.coordinates(level, first.pos()),
                    SourceText.id(level, first.pos(), source),
                    counted(counts.get(0), source),
                    confirm)
                    : Component.translatable(
                    "commands.drivebysable.remove.warn.single." + word(source),
                    SourceText.id(level, first.pos(), source),
                    SourceText.coordinates(level, first.pos()),
                    counted(counts.get(0), source),
                    confirm);
        } else {
            body = Component.translatable(
                    "commands.drivebysable.remove.warn.many." + kind(first) + "." + word(source),
                    counted(endpoints.size(), !source),
                    counted(touched, source),
                    confirm);
        }

        return SourceText.message(warning.append("\n").append(body.withStyle(ChatFormatting.RED)));
    }

    private static Component counted(final int count, final boolean outputs) {
        return outputs ? SourceText.outputNumber(count) : SourceText.sourceNumber(count);
    }

    private static String word(final boolean source) {
        return source ? "source" : "output";
    }

    private static String kind(final CableEndpoint endpoint) {
        return endpoint.hasModule() ? "modules" : "blocks";
    }
    //#endregion
}