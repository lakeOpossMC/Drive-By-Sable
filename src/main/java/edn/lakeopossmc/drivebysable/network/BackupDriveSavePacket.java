package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.advancement.CableAdvancements;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlockEntity;
import edn.lakeopossmc.drivebysable.cable.BackupDriveBounds;
import edn.lakeopossmc.drivebysable.menu.BackupDriveMenu;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.core.BlockPos;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// --- PLAYER CONFIRMED A SAVE IN THE BACKUP DRIVE SCREEN --- //
public record BackupDriveSavePacket(
        BlockPos drivePos,
        BlockPos offset,
        BlockPos size,
        int rotationSteps
) implements CustomPacketPayload {

    public static final Type<BackupDriveSavePacket> TYPE =
            new Type<>(DriveBySableMod.asResource("backup_drive_save"));

    public static final StreamCodec<ByteBuf, BackupDriveSavePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BackupDriveSavePacket::drivePos,
                    BlockPos.STREAM_CODEC, BackupDriveSavePacket::offset,
                    BlockPos.STREAM_CODEC, BackupDriveSavePacket::size,
                    ByteBufCodecs.VAR_INT, BackupDriveSavePacket::rotationSteps,
                    BackupDriveSavePacket::new
            );

    // * Convenience for the screen
    public static BackupDriveSavePacket of(
            final BlockPos drivePos,
            final int[] offset,
            final int[] size,
            final int rotationSteps
    ) {
        return new BackupDriveSavePacket(
                drivePos,
                new BlockPos(offset[0], offset[1], offset[2]),
                new BlockPos(size[0], size[1], size[2]),
                rotationSteps
        );
    }

    @Override
    public Type<BackupDriveSavePacket> type() {
        return TYPE;
    }

    public static void handle(final BackupDriveSavePacket payload, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }

        // * Authorisation comes from the open menu
        if (!(player.containerMenu instanceof final BackupDriveMenu menu)
                || !menu.getDrivePos().equals(payload.drivePos())) {
            return;
        }

        final NetworkBackupDriveBlockEntity drive = menu.getDrive();
        if (drive == null || drive.isRemoved()) {
            return;
        }

        final BlockPos offset = payload.offset();
        final BlockPos size = payload.size();
        final AABB bounds = BackupDriveBounds.of(
                menu.getDrivePos(),
                new int[]{offset.getX(), offset.getY(), offset.getZ()},
                new int[]{size.getX(), size.getY(), size.getZ()},
                payload.rotationSteps()
        );

        final Level level = drive.getLevel();
        final CableNetworkManager.BackupSnapshot snapshot = CableNetworkManager.get(level)
                .createBoundedBackupSnapshot(level, menu.getDrivePos(), drive.getSavedFacing(), bounds);

        drive.storeBoundedSnapshot(snapshot.data());

        if (snapshot.internalConnections() > 0) {
            CableAdvancements.award(player, CableAdvancements.NETWORK_SAVED);
            level.playSound(
                    null,
                    menu.getDrivePos(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.BLOCKS,
                    0.7F,
                    1.0F
            );
        }

        DriveBySableMod.LOGGER.info(
                "[schematic-debug] Bounded save at {} -> {} connections kept, {} skipped.",
                menu.getDrivePos(),
                snapshot.internalConnections(),
                snapshot.skippedConnections()
        );
    }
}