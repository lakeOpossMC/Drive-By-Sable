package edn.lakeopossmc.drivebysable.blocks;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.sublevel.SubLevel;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// --- BLOCK ENTITY FOR PRESERVER --- //
// * This block entity stores information for schematics
// * The entire network is stored (per sublevel)
public class NetworkBackupDriveBlockEntity extends BlockEntity {
    // --- KEY/SYMBOL SETUP --- //
    private static final String CABLE_NETWORK_KEY = "CableNetwork";
    private static final int RESTORE_RETRY_INTERVAL = 20;

    private CompoundTag pendingBackupData;
    private boolean needsRestore;
    private int restoreRetryCooldown;
    private int restoreAttempts;

    // --- GET POS AND STATE --- //
    public NetworkBackupDriveBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(CableBlockEntities.BACKUP_DRIVE.get(), pos, blockState);
    }

    //#region // --- HANDLE CONNECTION SAVES IN SCHEMATICS --- //
    // * Reuse loaded snapshot if placing, else build fresh from live network
    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.level == null) {
            return;
        }

        final SubLevelSchematicSerializationContext context = SubLevelSchematicSerializationContext.getCurrentContext();
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, this.worldPosition);
        final boolean reusingLoadedSnapshot = context != null
            && context.getType() == SubLevelSchematicSerializationContext.Type.PLACE
            && this.pendingBackupData != null
            && !this.pendingBackupData.isEmpty();
        final CableNetworkManager.BackupSnapshot liveSnapshot = reusingLoadedSnapshot
            ? null
            : CableNetworkManager.get(this.level).createBackupSnapshot(this.level, this.worldPosition, this.getFacing());
        CompoundTag dataToWrite = reusingLoadedSnapshot ? this.pendingBackupData.copy() : liveSnapshot.data();
        if (context != null && context.getType() == SubLevelSchematicSerializationContext.Type.PLACE) {
            dataToWrite = CableNetworkManager.transformBackupSnapshotForPlacement(
                dataToWrite,
                this.worldPosition,
                context
            );
            DriveBySableMod.LOGGER.info(
                "[schematic-debug] Applied placement transform to backup snapshot at {} during PLACE serialization.",
                this.worldPosition
            );
        }

        final int internalConnections = reusingLoadedSnapshot
            ? CableNetworkManager.countConnectionsInBackupSnapshot(dataToWrite)
            : liveSnapshot.internalConnections();
        final int skippedConnections = reusingLoadedSnapshot
            ? CableNetworkManager.countUnsupportedConnectionsInBackupSnapshot(dataToWrite)
            : liveSnapshot.skippedConnections();

        if (!dataToWrite.isEmpty()) {
            tag.put(CABLE_NETWORK_KEY, dataToWrite);
        }

        if (this.level.isClientSide() || context != null) {
            DriveBySableMod.LOGGER.info(
                "[schematic-debug] Serializing backup block at {} on {} (context={}, sublevel={}, reuseLoaded={}, snapshotVersion={}, ownerSnapshot={}, placementResolved={}) -> {} internal / {} skipped / wroteData={}.",
                this.worldPosition,
                this.level.isClientSide() ? "client" : "server",
                context == null ? "none" : context.getType(),
                subLevel == null ? "none" : subLevel.getUniqueId(),
                reusingLoadedSnapshot,
                dataToWrite.getInt("SnapshotVersion"),
                CableNetworkManager.isSubLevelOwnedBackupSnapshot(dataToWrite),
                dataToWrite.getBoolean("PlacementResolved"),
                internalConnections,
                skippedConnections,
                !dataToWrite.isEmpty()
            );
        }

        if (context != null
            && context.getType() == SubLevelSchematicSerializationContext.Type.SAVE
            && skippedConnections > 0) {
            DriveBySableMod.LOGGER.warn(
                "Backup block at {} skipped {} unsupported cable connections while saving schematic; only links whose endpoints stay inside the same blueprint batch are preserved.",
                this.worldPosition,
                skippedConnections
            );
        }
    }
    //#endregion

    // * Stash payload, actual restore happens on tick
    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(CABLE_NETWORK_KEY, Tag.TAG_COMPOUND)) {
            this.pendingBackupData = tag.getCompound(CABLE_NETWORK_KEY).copy();
            this.needsRestore = true;
            this.restoreRetryCooldown = 0;
            this.restoreAttempts = 0;
            DriveBySableMod.LOGGER.info(
                "[schematic-debug] Loaded backup payload for {} on {} with {} tag entries / {} stored connections (snapshotVersion={}, ownerSnapshot={}, placementResolved={}).",
                this.worldPosition,
                this.level == null ? "unknown-level" : this.level.isClientSide() ? "client" : "server",
                this.pendingBackupData.size(),
                CableNetworkManager.countConnectionsInBackupSnapshot(this.pendingBackupData),
                this.pendingBackupData.getInt("SnapshotVersion"),
                CableNetworkManager.isSubLevelOwnedBackupSnapshot(this.pendingBackupData),
                this.pendingBackupData.getBoolean("PlacementResolved")
            );
        } else {
            this.pendingBackupData = null;
            this.needsRestore = false;
            this.restoreRetryCooldown = 0;
            this.restoreAttempts = 0;
        }
    }

    //#region // --- RETRY RESTORE UNTIL SUBLEVEL READY --- //
    public static void serverTick(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final NetworkBackupDriveBlockEntity blockEntity
    ) {
        if (level.isClientSide() || !blockEntity.needsRestore || blockEntity.pendingBackupData == null) {
            return;
        }

        if (blockEntity.restoreRetryCooldown > 0) {
            blockEntity.restoreRetryCooldown--;
            return;
        }

        blockEntity.restoreAttempts++;
        final CableNetworkManager.RestoreResult restoreResult = CableNetworkManager.get(level)
            .restoreBackupSnapshot(level, pos, blockEntity.getFacing(), blockEntity.pendingBackupData);
        if (!restoreResult.attempted()) {
            DriveBySableMod.LOGGER.info(
                "[schematic-debug] Restore attempt {} for backup block at {} is waiting for a containing sublevel. Stored connections={}.",
                blockEntity.restoreAttempts,
                pos,
                CableNetworkManager.countConnectionsInBackupSnapshot(blockEntity.pendingBackupData)
            );
            blockEntity.restoreRetryCooldown = RESTORE_RETRY_INTERVAL;
            return;
        }

        DriveBySableMod.LOGGER.info(
            "[schematic-debug] Restore attempt {} for backup block at {} -> expected={}, restored={}, existing={}, deferred={}, skipped={}.",
            blockEntity.restoreAttempts,
            pos,
            restoreResult.expectedConnections(),
            restoreResult.restoredConnections(),
            restoreResult.existingConnections(),
            restoreResult.deferredConnections(),
            restoreResult.skippedConnections()
        );

        if (restoreResult.deferredConnections() > 0) {
            blockEntity.restoreRetryCooldown = RESTORE_RETRY_INTERVAL;
            return;
        }

        if (restoreResult.expectedConnections() > 0
            && restoreResult.restoredConnections() == 0
            && restoreResult.existingConnections() == 0) {
            DriveBySableMod.LOGGER.warn(
                "[schematic-debug] Backup block at {} attempted restore but matched 0/{} connections. This usually means the placement transform or endpoint positions do not line up yet.",
                pos,
                restoreResult.expectedConnections()
            );
        }

        if (restoreResult.skippedConnections() > 0) {
            DriveBySableMod.LOGGER.warn(
                "Backup block at {} did not restore {} unsupported cable connections; cross-sublevel and sublevel-to-world links are intentionally skipped.",
                pos,
                restoreResult.skippedConnections()
            );
        }

        blockEntity.pendingBackupData = null;
        blockEntity.needsRestore = false;
        blockEntity.restoreRetryCooldown = 0;
        blockEntity.restoreAttempts = 0;
        blockEntity.setChanged();
    }
    //#endregion

    // --- LEFTOVER FROM DRIVEBYWIRE --- //
    // * Probably useless, since the blockstates have been removed from the main block for simplicity
    private Direction getFacing() {
        final BlockState blockState = this.getBlockState();
        return blockState.hasProperty(HorizontalDirectionalBlock.FACING)
            ? blockState.getValue(HorizontalDirectionalBlock.FACING)
            : Direction.NORTH;
    }
}
