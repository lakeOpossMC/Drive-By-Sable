package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import net.createmod.catnip.math.VecHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import edn.lakeopossmc.drivebysable.cable.BackupDriveBounds;
import edn.lakeopossmc.drivebysable.cable.BackupDriveCapture;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import edn.lakeopossmc.drivebysable.network.BackupDriveHighlightPacket;
import edn.lakeopossmc.drivebysable.network.NetworkAnchorSavedPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// --- NETWORK ANCHOR STORAGE --- //
public class NetworkAnchorBlockEntity extends SmartBlockEntity {

    private static final String SNAPSHOT_KEY = "AnchorSnapshot";
    private static final String RADIUS_KEY = "Radius";
    private static final String APPLIED_AT_KEY = "AppliedAt";
    private static final String HAS_DATA_KEY = "HasData";

    private static final float BEACON_VOLUME = 1.4F;
    private static final double HIGHLIGHT_RANGE = 64.0D;

    public static final int MIN_RADIUS = 16;
    public static final int MAX_RADIUS = 1024;
    public static final int DEFAULT_RADIUS = 16;

    @Nullable
    private CompoundTag snapshot;

    private int radius;

    @Nullable
    private BlockPos appliedAt;

    private boolean clientHasSnapshot;

    private ScrollValueBehaviour radiusScroll;

    private int outputsOutOfRadius;
    private int outputsOnOtherLevel;
    private int sourcesOnOtherLevel;

    public NetworkAnchorBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(CableBlockEntities.NETWORK_ANCHOR.get(), pos, blockState);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        radiusScroll = new RadiusScrollBehaviour(
                Component.translatable("drivebysable.network_anchor.radius"),
                this,
                new RadiusSlot()
        )
                .between(MIN_RADIUS, MAX_RADIUS)
                .withCallback(this::setRadius);

        radiusScroll.setValue(DEFAULT_RADIUS);
        radius = DEFAULT_RADIUS;

        behaviours.add(radiusScroll);
    }

    private static final int BOARD_STEP = 16;
    private static final int BOARD_MAX = MAX_RADIUS / BOARD_STEP;
    private static final int BOARD_MILESTONE = 8;

    private static class RadiusScrollBehaviour extends ScrollValueBehaviour {
        private final NetworkAnchorBlockEntity anchor;

        RadiusScrollBehaviour(final Component label, final NetworkAnchorBlockEntity be, final ValueBoxTransform slot) {
            super(label, be, slot);
            anchor = be;
        }

        // * Locked once something is stored
        @Override
        public boolean isActive() {
            return super.isActive() && !anchor.hasStoredSnapshot();
        }

        @Override
        public ValueSettingsBoard createBoard(final Player player, final BlockHitResult hitResult) {
            return new ValueSettingsBoard(
                    label,
                    BOARD_MAX,
                    BOARD_MILESTONE,
                    List.of(Component.translatable("drivebysable.network_anchor.radius_row")),
                    new ValueSettingsFormatter(settings -> Component.literal(String.valueOf(toRadius(settings))))
            );
        }

        @Override
        public ValueSettings getValueSettings() {
            return new ValueSettings(0, Math.clamp(value / BOARD_STEP, 1, BOARD_MAX));
        }

        @Override
        public void setValueSettings(final Player player, final ValueSettings settings, final boolean ctrlHeld) {
            final int radius = toRadius(settings);
            if (radius == value) {
                return;
            }

            setValue(radius);
            playFeedbackSound(this);
        }

        private static int toRadius(final ValueSettings settings) {
            return Math.clamp(settings.value() * BOARD_STEP, MIN_RADIUS, MAX_RADIUS);
        }
    }

    private static class RadiusSlot extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8.0D, 4.0D, 15.5D);
        }

        @Override
        protected boolean isSideActive(final BlockState state, final Direction direction) {
            return direction.getAxis().isHorizontal();
        }
    }

    //#region // --- RADIUS --- //
    public int getRadius() {
        return radius;
    }

    public void setRadius(final int newRadius) {
        radius = Math.clamp(newRadius, MIN_RADIUS, MAX_RADIUS);

        if (radiusScroll != null && radiusScroll.getValue() != radius) {
            radiusScroll.setValue(radius);
        }

        setChanged();
    }

    // * A cube centred on the anchor
    public AABB bounds() {
        return new AABB(worldPosition).inflate(radius);
    }
    //#endregion

    //#region // --- CAPTURE AND RESTORE --- //
    public boolean hasStoredSnapshot() {
        return level != null && level.isClientSide ? clientHasSnapshot : snapshot != null;
    }

    // * Right click capture
    public boolean capture() {
        if (level == null || level.isClientSide) {
            return false;
        }

        final AABB bounds = bounds();

        tallyRejections(bounds);

        final CableNetworkManager.BackupSnapshot captured = CableNetworkManager.get(level)
                .createBoundedBackupSnapshot(level, worldPosition, Direction.NORTH, bounds);

        snapshot = captured.data();

        appliedAt = worldPosition.immutable();

        markStored(true);
        playAnchorSound(SoundEvents.RESPAWN_ANCHOR_CHARGE);
        return true;
    }

    // * Reports what was taken
    public void showCaptureReport(final ServerPlayer player) {
        if (snapshot == null) {
            return;
        }

        PacketDistributor.sendToPlayer(player, NetworkAnchorSavedPacket.saved(
                radius,
                CableNetworkManager.countStoredSources(snapshot),
                CableNetworkManager.countConnectionsInBackupSnapshot(snapshot),
                outputsOutOfRadius,
                outputsOnOtherLevel,
                sourcesOnOtherLevel
        ));
    }

    private void tallyRejections(final AABB bounds) {
        outputsOutOfRadius = 0;
        outputsOnOtherLevel = 0;
        sourcesOnOtherLevel = 0;

        final SubLevel anchorSubLevel = BackupDriveCapture.subLevelOf(level, worldPosition);

        for (final Map.Entry<BlockPos, Map<String, Set<CableNetworkSink>>> source
                : CableNetworkManager.get(level).sourcesWithSinks().entrySet()) {

            final BlockPos sourcePos = source.getKey();
            final boolean sameLevel = BackupDriveCapture.isSameLevel(
                    anchorSubLevel, BackupDriveCapture.subLevelOf(level, sourcePos));

            if (!withinBounds(bounds, sourcePos, anchorSubLevel)) {
                continue;
            }

            // * Only a problem while cross level saving is off
            if (!sameLevel && !BackupDriveCapture.crossLevelSavingAllowed()) {
                sourcesOnOtherLevel++;
                continue;
            }

            for (final Set<CableNetworkSink> sinks : source.getValue().values()) {
                for (final CableNetworkSink sink : sinks) {
                    final BlockPos sinkPos = sink.blockPos();

                    if (!BackupDriveCapture.isSameLevel(
                            anchorSubLevel, BackupDriveCapture.subLevelOf(level, sinkPos))
                            && !BackupDriveCapture.crossLevelSavingAllowed()) {
                        if (withinBounds(bounds, sinkPos, anchorSubLevel)) {
                            outputsOnOtherLevel++;
                        }
                        continue;
                    }

                    if (!withinBounds(bounds, sinkPos, anchorSubLevel)) {
                        outputsOutOfRadius++;
                    }
                }
            }
        }
    }

    // * Kept for the out of range counts
    private boolean withinBounds(final AABB bounds, final BlockPos pos, @Nullable final SubLevel anchorSubLevel) {
        final SubLevel posSubLevel = BackupDriveCapture.subLevelOf(level, pos);
        if (BackupDriveCapture.isSameLevel(anchorSubLevel, posSubLevel)) {
            return BackupDriveBounds.contains(bounds, pos);
        }

        final Vec3 centre = Vec3.atCenterOf(pos);
        final Vec3 world = posSubLevel == null ? centre : posSubLevel.logicalPose().transformPosition(centre);
        final Vec3 anchorSpace = anchorSubLevel == null
                ? world
                : anchorSubLevel.logicalPose().transformPositionInverse(world);

        return bounds.contains(anchorSpace);
    }

    // * Reports what is about to be thrown away
    public void showClearReport(final ServerPlayer player) {
        if (snapshot == null) {
            return;
        }

        PacketDistributor.sendToPlayer(player, NetworkAnchorSavedPacket.cleared(
                radius,
                CableNetworkManager.countStoredSources(snapshot),
                CableNetworkManager.countConnectionsInBackupSnapshot(snapshot)
        ));
    }

    // * Deliberate discard by a player
    public void clearSnapshot() {
        releaseSnapshot();
        playAnchorSound(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value());
    }

    private void releaseSnapshot() {
        snapshot = null;
        markStored(false);
    }

    private void playAnchorSound(final SoundEvent sound) {
        playAnchorSound(sound, 1.0F);
    }

    private void playAnchorSound(final SoundEvent sound, final float volume) {
        if (level == null || level.isClientSide) {
            return;
        }

        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, 1.0F);
    }

    public void onPlaced() {
        setChanged();
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide || snapshot == null) {
            return;
        }

        if (worldPosition.equals(appliedAt)) {
            return;
        }

        appliedAt = worldPosition.immutable();
        setChanged();
        restore();
    }

    private void restore() {
        if (level == null || snapshot == null) {
            return;
        }

        // * Sources read out before the snapshot is discarded
        final Map<BlockPos, Set<String>> restoredSources =
                CableNetworkManager.storedSourceModules(snapshot, worldPosition);

        final CableNetworkManager.RestoreResult result = CableNetworkManager.get(level)
                .restoreBackupSnapshot(level, worldPosition, Direction.NORTH, snapshot.copy());

        // * Summarised after the restore
        final CableNetworkManager.SnapshotSummary summary = CableNetworkManager.get(level)
                .summariseSnapshot(level, worldPosition, Direction.NORTH, snapshot);

        reportLoad(summary);

        playAnchorSound(result.restoredConnections() > 0
                ? SoundEvents.BEACON_ACTIVATE
                : SoundEvents.BEACON_DEACTIVATE, BEACON_VOLUME);

        if (result.restoredConnections() > 0) {
            showLoadHighlight(restoredSources);
        }

        releaseSnapshot();
    }

    private void reportLoad(final CableNetworkManager.SnapshotSummary summary) {
        if (!(level instanceof final ServerLevel serverLevel)) {
            return;
        }

        final NetworkAnchorSavedPacket packet = NetworkAnchorSavedPacket.loaded(
                radius,
                summary.loadedSources(),
                summary.loadedSinks(),
                summary.missingSources(),
                summary.missingSinks()
        );

        for (final ServerPlayer player : serverLevel.players()) {
            if (player.blockPosition().closerThan(worldPosition, HIGHLIGHT_RANGE)) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    private void showLoadHighlight(final Map<BlockPos, Set<String>> sources) {
        if (sources.isEmpty() || !(level instanceof final ServerLevel serverLevel)) {
            return;
        }

        final List<BlockPos> positions = new ArrayList<>();
        final List<String> modules = new ArrayList<>();
        for (final Map.Entry<BlockPos, Set<String>> entry : sources.entrySet()) {
            for (final String module : entry.getValue()) {
                positions.add(entry.getKey());
                modules.add(module);
            }
        }

        final BackupDriveHighlightPacket packet =
                new BackupDriveHighlightPacket(worldPosition, positions, modules);

        for (final ServerPlayer player : serverLevel.players()) {
            if (player.blockPosition().closerThan(worldPosition, HIGHLIGHT_RANGE)) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    private void markStored(final boolean stored) {
        setChanged();

        if (level != null && !level.isClientSide) {
            notifyUpdate();
        }

        if (level == null) {
            return;
        }

        final BlockState state = getBlockState();
        if (state.hasProperty(NetworkAnchorBlock.STORED) && state.getValue(NetworkAnchorBlock.STORED) != stored) {
            level.setBlock(worldPosition, state.setValue(NetworkAnchorBlock.STORED, stored), Block.UPDATE_ALL);
        }
    }
    //#endregion

    //#region // --- PERSISTENCE --- //
    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        radius = tag.contains(RADIUS_KEY) ? Math.clamp(tag.getInt(RADIUS_KEY), MIN_RADIUS, MAX_RADIUS) : DEFAULT_RADIUS;
        if (radiusScroll != null) {
            radiusScroll.setValue(radius);
        }

        if (clientPacket) {
            clientHasSnapshot = tag.getBoolean(HAS_DATA_KEY);
            return;
        }
        snapshot = tag.contains(SNAPSHOT_KEY, Tag.TAG_COMPOUND) ? tag.getCompound(SNAPSHOT_KEY).copy() : null;
        appliedAt = tag.contains(APPLIED_AT_KEY) ? BlockPos.of(tag.getLong(APPLIED_AT_KEY)) : null;
    }

    @Override
    public void writeSafe(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);

        tag.putInt(RADIUS_KEY, radius);
        if (snapshot != null) {
            tag.put(SNAPSHOT_KEY, snapshot.copy());
        }
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);

        tag.putInt(RADIUS_KEY, radius);

        if (clientPacket) {
            tag.putBoolean(HAS_DATA_KEY, snapshot != null);
            return;
        }

        if (snapshot != null) {
            tag.put(SNAPSHOT_KEY, snapshot.copy());
        }
        if (appliedAt != null) {
            tag.putLong(APPLIED_AT_KEY, appliedAt.asLong());
        }
    }
    //#endregion
}