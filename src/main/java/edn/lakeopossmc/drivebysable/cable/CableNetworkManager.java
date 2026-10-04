package edn.lakeopossmc.drivebysable.cable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.SubTargetCableEndpoint;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.MultiChannelCableBusBlockEntity;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import edn.lakeopossmc.drivebysable.legacy.LegacyTypewriterCompat;
import edn.lakeopossmc.drivebysable.legacy.LegacyWireCompat;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.InputKey;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.ModuleSinkKey;
import edn.lakeopossmc.drivebysable.util.BlockFace;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

// --- CORE MANAGER FOR THE CABLE NETWORK --- //
// * One instance per level, server is authoritative, client keeps a mirror
// * Handles connections, signals, sublevel moves, and schematic backup
public final class CableNetworkManager {
    public static final String WORLD_CHANNEL = "world";
    private static final String CONNECTIONS_KEY = "Connections";
    private static final String SOURCE_KEY = "Source";
    private static final String SINK_KEY = "Sink";
    private static final String SOURCE_OWNER_KEY = "SourceOwnerSubLevel";

    // * Which module on the source block owns this connection
    private static final String SOURCE_MODULE_KEY = "SourceModule";
    private static final String SINK_OWNER_KEY = "SinkOwnerSubLevel";
    private static final String DIRECTION_KEY = "Direction";
    private static final String CHANNEL_KEY = "Channel";
    private static final String SINK_CHANNEL_KEY = "SinkChannel";
    private static final String FACING_KEY = "Facing";
    private static final String UNSUPPORTED_CONNECTIONS_KEY = "UnsupportedConnections";
    private static final String SNAPSHOT_VERSION_KEY = "SnapshotVersion";
    private static final String OWNER_SUB_LEVEL_KEY = "OwnerSubLevel";
    private static final String PLACEMENT_RESOLVED_KEY = "PlacementResolved";
    private static final int RELATIVE_SNAPSHOT_VERSION = 2;
    private static final int OWNER_AWARE_SNAPSHOT_VERSION = 3;
    // * Endpoints held as offsets in the drive's own space, measured through world
    // * space. Survives a paste without needing any sublevel to be identified
    private static final int WORLD_SPACE_SNAPSHOT_VERSION = 4;
    // * Where the drive stood when it captured. A drive reading a world space
    // * snapshot from somewhere else is a pasted copy, which is the only time
    // * binding is wanted
    private static final String SAVED_DRIVE_POS_KEY = "SavedDrivePos";
    // * Which level an endpoint sat on at capture, relative to the drive
    private static final String SOURCE_LEVEL_KEY = "SourceLevel";
    private static final String SINK_LEVEL_KEY = "SinkLevel";
    private static final byte ENDPOINT_ON_DRIVE_LEVEL = 0;
    private static final byte ENDPOINT_ON_OTHER_SUB_LEVEL = 1;
    private static final byte ENDPOINT_IN_WORLD = 2;
    // * The unrounded drive space position and block id of each endpoint
    private static final String SOURCE_EXACT_KEY = "SourceExact";
    private static final String SINK_EXACT_KEY = "SinkExact";
    private static final String SOURCE_BLOCK_KEY = "SourceBlock";
    private static final String SINK_BLOCK_KEY = "SinkBlock";
    // * How far around the expected point a cross level endpoint is looked for
    private static final int CROSS_LEVEL_SEARCH_RADIUS = 1;
    // * How long a paste may sit without any new endpoint appearing before binding
    private static final long BIND_STALL_TICKS = 100L;
    private static final WorldAttached<CableNetworkManager> CLIENT_MANAGERS = new WorldAttached<>(level -> new CableNetworkManager(() -> {}));

    private final Map<Long, Map<String, Set<CableNetworkSink>>> sinks = new HashMap<>();
    private final Map<Long, Set<SinkReference>> sinkReferences = new HashMap<>();
    private final Map<Long, Map<String, Integer>> sourceValues = new HashMap<>();
    private final Map<BlockFace, CableNetworkNode> nodes = new HashMap<>();
    // * Module sinks are addressed by name rather than by face
    private final Map<ModuleSinkKey, CableNetworkNode> moduleNodes = new HashMap<>();
    private final Set<BlockFace> staleFaces = new HashSet<>();
    private final Set<Long> pendingAssemblyPositions = new HashSet<>();
    private final Runnable dirtyMarker;
    private boolean attachedToLevel;
    private boolean graphDirty;

    CableNetworkManager(final Runnable dirtyMarker) {
        this.dirtyMarker = dirtyMarker;
    }

    // * Server uses saved data, client uses a per level mirror
    public static CableNetworkManager get(final Level level) {
        if (level instanceof final ServerLevel serverLevel) {
            return CableNetworkSavedData.get(serverLevel);
        }

        return CLIENT_MANAGERS.get(level);
    }

    //#region // --- STATIC ENTRY POINTS --- //
    // * Thin wrappers so callers dont need a manager instance
    public static ConnectionResult createConnection(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel
    ) {
        return createConnection(level, source, sinkPos, sinkDirection, channel, CableNetworkSink.BLOCK_FACE);
    }

    public static ConnectionResult createConnection(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel,
            final String sinkChannel
    ) {
        return get(level).addConnection(level, source, sinkPos, sinkDirection, channel, sinkChannel);
    }

    public static boolean hasConnection(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel
    ) {
        return get(level).containsConnection(source, sinkPos, sinkDirection, channel);
    }

    public static boolean hasConnection(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel,
            final String sinkChannel
    ) {
        return get(level).containsConnection(source, sinkPos, sinkDirection, channel, sinkChannel);
    }

    public static boolean removeConnection(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel
    ) {
        return removeConnection(level, source, sinkPos, sinkDirection, channel, CableNetworkSink.BLOCK_FACE);
    }

    public static boolean removeConnection(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel,
            final String sinkChannel
    ) {
        return get(level).removeConnectionInternal(level, source, sinkPos, sinkDirection, channel, sinkChannel);
    }

    // * Drop every connection feeding one module channel, used when a module is deleted
    public static boolean removeAllToModuleSink(final Level level, final BlockPos sinkPos, final String sinkChannel) {
        return get(level).removeAllToModuleSinkInternal(level, sinkPos, sinkChannel);
    }

    // * Follow a module through a rename without dropping its wiring
    public static boolean remapModuleSink(
            final Level level,
            final BlockPos sinkPos,
            final String oldSinkChannel,
            final String newSinkChannel
    ) {
        return get(level).remapModuleSinkInternal(level, sinkPos, oldSinkChannel, newSinkChannel);
    }

    public static boolean removeAllFromSource(final ServerPlayer serverPlayer, final Level level, final BlockPos source) {
        return get(level).removeAllFromSourceInternal(serverPlayer, level, source);
    }

    // * Everything wired to one sub target of a block
    public static boolean removeAllForSubTarget(
            final ServerPlayer serverPlayer,
            final Level level,
            final BlockPos pos,
            final String subTarget
    ) {
        return get(level).removeAllForSubTargetInternal(serverPlayer, level, pos, subTarget);
    }

    // * Does this sub target have anything wired to it, either end?
    public static boolean hasConnectionsForSubTarget(final Level level, final BlockPos pos, final String subTarget) {
        return get(level).hasConnectionsForSubTargetInternal(level, pos, subTarget);
    }

    public static boolean removeAllFromSourceChannel(final Level level, final BlockPos source, final String channel) {
        return get(level).removeAllFromSourceChannelInternal(level, source, channel);
    }

    public static boolean remapSourceChannel(final Level level, final BlockPos source, final String oldChannel, final String newChannel) {
        return get(level).remapSourceChannelInternal(level, source, oldChannel, newChannel);
    }

    public static void trySetSignalAt(final Level level, final BlockPos source, final String channel, final int value) {
        get(level).setSource(level, source, channel, value);
    }

    // * Remaps both origin and destination manager if a sublevel move crosses levels
    public static void handleAssemblyMove(
            final ServerLevel originLevel,
            final ServerLevel resultingLevel,
            final BlockPos oldPos,
            final SubLevelAssemblyHelper.AssemblyTransform transform
    ) {
        final CableNetworkManager originManager = get(originLevel);
        originManager.remapMovedBlockInternal(oldPos, transform);
        originManager.pendingAssemblyPositions.remove(oldPos.asLong());

        if (resultingLevel != originLevel) {
            final CableNetworkManager resultingManager = get(resultingLevel);
            if (resultingManager != originManager) {
                resultingManager.remapMovedBlockInternal(oldPos, transform);
                resultingManager.pendingAssemblyPositions.remove(oldPos.asLong());
            }
        }
    }

    // * Marks positions about to be pulled into a sublevel
    public static void markPendingAssembly(final ServerLevel level, final Iterable<BlockPos> positions) {
        final CableNetworkManager manager = get(level);
        for (final BlockPos pos : positions) {
            manager.pendingAssemblyPositions.add(pos.asLong());
        }
    }

    public static boolean isPendingAssembly(final Level level, final BlockPos pos) {
        return get(level).pendingAssemblyPositions.contains(pos.asLong());
    }
    //#endregion
    //#region // --- CONNECTION ADD AND REMOVE --- //
    // * Validates block distance, channel, and source/sink caps before linking
    public ConnectionResult addConnection(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel
    ) {
        return addConnection(level, source, sinkPos, sinkDirection, channel, CableNetworkSink.BLOCK_FACE);
    }

    public ConnectionResult addConnection(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel,
            final String sinkChannel
    ) {
        final CableNetworkSink sink = CableNetworkSink.of(sinkPos, sinkDirection, sinkChannel);

        // * A panel can legitimately feed one of its own modules from another
        if (source.equals(sinkPos) && (!sink.isModule() || channel.equals(sink.sinkChannel()))) {
            return ConnectionResult.FAIL_SAME_BLOCK;
        }

        // * Linked Gearbox stress links have their own rules
        if (LinkedGearboxLinks.isStressConnection(level, source, channel, sink.sinkChannel())) {
            final ConnectionResult gearboxResult = LinkedGearboxLinks.validateLink(level, source, sinkPos, channel, sink.sinkChannel());
            if (!gearboxResult.isSuccess()) {
                return gearboxResult;
            }
        }

        if (!isValidChannel(level, source, channel)) {
            return ConnectionResult.FAIL_INVALID_CHANNEL;
        }

        if (!isValidSinkChannel(level, sinkPos, sink.sinkChannel())) {
            return ConnectionResult.FAIL_INVALID_SINK_CHANNEL;
        }

        final RangeResult range = checkRange(level, source, sinkPos);
        if (range == RangeResult.CROSS_LEVEL) {
            return ConnectionResult.FAIL_CROSS_LEVEL;
        }
        if (range == RangeResult.OUT_OF_RANGE) {
            return ConnectionResult.FAIL_OUT_OF_RANGE;
        }

        final long sourceKey = source.asLong();
        if (exceedsSourceLimit(level, source)) {
            return ConnectionResult.FAIL_TOO_MANY_SOURCES;
        }

        if (exceedsSinkLimit(source, channel)) {
            return ConnectionResult.FAIL_TOO_MANY_SINKS;
        }

        final Set<CableNetworkSink> sinksOnChannel = getOrCreateSinksOnChannel(source, channel);

        if (!sinksOnChannel.add(sink)) {
            return ConnectionResult.FAIL_EXISTS;
        }

        addSinkReference(sourceKey, channel, sink);
        dirtyMarker.run();
        applySignalToSink(level, sourceKey, channel, sink, getCurrentSignal(level, source, channel));
        return ConnectionResult.OK;
    }

    // * Multi channel sources must offer the channel, otherwise only world channel is valid
    private boolean isValidChannel(final Level level, final BlockPos source, final String channel) {
        final Block sourceBlock = level.getBlockState(source).getBlock();
        if (sourceBlock instanceof final MultiChannelCableSource multiChannelSource) {
            return multiChannelSource.cable$getChannels(level, source).contains(channel);
        }
        return WORLD_CHANNEL.equals(channel);
    }

    // * The source cap is counted per domain
    public static String sourceLimitLangKey(final Level level, final BlockPos source) {
        return isLooseInWorld(level, source)
                ? "drivebysable.invalid_op.too_many_sources_world"
                : "drivebysable.invalid_op.too_many_sources_sublevel";
    }

    // * The world and each sublevel carry separate source budgets
    private static boolean isLooseInWorld(final Level level, final BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos) == null;
    }

    private static int sourceLimitFor(final Level level, final BlockPos source) {
        return isLooseInWorld(level, source)
                ? CableConfig.CONFIG.maxSourcesInWorld.get()
                : CableConfig.CONFIG.maxSourcesPerSubLevel.get();
    }

    // * Would adding a brand new source here push this domain over the limit?
    public static boolean wouldExceedSourceLimit(final Level level, final BlockPos source) {
        final CableNetworkManager manager = get(level);
        return manager != null && manager.exceedsSourceLimit(level, source);
    }

    // * Instance form so addConnection does not bounce back through get(level)
    private boolean exceedsSourceLimit(final Level level, final BlockPos source) {
        // * Linked gearbox links have no direction
        if (sinks.containsKey(source.asLong()) || LinkedGearboxLinks.isGearbox(level, source)) {
            return false;
        }
        return countSourcesInSameDomain(level, source) >= sourceLimitFor(level, source);
    }

    // * The cap is per source AND channel
    public static boolean wouldExceedSinkLimit(final Level level, final BlockPos source, final String channel) {
        final CableNetworkManager manager = get(level);
        return manager != null && manager.exceedsSinkLimit(source, channel);
    }

    private boolean exceedsSinkLimit(final BlockPos source, final String channel) {
        // * Gearbox links are only limited by range
        if (LinkedGearboxLinks.STRESS_CHANNEL.equals(channel)) {
            return false;
        }
        final Set<CableNetworkSink> sinksOnChannel = sinks
                .getOrDefault(source.asLong(), Map.of())
                .get(channel);
        return sinksOnChannel != null && sinksOnChannel.size() >= CableConfig.CONFIG.maxOutputsPerChannel.get();
    }

    // * Outcome of the configured range limit for one connection
    public enum RangeResult {
        OK,
        OUT_OF_RANGE,
        CROSS_LEVEL;

        public boolean blocked() {
            return this != OK;
        }
    }

    //#region // --- RANGE AND CROSS-LEVEL LIMITS --- //
    public static RangeResult checkRange(final Level level, final BlockPos source, final BlockPos sinkPos) {
        final boolean enforceRange = CableConfig.CONFIG.rangeLimitEnforced.get();
        final boolean forbidCrossLevel = CableConfig.CONFIG.forbidCrossLevelConnections.get();

        // * Neither gate is on, so nothing needs resolving
        if (!enforceRange && !forbidCrossLevel) {
            return RangeResult.OK;
        }

        final SubLevel sourceSubLevel = Sable.HELPER.getContaining(level, source);
        final SubLevel sinkSubLevel = Sable.HELPER.getContaining(level, sinkPos);
        final boolean sameContext = isSameSubLevelContext(sourceSubLevel, sinkSubLevel);

        if (forbidCrossLevel && !sameContext) {
            return RangeResult.CROSS_LEVEL;
        }

        if (!enforceRange) {
            return RangeResult.OK;
        }

        final double distanceSqr = sameContext
                ? source.distSqr(sinkPos)
                : toWorldSpace(source, sourceSubLevel).distanceToSqr(toWorldSpace(sinkPos, sinkSubLevel));

        final long limit = CableConfig.CONFIG.rangeLimit.get();
        return distanceSqr > (double) limit * limit ? RangeResult.OUT_OF_RANGE : RangeResult.OK;
    }

    // * Straight line distance where both blocks appear in the world
    public static double worldSpaceDistanceSqr(final Level level, final BlockPos first, final BlockPos second) {
        final SubLevel firstSubLevel = Sable.HELPER.getContaining(level, first);
        final SubLevel secondSubLevel = Sable.HELPER.getContaining(level, second);
        if (isSameSubLevelContext(firstSubLevel, secondSubLevel)) {
            return first.distSqr(second);
        }
        return toWorldSpace(first, firstSubLevel).distanceToSqr(toWorldSpace(second, secondSubLevel));
    }

    public static boolean isInSubLevel(final Level level, final BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos) != null;
    }

    private static Vec3 toWorldSpace(final BlockPos pos, final SubLevel subLevel) {
        final Vec3 centre = Vec3.atCenterOf(pos);
        return subLevel == null ? centre : subLevel.logicalPose().transformPosition(centre);
    }

    // * Both in the world, or both in the same sublevel
    private static boolean isSameSubLevelContext(final SubLevel first, final SubLevel second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        return Objects.equals(first.getUniqueId(), second.getUniqueId());
    }
    //#endregion

    // * Empty means a plain block face, which any block can be
    private boolean isValidSinkChannel(final Level level, final BlockPos sinkPos, final String sinkChannel) {
        if (sinkChannel.isEmpty()) {
            return true;
        }

        if (LinkedGearboxLinks.STRESS_CHANNEL.equals(sinkChannel) && LinkedGearboxLinks.isGearbox(level, sinkPos)) {
            return true;
        }

        return level.getBlockState(sinkPos).getBlock() instanceof final ModuleSinkTarget target
                && target.cable$getSinkChannels(level, sinkPos).contains(sinkChannel);
    }

    public boolean containsConnection(
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel
    ) {
        return containsConnection(source, sinkPos, sinkDirection, channel, CableNetworkSink.BLOCK_FACE);
    }

    public boolean containsConnection(
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel,
            final String sinkChannel
    ) {
        return sinks.getOrDefault(source.asLong(), Map.of())
                .getOrDefault(channel, Set.of())
                .contains(CableNetworkSink.of(sinkPos, sinkDirection, sinkChannel));
    }

    public boolean removeConnectionInternal(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel
    ) {
        return removeConnectionInternal(level, source, sinkPos, sinkDirection, channel, CableNetworkSink.BLOCK_FACE);
    }

    public boolean removeConnectionInternal(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel,
            final String sinkChannel
    ) {
        final long sourceKey = source.asLong();
        final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(sourceKey);
        if (perChannel == null) {
            return false;
        }

        final Set<CableNetworkSink> sinksOnChannel = perChannel.get(channel);
        if (sinksOnChannel == null) {
            return false;
        }

        final CableNetworkSink sink = CableNetworkSink.of(sinkPos, sinkDirection, sinkChannel);
        if (!sinksOnChannel.remove(sink)) {
            return false;
        }

        removeSinkReference(sourceKey, channel, sink);
        applySignalToSink(level, sourceKey, channel, sink, 0);

        if (sinksOnChannel.isEmpty()) {
            perChannel.remove(channel);
        }
        if (perChannel.isEmpty()) {
            sinks.remove(sourceKey);
        }

        dirtyMarker.run();
        return true;
    }
    //#endregion

    //#region // --- BULK REMOVE AND REMAP --- //
    // * Refunds cables to player if given, used by block break and cutter
    public boolean removeAllForSubTargetInternal(
            final ServerPlayer serverPlayer,
            final Level level,
            final BlockPos pos,
            final String subTarget
    ) {
        if (subTarget == null || subTarget.isEmpty()) {
            return removeAllFromSourceInternal(serverPlayer, level, pos);
        }

        if (!(level.getBlockState(pos).getBlock() instanceof final SubTargetCableEndpoint endpoint)) {
            return removeAllFromSourceInternal(serverPlayer, level, pos);
        }

        final long sourceKey = pos.asLong();
        boolean changed = false;

        // * Source side
        final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(sourceKey);
        if (perChannel != null) {
            for (final String channel : Set.copyOf(perChannel.keySet())) {
                if (!subTarget.equals(endpoint.cable$subTargetForChannel(level, pos, channel))) {
                    continue;
                }

                final Set<CableNetworkSink> sinksOnChannel = perChannel.remove(channel);
                if (sinksOnChannel == null) {
                    continue;
                }

                for (final CableNetworkSink sink : sinksOnChannel) {
                    refundCable(serverPlayer);
                    removeSinkReference(sourceKey, channel, sink);
                    applySignalToSink(level, sourceKey, channel, sink, 0);
                    changed = true;
                }

                final Map<String, Integer> values = sourceValues.get(sourceKey);
                if (values != null) {
                    values.remove(channel);
                    if (values.isEmpty()) {
                        sourceValues.remove(sourceKey);
                    }
                }
            }

            if (perChannel.isEmpty()) {
                sinks.remove(sourceKey);
            }
        }

        // * Sink side, anything feeding a module channel on this sub target
        final Set<SinkReference> references = sinkReferences.get(sourceKey);
        if (references != null) {
            for (final SinkReference reference : Set.copyOf(references)) {
                final String sinkChannel = reference.sinkChannel();
                if (sinkChannel.isEmpty()
                        || !subTarget.equals(endpoint.cable$subTargetForChannel(level, pos, sinkChannel))) {
                    continue;
                }

                final Map<String, Set<CableNetworkSink>> feederChannels = sinks.get(reference.sourcePos());
                if (feederChannels == null) {
                    continue;
                }

                final Set<CableNetworkSink> feederSinks = feederChannels.get(reference.channel());
                if (feederSinks == null) {
                    continue;
                }

                final CableNetworkSink sink = new CableNetworkSink(sourceKey, reference.direction(), sinkChannel);
                if (!feederSinks.remove(sink)) {
                    continue;
                }

                refundCable(serverPlayer);
                removeSinkReference(reference.sourcePos(), reference.channel(), sink);
                applySignalToSink(level, reference.sourcePos(), reference.channel(), sink, 0);
                changed = true;

                if (feederSinks.isEmpty()) {
                    feederChannels.remove(reference.channel());
                }
                if (feederChannels.isEmpty()) {
                    sinks.remove(reference.sourcePos());
                }
            }
        }

        if (changed) {
            dirtyMarker.run();
        }
        return changed;
    }

    public boolean hasConnectionsForSubTargetInternal(final Level level, final BlockPos pos, final String subTarget) {
        if (subTarget == null || subTarget.isEmpty()
                || !(level.getBlockState(pos).getBlock() instanceof final SubTargetCableEndpoint endpoint)) {
            final Map<String, Set<CableNetworkSink>> all = sinks.get(pos.asLong());
            return all != null && all.values().stream().anyMatch(set -> !set.isEmpty());
        }

        final long key = pos.asLong();
        final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(key);
        if (perChannel != null) {
            for (final Map.Entry<String, Set<CableNetworkSink>> entry : perChannel.entrySet()) {
                if (!entry.getValue().isEmpty()
                        && subTarget.equals(endpoint.cable$subTargetForChannel(level, pos, entry.getKey()))) {
                    return true;
                }
            }
        }

        final Set<SinkReference> references = sinkReferences.get(key);
        if (references != null) {
            for (final SinkReference reference : references) {
                if (!reference.sinkChannel().isEmpty()
                        && subTarget.equals(endpoint.cable$subTargetForChannel(level, pos, reference.sinkChannel()))) {
                    return true;
                }
            }
        }
        return false;
    }

    // * Hand a cable back for a removed connection
    public static int countStoredSources(final CompoundTag snapshot) {
        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return 0;
        }

        final Set<String> sources = new LinkedHashSet<>();
        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            // * A gearbox link has no source end
            if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)
                    || isTransceiverLinkTag(connection)) {
                continue;
            }

            sources.add(connection.getLong(SOURCE_KEY) + "|" + connection.getString(SOURCE_MODULE_KEY));
        }
        return sources.size();
    }

    public static int countStoredOutputs(final CompoundTag snapshot) {
        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return 0;
        }

        int outputs = 0;
        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (entry instanceof final CompoundTag connection && !isTransceiverLinkTag(connection)) {
                outputs++;
            }
        }
        return outputs;
    }

    public static int countStoredTransceivers(final CompoundTag snapshot) {
        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return 0;
        }

        final Set<String> transceivers = new LinkedHashSet<>();
        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)
                    || !isTransceiverLinkTag(connection)) {
                continue;
            }

            transceivers.add(storedEndpointKey(connection, SOURCE_KEY, SOURCE_OWNER_KEY, SOURCE_LEVEL_KEY));
            transceivers.add(storedEndpointKey(connection, SINK_KEY, SINK_OWNER_KEY, SINK_LEVEL_KEY));
        }
        return transceivers.size();
    }

    // * One saved end as written, whichever way this snapshot positions its ends
    private static String storedEndpointKey(
            final CompoundTag connection,
            final String positionKey,
            final String ownerKey,
            final String levelKey
    ) {
        return connection.getLong(positionKey)
                + "|" + (connection.hasUUID(ownerKey) ? connection.getUUID(ownerKey) : "")
                + "|" + connection.getByte(levelKey);
    }

    // * A saved Linked Gearbox link
    public static boolean isTransceiverLinkTag(final CompoundTag connection) {
        return LinkedGearboxLinks.isStressLink(
                connection.getString(CHANNEL_KEY), connection.getString(SINK_CHANNEL_KEY));
    }

    // * Is this saved connection already in place?
    private boolean isAlreadyConnected(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel,
            final String sinkChannel
    ) {
        if (LinkedGearboxLinks.isStressLink(channel, sinkChannel)) {
            return LinkedGearboxLinks.sameCableNetwork(level, source, sinkPos);
        }
        return containsConnection(source, sinkPos, sinkDirection, channel, sinkChannel);
    }

    public Map<BlockPos, Map<String, Set<CableNetworkSink>>> sourcesWithSinks() {
        final Map<BlockPos, Map<String, Set<CableNetworkSink>>> result = new LinkedHashMap<>();
        for (final Map.Entry<Long, Map<String, Set<CableNetworkSink>>> entry : sinks.entrySet()) {
            result.put(BlockPos.of(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public static Map<BlockPos, Set<String>> storedSourceModules(final CompoundTag snapshot, final BlockPos origin) {
        final Map<BlockPos, Set<String>> sources = new LinkedHashMap<>();
        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return sources;
        }

        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)) {
                continue;
            }

            final BlockPos source = origin.offset(BlockPos.of(connection.getLong(SOURCE_KEY)));
            sources.computeIfAbsent(source, key -> new LinkedHashSet<>())
                    .add(connection.getString(SOURCE_MODULE_KEY));

            // * Both ends of a gearbox link are the same kind of thing
            if (isTransceiverLinkTag(connection)) {
                sources.computeIfAbsent(origin.offset(BlockPos.of(connection.getLong(SINK_KEY))),
                        key -> new LinkedHashSet<>()).add("");
            }
        }
        return sources;
    }

    public static int countConnectionsForSubTarget(final Level level, final BlockPos pos, final String subTarget) {
        return get(level).countConnectionsForSubTargetInternal(level, pos, subTarget);
    }

    public int countConnectionsForSubTargetInternal(final Level level, final BlockPos pos, final String subTarget) {
        final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(pos.asLong());
        if (perChannel == null) {
            return 0;
        }

        if (subTarget == null || subTarget.isEmpty()
                || !(level.getBlockState(pos).getBlock() instanceof final SubTargetCableEndpoint endpoint)) {
            int total = 0;
            for (final Set<CableNetworkSink> sinksOnChannel : perChannel.values()) {
                total += sinksOnChannel.size();
            }
            return total;
        }

        int total = 0;
        for (final Map.Entry<String, Set<CableNetworkSink>> entry : perChannel.entrySet()) {
            if (subTarget.equals(endpoint.cable$subTargetForChannel(level, pos, entry.getKey()))) {
                total += entry.getValue().size();
            }
        }
        return total;
    }

    public static void refundCables(final ServerPlayer serverPlayer, final Level level, final int count) {
        for (int index = 0; index < count; index++) {
            get(level).refundCable(serverPlayer);
        }
    }

    private void refundCable(final ServerPlayer serverPlayer) {
        if (serverPlayer == null || !CableConfig.CONFIG.shouldConsumeCables.get() || serverPlayer.hasInfiniteMaterials()) {
            return;
        }

        final ItemStack cable = new ItemStack(CableItems.CABLE.get());
        if (!serverPlayer.addItem(cable)) {
            serverPlayer.drop(cable, false);
        }
    }

    public boolean removeAllFromSourceInternal(final ServerPlayer serverPlayer, final Level level, final BlockPos source) {
        final long sourceKey = source.asLong();
        final Map<String, Set<CableNetworkSink>> perChannel = sinks.remove(sourceKey);
        sourceValues.remove(sourceKey);
        if (perChannel == null) {
            return false;
        }

        perChannel.forEach((channel, sinksOnChannel) -> sinksOnChannel.forEach(sink -> {
            if (serverPlayer != null && CableConfig.CONFIG.shouldConsumeCables.get() && !serverPlayer.hasInfiniteMaterials()) {
                final ItemStack cable = new ItemStack(CableItems.CABLE.get());
                if (!serverPlayer.addItem(cable)) {
                    serverPlayer.drop(cable, false);
                }
            }
            removeSinkReference(sourceKey, channel, sink);
            applySignalToSink(level, sourceKey, channel, sink, 0);
        }));
        dirtyMarker.run();
        return true;
    }

    public boolean removeAllFromSourceChannelInternal(final Level level, final BlockPos source, final String channel) {
        final long sourceKey = source.asLong();
        final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(sourceKey);
        if (perChannel == null) {
            return false;
        }

        final Set<CableNetworkSink> sinksOnChannel = perChannel.remove(channel);
        if (sinksOnChannel == null) {
            return false;
        }

        sinksOnChannel.forEach(sink -> {
            removeSinkReference(sourceKey, channel, sink);
            applySignalToSink(level, sourceKey, channel, sink, 0);
        });

        if (perChannel.isEmpty()) {
            sinks.remove(sourceKey);
        }

        final Map<String, Integer> values = sourceValues.get(sourceKey);
        if (values != null) {
            values.remove(channel);
            if (values.isEmpty()) {
                sourceValues.remove(sourceKey);
            }
        }

        dirtyMarker.run();
        return true;
    }

    // * Drops every connection pointing at one module channel
    public boolean removeAllToSinkInternal(final Level level, final BlockPos sinkPos) {
        return removeIncoming(level, sinkPos, null);
    }

    public boolean removeAllToModuleSinkInternal(final Level level, final BlockPos sinkPos, final String sinkChannel) {
        if (sinkChannel == null || sinkChannel.isEmpty()) {
            return false;
        }

        return removeIncoming(level, sinkPos, sinkChannel);
    }

    // * A null channel takes them all
    private boolean removeIncoming(final Level level, final BlockPos sinkPos, @Nullable final String sinkChannel) {
        final long sinkKey = sinkPos.asLong();
        final Set<SinkReference> references = sinkReferences.get(sinkKey);
        if (references == null || references.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (final SinkReference reference : Set.copyOf(references)) {
            if (sinkChannel != null && !sinkChannel.equals(reference.sinkChannel())) {
                continue;
            }

            final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(reference.sourcePos());
            if (perChannel == null) {
                continue;
            }

            final Set<CableNetworkSink> sinksOnChannel = perChannel.get(reference.channel());
            if (sinksOnChannel == null) {
                continue;
            }

            final CableNetworkSink sink = new CableNetworkSink(sinkKey, reference.direction(), reference.sinkChannel());
            if (!sinksOnChannel.remove(sink)) {
                continue;
            }

            removeSinkReference(reference.sourcePos(), reference.channel(), sink);
            applySignalToSink(level, reference.sourcePos(), reference.channel(), sink, 0);

            if (sinksOnChannel.isEmpty()) {
                perChannel.remove(reference.channel());
            }
            if (perChannel.isEmpty()) {
                sinks.remove(reference.sourcePos());
            }
            changed = true;
        }

        if (changed) {
            dirtyMarker.run();
        }
        return changed;
    }

    // * Retarget every connection from one module channel to another
    public boolean remapModuleSinkInternal(
            final Level level,
            final BlockPos sinkPos,
            final String oldSinkChannel,
            final String newSinkChannel
    ) {
        if (oldSinkChannel == null || oldSinkChannel.isEmpty() || newSinkChannel == null || newSinkChannel.isEmpty()) {
            return false;
        }
        if (oldSinkChannel.equals(newSinkChannel)) {
            return false;
        }

        final long sinkKey = sinkPos.asLong();
        final Set<SinkReference> references = sinkReferences.get(sinkKey);
        if (references == null || references.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (final SinkReference reference : Set.copyOf(references)) {
            if (!oldSinkChannel.equals(reference.sinkChannel())) {
                continue;
            }

            final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(reference.sourcePos());
            if (perChannel == null) {
                continue;
            }

            final Set<CableNetworkSink> sinksOnChannel = perChannel.get(reference.channel());
            if (sinksOnChannel == null) {
                continue;
            }

            final CableNetworkSink oldSink = new CableNetworkSink(sinkKey, reference.direction(), oldSinkChannel);
            if (!sinksOnChannel.remove(oldSink)) {
                continue;
            }

            removeSinkReference(reference.sourcePos(), reference.channel(), oldSink);
            applySignalToSink(level, reference.sourcePos(), reference.channel(), oldSink, 0);

            final CableNetworkSink newSink = new CableNetworkSink(sinkKey, reference.direction(), newSinkChannel);
            sinksOnChannel.add(newSink);
            addSinkReference(reference.sourcePos(), reference.channel(), newSink);
            applySignalToSink(
                    level,
                    reference.sourcePos(),
                    reference.channel(),
                    newSink,
                    getCurrentSignal(level, BlockPos.of(reference.sourcePos()), reference.channel())
            );
            changed = true;
        }

        if (changed) {
            dirtyMarker.run();
        }
        return changed;
    }

    // * Moves connections to a new channel name without touching endpoints
    public boolean remapSourceChannelInternal(final Level level, final BlockPos source, final String oldChannel, final String newChannel) {
        final long sourceKey = source.asLong();
        final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(sourceKey);
        if (perChannel == null) {
            return false;
        }

        final Set<CableNetworkSink> sinksOnChannel = perChannel.remove(oldChannel);
        if (sinksOnChannel == null) {
            return false;
        }

        final int currentSignal = getCurrentSignal(level, source, oldChannel);
        final Set<CableNetworkSink> destination = perChannel.computeIfAbsent(newChannel, ignored -> new HashSet<>());
        for (final CableNetworkSink sink : sinksOnChannel) {
            removeSinkReference(sourceKey, oldChannel, sink);
            applySignalToSink(level, sourceKey, oldChannel, sink, 0);

            destination.add(sink);
            addSinkReference(sourceKey, newChannel, sink);
            applySignalToSink(level, sourceKey, newChannel, sink, currentSignal);
        }

        final Map<String, Integer> values = sourceValues.get(sourceKey);
        if (values != null) {
            final Integer value = values.remove(oldChannel);
            if (value != null) {
                values.put(newChannel, value);
            }
        }

        dirtyMarker.run();
        return true;
    }
    //#endregion

    //#region // --- SIGNAL SETTING --- //
    // * Called from redstone and controller compat to push a value in
    public void setSource(final Level level, final BlockPos source, final String channel, final int signal) {
        final long sourceKey = source.asLong();
        final Map<String, Integer> values = sourceValues.computeIfAbsent(sourceKey, ignored -> new HashMap<>());
        if (signal <= 0) {
            values.remove(channel);
            if (values.isEmpty()) {
                sourceValues.remove(sourceKey);
            }
        } else {
            values.put(channel, signal);
        }

        final Set<CableNetworkSink> sinksOnChannel = sinks.getOrDefault(sourceKey, Map.of()).get(channel);
        if (sinksOnChannel == null) {
            return;
        }

        sinksOnChannel.forEach(sink -> applySignalToSink(level, sourceKey, channel, sink, signal));
    }

    public void clearSourceSignals(final Level level, final BlockPos source) {
        final Map<String, Integer> values = sourceValues.remove(source.asLong());
        if (values == null) {
            return;
        }

        values.keySet().forEach(channel -> {
            final Set<CableNetworkSink> sinksOnChannel = sinks.getOrDefault(source.asLong(), Map.of()).get(channel);
            if (sinksOnChannel != null) {
                sinksOnChannel.forEach(sink -> applySignalToSink(level, source.asLong(), channel, sink, 0));
            }
        });
    }
    //#endregion

    //#region // --- READ ONLY ACCESSORS --- //
    // * Is this block sending a signal on any of its named channels
    public boolean isSendingOnChannels(final BlockPos source) {
        final Map<String, Integer> values = sourceValues.get(source.asLong());
        if (values == null) {
            return false;
        }
        for (final String channel : values.keySet()) {
            if (!WORLD_CHANNEL.equals(channel)) {
                return true;
            }
        }
        return false;
    }

    // * Changes whenever what the block sends on its named channels changes
    // * Lets a hub blink its bulb on every change without copying the values
    public int channelSignalHash(final BlockPos source) {
        final Map<String, Integer> values = sourceValues.get(source.asLong());
        if (values == null) {
            return 0;
        }
        int hash = 0;
        for (final Map.Entry<String, Integer> entry : values.entrySet()) {
            if (!WORLD_CHANNEL.equals(entry.getKey())) {
                hash += entry.getKey().hashCode() * 31 + entry.getValue();
            }
        }
        return hash;
    }

    public Map<String, Integer> getSourceSignals(final BlockPos source) {
        final Map<String, Integer> values = sourceValues.get(source.asLong());
        return values == null ? Map.of() : Map.copyOf(values);
    }

    public boolean isSource(final BlockPos pos) {
        return sinks.containsKey(pos.asLong());
    }

    // * One connection seen from the receiving end
    public record IncomingConnection(BlockPos source, String channel, Direction direction, String sinkChannel) {
        public boolean isModule() {
            return !sinkChannel.isEmpty();
        }
    }

    // * What drives this block
    public List<IncomingConnection> getIncoming(final BlockPos sinkPos) {
        final Set<SinkReference> references = sinkReferences.get(sinkPos.asLong());
        if (references == null) {
            return List.of();
        }

        final List<IncomingConnection> incoming = new ArrayList<>(references.size());
        for (final SinkReference reference : references) {
            incoming.add(new IncomingConnection(
                    BlockPos.of(reference.sourcePos()),
                    reference.channel(),
                    Direction.from3DDataValue(reference.direction()),
                    reference.sinkChannel()));
        }
        incoming.sort(Comparator
                .comparing(IncomingConnection::sinkChannel)
                .thenComparing(IncomingConnection::channel));
        return incoming;
    }

    public boolean isOutput(final BlockPos pos) {
        final Set<SinkReference> references = sinkReferences.get(pos.asLong());
        return references != null && !references.isEmpty();
    }

    public boolean isEndpoint(final BlockPos pos) {
        return isSource(pos) || isOutput(pos);
    }

    // * Everything the network touches
    public List<BlockPos> getEndpointPositions() {
        final Set<Long> keys = new LinkedHashSet<>(sinks.keySet());
        keys.addAll(sinkReferences.keySet());

        final List<BlockPos> positions = new ArrayList<>(keys.size());
        for (final long key : keys) {
            positions.add(BlockPos.of(key));
        }
        return positions;
    }

    // * What this source drives, by channel, ordered for display
    public Map<String, List<CableNetworkSink>> getConnections(final BlockPos source) {
        final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(source.asLong());
        if (perChannel == null) {
            return Map.of();
        }

        final Map<String, List<CableNetworkSink>> copy = new LinkedHashMap<>();
        perChannel.keySet().stream().sorted().forEach(channel -> copy.put(channel, List.copyOf(perChannel.get(channel))));
        return copy;
    }

    // * Deep copy so callers cant mutate live state
    public Map<Long, Map<String, Set<CableNetworkSink>>> getNetwork() {
        final Map<Long, Map<String, Set<CableNetworkSink>>> copy = new HashMap<>();
        sinks.forEach((source, perChannel) -> {
            final Map<String, Set<CableNetworkSink>> channelCopy = new HashMap<>();
            perChannel.forEach((channel, sinksOnChannel) -> channelCopy.put(channel, Set.copyOf(sinksOnChannel)));
            copy.put(source, Map.copyOf(channelCopy));
        });
        return Map.copyOf(copy);
    }

    public int getSignalAt(final BlockPos sinkPos, final Direction direction) {
        final CableNetworkNode node = nodes.get(BlockFace.of(sinkPos, direction));
        return node == null ? 0 : node.getSignal();
    }

    public int getModuleSinkSignal(final BlockPos sinkPos, final String sinkChannel) {
        final CableNetworkNode node = moduleNodes.get(ModuleSinkKey.of(sinkPos, sinkChannel));
        return node == null ? 0 : node.getSignal();
    }

    // * Every module channel on this block that the network is currently driving
    public Map<String, Integer> getModuleSinkSignals(final BlockPos sinkPos) {
        final long key = sinkPos.asLong();
        final Map<String, Integer> values = new HashMap<>();
        moduleNodes.forEach((moduleKey, node) -> {
            if (moduleKey.position() == key) {
                values.put(moduleKey.channel(), node.getSignal());
            }
        });
        return values;
    }

    // * Strongest signal out of a vanilla signal source, else best neighbor
    public static int computeWorldSignal(final Level level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (state.isSignalSource()) {
            return Arrays.stream(Direction.values())
                    .mapToInt(direction -> state.getSignal(level, pos, direction))
                    .max()
                    .orElse(0);
        }

        return level.getBestNeighborSignal(pos);
    }
    //#endregion

    //#region // --- SCHEMATIC BACKUP SNAPSHOTS --- //
    // * Backup drive uses these to save and restore connections through schematics
    public BackupSnapshot createBoundedBackupSnapshot(
            final Level level,
            final BlockPos backupPos,
            final Direction savedFacing,
            final AABB bounds
    ) {
        final CompoundTag tag = new CompoundTag();
        final ListTag connections = new ListTag();
        final SubLevel driveSubLevel = BackupDriveCapture.subLevelOf(level, backupPos);
        int internalConnections = 0;
        int skippedConnections = 0;

        final List<BlockPos[]> transceiverLinks = LinkedGearboxLinks.linksToSave(level,
                pos -> BackupDriveCapture.withinRegion(level, bounds, driveSubLevel, pos)
                        && !isGhostEndpoint(level, pos));

        // * An offset from the drive only survives a paste while both ends move
        // * together, which is only true on one level. Once a capture reaches onto
        // * another level the ends have to be recorded against their own sublevels
        // * instead, so placement can put them back against the new plots
        final boolean ownerAware = capturesAnotherLevel(level, bounds, driveSubLevel)
                || reachesAnotherLevel(level, driveSubLevel, transceiverLinks);

        for (final Map.Entry<Long, Map<String, Set<CableNetworkSink>>> sourceEntry : sinks.entrySet()) {
            final BlockPos sourcePos = BlockPos.of(sourceEntry.getKey());

            if (!BackupDriveCapture.isSourceCapturable(level, bounds, driveSubLevel, sourcePos)) {
                skippedConnections += countPlainConnections(sourceEntry.getValue());
                continue;
            }

            // * A source with no block is a ghost
            if (isGhostEndpoint(level, sourcePos)) {
                continue;
            }

            for (final Map.Entry<String, Set<CableNetworkSink>> channelEntry : sourceEntry.getValue().entrySet()) {
                for (final CableNetworkSink sink : channelEntry.getValue()) {
                    // * Gearbox links go in further down
                    if (LinkedGearboxLinks.isStressLink(channelEntry.getKey(), sink.sinkChannel())) {
                        continue;
                    }

                    if (!BackupDriveCapture.isSinkCapturable(level, bounds, driveSubLevel, sink.blockPos())) {
                        skippedConnections++;
                        continue;
                    }

                    if (isGhostEndpoint(level, sink.blockPos())) {
                        continue;
                    }

                    final CompoundTag connection = writeBoundedConnection(level, backupPos, driveSubLevel, ownerAware,
                            sourcePos, sink.blockPos(), sink.direction(), channelEntry.getKey(), sink.sinkChannel());

                    // * Recorded while the source is still there
                    final String module = moduleOwnerOf(level, sourcePos, channelEntry.getKey());
                    if (!module.isEmpty()) {
                        connection.putString(SOURCE_MODULE_KEY, module);
                    }

                    connections.add(connection);
                    internalConnections++;
                }
            }
        }

        // * Which end is written as the source means nothing
        for (final BlockPos[] link : transceiverLinks) {
            connections.add(writeBoundedConnection(level, backupPos, driveSubLevel, ownerAware,
                    link[0], link[1], Direction.UP.get3DDataValue(),
                    LinkedGearboxLinks.STRESS_CHANNEL, LinkedGearboxLinks.STRESS_CHANNEL));
            internalConnections++;
        }

        if (!connections.isEmpty()) {
            tag.put(CONNECTIONS_KEY, connections);
            tag.putString(FACING_KEY, savedFacing.getName());
            tag.putInt(SNAPSHOT_VERSION_KEY,
                    ownerAware ? WORLD_SPACE_SNAPSHOT_VERSION : RELATIVE_SNAPSHOT_VERSION);
            if (ownerAware) {
                tag.putLong(SAVED_DRIVE_POS_KEY, backupPos.asLong());
            }
        }
        if (skippedConnections > 0) {
            tag.putInt(UNSUPPORTED_CONNECTIONS_KEY, skippedConnections);
        }

        return new BackupSnapshot(tag, internalConnections, skippedConnections);
    }

    private CompoundTag writeBoundedConnection(
            final Level level,
            final BlockPos backupPos,
            @Nullable final SubLevel driveSubLevel,
            final boolean ownerAware,
            final BlockPos sourcePos,
            final BlockPos sinkPos,
            final int sinkDirection,
            final String channel,
            final String sinkChannel
    ) {
        final CompoundTag connection = new CompoundTag();
        if (ownerAware) {
            connection.putLong(SOURCE_KEY,
                    worldSpaceOffset(level, backupPos, driveSubLevel, sourcePos).asLong());
            connection.putLong(SINK_KEY,
                    worldSpaceOffset(level, backupPos, driveSubLevel, sinkPos).asLong());
            connection.putByte(SOURCE_LEVEL_KEY, endpointLevelKind(level, driveSubLevel, sourcePos));
            connection.putByte(SINK_LEVEL_KEY, endpointLevelKind(level, driveSubLevel, sinkPos));
            writeExactEndpoint(level, connection, SOURCE_EXACT_KEY, SOURCE_BLOCK_KEY,
                    backupPos, driveSubLevel, sourcePos);
            writeExactEndpoint(level, connection, SINK_EXACT_KEY, SINK_BLOCK_KEY,
                    backupPos, driveSubLevel, sinkPos);
        } else {
            connection.putLong(SOURCE_KEY, sourcePos.subtract(backupPos).asLong());
            connection.putLong(SINK_KEY, sinkPos.subtract(backupPos).asLong());
        }
        connection.putByte(DIRECTION_KEY, (byte) sinkDirection);
        connection.putString(CHANNEL_KEY, channel);
        if (!sinkChannel.isEmpty()) {
            connection.putString(SINK_CHANNEL_KEY, sinkChannel);
        }
        return connection;
    }

    // * Is either end of a saved gearbox link on a level other than the drive's
    private static boolean reachesAnotherLevel(
            final Level level,
            @Nullable final SubLevel driveSubLevel,
            final List<BlockPos[]> links
    ) {
        if (!BackupDriveCapture.crossLevelSavingAllowed()) {
            return false;
        }
        for (final BlockPos[] link : links) {
            for (final BlockPos end : link) {
                if (!BackupDriveCapture.isSameLevel(driveSubLevel, BackupDriveCapture.subLevelOf(level, end))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int countPlainConnections(final Map<String, Set<CableNetworkSink>> perChannel) {
        int total = 0;
        for (final Map.Entry<String, Set<CableNetworkSink>> entry : perChannel.entrySet()) {
            for (final CableNetworkSink sink : entry.getValue()) {
                if (!LinkedGearboxLinks.isStressLink(entry.getKey(), sink.sinkChannel())) {
                    total++;
                }
            }
        }
        return total;
    }

    // * Does anything this region would capture sit on a level of its own
    private boolean capturesAnotherLevel(
            final Level level,
            final AABB bounds,
            final SubLevel driveSubLevel
    ) {
        if (!BackupDriveCapture.crossLevelSavingAllowed()) {
            return false;
        }

        for (final Map.Entry<Long, Map<String, Set<CableNetworkSink>>> sourceEntry : sinks.entrySet()) {
            final BlockPos sourcePos = BlockPos.of(sourceEntry.getKey());
            if (!BackupDriveCapture.isSourceCapturable(level, bounds, driveSubLevel, sourcePos)) {
                continue;
            }

            if (countPlainConnections(sourceEntry.getValue()) == 0) {
                continue;
            }

            if (!BackupDriveCapture.isSameLevel(driveSubLevel, BackupDriveCapture.subLevelOf(level, sourcePos))) {
                return true;
            }

            for (final Map.Entry<String, Set<CableNetworkSink>> channelEntry : sourceEntry.getValue().entrySet()) {
                for (final CableNetworkSink sink : channelEntry.getValue()) {
                    if (LinkedGearboxLinks.isStressLink(channelEntry.getKey(), sink.sinkChannel())) {
                        continue;
                    }
                    if (BackupDriveCapture.isSinkCapturable(level, bounds, driveSubLevel, sink.blockPos())
                            && !BackupDriveCapture.isSameLevel(
                            driveSubLevel, BackupDriveCapture.subLevelOf(level, sink.blockPos()))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // * Where the endpoint sits relative to the drive, as the player sees it
    // * Sublevel identity is deliberately not recorded
    private BlockPos worldSpaceOffset(
            final Level level,
            final BlockPos drivePos,
            final SubLevel driveSubLevel,
            final BlockPos endpointPos
    ) {
        final Vec3 driveSpace = toDriveSpace(level, driveSubLevel, endpointPos);
        return BlockPos.containing(driveSpace).subtract(drivePos);
    }

    // * Centre of the block in the drive's space
    private void writeExactEndpoint(
            final Level level,
            final CompoundTag connection,
            final String exactKey,
            final String blockKey,
            final BlockPos drivePos,
            @Nullable final SubLevel driveSubLevel,
            final BlockPos endpointPos
    ) {
        final SubLevel posSubLevel = BackupDriveCapture.subLevelOf(level, endpointPos);
        final Vec3 driveSpace = BackupDriveCapture.isSameLevel(driveSubLevel, posSubLevel)
                ? Vec3.atCenterOf(endpointPos)
                : toDriveSpace(level, driveSubLevel, endpointPos);

        final CompoundTag exact = new CompoundTag();
        exact.putDouble("X", driveSpace.x - drivePos.getX());
        exact.putDouble("Y", driveSpace.y - drivePos.getY());
        exact.putDouble("Z", driveSpace.z - drivePos.getZ());
        connection.put(exactKey, exact);
        connection.putString(blockKey, blockIdAt(level, endpointPos));
    }

    private static String blockIdAt(final Level level, final BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }

    private static byte endpointLevelKind(final Level level, @Nullable final SubLevel driveSubLevel, final BlockPos pos) {
        final SubLevel posSubLevel = BackupDriveCapture.subLevelOf(level, pos);
        if (BackupDriveCapture.isSameLevel(driveSubLevel, posSubLevel)) {
            return ENDPOINT_ON_DRIVE_LEVEL;
        }
        return posSubLevel == null ? ENDPOINT_IN_WORLD : ENDPOINT_ON_OTHER_SUB_LEVEL;
    }

    // * Loaded, and nothing there
    private static boolean isGhostEndpoint(final Level level, final BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).isAir();
    }

    private Vec3 toDriveSpace(final Level level, final SubLevel driveSubLevel, final BlockPos pos) {
        final SubLevel posSubLevel = BackupDriveCapture.subLevelOf(level, pos);
        if (BackupDriveCapture.isSameLevel(driveSubLevel, posSubLevel)) {
            return Vec3.atLowerCornerOf(pos);
        }

        final Vec3 world = posSubLevel == null
                ? Vec3.atCenterOf(pos)
                : posSubLevel.logicalPose().transformPosition(Vec3.atCenterOf(pos));

        return driveSubLevel == null
                ? world
                : driveSubLevel.logicalPose().transformPositionInverse(world);
    }

    // * Drives that arrived holding a world space snapshot and have not been pinned yet
    private final Set<BlockPos> awaitingBind = new LinkedHashSet<>();

    // * Cable Buses that have loaded but not yet pushed their channels out
    private final Set<BlockPos> awaitingPublish = new LinkedHashSet<>();

    public void queueForPublish(final BlockPos busPos) {
        this.awaitingPublish.add(busPos.immutable());
    }

    public void tickPendingPublishes(final Level level) {
        if (this.awaitingPublish.isEmpty()) {
            return;
        }

        for (final BlockPos busPos : List.copyOf(this.awaitingPublish)) {
            if (!level.isLoaded(busPos)) {
                continue;
            }

            this.awaitingPublish.remove(busPos);

            final var blockEntity = level.getBlockEntity(busPos);
            if (blockEntity instanceof final MultiChannelCableBusBlockEntity bus) {
                bus.publishAllNow();
            } else if (blockEntity instanceof final IntegratedSensorBusBlockEntity sensor) {
                sensor.publishAllNow();
            }
        }
    }

    public void queueForBinding(final BlockPos drivePos) {
        this.awaitingBind.add(drivePos.immutable());
    }

    public void stopWaitingToBind(final BlockPos drivePos) {
        this.awaitingBind.remove(drivePos.immutable());
        this.bindProgress.remove(drivePos.immutable());
    }

    // * Retried every tick until each one takes
    public void tickPendingBinds(final Level level) {
        if (this.awaitingBind.isEmpty()) {
            return;
        }

        for (final BlockPos drivePos : List.copyOf(this.awaitingBind)) {
            if (!level.isLoaded(drivePos)) {
                continue;
            }

            if (level.getBlockEntity(drivePos) instanceof final WorldSpaceSnapshotHolder holder) {
                holder.tryBindWorldSpaceSnapshot();
            } else {
                this.awaitingBind.remove(drivePos);
            }
        }
    }

    // * Pins a world space snapshot to the sublevels it actually landed on
    // * Returns null while any endpoint is still missing
    @Nullable
    public CompoundTag bindWorldSpaceSnapshot(
            final Level level,
            final BlockPos drivePos,
            final CompoundTag snapshot
    ) {
        return bindWorldSpaceSnapshot(level, drivePos, snapshot, false);
    }

    // * giveUpWhenStalled lets a holder drop endpoints that never arrive
    @Nullable
    public CompoundTag bindWorldSpaceSnapshot(
            final Level level,
            final BlockPos drivePos,
            final CompoundTag snapshot,
            final boolean giveUpWhenStalled
    ) {
        if (!isWorldSpaceSnapshot(snapshot) || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return null;
        }

        final SubLevel driveSubLevel = BackupDriveCapture.subLevelOf(level, drivePos);
        final ListTag bound = new ListTag();
        int resolved = 0;
        int missing = 0;
        final List<CompoundTag> unresolved = new ArrayList<>();

        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)) {
                continue;
            }

            final BlockPos sourcePos = resolveWorldSpaceEndpoint(level, drivePos, driveSubLevel,
                    connection, SOURCE_KEY, SOURCE_LEVEL_KEY);
            final BlockPos sinkPos = resolveWorldSpaceEndpoint(level, drivePos, driveSubLevel,
                    connection, SINK_KEY, SINK_LEVEL_KEY);

            // * Something has not been placed yet, so binding now would pin the wrong thing
            if (sourcePos == null || sinkPos == null) {
                missing++;
                unresolved.add(connection);
                continue;
            }
            resolved++;

            final CompoundTag boundConnection = new CompoundTag();
            writeOwnedEndpoint(level, boundConnection, SOURCE_KEY, SOURCE_OWNER_KEY, sourcePos);
            writeOwnedEndpoint(level, boundConnection, SINK_KEY, SINK_OWNER_KEY, sinkPos);
            boundConnection.putByte(DIRECTION_KEY, connection.getByte(DIRECTION_KEY));
            boundConnection.putString(CHANNEL_KEY, connection.getString(CHANNEL_KEY));

            final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
            if (!sinkChannel.isEmpty()) {
                boundConnection.putString(SINK_CHANNEL_KEY, sinkChannel);
            }

            bound.add(boundConnection);
        }

        // * Wait while the paste is still landing
        if (missing > 0) {
            if (!giveUpWhenStalled || !hasBindStalled(level, drivePos, resolved)) {
                return null;
            }

            DriveBySableMod.LOGGER.warn(
                    "[ghost-source] Snapshot at {} still had {} connection(s) that could not be matched to a "
                            + "block after {} ticks. Binding the {} that landed and dropping the rest.",
                    drivePos, missing, BIND_STALL_TICKS, resolved
            );
            for (final CompoundTag connection : unresolved) {
                DriveBySableMod.LOGGER.warn("[ghost-source]   unmatched: {}", describeUnresolved(level, drivePos, connection));
            }
        }
        bindProgress.remove(drivePos);

        // * Nothing readable at all and nothing given up on, leave it as it was
        if (bound.isEmpty() && missing == 0) {
            return null;
        }

        final CompoundTag result = new CompoundTag();
        result.put(CONNECTIONS_KEY, bound);
        result.putInt(SNAPSHOT_VERSION_KEY, OWNER_AWARE_SNAPSHOT_VERSION);
        result.putBoolean(PLACEMENT_RESOLVED_KEY, true);
        if (driveSubLevel != null) {
            result.putUUID(OWNER_SUB_LEVEL_KEY, driveSubLevel.getUniqueId());
        }
        final int unsupported = snapshot.getInt(UNSUPPORTED_CONNECTIONS_KEY) + missing;
        if (unsupported > 0) {
            result.putInt(UNSUPPORTED_CONNECTIONS_KEY, unsupported);
        }

        return result;
    }

    // * Runtime only. When the count of landed endpoints last changed, per holder
    private final Map<BlockPos, long[]> bindProgress = new HashMap<>();

    private boolean hasBindStalled(final Level level, final BlockPos drivePos, final int resolved) {
        final long now = level.getGameTime();
        final long[] progress = bindProgress.computeIfAbsent(drivePos.immutable(), ignored -> new long[]{-1L, now});
        if (progress[0] != resolved) {
            progress[0] = resolved;
            progress[1] = now;
            return false;
        }
        return now - progress[1] >= BIND_STALL_TICKS;
    }

    // * Against its own sublevel's plot, or absolute when loose in the world
    // * Matches what resolveOwnerAwareEndpoint reads back
    private void writeOwnedEndpoint(
            final Level level,
            final CompoundTag connection,
            final String positionKey,
            final String ownerKey,
            final BlockPos endpointPos
    ) {
        final SubLevel endpointSubLevel = BackupDriveCapture.subLevelOf(level, endpointPos);
        if (endpointSubLevel == null) {
            connection.putLong(positionKey, endpointPos.asLong());
            return;
        }

        connection.putUUID(ownerKey, endpointSubLevel.getUniqueId());
        connection.putLong(positionKey,
                endpointPos.subtract(endpointSubLevel.getPlot().getCenterBlock()).asLong());
    }

    // * Turns a drive space offset back into the block that now sits there
    // * Whatever sublevel occupies that spot at load time is the right one
    // * Null when nothing suitable is there (yet)
    @Nullable
    private BlockPos resolveWorldSpaceEndpoint(
            final Level level,
            final BlockPos drivePos,
            final SubLevel driveSubLevel,
            final CompoundTag connection,
            final String positionKey,
            final String levelKey
    ) {
        final boolean isSource = SOURCE_KEY.equals(positionKey);
        final String exactKey = isSource ? SOURCE_EXACT_KEY : SINK_EXACT_KEY;
        final String blockKey = isSource ? SOURCE_BLOCK_KEY : SINK_BLOCK_KEY;

        final BlockPos inDriveSpace = drivePos.offset(BlockPos.of(connection.getLong(positionKey)));
        // * Absent on snapshots saved before the level was recorded
        final int kind = connection.contains(levelKey, Tag.TAG_BYTE) ? connection.getByte(levelKey) : -1;
        final String blockId = connection.getString(blockKey);

        // * The block has to be able to play its part
        final String channel = connection.getString(CHANNEL_KEY);
        final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
        final Predicate<BlockPos> fitsRole = isSource
                ? pos -> isValidChannel(level, pos, channel)
                : pos -> isValidSinkChannel(level, pos, sinkChannel);

        // * Same level as the drive moves with it block for block, so only the exact spot counts
        if (kind == ENDPOINT_ON_DRIVE_LEVEL || kind < 0) {
            if (isCandidate(level, inDriveSpace, blockId, fitsRole)) {
                return inDriveSpace;
            }
            if (kind == ENDPOINT_ON_DRIVE_LEVEL) {
                return null;
            }
        }

        final Vec3 drivePoint = connection.contains(exactKey, Tag.TAG_COMPOUND)
                ? Vec3.atLowerCornerOf(drivePos).add(
                connection.getCompound(exactKey).getDouble("X"),
                connection.getCompound(exactKey).getDouble("Y"),
                connection.getCompound(exactKey).getDouble("Z"))
                : Vec3.atCenterOf(inDriveSpace);
        final Vec3 world = driveSubLevel == null
                ? drivePoint
                : driveSubLevel.logicalPose().transformPosition(drivePoint);

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        // * Loose in the world, only worth a look when the drive is not already the world
        if ((kind == ENDPOINT_IN_WORLD || kind < 0) && driveSubLevel != null) {
            final SearchHit hit = searchAround(level, world, blockId,
                    fitsRole.and(pos -> Sable.HELPER.getContaining(level, pos) == null));
            if (hit != null) {
                best = hit.pos();
                bestDistance = hit.distanceSqr();
            }
        }

        if (kind == ENDPOINT_ON_OTHER_SUB_LEVEL || kind < 0) {
            final double reach = CROSS_LEVEL_SEARCH_RADIUS + 1.0;
            for (final SubLevel candidate : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(
                    world.x - reach, world.y - reach, world.z - reach,
                    world.x + reach, world.y + reach, world.z + reach))) {

                if (BackupDriveCapture.isSameLevel(driveSubLevel, candidate)) {
                    continue;
                }

                final Vec3 local = candidate.logicalPose().transformPositionInverse(world);
                final SearchHit hit = searchAround(level, local, blockId,
                        fitsRole.and(pos -> BackupDriveCapture.isSameLevel(
                                candidate, BackupDriveCapture.subLevelOf(level, pos))));
                if (hit != null && hit.distanceSqr() < bestDistance) {
                    best = hit.pos();
                    bestDistance = hit.distanceSqr();
                }
            }
        }

        return best;
    }

    private record SearchHit(BlockPos pos, double distanceSqr) {
    }

    private static boolean isCandidate(
            final Level level,
            final BlockPos pos,
            final String blockId,
            final Predicate<BlockPos> fitsRole
    ) {
        final BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (!blockId.isEmpty() && !BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(blockId)) {
            return false;
        }
        return fitsRole.test(pos);
    }

    // * The closest suitable block to a point, within the search radius
    @Nullable
    private static SearchHit searchAround(
            final Level level,
            final Vec3 point,
            final String blockId,
            final Predicate<BlockPos> fitsRole
    ) {
        final BlockPos centre = BlockPos.containing(point);
        final double maxDistanceSqr = blockId.isEmpty()
                ? 1.0
                : (CROSS_LEVEL_SEARCH_RADIUS + 0.5) * (CROSS_LEVEL_SEARCH_RADIUS + 0.5) * 3.0;

        BlockPos best = null;
        double bestDistance = maxDistanceSqr;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -CROSS_LEVEL_SEARCH_RADIUS; dx <= CROSS_LEVEL_SEARCH_RADIUS; dx++) {
            for (int dy = -CROSS_LEVEL_SEARCH_RADIUS; dy <= CROSS_LEVEL_SEARCH_RADIUS; dy++) {
                for (int dz = -CROSS_LEVEL_SEARCH_RADIUS; dz <= CROSS_LEVEL_SEARCH_RADIUS; dz++) {
                    cursor.setWithOffset(centre, dx, dy, dz);
                    final double distance = Vec3.atCenterOf(cursor).distanceToSqr(point);
                    if (distance > bestDistance) {
                        continue;
                    }

                    final BlockPos candidate = cursor.immutable();
                    if (isCandidate(level, candidate, blockId, fitsRole)) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }

        return best == null ? null : new SearchHit(best, bestDistance);
    }

    // * One line per connection that never landed, so a failed paste can be diagnosed from the log
    private String describeUnresolved(
            final Level level,
            final BlockPos drivePos,
            final CompoundTag connection
    ) {
        final StringBuilder line = new StringBuilder("channel '").append(connection.getString(CHANNEL_KEY)).append("'");
        for (final String[] keys : new String[][]{
                {"source", SOURCE_KEY, SOURCE_LEVEL_KEY, SOURCE_BLOCK_KEY},
                {"output", SINK_KEY, SINK_LEVEL_KEY, SINK_BLOCK_KEY}}) {
            final BlockPos expected = drivePos.offset(BlockPos.of(connection.getLong(keys[1])));
            final String kind = !connection.contains(keys[2], Tag.TAG_BYTE) ? "unrecorded"
                    : switch (connection.getByte(keys[2])) {
                case ENDPOINT_ON_DRIVE_LEVEL -> "same level";
                case ENDPOINT_ON_OTHER_SUB_LEVEL -> "other sub-level";
                case ENDPOINT_IN_WORLD -> "world";
                default -> "unknown";
            };
            line.append(", ").append(keys[0]).append(" [").append(kind)
                    .append(", saved ").append(connection.getString(keys[3]).isEmpty() ? "?" : connection.getString(keys[3]))
                    .append(", at drive offset ").append(expected.getX() - drivePos.getX()).append(' ')
                    .append(expected.getY() - drivePos.getY()).append(' ')
                    .append(expected.getZ() - drivePos.getZ())
                    .append(", found there ").append(blockIdAt(level, expected)).append(']');
        }
        return line.toString();
    }

    private static int countConnections(final Map<String, Set<CableNetworkSink>> perChannel) {
        int total = 0;
        for (final Set<CableNetworkSink> sinksOnChannel : perChannel.values()) {
            total += sinksOnChannel.size();
        }
        return total;
    }

    public BackupSnapshot createBackupSnapshot(final Level level, final BlockPos backupPos, final Direction savedFacing) {
        final SubLevel backupSubLevel = Sable.HELPER.getContaining(level, backupPos);
        if (backupSubLevel == null) {
            return new BackupSnapshot(new CompoundTag(), 0, 0);
        }

        final SubLevelSchematicSerializationContext context = SubLevelSchematicSerializationContext.getCurrentContext();
        if (context != null && context.getType() == SubLevelSchematicSerializationContext.Type.SAVE) {
            return createSchematicBackupSnapshot(level, backupPos, backupSubLevel, context);
        }

        return createRelativeBackupSnapshot(level, backupPos, backupSubLevel, savedFacing);
    }

    public CompoundTag pruneRestoredConnections(
            final Level level,
            final BlockPos backupPos,
            final Direction currentFacing,
            final CompoundTag snapshot
    ) {
        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return new CompoundTag();
        }

        final Rotation rotation = placementRotation(snapshot, currentFacing);
        final boolean ownerAware = isOwnerAware(snapshot);
        final boolean worldSpace = isWorldSpaceSnapshot(snapshot);
        final ListTag remaining = new ListTag();

        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection)) {
                continue;
            }

            if (!isReadable(connection)) {
                // * Unreadable entries are kept
                remaining.add(connection.copy());
                continue;
            }

            final String channel = connection.getString(CHANNEL_KEY);
            final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
            final ResolvedPair resolved = resolveEndpoints(level, connection, backupPos, rotation, ownerAware, worldSpace);

            // * Cannot judge it yet, so hold on to it
            if (resolved.deferred()) {
                remaining.add(connection.copy());
                continue;
            }

            final BlockPos sourcePos = resolved.source();
            final BlockPos sinkPos = resolved.sink();
            final Direction sinkDirection = resolved.sinkDirection();

            // * Already placed, so there is nothing left to hold on to
            if (isAlreadyConnected(level, sourcePos, sinkPos, sinkDirection, channel, sinkChannel)) {
                continue;
            }

            // * Anything still missing is kept
            remaining.add(connection.copy());
        }

        if (remaining.isEmpty()) {
            return new CompoundTag();
        }

        final CompoundTag pruned = new CompoundTag();
        pruned.put(CONNECTIONS_KEY, remaining);
        pruned.putString(FACING_KEY, snapshot.getString(FACING_KEY));
        pruned.putInt(SNAPSHOT_VERSION_KEY, snapshot.getInt(SNAPSHOT_VERSION_KEY));
        if (snapshot.hasUUID(OWNER_SUB_LEVEL_KEY)) {
            pruned.putUUID(OWNER_SUB_LEVEL_KEY, snapshot.getUUID(OWNER_SUB_LEVEL_KEY));
        }
        if (snapshot.contains(PLACEMENT_RESOLVED_KEY)) {
            pruned.putBoolean(PLACEMENT_RESOLVED_KEY, snapshot.getBoolean(PLACEMENT_RESOLVED_KEY));
        }
        if (snapshot.contains(UNSUPPORTED_CONNECTIONS_KEY)) {
            pruned.putInt(UNSUPPORTED_CONNECTIONS_KEY, snapshot.getInt(UNSUPPORTED_CONNECTIONS_KEY));
        }
        return pruned;
    }

    // * Could this connection still be made if the player fixed things?
    private boolean isRestorable(
            final Level level,
            final BlockPos sourcePos,
            final String channel,
            final BlockPos sinkPos,
            final String sinkChannel
    ) {
        if (!level.getBlockState(sourcePos).isAir() && !isValidChannel(level, sourcePos, channel)) {
            return false;
        }

        return level.getBlockState(sinkPos).isAir() || isValidSinkChannel(level, sinkPos, sinkChannel);
    }

    public record SnapshotSummary(
            int loadedSources,
            int missingSources,
            int loadedSinks,
            int missingSinks,
            int loadedTransceivers,
            int missingTransceivers
    ) {
    }

    public SnapshotSummary summariseSnapshot(
            final Level level,
            final BlockPos backupPos,
            final Direction currentFacing,
            final CompoundTag snapshot
    ) {
        // * Keyed by module
        final Map<String, Boolean> sources = new LinkedHashMap<>();
        final Map<String, Boolean> sinks = new LinkedHashMap<>();
        final Map<BlockPos, Boolean> transceivers = new LinkedHashMap<>();
        int unplacedTransceivers = 0;

        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return new SnapshotSummary(0, 0, 0, 0, 0, 0);
        }

        final Rotation rotation = placementRotation(snapshot, currentFacing);
        final boolean ownerAware = isOwnerAware(snapshot);
        final boolean worldSpace = isWorldSpaceSnapshot(snapshot);

        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)) {
                continue;
            }

            final String channel = connection.getString(CHANNEL_KEY);
            final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
            final ResolvedPair resolved = resolveEndpoints(level, connection, backupPos, rotation, ownerAware, worldSpace);
            final boolean transceiverLink = isTransceiverLinkTag(connection);

            // * Still waiting on a sublevel
            if (resolved.deferred()) {
                if (transceiverLink) {
                    unplacedTransceivers += 2;
                    continue;
                }
                sources.merge("deferred|" + sources.size(), false, Boolean::logicalOr);
                sinks.merge("deferred|" + sinks.size(), false, Boolean::logicalOr);
                continue;
            }

            final BlockPos sourcePos = resolved.source();
            final BlockPos sinkPos = resolved.sink();
            final Direction sinkDirection = resolved.sinkDirection();

            final boolean present = isAlreadyConnected(level, sourcePos, sinkPos, sinkDirection, channel, sinkChannel);

            if (transceiverLink) {
                transceivers.merge(sourcePos.immutable(), present, Boolean::logicalOr);
                transceivers.merge(sinkPos.immutable(), present, Boolean::logicalOr);
                continue;
            }

            // * A module is its own source
            sources.merge(sourceIdentity(level, sourcePos, connection), present, Boolean::logicalOr);
            // * One entry per connection
            sinks.merge(
                    sourcePos.toShortString() + "|" + channel
                            + "->" + sinkPos.toShortString() + "|" + sinkDirection + "|" + sinkChannel,
                    present,
                    Boolean::logicalOr
            );
        }

        int loadedTransceivers = 0;
        int missingTransceivers = unplacedTransceivers;
        for (final Map.Entry<BlockPos, Boolean> transceiver : transceivers.entrySet()) {
            if (transceiver.getValue()) {
                loadedTransceivers++;
            } else if (!LinkedGearboxLinks.isGearbox(level, transceiver.getKey())) {
                missingTransceivers++;
            }
        }

        return new SnapshotSummary(
                (int) sources.values().stream().filter(Boolean::booleanValue).count(),
                (int) sources.values().stream().filter(loaded -> !loaded).count(),
                (int) sinks.values().stream().filter(Boolean::booleanValue).count(),
                (int) sinks.values().stream().filter(loaded -> !loaded).count(),
                loadedTransceivers,
                missingTransceivers
        );
    }

    //#region // --- HOLES A LOAD COULD NOT FILL --- //
    private static final int MISSING_BLOCK_LIMIT = 256;

    // * Where a load wanted a block and found empty space instead
    public List<BlockPos> missingBlocksInSnapshot(
            final Level level,
            final BlockPos backupPos,
            final Direction currentFacing,
            final CompoundTag snapshot
    ) {
        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        final Rotation rotation = placementRotation(snapshot, currentFacing);
        final boolean ownerAware = isOwnerAware(snapshot);
        final boolean worldSpace = isWorldSpaceSnapshot(snapshot);

        final Set<BlockPos> missing = new LinkedHashSet<>();
        final SubLevel holderSubLevel = worldSpace ? BackupDriveCapture.subLevelOf(level, backupPos) : null;
        int unplaceable = 0;

        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)) {
                continue;
            }

            final BlockPos source = reportableEndpoint(
                    level, connection, backupPos, rotation, ownerAware, worldSpace, holderSubLevel, true);
            final BlockPos sink = reportableEndpoint(
                    level, connection, backupPos, rotation, ownerAware, worldSpace, holderSubLevel, false);

            // * Both ends known, so this one can be ruled out if it already made it
            if (source != null && sink != null) {
                final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
                final Direction sinkDirection = ownerAware || worldSpace
                        ? Direction.from3DDataValue(connection.getByte(DIRECTION_KEY))
                        : resolveSinkDirection(connection, sinkChannel, rotation);

                if (isAlreadyConnected(level,
                        source, sink, sinkDirection, connection.getString(CHANNEL_KEY), sinkChannel)) {
                    continue;
                }
            }

            if (source == null || sink == null) {
                unplaceable++;
            }

            addIfEmpty(level, source, missing);
            addIfEmpty(level, sink, missing);

            if (missing.size() >= MISSING_BLOCK_LIMIT) {
                break;
            }
        }

        if (!missing.isEmpty() || unplaceable > 0) {
            DriveBySableMod.LOGGER.info(
                    "[load-gaps] {} empty spot(s) to call out from {}, {} endpoint(s) with nowhere to point at.",
                    missing.size(),
                    backupPos,
                    unplaceable
            );
        }

        return List.copyOf(missing);
    }

    @Nullable
    private BlockPos reportableEndpoint(
            final Level level,
            final CompoundTag connection,
            final BlockPos backupPos,
            final Rotation rotation,
            final boolean ownerAware,
            final boolean worldSpace,
            @Nullable final SubLevel holderSubLevel,
            final boolean source
    ) {
        if (worldSpace
                && connection.contains(SOURCE_KEY, Tag.TAG_LONG)
                && connection.contains(SINK_KEY, Tag.TAG_LONG)) {
            final String positionKey = source ? SOURCE_KEY : SINK_KEY;
            final String levelKey = source ? SOURCE_LEVEL_KEY : SINK_LEVEL_KEY;

            final BlockPos found = resolveWorldSpaceEndpoint(
                    level, backupPos, holderSubLevel, connection, positionKey, levelKey);

            // * Found means a block that fits the part is sitting there
            return found != null
                    ? found
                    : intendedWorldSpaceEndpoint(level, backupPos, holderSubLevel, connection, positionKey, levelKey);
        }

        if (ownerAware) {
            final ResolvedEndpoint resolved = resolveOwnerAwareEndpoint(
                    level,
                    connection,
                    source ? SOURCE_KEY : SINK_KEY,
                    source ? SOURCE_OWNER_KEY : SINK_OWNER_KEY,
                    backupPos
            );

            return resolved.isDeferred() ? null : resolved.position();
        }

        return source
                ? resolveSource(connection, backupPos, rotation)
                : resolveSink(connection, backupPos, rotation);
    }

    // * Where a world-space endpoint was meant to land
    @Nullable
    private BlockPos intendedWorldSpaceEndpoint(
            final Level level,
            final BlockPos backupPos,
            @Nullable final SubLevel holderSubLevel,
            final CompoundTag connection,
            final String positionKey,
            final String levelKey
    ) {
        final BlockPos inHolderSpace = backupPos.offset(BlockPos.of(connection.getLong(positionKey)));
        final int kind = connection.contains(levelKey, Tag.TAG_BYTE) ? connection.getByte(levelKey) : -1;

        if (kind == ENDPOINT_ON_DRIVE_LEVEL || kind < 0) {
            return inHolderSpace;
        }

        if (kind == ENDPOINT_IN_WORLD && holderSubLevel == null) {
            return inHolderSpace;
        }

        final Vec3 world = savedWorldPoint(backupPos, holderSubLevel, connection, positionKey, inHolderSpace);

        if (kind == ENDPOINT_IN_WORLD) {
            return BlockPos.containing(world);
        }

        // * Inside some other sublevel
        final double reach = CROSS_LEVEL_SEARCH_RADIUS + 1.0;
        for (final SubLevel candidate : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(
                world.x - reach, world.y - reach, world.z - reach,
                world.x + reach, world.y + reach, world.z + reach))) {

            if (BackupDriveCapture.isSameLevel(holderSubLevel, candidate)) {
                continue;
            }

            final BlockPos local = BlockPos.containing(candidate.logicalPose().transformPositionInverse(world));

            if (BackupDriveCapture.isSameLevel(candidate, BackupDriveCapture.subLevelOf(level, local))) {
                return local;
            }
        }

        return null;
    }

    private static Vec3 savedWorldPoint(
            final BlockPos backupPos,
            @Nullable final SubLevel holderSubLevel,
            final CompoundTag connection,
            final String positionKey,
            final BlockPos inHolderSpace
    ) {
        final String exactKey = SOURCE_KEY.equals(positionKey) ? SOURCE_EXACT_KEY : SINK_EXACT_KEY;
        final Vec3 holderPoint = connection.contains(exactKey, Tag.TAG_COMPOUND)
                ? Vec3.atLowerCornerOf(backupPos).add(
                connection.getCompound(exactKey).getDouble("X"),
                connection.getCompound(exactKey).getDouble("Y"),
                connection.getCompound(exactKey).getDouble("Z"))
                : Vec3.atCenterOf(inHolderSpace);

        return holderSubLevel == null
                ? holderPoint
                : holderSubLevel.logicalPose().transformPosition(holderPoint);
    }

    // * Only genuinely empty space counts
    private static void addIfEmpty(
            final Level level,
            @Nullable final BlockPos pos,
            final Set<BlockPos> missing
    ) {
        if (pos != null && level.getBlockState(pos).isAir()) {
            missing.add(pos.immutable());
        }
    }
    //#endregion

    public Map<BlockPos, Set<String>> connectedSourceModules(
            final Level level,
            final BlockPos backupPos,
            final Direction currentFacing,
            final CompoundTag snapshot
    ) {
        final Map<BlockPos, Set<String>> connected = new LinkedHashMap<>();
        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return connected;
        }

        final Rotation rotation = placementRotation(snapshot, currentFacing);
        final boolean ownerAware = isOwnerAware(snapshot);
        final boolean worldSpace = isWorldSpaceSnapshot(snapshot);

        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)) {
                continue;
            }

            final String channel = connection.getString(CHANNEL_KEY);
            final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
            if (isTransceiverLinkTag(connection)) {
                continue;
            }

            final ResolvedPair resolved = resolveEndpoints(level, connection, backupPos, rotation, ownerAware, worldSpace);
            if (resolved.deferred()) {
                continue;
            }

            final BlockPos sourcePos = resolved.source();
            final BlockPos sinkPos = resolved.sink();
            final Direction sinkDirection = resolved.sinkDirection();

            if (!isAlreadyConnected(level, sourcePos, sinkPos, sinkDirection, channel, sinkChannel)) {
                continue;
            }

            final String owner = moduleOwnerOf(level, sourcePos, channel);
            connected.computeIfAbsent(sourcePos.immutable(), ignored -> new LinkedHashSet<>()).add(owner);
        }
        return connected;
    }

    // * Every saved transceiver that is joined to one it was saved with
    public Set<BlockPos> joinedTransceivers(
            final Level level,
            final BlockPos backupPos,
            final Direction currentFacing,
            final CompoundTag snapshot
    ) {
        final Set<BlockPos> joined = new LinkedHashSet<>();
        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return joined;
        }

        final Rotation rotation = placementRotation(snapshot, currentFacing);
        final boolean ownerAware = isOwnerAware(snapshot);
        final boolean worldSpace = isWorldSpaceSnapshot(snapshot);

        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)
                    || !isTransceiverLinkTag(connection)) {
                continue;
            }

            final ResolvedPair resolved = resolveEndpoints(level, connection, backupPos, rotation, ownerAware, worldSpace);
            if (resolved.deferred()
                    || !LinkedGearboxLinks.sameCableNetwork(level, resolved.source(), resolved.sink())) {
                continue;
            }

            joined.add(resolved.source().immutable());
            joined.add(resolved.sink().immutable());
        }
        return joined;
    }

    // * What counts as one source for reporting?
    private static String sourceIdentity(final Level level, final BlockPos source, final CompoundTag connection) {
        // * The module recorded at save time
        String module = connection.getString(SOURCE_MODULE_KEY);

        if (module.isEmpty()) {
            module = moduleOwnerOf(level, source, connection.getString(CHANNEL_KEY));
        }

        return source.toShortString() + "|" + module;
    }

    // * Which module on this block owns the channel
    private static String moduleOwnerOf(final Level level, final BlockPos source, final String channel) {
        if (!(level.getBlockState(source).getBlock() instanceof final SubTargetCableEndpoint endpoint)) {
            return "";
        }

        final String owner = endpoint.cable$subTargetForChannel(level, source, channel);
        return owner == null ? "" : owner;
    }

    public int countPendingConnections(
            final Level level,
            final BlockPos backupPos,
            final Direction currentFacing,
            final CompoundTag snapshot
    ) {
        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return 0;
        }

        final Rotation rotation = placementRotation(snapshot, currentFacing);
        final boolean ownerAware = isOwnerAware(snapshot);
        final boolean worldSpace = isWorldSpaceSnapshot(snapshot);
        int pending = 0;

        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)) {
                continue;
            }

            final String channel = connection.getString(CHANNEL_KEY);
            final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
            final ResolvedPair resolved = resolveEndpoints(level, connection, backupPos, rotation, ownerAware, worldSpace);

            // * Not chargeable until we know where it lands
            if (resolved.deferred()) {
                continue;
            }

            final BlockPos sourcePos = resolved.source();
            final BlockPos sinkPos = resolved.sink();
            final Direction sinkDirection = resolved.sinkDirection();

            // * Counted only when it is missing
            if (!isAlreadyConnected(level, sourcePos, sinkPos, sinkDirection, channel, sinkChannel)
                    && isRestorable(level, sourcePos, channel, sinkPos, sinkChannel)) {
                pending++;
            }
        }
        return pending;
    }

    //#region // --- SHARED SNAPSHOT READING --- //
    // * One connection's endpoints, resolved in whatever space the snapshot uses
    private record ResolvedPair(BlockPos source, BlockPos sink, Direction sinkDirection, boolean deferred) {
        private static ResolvedPair waiting() {
            return new ResolvedPair(BlockPos.ZERO, BlockPos.ZERO, Direction.UP, true);
        }
    }

    private static boolean isOwnerAware(final CompoundTag snapshot) {
        return snapshot.getInt(SNAPSHOT_VERSION_KEY) >= OWNER_AWARE_SNAPSHOT_VERSION;
    }

    public static boolean isWorldSpaceSnapshot(final CompoundTag snapshot) {
        return snapshot != null && snapshot.getInt(SNAPSHOT_VERSION_KEY) == WORLD_SPACE_SNAPSHOT_VERSION;
    }

    // * True only for a drive that did not capture this itself
    public static boolean isPastedCopy(final CompoundTag snapshot, final BlockPos drivePos) {
        if (!isWorldSpaceSnapshot(snapshot) || !snapshot.contains(SAVED_DRIVE_POS_KEY, Tag.TAG_LONG)) {
            return false;
        }

        return !BlockPos.of(snapshot.getLong(SAVED_DRIVE_POS_KEY)).equals(drivePos);
    }

    private ResolvedPair resolveEndpoints(
            final Level level,
            final CompoundTag connection,
            final BlockPos backupPos,
            final Rotation rotation,
            final boolean ownerAware,
            final boolean worldSpace
    ) {
        if (worldSpace
                && connection.contains(SOURCE_KEY, Tag.TAG_LONG)
                && connection.contains(SINK_KEY, Tag.TAG_LONG)) {
            final SubLevel driveSubLevel = BackupDriveCapture.subLevelOf(level, backupPos);
            final BlockPos source = resolveWorldSpaceEndpoint(level, backupPos, driveSubLevel,
                    connection, SOURCE_KEY, SOURCE_LEVEL_KEY);
            final BlockPos sink = resolveWorldSpaceEndpoint(level, backupPos, driveSubLevel,
                    connection, SINK_KEY, SINK_LEVEL_KEY);
            // * Not there yet, judged later
            if (source == null || sink == null) {
                return ResolvedPair.waiting();
            }
            return new ResolvedPair(
                    source,
                    sink,
                    Direction.from3DDataValue(connection.getByte(DIRECTION_KEY)),
                    false
            );
        }

        if (ownerAware) {
            final ResolvedEndpoint source = resolveOwnerAwareEndpoint(level, connection, SOURCE_KEY, SOURCE_OWNER_KEY, backupPos);
            final ResolvedEndpoint sink = resolveOwnerAwareEndpoint(level, connection, SINK_KEY, SINK_OWNER_KEY, backupPos);
            if (source.isDeferred() || sink.isDeferred()) {
                return ResolvedPair.waiting();
            }

            return new ResolvedPair(
                    source.position(),
                    sink.position(),
                    Direction.from3DDataValue(connection.getByte(DIRECTION_KEY)),
                    false
            );
        }

        final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
        return new ResolvedPair(
                resolveSource(connection, backupPos, rotation),
                resolveSink(connection, backupPos, rotation),
                resolveSinkDirection(connection, sinkChannel, rotation),
                false
        );
    }

    // --- LEGACY PAYLOADS WAITING ON A MANUAL LOAD --- //
    // * Runtime only, never saved
    private final Map<BlockPos, Integer> legacyPayloadsAwaitingLoad = new HashMap<>();

    public void recordLegacyPayloadAwaitingLoad(final BlockPos drivePos, final int connections) {
        if (drivePos == null || connections <= 0) {
            return;
        }

        this.legacyPayloadsAwaitingLoad.put(drivePos.immutable(), connections);
    }

    public void forgetLegacyPayloadAwaitingLoad(final BlockPos drivePos) {
        if (drivePos != null) {
            this.legacyPayloadsAwaitingLoad.remove(drivePos.immutable());
        }
    }

    // * Is some Drive holding at least this much, still unloaded
    public boolean hasLegacyPayloadAwaitingLoad(final int atLeast) {
        return this.legacyPayloadsAwaitingLoad.values().stream().anyMatch(held -> held >= atLeast);
    }

    private static final int REGION_SANITY_LIMIT = 512;

    // --- REGION THAT COVERS A SNAPSHOT --- //
    // * Offset and size relative to the drive
    public record SnapshotRegion(BlockPos offset, BlockPos size) {
    }

    // * Measures where a snapshot actually lands
    @Nullable
    public SnapshotRegion deriveSnapshotRegion(
            final Level level,
            final BlockPos drivePos,
            final Direction currentFacing,
            final CompoundTag snapshot
    ) {
        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return null;
        }

        final Rotation rotation = placementRotation(snapshot, currentFacing);
        final boolean ownerAware = isOwnerAware(snapshot);
        final boolean worldSpace = isWorldSpaceSnapshot(snapshot);

        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        boolean any = false;

        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)) {
                continue;
            }

            final ResolvedPair resolved = resolveEndpoints(level, connection, drivePos, rotation, ownerAware, worldSpace);

            if (resolved.deferred()) {
                return null;
            }

            for (final BlockPos endpoint : List.of(resolved.source(), resolved.sink())) {
                final BlockPos relative = endpoint.subtract(drivePos);
                minX = Math.min(minX, relative.getX());
                minY = Math.min(minY, relative.getY());
                minZ = Math.min(minZ, relative.getZ());
                maxX = Math.max(maxX, relative.getX());
                maxY = Math.max(maxY, relative.getY());
                maxZ = Math.max(maxZ, relative.getZ());
                any = true;
            }
        }

        if (!any) {
            return null;
        }

        final BlockPos offset = new BlockPos(minX, minY, minZ);
        final BlockPos size = new BlockPos(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);

        // * A box this big means the endpoints did not land where they should have,
        // * not that the player built something enormous. Refuse it and say where
        // * they actually resolved, rather than writing a nonsense region
        if (size.getX() > REGION_SANITY_LIMIT
                || size.getY() > REGION_SANITY_LIMIT
                || size.getZ() > REGION_SANITY_LIMIT) {
            DriveBySableMod.LOGGER.warn(
                    "[drivebywire-migration] Refusing a fitted region of {} at offset {} for the Drive at {}. "
                            + "Endpoints resolved to a box that large, which means they did not resolve correctly. "
                            + "snapshotVersion={}, ownerSnapshot={}, connections={}.",
                    size,
                    offset,
                    drivePos,
                    snapshot.getInt(SNAPSHOT_VERSION_KEY),
                    isSubLevelOwnedBackupSnapshot(snapshot),
                    countConnectionsInBackupSnapshot(snapshot)
            );
            return null;
        }

        // * Bounds are inclusive, the region size is not
        return new SnapshotRegion(offset, size);
    }

    private static boolean isReadable(final CompoundTag connection) {
        return connection.contains(SOURCE_KEY, Tag.TAG_LONG)
                && connection.contains(SINK_KEY, Tag.TAG_LONG)
                && connection.contains(DIRECTION_KEY, Tag.TAG_BYTE)
                && connection.contains(CHANNEL_KEY, Tag.TAG_STRING);
    }

    private static Rotation placementRotation(final CompoundTag snapshot, final Direction currentFacing) {
        final int snapshotVersion = snapshot.getInt(SNAPSHOT_VERSION_KEY);
        final Direction savedFacing = Direction.byName(snapshot.getString(FACING_KEY));
        return snapshotVersion >= OWNER_AWARE_SNAPSHOT_VERSION || savedFacing == null
                ? Rotation.NONE
                : getRotation(savedFacing, currentFacing);
    }

    private static BlockPos resolveSource(final CompoundTag connection, final BlockPos backupPos, final Rotation rotation) {
        return backupPos.offset(rotateRelative(BlockPos.of(connection.getLong(SOURCE_KEY)), rotation));
    }

    private static BlockPos resolveSink(final CompoundTag connection, final BlockPos backupPos, final Rotation rotation) {
        return backupPos.offset(rotateRelative(BlockPos.of(connection.getLong(SINK_KEY)), rotation));
    }

    // * A module sink keeps its stored direction
    private static Direction resolveSinkDirection(
            final CompoundTag connection,
            final String sinkChannel,
            final Rotation rotation
    ) {
        final Direction stored = Direction.from3DDataValue(connection.getByte(DIRECTION_KEY));
        return sinkChannel.isEmpty() ? rotateDirection(stored, rotation) : stored;
    }
    //#endregion

    // * Has every endpoint this snapshot would connect actually been placed?
    public boolean hasAllEndpointsLanded(
            final Level level,
            final BlockPos holderPos,
            final Direction currentFacing,
            final CompoundTag snapshot,
            final boolean giveUpWhenStalled
    ) {
        if (snapshot == null || !snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return true;
        }

        final Rotation rotation = placementRotation(snapshot, currentFacing);
        final boolean ownerAware = isOwnerAware(snapshot);
        final boolean worldSpace = isWorldSpaceSnapshot(snapshot);
        int landed = 0;
        int missing = 0;

        for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)) {
                continue;
            }

            final ResolvedPair resolved = resolveEndpoints(level, connection, holderPos, rotation, ownerAware, worldSpace);
            if (resolved.deferred()
                    || isGhostEndpoint(level, resolved.source())
                    || isGhostEndpoint(level, resolved.sink())) {
                missing++;
            } else {
                landed++;
            }
        }

        if (missing == 0) {
            bindProgress.remove(holderPos);
            return true;
        }

        if (!giveUpWhenStalled || !hasBindStalled(level, holderPos, landed)) {
            return false;
        }

        DriveBySableMod.LOGGER.warn(
                "[ghost-source] Snapshot at {} still had {} connection(s) with no block after {} ticks. "
                        + "Loading the {} that landed.",
                holderPos, missing, BIND_STALL_TICKS, landed
        );
        bindProgress.remove(holderPos);
        return true;
    }

    public RestoreResult restoreBackupSnapshot(
            final Level level,
            final BlockPos backupPos,
            final Direction currentFacing,
            final CompoundTag snapshot
    ) {
        if (isWorldSpaceSnapshot(snapshot)) {
            // * A pasted copy that never got pinned is still position based
            // * Resolving anyway lands the outputs on whatever happens to be there
            // * Usually the source's own sublevel
            // * Refuse and let the player try again once it has been pinned
            if (isPastedCopy(snapshot, backupPos)) {
                DriveBySableMod.LOGGER.warn(
                        "[schematic-debug] Drive {} was asked to load a pasted snapshot that has not been "
                                + "pinned to its sublevels yet. Nothing was restored.",
                        backupPos
                );

                return new RestoreResult(0, 0, countConnectionsInBackupSnapshot(snapshot), 0,
                        countConnectionsInBackupSnapshot(snapshot), false);
            }

            return restoreWorldSpaceBackupSnapshot(level, backupPos, snapshot);
        }

        final int snapshotVersion = snapshot.getInt(SNAPSHOT_VERSION_KEY);
        if (snapshotVersion >= OWNER_AWARE_SNAPSHOT_VERSION) {
            return restoreOwnerAwareBackupSnapshot(level, backupPos, snapshot);
        }

        return restoreRelativeBackupSnapshot(level, backupPos, currentFacing, snapshot);
    }

    // * Stores endpoints as offsets from the backup block itself
    private BackupSnapshot createRelativeBackupSnapshot(
            final Level level,
            final BlockPos backupPos,
            final SubLevel backupSubLevel,
            final Direction savedFacing
    ) {
        final CompoundTag tag = new CompoundTag();
        final ListTag connections = new ListTag();
        int internalConnections = 0;
        int skippedConnections = 0;

        for (final Map.Entry<Long, Map<String, Set<CableNetworkSink>>> sourceEntry : sinks.entrySet()) {
            final BlockPos sourcePos = BlockPos.of(sourceEntry.getKey());
            final boolean sourceInside = isSameSubLevel(backupSubLevel, Sable.HELPER.getContaining(level, sourcePos));

            for (final Map.Entry<String, Set<CableNetworkSink>> channelEntry : sourceEntry.getValue().entrySet()) {
                for (final CableNetworkSink sink : channelEntry.getValue()) {
                    final BlockPos sinkPos = BlockPos.of(sink.position());
                    final boolean sinkInside = isSameSubLevel(backupSubLevel, Sable.HELPER.getContaining(level, sinkPos));

                    final boolean reachesOff = sourceInside != sinkInside;
                    final boolean keep = (sourceInside && sinkInside)
                            || (reachesOff && BackupDriveCapture.crossLevelSavingAllowed());

                    if (keep) {
                        final CompoundTag connection = new CompoundTag();
                        connection.putLong(SOURCE_KEY, sourcePos.subtract(backupPos).asLong());
                        connection.putLong(SINK_KEY, sinkPos.subtract(backupPos).asLong());
                        connection.putByte(DIRECTION_KEY, (byte) sink.direction());
                        connection.putString(CHANNEL_KEY, channelEntry.getKey());
                        if (sink.isModule()) {
                            connection.putString(SINK_CHANNEL_KEY, sink.sinkChannel());
                        }
                        connections.add(connection);
                        internalConnections++;
                    } else if (reachesOff) {
                        skippedConnections++;
                    }
                }
            }
        }

        if (!connections.isEmpty()) {
            tag.put(CONNECTIONS_KEY, connections);
            tag.putString(FACING_KEY, savedFacing.getName());
            tag.putInt(SNAPSHOT_VERSION_KEY, RELATIVE_SNAPSHOT_VERSION);
        }
        if (skippedConnections > 0) {
            tag.putInt(UNSUPPORTED_CONNECTIONS_KEY, skippedConnections);
        }

        return new BackupSnapshot(tag, internalConnections, skippedConnections);
    }

    // * Only keeps links whose both ends stay inside the same blueprint batch
    private BackupSnapshot createSchematicBackupSnapshot(
            final Level level,
            final BlockPos backupPos,
            final SubLevel backupSubLevel,
            final SubLevelSchematicSerializationContext context
    ) {
        final CompoundTag tag = new CompoundTag();
        final SubLevelSchematicSerializationContext.SchematicMapping ownerMapping = context.getMapping(backupSubLevel);
        if (ownerMapping != null) {
            tag.putUUID(OWNER_SUB_LEVEL_KEY, ownerMapping.newUUID());
        }

        final ListTag connections = new ListTag();
        int preservedConnections = 0;
        int skippedConnections = 0;

        for (final Map.Entry<Long, Map<String, Set<CableNetworkSink>>> sourceEntry : sinks.entrySet()) {
            final BlockPos sourcePos = BlockPos.of(sourceEntry.getKey());
            final SubLevel sourceSubLevel = Sable.HELPER.getContaining(level, sourcePos);
            if (!isSameSubLevel(backupSubLevel, sourceSubLevel)) {
                continue;
            }

            for (final Map.Entry<String, Set<CableNetworkSink>> channelEntry : sourceEntry.getValue().entrySet()) {
                for (final CableNetworkSink sink : channelEntry.getValue()) {
                    final BlockPos sinkPos = BlockPos.of(sink.position());
                    final SubLevel sinkSubLevel = Sable.HELPER.getContaining(level, sinkPos);

                    final CompoundTag connection = new CompoundTag();
                    final boolean wroteSource = writeSchematicEndpoint(connection, SOURCE_KEY, SOURCE_OWNER_KEY, sourcePos, sourceSubLevel, context);
                    final boolean wroteSink = writeSchematicEndpoint(connection, SINK_KEY, SINK_OWNER_KEY, sinkPos, sinkSubLevel, context);
                    if (!wroteSource || !wroteSink) {
                        skippedConnections++;
                        continue;
                    }

                    connection.putByte(DIRECTION_KEY, (byte) sink.direction());
                    connection.putString(CHANNEL_KEY, channelEntry.getKey());
                    if (sink.isModule()) {
                        connection.putString(SINK_CHANNEL_KEY, sink.sinkChannel());
                    }
                    connections.add(connection);
                    preservedConnections++;
                }
            }
        }

        if (!connections.isEmpty()) {
            tag.put(CONNECTIONS_KEY, connections);
            tag.putInt(SNAPSHOT_VERSION_KEY, OWNER_AWARE_SNAPSHOT_VERSION);
        }
        if (skippedConnections > 0) {
            tag.putInt(UNSUPPORTED_CONNECTIONS_KEY, skippedConnections);
        }

        return new BackupSnapshot(tag, preservedConnections, skippedConnections);
    }

    private boolean writeSchematicEndpoint(
            final CompoundTag connection,
            final String positionKey,
            final String ownerKey,
            final BlockPos endpointPos,
            final SubLevel endpointSubLevel,
            final SubLevelSchematicSerializationContext context
    ) {
        if (endpointSubLevel == null) {
            return false;
        }

        final SubLevelSchematicSerializationContext.SchematicMapping mapping = context.getMapping(endpointSubLevel);
        if (mapping != null) {
            connection.putUUID(ownerKey, mapping.newUUID());
            connection.putLong(positionKey, mapping.transform().apply(endpointPos).asLong());
            return true;
        }

        return context.getBoundingBox() != null
                && context.getBoundingBox().contains(endpointPos.getX(), endpointPos.getY(), endpointPos.getZ())
                && writeMainTemplateEndpoint(connection, positionKey, endpointPos, context);
    }

    private boolean writeMainTemplateEndpoint(
            final CompoundTag connection,
            final String positionKey,
            final BlockPos endpointPos,
            final SubLevelSchematicSerializationContext context
    ) {
        if (context.getPlaceTransform() == null) {
            return false;
        }

        connection.putLong(positionKey, context.getPlaceTransform().apply(endpointPos).asLong());
        return true;
    }

    // * Puts each endpoint back wherever that spot is now, whichever level owns it
    private RestoreResult restoreWorldSpaceBackupSnapshot(
            final Level level,
            final BlockPos backupPos,
            final CompoundTag snapshot
    ) {
        final SubLevel driveSubLevel = BackupDriveCapture.subLevelOf(level, backupPos);
        int restoredConnections = 0;
        int existingConnections = 0;
        int expectedConnections = 0;
        int skippedConnections = 0;

        if (snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            for (final Tag entry : snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
                if (!(entry instanceof final CompoundTag connection) || !isReadable(connection)) {
                    continue;
                }

                expectedConnections++;

                final BlockPos sourcePos = resolveWorldSpaceEndpoint(level, backupPos, driveSubLevel,
                        connection, SOURCE_KEY, SOURCE_LEVEL_KEY);
                final BlockPos sinkPos = resolveWorldSpaceEndpoint(level, backupPos, driveSubLevel,
                        connection, SINK_KEY, SINK_LEVEL_KEY);
                if (sourcePos == null || sinkPos == null) {
                    skippedConnections++;
                    continue;
                }
                final String channel = connection.getString(CHANNEL_KEY);
                final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
                final Direction sinkDirection = Direction.from3DDataValue(connection.getByte(DIRECTION_KEY));

                if (isAlreadyConnected(level, sourcePos, sinkPos, sinkDirection, channel, sinkChannel)) {
                    existingConnections++;
                    continue;
                }

                if (addConnection(level, sourcePos, sinkPos, sinkDirection, channel, sinkChannel).isSuccess()) {
                    restoredConnections++;
                } else {
                    skippedConnections++;
                }
            }
        }

        return new RestoreResult(restoredConnections, existingConnections, 0,
                skippedConnections, expectedConnections, true);
    }

    // * Reapplies offsets from the relative snapshot at the current backup pos
    private RestoreResult restoreRelativeBackupSnapshot(
            final Level level,
            final BlockPos backupPos,
            final Direction currentFacing,
            final CompoundTag snapshot
    ) {
        // * Null means the drive is loose in world
        final SubLevel backupSubLevel = Sable.HELPER.getContaining(level, backupPos);

        final int snapshotVersion = snapshot.getInt(SNAPSHOT_VERSION_KEY);
        final Direction savedFacing = Direction.byName(snapshot.getString(FACING_KEY));
        final Rotation rotation = snapshotVersion >= OWNER_AWARE_SNAPSHOT_VERSION || savedFacing == null
                ? Rotation.NONE
                : getRotation(savedFacing, currentFacing);
        int restoredConnections = 0;
        int deferredConnections = 0;
        int existingConnections = 0;
        int expectedConnections = 0;

        if (snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            final ListTag connections = snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND);
            for (final Tag entry : connections) {
                if (!(entry instanceof final CompoundTag connection)) {
                    continue;
                }

                if (!connection.contains(SOURCE_KEY, Tag.TAG_LONG)
                        || !connection.contains(SINK_KEY, Tag.TAG_LONG)
                        || !connection.contains(DIRECTION_KEY, Tag.TAG_BYTE)
                        || !connection.contains(CHANNEL_KEY, Tag.TAG_STRING)) {
                    continue;
                }

                expectedConnections++;
                final BlockPos sourcePos = backupPos.offset(rotateRelative(BlockPos.of(connection.getLong(SOURCE_KEY)), rotation));
                final BlockPos sinkPos = backupPos.offset(rotateRelative(BlockPos.of(connection.getLong(SINK_KEY)), rotation));
                final String channel = connection.getString(CHANNEL_KEY);
                final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
                // * Module sinks carry no facing
                final Direction sinkDirection = sinkChannel.isEmpty()
                        ? rotateDirection(Direction.from3DDataValue(connection.getByte(DIRECTION_KEY)), rotation)
                        : Direction.from3DDataValue(connection.getByte(DIRECTION_KEY));

                // * A relative snapshot predates cross level saving
                // * It can be converted now, as long as the config permits it
                if (!BackupDriveCapture.isSameLevel(backupSubLevel, Sable.HELPER.getContaining(level, sourcePos))
                        || !BackupDriveCapture.isSameLevel(backupSubLevel, Sable.HELPER.getContaining(level, sinkPos))) {
                    if (CableConfig.CONFIG.forbidCrossLevelConnections.get()) {
                        deferredConnections++;
                        continue;
                    }
                }

                if (isAlreadyConnected(level, sourcePos, sinkPos, sinkDirection, channel, sinkChannel)) {
                    existingConnections++;
                    continue;
                }

                if (isGhostEndpoint(level, sourcePos) || isGhostEndpoint(level, sinkPos)) {
                    deferredConnections++;
                    continue;
                }

                if (addConnection(level, sourcePos, sinkPos, sinkDirection, channel, sinkChannel).isSuccess()) {
                    restoredConnections++;
                }
            }
        }

        return new RestoreResult(
                restoredConnections,
                existingConnections,
                deferredConnections,
                snapshot.getInt(UNSUPPORTED_CONNECTIONS_KEY),
                expectedConnections,
                true
        );
    }

    // * Waits for both endpoints sublevels to exist before wiring back up
    private RestoreResult restoreOwnerAwareBackupSnapshot(
            final Level level,
            final BlockPos backupPos,
            final CompoundTag snapshot
    ) {
        int restoredConnections = 0;
        int deferredConnections = 0;
        int existingConnections = 0;
        int expectedConnections = 0;

        if (snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            final ListTag connections = snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND);
            for (final Tag entry : connections) {
                if (!(entry instanceof final CompoundTag connection)) {
                    continue;
                }

                if (!connection.contains(SOURCE_KEY, Tag.TAG_LONG)
                        || !connection.contains(SINK_KEY, Tag.TAG_LONG)
                        || !connection.contains(DIRECTION_KEY, Tag.TAG_BYTE)
                        || !connection.contains(CHANNEL_KEY, Tag.TAG_STRING)) {
                    continue;
                }

                expectedConnections++;
                final ResolvedEndpoint source = resolveOwnerAwareEndpoint(level, connection, SOURCE_KEY, SOURCE_OWNER_KEY, backupPos);
                final ResolvedEndpoint sink = resolveOwnerAwareEndpoint(level, connection, SINK_KEY, SINK_OWNER_KEY, backupPos);
                if (source.isDeferred() || sink.isDeferred()) {
                    deferredConnections++;
                    continue;
                }

                final BlockPos sourcePos = source.position();
                final BlockPos sinkPos = sink.position();
                final Direction sinkDirection = Direction.from3DDataValue(connection.getByte(DIRECTION_KEY));
                final String channel = connection.getString(CHANNEL_KEY);
                final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);

                if (isAlreadyConnected(level, sourcePos, sinkPos, sinkDirection, channel, sinkChannel)) {
                    existingConnections++;
                    continue;
                }

                if (isGhostEndpoint(level, sourcePos) || isGhostEndpoint(level, sinkPos)) {
                    deferredConnections++;
                    continue;
                }

                if (addConnection(level, sourcePos, sinkPos, sinkDirection, channel, sinkChannel).isSuccess()) {
                    restoredConnections++;
                }
            }
        }

        return new RestoreResult(
                restoredConnections,
                existingConnections,
                deferredConnections,
                snapshot.getInt(UNSUPPORTED_CONNECTIONS_KEY),
                expectedConnections,
                true
        );
    }

    // * Finds current position for a saved endpoint, deferred if its sublevel isnt loaded yet
    private ResolvedEndpoint resolveOwnerAwareEndpoint(
            final Level level,
            final CompoundTag connection,
            final String positionKey,
            final String ownerKey
    ) {
        return resolveOwnerAwareEndpoint(level, connection, positionKey, ownerKey, BlockPos.ZERO);
    }

    private ResolvedEndpoint resolveOwnerAwareEndpoint(
            final Level level,
            final CompoundTag connection,
            final String positionKey,
            final String ownerKey,
            final BlockPos drivePos
    ) {
        if (connection.hasUUID(ownerKey)) {
            final UUID ownerId = connection.getUUID(ownerKey);
            final SubLevel ownerSubLevel = SubLevelContainer.getContainer(level).getSubLevel(ownerId);
            if (ownerSubLevel == null) {
                DriveBySableMod.LOGGER.info(
                        "[schematic-debug] Deferred owner-aware endpoint {} because subLevel {} is not available yet.",
                        positionKey,
                        ownerId
                );
                return ResolvedEndpoint.waiting();
            }

            return ResolvedEndpoint.resolved(ownerSubLevel.getPlot().getCenterBlock().offset(BlockPos.of(connection.getLong(positionKey))));
        }

        // * A legacy endpoint this mod moved into the Drive's space
        final String relativeKey = SOURCE_KEY.equals(positionKey)
                ? LegacyWireCompat.SOURCE_DRIVE_RELATIVE_KEY
                : LegacyWireCompat.SINK_DRIVE_RELATIVE_KEY;

        if (connection.getBoolean(relativeKey)) {
            return ResolvedEndpoint.resolved(drivePos.offset(BlockPos.of(connection.getLong(positionKey))));
        }

        return ResolvedEndpoint.resolved(BlockPos.of(connection.getLong(positionKey)));
    }
    //#endregion

    //#region // --- LEVEL ATTACH AND PERSISTENCE --- //
    public void attachLevel(final Level level) {
        if (attachedToLevel) {
            return;
        }

        attachedToLevel = true;
        sinks.forEach((sourceKey, perChannel) -> {
            final Set<CableNetworkSink> worldSinks = perChannel.get(WORLD_CHANNEL);
            if (worldSinks == null || worldSinks.isEmpty()) {
                return;
            }

            final BlockPos sourcePos = BlockPos.of(sourceKey);
            final int signal = getCurrentSignal(level, sourcePos, WORLD_CHANNEL);
            worldSinks.forEach(sink -> applySignalToSink(level, sourceKey, WORLD_CHANNEL, sink, signal));
        });
    }

    // * Rebuilds node graph
    public void markDirtyIfChunkInvolved(final ChunkPos chunk) {
        if (graphDirty) {
            return;
        }

        for (final Map.Entry<Long, Map<String, Set<CableNetworkSink>>> sourceEntry : sinks.entrySet()) {
            if (isInChunk(BlockPos.of(sourceEntry.getKey()), chunk)) {
                graphDirty = true;
                return;
            }

            for (final Set<CableNetworkSink> sinksOnChannel : sourceEntry.getValue().values()) {
                for (final CableNetworkSink sink : sinksOnChannel) {
                    if (isInChunk(sink.blockPos(), chunk)) {
                        graphDirty = true;
                        return;
                    }
                }
            }
        }
    }

    // * Checks graph against the world in loaded chunks and tries to kill orphans
    public void pruneOrphanedEndpoints(final Level level, final ChunkAccess chunk) {
        final ChunkPos chunkPos = chunk.getPos();

        final List<BlockPos> orphanedSources = new ArrayList<>();
        for (final long sourceKey : sinks.keySet()) {
            final BlockPos pos = BlockPos.of(sourceKey);
            if (isInChunk(pos, chunkPos) && isOrphanedEndpoint(chunk, pos)) {
                orphanedSources.add(pos);
            }
        }

        final List<Long> orphanedSinks = new ArrayList<>();
        for (final long sinkKey : sinkReferences.keySet()) {
            final BlockPos pos = BlockPos.of(sinkKey);
            if (isInChunk(pos, chunkPos) && isOrphanedEndpoint(chunk, pos)) {
                orphanedSinks.add(sinkKey);
            }
        }

        if (orphanedSources.isEmpty() && orphanedSinks.isEmpty()) {
            return;
        }

        for (final BlockPos source : orphanedSources) {
            removeAllFromSourceInternal(null, level, source);
        }
        for (final long sinkKey : orphanedSinks) {
            dropEverythingIntoSink(level, sinkKey);
        }

        graphDirty = true;
        dirtyMarker.run();

        // * Logged in case this doesn't fix ghostly sources
        DriveBySableMod.LOGGER.info(
                "[ghost-source] Dropped {} orphaned source(s) and {} orphaned output(s) in chunk {} "
                        + "of {}, none of which had a block left. Sources: {}. Outputs: {}.",
                orphanedSources.size(),
                orphanedSinks.size(),
                chunkPos,
                level.dimension().location(),
                orphanedSources,
                orphanedSinks.stream().map(BlockPos::of).toList()
        );
    }

    //#region // --- SUBLEVEL PLOT CLEANUP --- //
    public static void purgeRemovedPlot(final ServerLevel level, final LevelPlot plot, final String reason) {
        LinkedGearboxLinks.leaveNetworkWhere(level, pos -> isInPlot(plot, pos.asLong()));
        get(level).purgeEndpointsWhere(level, key -> isInPlot(plot, key), reason);
    }

    private static boolean isInPlot(final LevelPlot plot, final long key) {
        return plot.contains(new ChunkPos(
                SectionPos.blockToSectionCoord(BlockPos.getX(key)),
                SectionPos.blockToSectionCoord(BlockPos.getZ(key))
        ));
    }

    // * Drops every source and output whose position matches
    private int purgeEndpointsWhere(final Level level, final LongPredicate isStale, final String reason) {
        final List<Long> staleSources = new ArrayList<>();
        for (final long sourceKey : sinks.keySet()) {
            if (isStale.test(sourceKey)) {
                staleSources.add(sourceKey);
            }
        }

        final List<Long> staleSinks = new ArrayList<>();
        for (final long sinkKey : sinkReferences.keySet()) {
            if (isStale.test(sinkKey)) {
                staleSinks.add(sinkKey);
            }
        }

        int dropped = 0;

        for (final long sourceKey : staleSources) {
            final Map<String, Set<CableNetworkSink>> perChannel = sinks.remove(sourceKey);
            sourceValues.remove(sourceKey);
            if (perChannel == null) {
                continue;
            }

            for (final Map.Entry<String, Set<CableNetworkSink>> channelEntry : perChannel.entrySet()) {
                for (final CableNetworkSink sink : channelEntry.getValue()) {
                    removeSinkReference(sourceKey, channelEntry.getKey(), sink);
                    dropped++;

                    // * Blocks inside the region are gone, only real outputs get told
                    if (!isStale.test(sink.position())) {
                        applySignalToSink(level, sourceKey, channelEntry.getKey(), sink, 0);
                    }
                }
            }
        }

        for (final long sinkKey : staleSinks) {
            final Set<SinkReference> references = sinkReferences.remove(sinkKey);
            if (references == null) {
                continue;
            }

            for (final SinkReference reference : references) {
                final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(reference.sourcePos());
                if (perChannel == null) {
                    continue;
                }

                final Set<CableNetworkSink> sinksOnChannel = perChannel.get(reference.channel());
                if (sinksOnChannel == null) {
                    continue;
                }

                if (sinksOnChannel.remove(new CableNetworkSink(sinkKey, reference.direction(), reference.sinkChannel()))) {
                    dropped++;
                }
                if (sinksOnChannel.isEmpty()) {
                    perChannel.remove(reference.channel());
                }
                if (perChannel.isEmpty()) {
                    sinks.remove(reference.sourcePos());
                    sourceValues.remove(reference.sourcePos());
                }
            }
        }

        // * Silently forget graph state for the region so nothing pokes a vanished plot
        sourceValues.keySet().removeIf(isStale::test);
        nodes.keySet().removeIf(face -> isStale.test(face.pos()));
        staleFaces.removeIf(face -> isStale.test(face.pos()));
        moduleNodes.keySet().removeIf(key -> isStale.test(key.position()));

        if (dropped > 0) {
            dirtyMarker.run();
            DriveBySableMod.LOGGER.info(
                    "[ghost-source] Dropped {} connection(s) from {} source(s) and {} output(s) left behind in {} ({}).",
                    dropped,
                    staleSources.size(),
                    staleSinks.size(),
                    level.dimension().location(),
                    reason
            );
        }

        return dropped;
    }
    //#endregion

    // * Air, and not a block that a sublevel assembly is halfway through moving
    private boolean isOrphanedEndpoint(final ChunkAccess chunk, final BlockPos pos) {
        return chunk.getBlockState(pos).isAir() && !pendingAssemblyPositions.contains(pos.asLong());
    }

    // * Every connection pointing at one position, whichever source owns it
    private void dropEverythingIntoSink(final Level level, final long sinkKey) {
        final Set<SinkReference> references = sinkReferences.remove(sinkKey);
        if (references == null) {
            return;
        }

        for (final SinkReference reference : references) {
            final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(reference.sourcePos());
            if (perChannel == null) {
                continue;
            }

            final Set<CableNetworkSink> sinksOnChannel = perChannel.get(reference.channel());
            if (sinksOnChannel == null) {
                continue;
            }

            final CableNetworkSink sink =
                    new CableNetworkSink(sinkKey, reference.direction(), reference.sinkChannel());
            if (!sinksOnChannel.remove(sink)) {
                continue;
            }

            applySignalToSink(level, reference.sourcePos(), reference.channel(), sink, 0);

            if (sinksOnChannel.isEmpty()) {
                perChannel.remove(reference.channel());
            }
            if (perChannel.isEmpty()) {
                sinks.remove(reference.sourcePos());
                sourceValues.remove(reference.sourcePos());
            }
        }
    }

    private static boolean isInChunk(final BlockPos pos, final ChunkPos chunk) {
        return SectionPos.blockToSectionCoord(pos.getX()) == chunk.x
                && SectionPos.blockToSectionCoord(pos.getZ()) == chunk.z;
    }

    public void flushPendingGraphRebuild(final Level level) {
        if (!graphDirty) {
            return;
        }

        final Set<BlockFace> previousFaces = new HashSet<>(staleFaces);
        final Set<ModuleSinkKey> previousModuleKeys = new HashSet<>(moduleNodes.keySet());
        staleFaces.clear();
        nodes.clear();
        moduleNodes.clear();

        sinks.forEach((sourceKey, perChannel) -> perChannel.forEach((channel, sinksOnChannel) -> {
            final int signal = getCurrentSignal(level, BlockPos.of(sourceKey), channel);
            sinksOnChannel.forEach(sink -> applySignalToSink(level, sourceKey, channel, sink, signal));
        }));

        previousFaces.removeAll(nodes.keySet());
        previousFaces.forEach(face -> notifySink(level, face));

        // * A module that lost all of its feeds has to be told to go dark
        previousModuleKeys.removeAll(moduleNodes.keySet());
        previousModuleKeys.forEach(key -> pushModuleSignal(level, key, 0));

        graphDirty = false;
    }

    public CompoundTag save(final CompoundTag tag) {
        final ListTag connections = new ListTag();
        sinks.forEach((sourceKey, perChannel) -> perChannel.forEach((channel, sinksOnChannel) -> sinksOnChannel.forEach(sink -> {
            final CompoundTag connection = new CompoundTag();
            connection.putLong(SOURCE_KEY, sourceKey);
            connection.putLong(SINK_KEY, sink.position());
            connection.putByte(DIRECTION_KEY, (byte) sink.direction());
            connection.putString(CHANNEL_KEY, channel);
            if (sink.isModule()) {
                connection.putString(SINK_CHANNEL_KEY, sink.sinkChannel());
            }
            connections.add(connection);
        })));
        tag.put(CONNECTIONS_KEY, connections);
        return tag;
    }

    // * Rebuilds sinkReferences and nodes from the raw sinks map after loading
    public void load(final CompoundTag tag) {
        sinks.clear();
        sinkReferences.clear();
        sourceValues.clear();
        nodes.clear();
        moduleNodes.clear();
        staleFaces.clear();
        attachedToLevel = false;
        graphDirty = false;

        if (!tag.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return;
        }

        final ListTag connections = tag.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND);
        for (final Tag entry : connections) {
            if (!(entry instanceof final CompoundTag connection)) {
                continue;
            }

            if (!connection.contains(SOURCE_KEY, Tag.TAG_LONG)
                    || !connection.contains(SINK_KEY, Tag.TAG_LONG)
                    || !connection.contains(DIRECTION_KEY, Tag.TAG_BYTE)
                    || !connection.contains(CHANNEL_KEY, Tag.TAG_STRING)) {
                continue;
            }

            final long sourceKey = connection.getLong(SOURCE_KEY);
            final long sinkKey = connection.getLong(SINK_KEY);
            final int direction = connection.getByte(DIRECTION_KEY);
            final String channel = connection.getString(CHANNEL_KEY);
            // * Missing on anything saved before module sinks existed
            final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
            final CableNetworkSink sink = new CableNetworkSink(sinkKey, direction, sinkChannel);
            getOrCreateSinksOnChannel(BlockPos.of(sourceKey), channel).add(sink);
            addSinkReference(sourceKey, channel, sink);
        }

        // * Moved reload marker here
        graphDirty = true;

        translateLegacyTypewriterChannels();
    }

    // * Rewrites any surviving drivebywiretypewriter.key.* channel names to their DBS equivalents
    private void translateLegacyTypewriterChannels() {
        boolean changed = false;

        for (final Map.Entry<Long, Map<String, Set<CableNetworkSink>>> sourceEntry :
                new ArrayList<>(sinks.entrySet())) {
            final long sourceKey = sourceEntry.getKey();
            final Map<String, Set<CableNetworkSink>> perChannel = sourceEntry.getValue();

            for (final String channel : new ArrayList<>(perChannel.keySet())) {
                if (!LegacyTypewriterCompat.isLegacyChannel(channel)) {
                    continue;
                }

                final String translated = LegacyTypewriterCompat.translateChannel(channel);
                final Set<CableNetworkSink> sinksOnChannel = perChannel.remove(channel);
                perChannel.computeIfAbsent(translated, ignored -> new HashSet<>()).addAll(sinksOnChannel);

                for (final CableNetworkSink sink : sinksOnChannel) {
                    removeSinkReference(sourceKey, channel, sink);
                    addSinkReference(sourceKey, translated, sink);
                }

                changed = true;
            }
        }

        if (changed) {
            DriveBySableMod.LOGGER.info(
                    "[drivebywire-migration] Translated legacy drivebywiretypewriter channel names "
                            + "to DBS equivalents in the network graph."
            );
            dirtyMarker.run();
        }
    }

    public int mergeSavedConnections(final Level level, final CompoundTag tag) {
        if (tag == null || !tag.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return 0;
        }

        // * This writes into the graph without going through addConnection
        final boolean forbidCrossLevel = CableConfig.CONFIG.forbidCrossLevelConnections.get();

        int merged = 0;
        final ListTag connections = tag.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND);
        for (final Tag entry : connections) {
            if (!(entry instanceof final CompoundTag connection)) {
                continue;
            }

            if (!connection.contains(SOURCE_KEY, Tag.TAG_LONG)
                    || !connection.contains(SINK_KEY, Tag.TAG_LONG)
                    || !connection.contains(DIRECTION_KEY, Tag.TAG_BYTE)
                    || !connection.contains(CHANNEL_KEY, Tag.TAG_STRING)) {
                continue;
            }

            final long sourceKey = connection.getLong(SOURCE_KEY);
            final long sinkKey = connection.getLong(SINK_KEY);
            final int direction = connection.getByte(DIRECTION_KEY);
            // * The Typewriter addon stored its channels as translation keys
            final String channel = LegacyTypewriterCompat.translateChannel(connection.getString(CHANNEL_KEY));
            // * Absent on anything DBW wrote
            final String sinkChannel = connection.getString(SINK_CHANNEL_KEY);
            final CableNetworkSink sink = new CableNetworkSink(sinkKey, direction, sinkChannel);

            if (forbidCrossLevel && level != null && !isSameSubLevelContext(
                    Sable.HELPER.getContaining(level, BlockPos.of(sourceKey)),
                    Sable.HELPER.getContaining(level, BlockPos.of(sinkKey)))) {
                continue;
            }

            // * A duplicate is a no op
            if (!getOrCreateSinksOnChannel(BlockPos.of(sourceKey), channel).add(sink)) {
                continue;
            }

            addSinkReference(sourceKey, channel, sink);
            merged++;
        }

        if (merged > 0) {
            graphDirty = true;
            dirtyMarker.run();
        }

        return merged;
    }

    //#endregion

    //#region // --- SUBLEVEL MOVE REMAP --- //
    // * Rewrites stored positions when a sublevel structure gets moved or rotated
    private void remapMovedBlockInternal(final BlockPos oldPos, final SubLevelAssemblyHelper.AssemblyTransform transform) {
        final BlockPos newPos = transform.apply(oldPos);
        if (oldPos.equals(newPos)) {
            return;
        }

        final long oldKey = oldPos.asLong();
        final long newKey = newPos.asLong();
        final Map<String, Set<CableNetworkSink>> movedSourceConnections = sinks.remove(oldKey);
        final Map<String, Integer> movedSourceValues = sourceValues.remove(oldKey);
        final Set<SinkReference> movedSinkReferences = sinkReferences.remove(oldKey);
        if (movedSourceConnections == null && movedSourceValues == null && movedSinkReferences == null) {
            return;
        }

        staleFaces.addAll(nodes.keySet());
        boolean changed = false;

        if (movedSourceConnections != null) {
            final Map<String, Set<CableNetworkSink>> targetPerChannel = sinks.computeIfAbsent(newKey, ignored -> new HashMap<>());
            movedSourceConnections.forEach((channel, movedSinksOnChannel) -> {
                targetPerChannel.computeIfAbsent(channel, ignored -> new HashSet<>()).addAll(movedSinksOnChannel);
                movedSinksOnChannel.forEach(sink -> {
                    removeSinkReference(oldKey, channel, sink);
                    addSinkReference(newKey, channel, sink);
                });
            });
            changed = true;
        }

        if (movedSourceValues != null) {
            movedSourceValues.remove(WORLD_CHANNEL);
            if (!movedSourceValues.isEmpty()) {
                sourceValues.computeIfAbsent(newKey, ignored -> new HashMap<>()).putAll(movedSourceValues);
            }
            changed = true;
        }

        if (movedSinkReferences != null) {
            for (final SinkReference reference : movedSinkReferences) {
                final Map<String, Set<CableNetworkSink>> perChannel = sinks.get(reference.sourcePos());
                if (perChannel == null) {
                    continue;
                }

                final Set<CableNetworkSink> sinksOnChannel = perChannel.get(reference.channel());
                if (sinksOnChannel == null) {
                    continue;
                }

                final String sinkChannel = reference.sinkChannel();
                if (sinksOnChannel.remove(new CableNetworkSink(oldKey, reference.direction(), sinkChannel))) {
                    // * A module sink has no meaningful facing
                    final int newDirection = sinkChannel.isEmpty()
                            ? transform.getRotation().rotate(Direction.from3DDataValue(reference.direction())).get3DDataValue()
                            : reference.direction();
                    sinksOnChannel.add(new CableNetworkSink(newKey, newDirection, sinkChannel));
                    addSinkReference(newKey, reference.sourcePos(), reference.channel(), newDirection, sinkChannel);
                    changed = true;
                }
            }
        }

        if (changed) {
            graphDirty = true;
            dirtyMarker.run();
        }
    }

    //#endregion

    //#region // --- INTERNAL HELPERS --- //
    private Set<CableNetworkSink> getOrCreateSinksOnChannel(final BlockPos source, final String channel) {
        return sinks.computeIfAbsent(source.asLong(), ignored -> new HashMap<>())
                .computeIfAbsent(channel, ignored -> new HashSet<>());
    }

    private void addSinkReference(final long sourcePos, final String channel, final CableNetworkSink sink) {
        addSinkReference(sink.position(), sourcePos, channel, sink.direction(), sink.sinkChannel());
    }

    private void addSinkReference(
            final long sinkPos,
            final long sourcePos,
            final String channel,
            final int direction,
            final String sinkChannel
    ) {
        sinkReferences.computeIfAbsent(sinkPos, ignored -> new HashSet<>())
                .add(new SinkReference(sourcePos, channel, direction, sinkChannel));
    }

    private void removeSinkReference(final long sourcePos, final String channel, final CableNetworkSink sink) {
        final Set<SinkReference> references = sinkReferences.get(sink.position());
        if (references == null) {
            return;
        }

        references.remove(new SinkReference(sourcePos, channel, sink.direction(), sink.sinkChannel()));
        if (references.isEmpty()) {
            sinkReferences.remove(sink.position());
        }
    }

    private int countSourcesInSameDomain(final Level level, final BlockPos source) {
        final SubLevel sourceSubLevel = Sable.HELPER.getContaining(level, source);
        final UUID sourceSubLevelId = sourceSubLevel == null ? null : sourceSubLevel.getUniqueId();

        int count = 0;
        for (final long existingSourceKey : sinks.keySet()) {
            if (LinkedGearboxLinks.isGearbox(level, BlockPos.of(existingSourceKey))) {
                continue;
            }
            if (isSameSourceDomain(level, BlockPos.of(existingSourceKey), sourceSubLevelId)) {
                count++;
            }
        }
        return count;
    }

    private boolean isSameSourceDomain(final Level level, final BlockPos source, final UUID expectedSubLevelId) {
        final SubLevel sourceSubLevel = Sable.HELPER.getContaining(level, source);
        final UUID sourceSubLevelId = sourceSubLevel == null ? null : sourceSubLevel.getUniqueId();
        return Objects.equals(sourceSubLevelId, expectedSubLevelId);
    }

    private int getCurrentSignal(final Level level, final BlockPos source, final String channel) {
        final Integer stored = sourceValues.getOrDefault(source.asLong(), Map.of()).get(channel);
        if (stored != null) {
            return stored;
        }

        if (WORLD_CHANNEL.equals(channel)) {
            final int computed = computeWorldSignal(level, source);
            if (computed > 0) {
                sourceValues.computeIfAbsent(source.asLong(), ignored -> new HashMap<>()).put(channel, computed);
            }
            return computed;
        }

        return 0;
    }

    private void applySignalToSink(
            final Level level,
            final long sourcePos,
            final String channel,
            final CableNetworkSink sink,
            final int signal
    ) {
        final BlockPos sinkPos = BlockPos.of(sink.position());

        // * Module sinks have nothing to query them back
        if (sink.isModule()) {
            final ModuleSinkKey key = sink.moduleKey();
            final CableNetworkNode moduleNode = moduleNodes.computeIfAbsent(
                    key,
                    ignored -> new CableNetworkNode(sink.position(), sink.direction())
            );

            if (!moduleNode.setInput(new InputKey(sourcePos, channel), signal)) {
                return;
            }

            final int aggregated = moduleNode.getSignal();
            if (moduleNode.isEmpty()) {
                moduleNodes.remove(key);
            }

            pushModuleSignal(level, key, aggregated);
            return;
        }

        final Direction sinkDirection = Direction.from3DDataValue(sink.direction());
        final BlockFace face = BlockFace.of(sinkPos, sinkDirection);
        final CableNetworkNode node = nodes.computeIfAbsent(face, ignored -> new CableNetworkNode(sink.position(), sink.direction()));

        if (!node.setInput(new InputKey(sourcePos, channel), signal)) {
            return;
        }

        if (node.isEmpty()) {
            nodes.remove(face);
        }

        final BlockPos updatedPos = sinkPos.relative(sinkDirection);
        level.updateNeighborsAt(updatedPos, level.getBlockState(updatedPos).getBlock());
    }

    // * Hand the value to the block
    private void pushModuleSignal(final Level level, final ModuleSinkKey key, final int signal) {
        final BlockPos pos = key.blockPos();
        if (level.getBlockState(pos).getBlock() instanceof final ModuleSinkTarget target) {
            target.cable$applySinkSignal(level, pos, key.channel(), signal);
        }
    }

    private void notifySink(final Level level, final BlockFace face) {
        final BlockPos sinkPos = BlockPos.of(face.pos());
        final Direction sinkDirection = Direction.from3DDataValue(face.dir());
        final BlockPos updatedPos = sinkPos.relative(sinkDirection);
        level.updateNeighborsAt(updatedPos, level.getBlockState(updatedPos).getBlock());
    }
    //#endregion

    // --- NESTED TYPES --- //
    public enum ConnectionResult {
        OK("", ""),
        FAIL_EXISTS("Connection already exists!", "drivebysable.invalid_op.connection_exists"),
        // * Key depends on whether the source sits in a sublevel
        FAIL_TOO_MANY_SOURCES("Exceeded source limit for this structure!", ""),
        FAIL_TOO_MANY_SINKS("Exceeded sink limit for this source!", "drivebysable.invalid_op.too_many_sinks"),
        FAIL_SAME_BLOCK("Source and sink must be different blocks!", "drivebysable.invalid_op.same_block"),
        FAIL_INVALID_CHANNEL("This channel is not available on this source!", "drivebysable.invalid_op.stale_source_channel"),
        FAIL_INVALID_SINK_CHANNEL("This channel is not available on this output!", "drivebysable.invalid_op.stale_output_channel"),
        FAIL_OUT_OF_RANGE("That output is too far from this source!", "drivebysable.invalid_op.out_of_range"),
        FAIL_CROSS_LEVEL("Cross-level connections are disabled!", "drivebysable.invalid_op.cross_level"),
        FAIL_GEARBOX_TARGET_REQUIRED("Linked Gearboxes can only link to other Linked Gearboxes!", "drivebysable.invalid_op.gearbox_target_required"),
        FAIL_GEARBOX_SUBLEVEL_ONLY("Linked Gearboxes must be on a sublevel!", "drivebysable.invalid_op.gearbox_sublevel_only"),
        FAIL_GEARBOX_OUT_OF_RANGE("That Linked Gearbox is out of range!", "drivebysable.invalid_op.gearbox_out_of_range");

        private final String description;
        private final String langKey;

        ConnectionResult(final String description, final String langKey) {
            this.description = description;
            this.langKey = langKey;
        }

        public String getLangKey() {
            return langKey;
        }

        // * Some failures read differently depending on where the source is
        public String resolveLangKey(final Level level, final BlockPos source) {
            if (this == FAIL_TOO_MANY_SOURCES) {
                return sourceLimitLangKey(level, source);
            }
            return langKey;
        }

        public boolean isSuccess() {
            return this == OK;
        }

        public String getDescription() {
            return description;
        }
    }

    public record BackupSnapshot(CompoundTag data, int internalConnections, int skippedConnections) {
    }

    public record RestoreResult(
            int restoredConnections,
            int existingConnections,
            int deferredConnections,
            int skippedConnections,
            int expectedConnections,
            boolean attempted
    ) {
    }

    // * A stored connection targets a module rather than a face
    private static boolean isModuleSinkTag(final CompoundTag connection) {
        return connection.contains(SINK_CHANNEL_KEY, Tag.TAG_STRING)
                && !connection.getString(SINK_CHANNEL_KEY).isEmpty();
    }

    private record ResolvedEndpoint(BlockPos position, boolean isDeferred) {
        private static ResolvedEndpoint resolved(final BlockPos position) {
            return new ResolvedEndpoint(position, false);
        }

        private static ResolvedEndpoint waiting() {
            return new ResolvedEndpoint(BlockPos.ZERO, true);
        }
    }

    // * sinkChannel is empty for a block face, named for a module sink
    private record SinkReference(long sourcePos, String channel, int direction, String sinkChannel) {
        private SinkReference {
            sinkChannel = sinkChannel == null ? CableNetworkSink.BLOCK_FACE : sinkChannel;
        }
    }

    //#region // --- SNAPSHOT QUERY HELPERS --- //
    public static int countConnectionsInBackupSnapshot(final CompoundTag snapshot) {
        if (!snapshot.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return 0;
        }

        return snapshot.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND).size();
    }

    public static int countUnsupportedConnectionsInBackupSnapshot(final CompoundTag snapshot) {
        return snapshot.getInt(UNSUPPORTED_CONNECTIONS_KEY);
    }

    public static boolean isSubLevelOwnedBackupSnapshot(final CompoundTag snapshot) {
        return snapshot.hasUUID(OWNER_SUB_LEVEL_KEY);
    }

    // * Picks the right transform path based on snapshot version
    public static CompoundTag transformBackupSnapshotForPlacement(
            final CompoundTag snapshot,
            final BlockPos schematicBackupPos,
            final SubLevelSchematicSerializationContext context
    ) {
        if (context == null) {
            return snapshot;
        }

        final int snapshotVersion = snapshot.getInt(SNAPSHOT_VERSION_KEY);
        if (snapshotVersion >= OWNER_AWARE_SNAPSHOT_VERSION) {
            return transformOwnerAwareSnapshotForPlacement(snapshot, context);
        }

        if (snapshotVersion < RELATIVE_SNAPSHOT_VERSION || isSubLevelOwnedBackupSnapshot(snapshot)) {
            return snapshot;
        }

        return transformRelativeSnapshotForPlacement(snapshot, schematicBackupPos, context.getSetupTransform());
    }
    //#endregion

    public boolean hasSinks(final BlockPos pos, final String channel) {
        final Map<String, Set<CableNetworkSink>> channels = this.sinks.get(pos.asLong());
        if (channels == null) return false;
        final Set<CableNetworkSink> sinkSet = channels.get(channel);
        return sinkSet != null && !sinkSet.isEmpty();
    }

    //#region // --- PLACEMENT TRANSFORM MATH --- //
    // * Rewrites saved positions and directions to match rotation on paste
    private static CompoundTag transformOwnerAwareSnapshotForPlacement(
            final CompoundTag snapshot,
            final SubLevelSchematicSerializationContext context
    ) {
        if (snapshot.getBoolean(PLACEMENT_RESOLVED_KEY)
                || context.getSetupTransform() == null
                || context.getPlaceTransform() == null) {
            return snapshot;
        }

        final CompoundTag transformed = snapshot.copy();
        if (!transformed.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return transformed;
        }

        boolean changed = false;
        final ListTag connections = transformed.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND);
        for (final Tag entry : connections) {
            if (!(entry instanceof final CompoundTag connection)) {
                continue;
            }

            changed |= rewriteOwnerUuidForPlacement(connection, SOURCE_OWNER_KEY, context);
            changed |= rewriteOwnerUuidForPlacement(connection, SINK_OWNER_KEY, context);

            if (connection.contains(SOURCE_KEY, Tag.TAG_LONG) && !connection.hasUUID(SOURCE_OWNER_KEY)) {
                final BlockPos sourcePos = BlockPos.of(connection.getLong(SOURCE_KEY));
                connection.putLong(SOURCE_KEY, transformMainTemplatePosition(sourcePos, context).asLong());
                changed = true;
            }

            if (connection.contains(SINK_KEY, Tag.TAG_LONG) && !connection.hasUUID(SINK_OWNER_KEY)) {
                final BlockPos sinkPos = BlockPos.of(connection.getLong(SINK_KEY));
                connection.putLong(SINK_KEY, transformMainTemplatePosition(sinkPos, context).asLong());
                // * Module sinks are addressed by name
                if (connection.contains(DIRECTION_KEY, Tag.TAG_BYTE) && !isModuleSinkTag(connection)) {
                    final Direction direction = Direction.from3DDataValue(connection.getByte(DIRECTION_KEY));
                    connection.putByte(DIRECTION_KEY, (byte) transformDirection(direction, sinkPos, context.getSetupTransform()).get3DDataValue());
                }
                changed = true;
            }
        }

        changed |= rewriteOwnerUuidForPlacement(transformed, OWNER_SUB_LEVEL_KEY, context);

        if (changed) {
            transformed.putBoolean(PLACEMENT_RESOLVED_KEY, true);
        }

        return transformed;
    }

    private static boolean rewriteOwnerUuidForPlacement(
            final CompoundTag tag,
            final String ownerKey,
            final SubLevelSchematicSerializationContext context
    ) {
        if (!tag.hasUUID(ownerKey)) {
            return false;
        }

        final SubLevelSchematicSerializationContext.SchematicMapping mapping = context.getMapping(tag.getUUID(ownerKey));
        if (mapping == null) {
            return false;
        }

        tag.putUUID(ownerKey, mapping.newUUID());
        return true;
    }

    // * Same idea but for relative version snapshots
    private static CompoundTag transformRelativeSnapshotForPlacement(
            final CompoundTag snapshot,
            final BlockPos schematicBackupPos,
            final Function<BlockPos, BlockPos> setupTransform
    ) {
        if (setupTransform == null) {
            return snapshot;
        }

        final CompoundTag transformed = snapshot.copy();
        final BlockPos transformedBackupPos = setupTransform.apply(schematicBackupPos);
        final ListTag connections = transformed.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND);
        for (final Tag entry : connections) {
            if (!(entry instanceof final CompoundTag connection)) {
                continue;
            }

            if (!connection.contains(SOURCE_KEY, Tag.TAG_LONG)
                    || !connection.contains(SINK_KEY, Tag.TAG_LONG)
                    || !connection.contains(DIRECTION_KEY, Tag.TAG_BYTE)) {
                continue;
            }

            final BlockPos sourcePos = schematicBackupPos.offset(BlockPos.of(connection.getLong(SOURCE_KEY)));
            final BlockPos sinkPos = schematicBackupPos.offset(BlockPos.of(connection.getLong(SINK_KEY)));

            final BlockPos transformedSourcePos = setupTransform.apply(sourcePos);
            final BlockPos transformedSinkPos = setupTransform.apply(sinkPos);

            connection.putLong(SOURCE_KEY, transformedSourcePos.subtract(transformedBackupPos).asLong());
            connection.putLong(SINK_KEY, transformedSinkPos.subtract(transformedBackupPos).asLong());

            if (!isModuleSinkTag(connection)) {
                final Direction direction = Direction.from3DDataValue(connection.getByte(DIRECTION_KEY));
                final Direction transformedDirection = transformDirection(direction, schematicBackupPos, setupTransform);
                connection.putByte(DIRECTION_KEY, (byte) transformedDirection.get3DDataValue());
            }
        }

        final Direction savedFacing = Direction.byName(transformed.getString(FACING_KEY));
        if (savedFacing != null) {
            transformed.putString(FACING_KEY, transformDirection(savedFacing, schematicBackupPos, setupTransform).getName());
        }

        return transformed;
    }

    private static BlockPos transformMainTemplatePosition(
            final BlockPos schematicPosition,
            final SubLevelSchematicSerializationContext context
    ) {
        return context.getPlaceTransform().apply(context.getSetupTransform().apply(schematicPosition));
    }

    private static boolean isSameSubLevel(final SubLevel expected, final SubLevel actual) {
        return expected != null && actual != null && Objects.equals(expected.getUniqueId(), actual.getUniqueId());
    }

    private static Rotation getRotation(final Direction from, final Direction to) {
        if (from == to) {
            return Rotation.NONE;
        }
        if (from.getClockWise() == to) {
            return Rotation.CLOCKWISE_90;
        }
        if (from.getOpposite() == to) {
            return Rotation.CLOCKWISE_180;
        }
        if (from.getCounterClockWise() == to) {
            return Rotation.COUNTERCLOCKWISE_90;
        }
        return Rotation.NONE;
    }

    private static Direction rotateDirection(final Direction direction, final Rotation rotation) {
        return direction.getAxis().isVertical() ? direction : rotation.rotate(direction);
    }

    private static Direction transformDirection(
            final Direction direction,
            final BlockPos origin,
            final Function<BlockPos, BlockPos> setupTransform
    ) {
        if (direction.getAxis().isVertical()) {
            final BlockPos delta = setupTransform.apply(origin.relative(direction)).subtract(setupTransform.apply(origin));
            return Direction.fromDelta(delta.getX(), delta.getY(), delta.getZ());
        }

        final BlockPos delta = setupTransform.apply(origin.relative(direction)).subtract(setupTransform.apply(origin));
        final Direction transformed = Direction.fromDelta(delta.getX(), delta.getY(), delta.getZ());
        return transformed == null ? direction : transformed;
    }

    private static BlockPos rotateRelative(final BlockPos relative, final Rotation rotation) {
        return switch (rotation) {
            case NONE -> relative;
            case CLOCKWISE_90 -> new BlockPos(-relative.getZ(), relative.getY(), relative.getX());
            case CLOCKWISE_180 -> new BlockPos(-relative.getX(), relative.getY(), -relative.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(relative.getZ(), relative.getY(), -relative.getX());
        };
    }
    //#endregion
}