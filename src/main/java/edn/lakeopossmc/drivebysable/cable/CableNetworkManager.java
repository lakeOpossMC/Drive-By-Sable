package edn.lakeopossmc.drivebysable.cable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.InputKey;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.function.Function;

public final class CableNetworkManager {
    public static final String WORLD_CHANNEL = "world";
    private static final String CONNECTIONS_KEY = "Connections";
    private static final String SOURCE_KEY = "Source";
    private static final String SINK_KEY = "Sink";
    private static final String SOURCE_OWNER_KEY = "SourceOwnerSubLevel";
    private static final String SINK_OWNER_KEY = "SinkOwnerSubLevel";
    private static final String DIRECTION_KEY = "Direction";
    private static final String CHANNEL_KEY = "Channel";
    private static final String FACING_KEY = "Facing";
    private static final String UNSUPPORTED_CONNECTIONS_KEY = "UnsupportedConnections";
    private static final String SNAPSHOT_VERSION_KEY = "SnapshotVersion";
    private static final String OWNER_SUB_LEVEL_KEY = "OwnerSubLevel";
    private static final String PLACEMENT_RESOLVED_KEY = "PlacementResolved";
    private static final int RELATIVE_SNAPSHOT_VERSION = 2;
    private static final int OWNER_AWARE_SNAPSHOT_VERSION = 3;
    private static final int MAX_SOURCES = 64;
    private static final int MAX_SINKS_PER_SOURCE = 64;
    private static final WorldAttached<CableNetworkManager> CLIENT_MANAGERS = new WorldAttached<>(level -> new CableNetworkManager(() -> {}));

    private final Map<Long, Map<String, Set<CableNetworkSink>>> sinks = new HashMap<>();
    private final Map<Long, Set<SinkReference>> sinkReferences = new HashMap<>();
    private final Map<Long, Map<String, Integer>> sourceValues = new HashMap<>();
    private final Map<BlockFace, CableNetworkNode> nodes = new HashMap<>();
    private final Set<BlockFace> staleFaces = new HashSet<>();
    private final Runnable dirtyMarker;
    private boolean attachedToLevel;
    private boolean graphDirty;

    CableNetworkManager(final Runnable dirtyMarker) {
        this.dirtyMarker = dirtyMarker;
    }

    public static CableNetworkManager get(final Level level) {
        if (level instanceof final ServerLevel serverLevel) {
            return CableNetworkSavedData.get(serverLevel);
        }

        return CLIENT_MANAGERS.get(level);
    }

    public static ConnectionResult createConnection(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel
    ) {
        return get(level).addConnection(level, source, sinkPos, sinkDirection, channel);
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

    public static boolean removeConnection(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel
    ) {
        return get(level).removeConnectionInternal(level, source, sinkPos, sinkDirection, channel);
    }

    public static boolean removeAllFromSource(final ServerPlayer serverPlayer, final Level level, final BlockPos source) {
        return get(level).removeAllFromSourceInternal(serverPlayer, level, source);
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

    public static void handleAssemblyMove(
            final ServerLevel originLevel,
            final ServerLevel resultingLevel,
            final BlockPos oldPos,
            final SubLevelAssemblyHelper.AssemblyTransform transform
    ) {
        final CableNetworkManager originManager = get(originLevel);
        originManager.remapMovedBlockInternal(oldPos, transform);

        if (resultingLevel != originLevel) {
            final CableNetworkManager resultingManager = get(resultingLevel);
            if (resultingManager != originManager) {
                resultingManager.remapMovedBlockInternal(oldPos, transform);
            }
        }
    }

    public ConnectionResult addConnection(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel
    ) {
        if (source.equals(sinkPos)) {
            return ConnectionResult.FAIL_SAME_BLOCK;
        }

        if (!isValidChannel(level, source, channel)) {
            return ConnectionResult.FAIL_INVALID_CHANNEL;
        }

        final long sourceKey = source.asLong();
        if (!sinks.containsKey(sourceKey) && countSourcesInSameDomain(level, source) >= MAX_SOURCES) {
            return ConnectionResult.FAIL_TOO_MANY_SOURCES;
        }

        final Set<CableNetworkSink> sinksOnChannel = getOrCreateSinksOnChannel(source, channel);
        if (sinksOnChannel.size() >= MAX_SINKS_PER_SOURCE) {
            return ConnectionResult.FAIL_TOO_MANY_SINKS;
        }

        final CableNetworkSink sink = CableNetworkSink.of(sinkPos, sinkDirection);
        if (!sinksOnChannel.add(sink)) {
            return ConnectionResult.FAIL_EXISTS;
        }

        addSinkReference(sourceKey, channel, sink);
        dirtyMarker.run();
        applySignalToSink(level, sourceKey, channel, sink, getCurrentSignal(level, source, channel));
        return ConnectionResult.OK;
    }

    private boolean isValidChannel(final Level level, final BlockPos source, final String channel) {
        final Block sourceBlock = level.getBlockState(source).getBlock();
        if (sourceBlock instanceof final MultiChannelCableSource multiChannelSource) {
            return multiChannelSource.cable$getChannels(level, source).contains(channel);
        }
        return WORLD_CHANNEL.equals(channel);
    }

    public boolean containsConnection(
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel
    ) {
        return sinks.getOrDefault(source.asLong(), Map.of())
                .getOrDefault(channel, Set.of())
                .contains(CableNetworkSink.of(sinkPos, sinkDirection));
    }

    public boolean removeConnectionInternal(
            final Level level,
            final BlockPos source,
            final BlockPos sinkPos,
            final Direction sinkDirection,
            final String channel
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

        final CableNetworkSink sink = CableNetworkSink.of(sinkPos, sinkDirection);
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

    public Map<String, Integer> getSourceSignals(final BlockPos source) {
        final Map<String, Integer> values = sourceValues.get(source.asLong());
        return values == null ? Map.of() : Map.copyOf(values);
    }

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

    public RestoreResult restoreBackupSnapshot(
            final Level level,
            final BlockPos backupPos,
            final Direction currentFacing,
            final CompoundTag snapshot
    ) {
        final int snapshotVersion = snapshot.getInt(SNAPSHOT_VERSION_KEY);
        if (snapshotVersion >= OWNER_AWARE_SNAPSHOT_VERSION) {
            return restoreOwnerAwareBackupSnapshot(level, snapshot);
        }

        return restoreRelativeBackupSnapshot(level, backupPos, currentFacing, snapshot);
    }

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

                    if (sourceInside && sinkInside) {
                        final CompoundTag connection = new CompoundTag();
                        connection.putLong(SOURCE_KEY, sourcePos.subtract(backupPos).asLong());
                        connection.putLong(SINK_KEY, sinkPos.subtract(backupPos).asLong());
                        connection.putByte(DIRECTION_KEY, (byte) sink.direction());
                        connection.putString(CHANNEL_KEY, channelEntry.getKey());
                        connections.add(connection);
                        internalConnections++;
                    } else if (sourceInside || sinkInside) {
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

    private RestoreResult restoreRelativeBackupSnapshot(
            final Level level,
            final BlockPos backupPos,
            final Direction currentFacing,
            final CompoundTag snapshot
    ) {
        final SubLevel backupSubLevel = Sable.HELPER.getContaining(level, backupPos);
        if (backupSubLevel == null) {
            return new RestoreResult(0, 0, 0, snapshot.getInt(UNSUPPORTED_CONNECTIONS_KEY), 0, false);
        }

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
                final Direction sinkDirection = rotateDirection(Direction.from3DDataValue(connection.getByte(DIRECTION_KEY)), rotation);
                final String channel = connection.getString(CHANNEL_KEY);

                if (!isSameSubLevel(backupSubLevel, Sable.HELPER.getContaining(level, sourcePos))
                        || !isSameSubLevel(backupSubLevel, Sable.HELPER.getContaining(level, sinkPos))) {
                    deferredConnections++;
                    continue;
                }

                if (containsConnection(sourcePos, sinkPos, sinkDirection, channel)) {
                    existingConnections++;
                    continue;
                }

                if (addConnection(level, sourcePos, sinkPos, sinkDirection, channel).isSuccess()) {
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

    private RestoreResult restoreOwnerAwareBackupSnapshot(final Level level, final CompoundTag snapshot) {
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
                final ResolvedEndpoint source = resolveOwnerAwareEndpoint(level, connection, SOURCE_KEY, SOURCE_OWNER_KEY);
                final ResolvedEndpoint sink = resolveOwnerAwareEndpoint(level, connection, SINK_KEY, SINK_OWNER_KEY);
                if (source.isDeferred() || sink.isDeferred()) {
                    deferredConnections++;
                    continue;
                }

                final BlockPos sourcePos = source.position();
                final BlockPos sinkPos = sink.position();
                final Direction sinkDirection = Direction.from3DDataValue(connection.getByte(DIRECTION_KEY));
                final String channel = connection.getString(CHANNEL_KEY);

                if (containsConnection(sourcePos, sinkPos, sinkDirection, channel)) {
                    existingConnections++;
                    continue;
                }

                if (addConnection(level, sourcePos, sinkPos, sinkDirection, channel).isSuccess()) {
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

    private ResolvedEndpoint resolveOwnerAwareEndpoint(
            final Level level,
            final CompoundTag connection,
            final String positionKey,
            final String ownerKey
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

        return ResolvedEndpoint.resolved(BlockPos.of(connection.getLong(positionKey)));
    }

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

    public void flushPendingGraphRebuild(final Level level) {
        if (!graphDirty) {
            return;
        }

        final Set<BlockFace> previousFaces = new HashSet<>(staleFaces);
        staleFaces.clear();
        nodes.clear();

        sinks.forEach((sourceKey, perChannel) -> perChannel.forEach((channel, sinksOnChannel) -> {
            final int signal = getCurrentSignal(level, BlockPos.of(sourceKey), channel);
            sinksOnChannel.forEach(sink -> applySignalToSink(level, sourceKey, channel, sink, signal));
        }));

        previousFaces.removeAll(nodes.keySet());
        previousFaces.forEach(face -> notifySink(level, face));
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
            connections.add(connection);
        })));
        tag.put(CONNECTIONS_KEY, connections);
        return tag;
    }

    public void load(final CompoundTag tag) {
        sinks.clear();
        sinkReferences.clear();
        sourceValues.clear();
        nodes.clear();
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
            final CableNetworkSink sink = new CableNetworkSink(sinkKey, direction);
            getOrCreateSinksOnChannel(BlockPos.of(sourceKey), channel).add(sink);
            addSinkReference(sourceKey, channel, sink);
        }
    }

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

                if (sinksOnChannel.remove(new CableNetworkSink(oldKey, reference.direction()))) {
                    final Direction transformedDirection = transform.getRotation().rotate(Direction.from3DDataValue(reference.direction()));
                    sinksOnChannel.add(new CableNetworkSink(newKey, transformedDirection.get3DDataValue()));
                    addSinkReference(newKey, reference.sourcePos(), reference.channel(), transformedDirection.get3DDataValue());
                    changed = true;
                }
            }
        }

        if (changed) {
            graphDirty = true;
            dirtyMarker.run();
        }
    }

    private Set<CableNetworkSink> getOrCreateSinksOnChannel(final BlockPos source, final String channel) {
        return sinks.computeIfAbsent(source.asLong(), ignored -> new HashMap<>())
                .computeIfAbsent(channel, ignored -> new HashSet<>());
    }

    private void addSinkReference(final long sourcePos, final String channel, final CableNetworkSink sink) {
        addSinkReference(sink.position(), sourcePos, channel, sink.direction());
    }

    private void addSinkReference(final long sinkPos, final long sourcePos, final String channel, final int direction) {
        sinkReferences.computeIfAbsent(sinkPos, ignored -> new HashSet<>())
                .add(new SinkReference(sourcePos, channel, direction));
    }

    private void removeSinkReference(final long sourcePos, final String channel, final CableNetworkSink sink) {
        final Set<SinkReference> references = sinkReferences.get(sink.position());
        if (references == null) {
            return;
        }

        references.remove(new SinkReference(sourcePos, channel, sink.direction()));
        if (references.isEmpty()) {
            sinkReferences.remove(sink.position());
        }
    }

    private int countSourcesInSameDomain(final Level level, final BlockPos source) {
        final SubLevel sourceSubLevel = Sable.HELPER.getContaining(level, source);
        final UUID sourceSubLevelId = sourceSubLevel == null ? null : sourceSubLevel.getUniqueId();

        int count = 0;
        for (final long existingSourceKey : sinks.keySet()) {
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

    private void notifySink(final Level level, final BlockFace face) {
        final BlockPos sinkPos = BlockPos.of(face.pos());
        final Direction sinkDirection = Direction.from3DDataValue(face.dir());
        final BlockPos updatedPos = sinkPos.relative(sinkDirection);
        level.updateNeighborsAt(updatedPos, level.getBlockState(updatedPos).getBlock());
    }

    public enum ConnectionResult {
        OK(""),
        FAIL_EXISTS("Connection already exists!"),
        FAIL_TOO_MANY_SOURCES("Exceeded source limit for this structure!"),
        FAIL_TOO_MANY_SINKS("Exceeded sink limit for this source!"),
        FAIL_SAME_BLOCK("Source and sink must be different blocks!"),
        FAIL_INVALID_CHANNEL("This channel is not available on this source!");

        private final String description;

        ConnectionResult(final String description) {
            this.description = description;
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

    private record ResolvedEndpoint(BlockPos position, boolean isDeferred) {
        private static ResolvedEndpoint resolved(final BlockPos position) {
            return new ResolvedEndpoint(position, false);
        }

        private static ResolvedEndpoint waiting() {
            return new ResolvedEndpoint(BlockPos.ZERO, true);
        }
    }

    private record SinkReference(long sourcePos, String channel, int direction) {
    }

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

    public boolean hasSinks(final BlockPos pos, final String channel) {
        final Map<String, Set<CableNetworkSink>> channels = this.sinks.get(pos.asLong());
        if (channels == null) return false;
        final Set<CableNetworkSink> sinkSet = channels.get(channel);
        return sinkSet != null && !sinkSet.isEmpty();
    }

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
                if (connection.contains(DIRECTION_KEY, Tag.TAG_BYTE)) {
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

            final Direction direction = Direction.from3DDataValue(connection.getByte(DIRECTION_KEY));
            final Direction transformedDirection = transformDirection(direction, schematicBackupPos, setupTransform);
            connection.putByte(DIRECTION_KEY, (byte) transformedDirection.get3DDataValue());
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
}