package edn.lakeopossmc.drivebysable.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlockEntity;
import edn.lakeopossmc.drivebysable.cable.LinkedGearboxLinks;
import edn.lakeopossmc.drivebysable.network.CableNetworkFullSyncPacket;
import edn.lakeopossmc.drivebysable.network.SourceHighlightPacket;
import edn.lakeopossmc.drivebysable.network.SourceHighlightPacket.HighlightEndpoint;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// --- WHAT EACH COMMAND DOES WITH A @target[transceiver] --- //
final class TransceiverCommands {

    private static final SimpleCommandExceptionType NO_MODULES = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.target.transceiver.no_modules"));
    private static final SimpleCommandExceptionType NO_CHANNELS = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.info.transceiver.no_channels"));

    private TransceiverCommands() {
    }

    private static List<BlockPos> resolve(
            final CommandContext<CommandSourceStack> context,
            final CableTarget target,
            final boolean modules
    ) throws CommandSyntaxException {
        // * A transceiver has no modules to name
        if (modules) {
            throw NO_MODULES.create();
        }
        return CableTargets.resolveTransceivers(context.getSource(), target);
    }

    // * Every other transceiver in the same network
    private static List<BlockPos> linkedTo(final ServerLevel level, final BlockPos pos) {
        final List<BlockPos> others = new ArrayList<>(LinkedGearboxLinks.linkGroup(level, pos).members());
        others.remove(pos);
        return others;
    }

    //#region // --- INFO --- //
    static int summarize(
            final CommandContext<CommandSourceStack> context,
            final CableTarget target,
            final boolean modules
    ) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final ServerLevel level = source.getLevel();
        final List<BlockPos> targets = resolve(context, target, modules);

        if (targets.size() == 1) {
            return send(context, single(source, level, targets.get(0)));
        }
        return send(context, many(source, level, targets));
    }

    // * The transceivers this one is linked to
    private static MutableComponent single(final CommandSourceStack source, final ServerLevel level, final BlockPos pos) {
        final List<BlockPos> others = linkedTo(level, pos);

        final MutableComponent message = Component.translatable(
                "commands.drivebysable.info.header",
                SourceText.transceiver(level, pos),
                SourceText.coordinates(level, pos)
        ).withStyle(ChatFormatting.GRAY);

        message.append("\n").append(Component.translatable(
                "commands.drivebysable.info.transceiver.counts",
                SourceText.transceiverNumber(others.size())
        ).withStyle(ChatFormatting.GRAY));

        final List<Component> lines = new ArrayList<>(others.size());
        for (final BlockPos other : others) {
            lines.add(Component.translatable(
                    "commands.drivebysable.info.transceiver.line",
                    SourceText.transceiverLink(level, other),
                    SourceText.coordinates(level, other)));
        }
        CableLists.append(source, message, lines);
        return message;
    }

    private static MutableComponent many(
            final CommandSourceStack source,
            final ServerLevel level,
            final List<BlockPos> targets
    ) {
        final MutableComponent message = Component.translatable(
                "commands.drivebysable.info.found.transceivers",
                SourceText.number(targets.size()),
                SourceText.dimension(level)
        ).withStyle(ChatFormatting.GRAY);
        message.append("\n").append(Component.translatable(
                "commands.drivebysable.info.click.transceiver").withStyle(ChatFormatting.GRAY));

        final Map<BlockPos, Integer> counts = linkCounts(level, targets);
        final List<Component> lines = new ArrayList<>(targets.size());
        for (final BlockPos pos : targets) {
            lines.add(Component.translatable(
                    "commands.drivebysable.info.entry.transceiver",
                    SourceText.transceiverLink(level, pos),
                    SourceText.coordinates(level, pos),
                    SourceText.transceiverNumber(counts.getOrDefault(pos, 0))));
        }
        CableLists.append(source, message, lines);
        return message;
    }

    private static Map<BlockPos, Integer> linkCounts(final ServerLevel level, final List<BlockPos> targets) {
        final Map<BlockPos, Integer> counts = new HashMap<>();
        for (final BlockPos pos : targets) {
            if (counts.containsKey(pos)) {
                continue;
            }
            final Set<BlockPos> members = LinkedGearboxLinks.linkGroup(level, pos).members();
            for (final BlockPos member : members) {
                counts.put(member, members.size() - 1);
            }
        }
        return counts;
    }

    static int getChannel() throws CommandSyntaxException {
        throw NO_CHANNELS.create();
    }

    static int getLevel(
            final CommandContext<CommandSourceStack> context,
            final CableTarget target,
            final boolean modules
    ) throws CommandSyntaxException {
        CableTargets.requireSingle(target);
        final ServerLevel level = context.getSource().getLevel();
        final BlockPos pos = resolve(context, target, modules).get(0);

        return send(context, Component.translatable(
                "commands.drivebysable.info.level",
                SourceText.transceiverLink(level, pos),
                SourceText.levelLabel(level, pos),
                SourceText.coordinates(level, pos),
                SourceText.dimension(level)
        ).withStyle(ChatFormatting.GRAY));
    }

    static int getType(
            final CommandContext<CommandSourceStack> context,
            final CableTarget target,
            final boolean modules
    ) throws CommandSyntaxException {
        CableTargets.requireSingle(target);
        final ServerLevel level = context.getSource().getLevel();
        final BlockPos pos = resolve(context, target, modules).get(0);

        return send(context, Component.translatable(
                "commands.drivebysable.info.type",
                SourceText.transceiverLink(level, pos),
                SourceText.coordinates(level, pos),
                SourceText.dimension(level),
                SourceText.transceiverType()
        ).withStyle(ChatFormatting.GRAY));
    }
    //#endregion

    //#region // --- HIGHLIGHT --- //
    static int highlight(
            final CommandContext<CommandSourceStack> context,
            final CableTarget target,
            final boolean modules,
            final int seconds,
            final boolean withConnected
    ) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player = source.getPlayerOrException();
        final ServerLevel level = source.getLevel();

        final List<BlockPos> resolved = resolve(context, target, modules);
        final List<BlockPos> targets = resolved.size() > HighlightCommand.MAX_ENDPOINTS
                ? resolved.subList(0, HighlightCommand.MAX_ENDPOINTS)
                : resolved;

        final List<HighlightEndpoint> sent = new ArrayList<>(targets.size());
        for (final BlockPos pos : targets) {
            sent.add(new HighlightEndpoint(pos, "", true));
        }

        final List<HighlightEndpoint> connected = new ArrayList<>();
        final List<Integer> owners = new ArrayList<>();
        if (withConnected) {
            collect:
            for (int index = 0; index < targets.size(); index++) {
                for (final BlockPos other : linkedTo(level, targets.get(index))) {
                    if (connected.size() >= HighlightCommand.MAX_LINKS) {
                        break collect;
                    }
                    connected.add(new HighlightEndpoint(other, "", true));
                    owners.add(index);
                }
            }
        }

        final boolean infinite = seconds == HighlightCommand.INFINITE;
        PacketDistributor.sendToPlayer(player, new SourceHighlightPacket(
                sent,
                connected,
                owners,
                infinite ? SourceHighlightPacket.INFINITE : seconds * 20
        ));

        final String suffix = (withConnected ? ".connected" : "") + (infinite ? ".infinite" : "");
        final MutableComponent message;
        if (targets.size() == 1) {
            message = Component.translatable(
                    "commands.drivebysable.highlight.single" + suffix,
                    SourceText.transceiverLink(level, targets.get(0)),
                    SourceText.coordinates(level, targets.get(0)),
                    SourceText.number(seconds)
            ).withStyle(ChatFormatting.GRAY);
        } else {
            message = Component.translatable(
                    "commands.drivebysable.highlight.many.transceivers" + suffix,
                    SourceText.number(targets.size()),
                    SourceText.number(seconds)
            ).withStyle(ChatFormatting.GRAY);

            final List<Component> lines = new ArrayList<>(targets.size());
            for (final BlockPos pos : targets) {
                lines.add(Component.translatable(
                        "commands.drivebysable.highlight.entry",
                        SourceText.transceiverLink(level, pos),
                        SourceText.coordinates(level, pos)));
            }
            CableLists.append(source, message, lines);
        }

        message.append("\n").append(Component.translatable(
                "commands.drivebysable.highlight.disable",
                SourceText.clickable(
                        Component.translatable("commands.drivebysable.here"),
                        "/dbs highlight clear",
                        Component.translatable("commands.drivebysable.highlight.here.hover"))
        ).withStyle(ChatFormatting.GRAY));

        final Component feedback = SourceText.message(message);
        source.sendSuccess(() -> feedback, false);
        return targets.size();
    }
    //#endregion

    //#region // --- REMOVE --- //
    static int remove(
            final CommandContext<CommandSourceStack> context,
            final CableTarget target,
            final boolean modules,
            final boolean proceed,
            final boolean withConnected
    ) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final ServerLevel level = source.getLevel();
        final List<BlockPos> targets = resolve(context, target, modules);

        final Map<BlockPos, Integer> counts = linkCounts(level, targets);
        // * Transceivers linked to a target that were not targeted themselves
        final Set<BlockPos> others = new LinkedHashSet<>();
        for (final BlockPos pos : targets) {
            others.addAll(linkedTo(level, pos));
        }
        targets.forEach(others::remove);

        if (!proceed) {
            final Component warning = warning(level, targets, counts, others.size(), target, withConnected);
            source.sendSuccess(() -> warning, false);
            return 0;
        }

        final Set<BlockPos> leaving = new LinkedHashSet<>(targets);
        if (withConnected) {
            leaving.addAll(others);
        }

        LinkedGearboxLinks.leaveNetwork(level, leaving);
        for (final BlockPos pos : leaving) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof final LinkedGearboxBlockEntity gearbox) {
                gearbox.clearLinkingFrequency();
                gearbox.refreshLinks();
            }
        }

        // * Whoever ran it sees the links go straight away
        if (source.getEntity() instanceof final ServerPlayer player) {
            CableNetworkFullSyncPacket.sendTo(player);
        }

        final Component feedback = feedback(source, level, targets, counts, others.size(), withConnected);
        source.sendSuccess(() -> feedback, true);
        return leaving.size();
    }

    private static Component feedback(
            final CommandSourceStack source,
            final ServerLevel level,
            final List<BlockPos> targets,
            final Map<BlockPos, Integer> counts,
            final int others,
            final boolean withConnected
    ) {
        final String suffix = withConnected ? ".connected" : "";

        if (targets.size() == 1) {
            final BlockPos pos = targets.get(0);
            return SourceText.message(Component.translatable(
                    "commands.drivebysable.remove.transceiver.single" + suffix,
                    SourceText.transceiver(level, pos),
                    SourceText.coordinates(level, pos),
                    SourceText.transceiverNumber(counts.getOrDefault(pos, 0))
            ).withStyle(ChatFormatting.GRAY));
        }

        final MutableComponent message = Component.translatable(
                "commands.drivebysable.remove.transceiver.many" + suffix,
                SourceText.transceiverNumber(targets.size()),
                SourceText.transceiverNumber(others)
        ).withStyle(ChatFormatting.GRAY);

        final List<Component> lines = new ArrayList<>(targets.size());
        for (final BlockPos pos : targets) {
            lines.add(Component.translatable(
                    "commands.drivebysable.remove.transceiver.entry",
                    SourceText.transceiver(level, pos),
                    SourceText.coordinates(level, pos),
                    SourceText.transceiverNumber(counts.getOrDefault(pos, 0))));
        }
        CableLists.append(source, message, lines);
        return SourceText.message(message);
    }

    private static Component warning(
            final ServerLevel level,
            final List<BlockPos> targets,
            final Map<BlockPos, Integer> counts,
            final int others,
            final CableTarget target,
            final boolean withConnected
    ) {
        final String suffix = withConnected ? ".connected" : "";

        final MutableComponent warning = Component.empty().append(Component
                .translatable("commands.drivebysable.remove.warning")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        final Component confirm = SourceText.clickable(
                Component.translatable("commands.drivebysable.here"),
                "/dbs remove " + target.asCommand() + " true" + (withConnected ? " true" : ""),
                Component.translatable("commands.drivebysable.remove.confirm.hover"));

        final MutableComponent body;
        if (targets.size() == 1) {
            final BlockPos pos = targets.get(0);
            body = Component.translatable(
                    "commands.drivebysable.remove.warn.transceiver.single" + suffix,
                    SourceText.transceiver(level, pos),
                    SourceText.coordinates(level, pos),
                    SourceText.transceiverNumber(counts.getOrDefault(pos, 0)),
                    confirm);
        } else {
            body = Component.translatable(
                    "commands.drivebysable.remove.warn.transceiver.many" + suffix,
                    SourceText.transceiverNumber(targets.size()),
                    SourceText.transceiverNumber(others),
                    confirm);
        }

        return SourceText.message(warning.append("\n").append(body.withStyle(ChatFormatting.RED)));
    }
    //#endregion

    private static int send(final CommandContext<CommandSourceStack> context, final MutableComponent message) {
        final Component feedback = SourceText.message(message);
        context.getSource().sendSuccess(() -> feedback, false);
        return 1;
    }
}