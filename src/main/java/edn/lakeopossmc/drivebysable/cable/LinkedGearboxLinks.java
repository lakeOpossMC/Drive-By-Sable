package edn.lakeopossmc.drivebysable.cable;

import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlock;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlockEntity;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

// --- LINKED GEARBOX CABLE LINKS --- //
public final class LinkedGearboxLinks {

    public static final String STRESS_CHANNEL = "stress";

    private LinkedGearboxLinks() {
    }

    public static boolean isGearbox(final Level level, final BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof LinkedGearboxBlock;
    }

    public static boolean isStressConnection(final Level level, final BlockPos source, final String channel, final String sinkChannel) {
        return STRESS_CHANNEL.equals(channel) || STRESS_CHANNEL.equals(sinkChannel) || isGearbox(level, source);
    }

    // * A stored connection that is really a gearbox link
    public static boolean isStressLink(final String channel, final String sinkChannel) {
        return STRESS_CHANNEL.equals(channel) && STRESS_CHANNEL.equals(sinkChannel);
    }

    //#region // --- LOOKUPS --- //
    // * Every gearbox directly cabled to this one
    public static Set<BlockPos> cableNeighbours(final Level level, final BlockPos pos) {
        final CableNetworkManager manager = CableNetworkManager.get(level);
        if (manager == null) {
            return Set.of();
        }

        final Set<BlockPos> neighbours = new LinkedHashSet<>();
        for (final CableNetworkSink sink : manager.getConnections(pos).getOrDefault(STRESS_CHANNEL, List.of())) {
            if (STRESS_CHANNEL.equals(sink.sinkChannel())) {
                neighbours.add(sink.blockPos());
            }
        }
        for (final CableNetworkManager.IncomingConnection incoming : manager.getIncoming(pos)) {
            if (STRESS_CHANNEL.equals(incoming.channel()) && STRESS_CHANNEL.equals(incoming.sinkChannel())) {
                neighbours.add(incoming.source());
            }
        }
        neighbours.remove(pos);
        return neighbours;
    }

    // * Stored direction of an existing link
    public static BlockPos storedSourceOf(final Level level, final BlockPos first, final BlockPos second) {
        final CableNetworkManager manager = CableNetworkManager.get(level);
        if (manager == null) {
            return null;
        }
        if (manager.containsConnection(first, second, Direction.UP, STRESS_CHANNEL, STRESS_CHANNEL)) {
            return first;
        }
        if (manager.containsConnection(second, first, Direction.UP, STRESS_CHANNEL, STRESS_CHANNEL)) {
            return second;
        }
        return null;
    }

    public static boolean areCabled(final Level level, final BlockPos first, final BlockPos second) {
        return storedSourceOf(level, first, second) != null;
    }

    public record LinkGroup(Set<BlockPos> members, List<BlockPos[]> links) {
    }

    public static Set<BlockPos> linkNeighbours(final Level level, final BlockPos pos) {
        final Set<BlockPos> neighbours = new LinkedHashSet<>(cableNeighbours(level, pos));
        if (level.getBlockEntity(pos) instanceof final LinkedGearboxBlockEntity gearbox) {
            neighbours.addAll(gearbox.getPartners());
        }
        neighbours.remove(pos);
        return neighbours;
    }

    public static Set<BlockPos> cableNetwork(final Level level, final BlockPos start) {
        final Set<BlockPos> members = new LinkedHashSet<>();
        final Deque<BlockPos> queue = new ArrayDeque<>();
        members.add(start.immutable());
        queue.add(start);
        while (!queue.isEmpty()) {
            for (final BlockPos neighbour : cableNeighbours(level, queue.poll())) {
                if (members.add(neighbour.immutable())) {
                    queue.add(neighbour);
                }
            }
        }
        return members;
    }

    public static LinkGroup linkGroup(final Level level, final BlockPos start) {
        final Set<BlockPos> members = new LinkedHashSet<>();
        final List<BlockPos[]> links = new ArrayList<>();
        final Deque<BlockPos> queue = new ArrayDeque<>();
        members.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            final BlockPos current = queue.poll();
            for (final BlockPos neighbour : linkNeighbours(level, current)) {
                if (isGearbox(level, neighbour) && members.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }

        final List<BlockPos> remaining = new ArrayList<>(members);
        remaining.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
        final List<BlockPos> loop = new ArrayList<>();
        loop.add(remaining.removeFirst());
        while (!remaining.isEmpty()) {
            final BlockPos last = loop.getLast();
            BlockPos next = remaining.getFirst();
            double nextDistance = Double.MAX_VALUE;
            for (final BlockPos candidate : remaining) {
                final double distance = CableNetworkManager.worldSpaceDistanceSqr(level, last, candidate);
                if (distance < nextDistance) {
                    nextDistance = distance;
                    next = candidate;
                }
            }
            remaining.remove(next);
            loop.add(next);
        }
        for (int i = 0; i + 1 < loop.size(); i++) {
            links.add(new BlockPos[]{loop.get(i), loop.get(i + 1)});
        }
        if (loop.size() > 2) {
            links.add(new BlockPos[]{loop.getLast(), loop.getFirst()});
        }
        return new LinkGroup(members, links);
    }
    //#endregion

    public static BlockPos joinPoint(final Level level, final Set<BlockPos> network, final BlockPos newcomer) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (final BlockPos member : network) {
            if (member.equals(newcomer) || !checkPlacement(level, member, newcomer).isSuccess()) {
                continue;
            }
            final double distance = CableNetworkManager.worldSpaceDistanceSqr(level, member, newcomer);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = member;
            }
        }
        return best;
    }

    // * Why a gearbox cant join
    public static CableNetworkManager.ConnectionResult joinRefusal(final Level level, final Set<BlockPos> network, final BlockPos newcomer) {
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (final BlockPos member : network) {
            final double distance = CableNetworkManager.worldSpaceDistanceSqr(level, member, newcomer);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = member;
            }
        }
        return closest == null ? CableNetworkManager.ConnectionResult.OK : checkPlacement(level, closest, newcomer);
    }

    //#region // --- LEAVING A NETWORK --- //
    // * Server only
    public static int leaveNetwork(final Level level, final BlockPos leaving) {
        final BlockPos pos = leaving.immutable();
        final int refund = leaveNetwork(level, Set.of(pos));
        if (!level.isClientSide && level.isLoaded(pos) && isGearbox(level, pos)) {
            refreshLinks(level, Set.of(pos));
        }
        return refund;
    }

    public static int leaveNetwork(final Level level, final Collection<BlockPos> leaving) {
        final CableNetworkManager manager = CableNetworkManager.get(level);
        if (manager == null || level.isClientSide) {
            return 0;
        }

        final Set<BlockPos> gone = new LinkedHashSet<>();
        for (final BlockPos pos : leaving) {
            gone.add(pos.immutable());
        }

        int refund = 0;
        final Set<BlockPos> handled = new HashSet<>();
        for (final BlockPos pos : gone) {
            if (handled.contains(pos)) {
                continue;
            }
            final Set<BlockPos> remaining = cableNetwork(level, pos);
            final Set<BlockPos> leavingHere = new LinkedHashSet<>(remaining);
            leavingHere.retainAll(gone);
            handled.addAll(leavingHere);
            remaining.removeAll(leavingHere);
            refund += leaveOneNetwork(level, leavingHere, remaining);
        }
        return refund;
    }

    public static void leaveNetworkWhere(final Level level, final Predicate<BlockPos> isLeaving) {
        final CableNetworkManager manager = CableNetworkManager.get(level);
        if (manager == null || level.isClientSide) {
            return;
        }

        final Set<BlockPos> leaving = new LinkedHashSet<>();
        manager.sourcesWithSinks().forEach((source, perChannel) -> {
            for (final CableNetworkSink sink : perChannel.getOrDefault(STRESS_CHANNEL, Set.of())) {
                if (!STRESS_CHANNEL.equals(sink.sinkChannel())) {
                    continue;
                }
                if (isLeaving.test(source)) {
                    leaving.add(source);
                }
                if (isLeaving.test(sink.blockPos())) {
                    leaving.add(sink.blockPos());
                }
            }
        });
        if (!leaving.isEmpty()) {
            leaveNetwork(level, leaving);
        }
    }

    private static void refreshLinks(final Level level, final Collection<BlockPos> gearboxes) {
        for (final BlockPos pos : gearboxes) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof final LinkedGearboxBlockEntity gearbox) {
                gearbox.refreshLinks();
            }
        }
    }

    private static int leaveOneNetwork(final Level level, final Set<BlockPos> leavingHere, final Set<BlockPos> remaining) {
        int removed = 0;
        for (final BlockPos leaving : leavingHere) {
            for (final BlockPos neighbour : cableNeighbours(level, leaving)) {
                final BlockPos source = storedSourceOf(level, leaving, neighbour);
                if (source == null) {
                    continue;
                }
                final BlockPos sink = source.equals(leaving) ? neighbour : leaving;
                if (CableNetworkManager.removeConnection(level, source, sink, Direction.UP, STRESS_CHANNEL, STRESS_CHANNEL)) {
                    removed++;
                }
            }
        }

        int added = 0;
        final List<Set<BlockPos>> pieces = new ArrayList<>();
        final Set<BlockPos> placed = new HashSet<>();
        for (final BlockPos member : remaining) {
            if (placed.add(member)) {
                final Set<BlockPos> piece = cableNetwork(level, member);
                piece.retainAll(remaining);
                placed.addAll(piece);
                pieces.add(piece);
            }
        }

        final Set<List<BlockPos>> refused = new HashSet<>();
        while (pieces.size() > 1) {
            final Set<BlockPos> growing = pieces.getFirst();
            BlockPos bestFrom = null;
            BlockPos bestTo = null;
            int bestPiece = -1;
            double bestDistance = Double.MAX_VALUE;

            for (int i = 1; i < pieces.size(); i++) {
                for (final BlockPos from : growing) {
                    for (final BlockPos to : pieces.get(i)) {
                        if (refused.contains(List.of(from, to))
                                || !isGearbox(level, from) || !isGearbox(level, to)
                                || !checkPlacement(level, from, to).isSuccess()
                                || CableNetworkManager.checkRange(level, from, to) != CableNetworkManager.RangeResult.OK) {
                            continue;
                        }
                        final double distance = CableNetworkManager.worldSpaceDistanceSqr(level, from, to);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestFrom = from;
                            bestTo = to;
                            bestPiece = i;
                        }
                    }
                }
            }

            if (bestPiece < 0) {
                pieces.removeFirst();
                continue;
            }
            if (!CableNetworkManager.createConnection(level, bestFrom, bestTo, Direction.UP, STRESS_CHANNEL, STRESS_CHANNEL)
                    .isSuccess()) {
                refused.add(List.of(bestFrom, bestTo));
                continue;
            }
            added++;
            growing.addAll(pieces.remove(bestPiece));
        }

        refreshLinks(level, remaining);
        return removed - added;
    }
    //#endregion

    //#region // --- RULES --- //
    // * Sublevel and range rules for one pair
    public static CableNetworkManager.ConnectionResult checkPlacement(final Level level, final BlockPos first, final BlockPos second) {
        if (CableConfig.CONFIG.linkedGearboxSubLevelOnly.get()
                && (!CableNetworkManager.isInSubLevel(level, first) || !CableNetworkManager.isInSubLevel(level, second))) {
            return CableNetworkManager.ConnectionResult.FAIL_GEARBOX_SUBLEVEL_ONLY;
        }

        final long range = CableConfig.CONFIG.linkedGearboxRange.get();
        if (CableNetworkManager.worldSpaceDistanceSqr(level, first, second) > (double) range * range) {
            return CableNetworkManager.ConnectionResult.FAIL_GEARBOX_OUT_OF_RANGE;
        }
        return CableNetworkManager.ConnectionResult.OK;
    }

    public static CableNetworkManager.ConnectionResult validateLink(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final String channel,
            final String sinkChannel
    ) {
        if (!isGearbox(level, source) || !STRESS_CHANNEL.equals(channel)) {
            return CableNetworkManager.ConnectionResult.FAIL_INVALID_CHANNEL;
        }

        if (!isGearbox(level, sinkPos) || !STRESS_CHANNEL.equals(sinkChannel)) {
            return CableNetworkManager.ConnectionResult.FAIL_GEARBOX_TARGET_REQUIRED;
        }

        if (areCabled(level, source, sinkPos)) {
            return CableNetworkManager.ConnectionResult.FAIL_EXISTS;
        }

        return checkPlacement(level, source, sinkPos);
    }
    //#endregion
}