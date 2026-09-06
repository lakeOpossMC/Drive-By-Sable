package edn.lakeopossmc.drivebysable.compat.toolgun;

import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlockEntity;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.legacy.LegacyWireCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

// --- REFLECTION SURFACE FOR THE AERONAUTICS TOOLGUN --- //
public final class ToolgunCableApi {
    private final CableNetworkManager real;

    private ToolgunCableApi(final CableNetworkManager real) {
        this.real = real;
    }

    public static ToolgunCableApi get(final Level level) {
        return new ToolgunCableApi(CableNetworkManager.get(level));
    }

    // * A sublevel with connections but no backup drive saves an empty snapshot
    // * What the drive was explicitly saved with
    public CableNetworkManager.BackupSnapshot createBackupSnapshot(
            final Level level,
            final BlockPos backupPos,
            final Direction savedFacing
    ) {
        if (!(level.getBlockEntity(backupPos) instanceof final NetworkBackupDriveBlockEntity drive)) {
            return new CableNetworkManager.BackupSnapshot(new CompoundTag(), 0, 0);
        }

        final CompoundTag saved = drive.getBoundedSnapshot();
        if (saved == null || saved.isEmpty()) {
            return new CableNetworkManager.BackupSnapshot(new CompoundTag(), 0, 0);
        }

        return new CableNetworkManager.BackupSnapshot(
                saved.copy(),
                CableNetworkManager.countConnectionsInBackupSnapshot(saved),
                0
        );
    }

    // * New system in order for toolgun to use GUI for save/load on cable connections
    public CableNetworkManager.RestoreResult restoreBackupSnapshot(
            final Level level,
            final BlockPos backupBlockPos,
            final Direction facing,
            final CompoundTag snapshot
    ) {
        final int expected = CableNetworkManager.countConnectionsInBackupSnapshot(snapshot);

        if (snapshot == null || snapshot.isEmpty() || expected <= 0) {
            return new CableNetworkManager.RestoreResult(0, 0, 0, 0, 0, false);
        }

        if (!(level.getBlockEntity(backupBlockPos) instanceof final NetworkBackupDriveBlockEntity drive)) {
            if (LegacyWireCompat.isOwnerAware(snapshot)) {
                if (this.real.hasLegacyPayloadAwaitingLoad(expected)) {
                    DriveBySableMod.LOGGER.info(
                            "[drivebywire-migration] Toolgun snapshot of {} connections is already on a Drive "
                                    + "awaiting a manual load. Not restoring it automatically.",
                            expected
                    );

                    return new CableNetworkManager.RestoreResult(0, expected, 0, 0, expected, true);
                }

                // * Nothing is holding it
                DriveBySableMod.LOGGER.warn(
                        "[drivebywire-migration] Toolgun snapshot of {} connections has no Drive holding it. "
                                + "Nothing was restored, and nothing was placed automatically.",
                        expected
                );

                return new CableNetworkManager.RestoreResult(0, 0, 0, 0, expected, false);
            }

            // * A drive that has not been placed yet, worth another pass
            return new CableNetworkManager.RestoreResult(0, 0, 0, 0, expected, false);
        }

        final CableNetworkManager.RestoreResult done =
                new CableNetworkManager.RestoreResult(0, expected, 0, 0, expected, true);

        // * Never over the top of a save
        if (drive.hasStoredSnapshot()) {
            return done;
        }

        final CompoundTag inherited = LegacyWireCompat.normalizeFacing(snapshot.copy());

        // * Before storing, so the sync that storing triggers carries the region
        drive.storeBoundedSnapshot(inherited);
        drive.onLegacyPayloadAdopted();

        DriveBySableMod.LOGGER.info(
                "[drivebywire-migration] Toolgun handed a payload to the Drive at {}: "
                        + "{} connections, snapshotVersion={}, region offset {} size {}.",
                backupBlockPos,
                expected,
                LegacyWireCompat.snapshotVersion(inherited),
                drive.getRegionOffset(),
                drive.getRegionSize()
        );

        return done;
    }

    public static CompoundTag transformBackupSnapshotForPlacement(
            final CompoundTag snapshot,
            final BlockPos schematicBackupPos,
            final SubLevelSchematicSerializationContext context
    ) {
        return CableNetworkManager.transformBackupSnapshotForPlacement(snapshot, schematicBackupPos, context);
    }

    public static int computeWorldSignal(final Level level, final BlockPos pos) {
        return CableNetworkManager.computeWorldSignal(level, pos);
    }

    public static void trySetSignalAt(final Level level, final BlockPos source, final String channel, final int value) {
        CableNetworkManager.trySetSignalAt(level, source, channel, value);
    }
}