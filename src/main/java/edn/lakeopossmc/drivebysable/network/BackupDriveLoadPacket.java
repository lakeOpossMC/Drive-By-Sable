package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlockEntity;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.menu.BackupDriveMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;
import java.util.Set;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.ArrayList;
import java.util.Map;

// --- PLAYER ASKED A BACKUP DRIVE TO RESTORE ITS SAVE --- //
// * The cost is paid here
public record BackupDriveLoadPacket(BlockPos drivePos) implements CustomPacketPayload {

    private static final float BEACON_VOLUME = 1.4F;

    public static final Type<BackupDriveLoadPacket> TYPE =
            new Type<>(DriveBySableMod.asResource("backup_drive_load"));

    public static final StreamCodec<ByteBuf, BackupDriveLoadPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BackupDriveLoadPacket::drivePos,
                    BackupDriveLoadPacket::new
            );

    @Override
    public Type<BackupDriveLoadPacket> type() {
        return TYPE;
    }


    private static int moduleCount(final Map<BlockPos, Set<String>> connected) {
        int count = 0;
        for (final Set<String> modules : connected.values()) {
            count += modules.size();
        }
        return count;
    }

    public static void handle(final BackupDriveLoadPacket payload, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }

        // * Authorised by the open menu
        if (!(player.containerMenu instanceof final BackupDriveMenu menu)
                || !menu.getDrivePos().equals(payload.drivePos())) {
            return;
        }

        final NetworkBackupDriveBlockEntity drive = menu.getDrive();
        if (drive == null || drive.isRemoved()) {
            return;
        }

        // * Last chance to pin it before anything is resolved by position
        drive.tryBindWorldSpaceSnapshot();

        final CompoundTag snapshot = drive.getBoundedSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }

        final int cost = drive.getPendingConnectionCount();
        final ItemStack cables = menu.getCableStack();

        // * Creative players are not charged
        final boolean free = player.hasInfiniteMaterials()
                || !edn.lakeopossmc.drivebysable.CableConfig.CONFIG.shouldConsumeCables.get();
        if (!free && cables.getCount() < cost) {
            return;
        }


        final Level level = drive.getLevel();

        // * Sampled before and after
        final Map<BlockPos, Set<String>> connectedBefore = CableNetworkManager.get(level)
                .connectedSourceModules(level, menu.getDrivePos(), drive.getSavedFacing(), snapshot);
        final CableNetworkManager.SnapshotSummary before = CableNetworkManager.get(level)
                .summariseSnapshot(level, menu.getDrivePos(), drive.getSavedFacing(), snapshot);
        final Set<BlockPos> transceiversBefore = CableNetworkManager.get(level)
                .joinedTransceivers(level, menu.getDrivePos(), drive.getSavedFacing(), snapshot);

        final CableNetworkManager.RestoreResult result = CableNetworkManager.get(level)
                .restoreBackupSnapshot(level, menu.getDrivePos(), drive.getSavedFacing(), snapshot);

        // * Summarised before anything is cleared
        final CableNetworkManager.SnapshotSummary summary = CableNetworkManager.get(level)
                .summariseSnapshot(level, menu.getDrivePos(), drive.getSavedFacing(), snapshot);

        final Map<BlockPos, Set<String>> connectedAfter = CableNetworkManager.get(level)
                .connectedSourceModules(level, menu.getDrivePos(), drive.getSavedFacing(), snapshot);

        final List<BlockPos> highlightPositions = new ArrayList<>();
        final List<String> highlightModules = new ArrayList<>();

        connectedAfter.forEach((pos, modules) -> {
            final Set<String> already = connectedBefore.getOrDefault(pos, Set.of());
            for (final String module : modules) {
                if (!already.contains(module)) {
                    highlightPositions.add(pos);
                    highlightModules.add(module);
                }
            }
        });
        final int loadedSources = highlightPositions.size();

        // * Transceivers this load joined up are shown too
        for (final BlockPos pos : CableNetworkManager.get(level)
                .joinedTransceivers(level, menu.getDrivePos(), drive.getSavedFacing(), snapshot)) {
            if (!transceiversBefore.contains(pos)) {
                highlightPositions.add(pos);
                highlightModules.add("");
            }
        }

        if (!highlightPositions.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new BackupDriveHighlightPacket(
                    menu.getDrivePos(),
                    List.copyOf(highlightPositions),
                    List.copyOf(highlightModules)
            ));
        }

        // * Called out in the world as well as counted in the report
        final List<BlockPos> missingBlocks = CableNetworkManager.get(level)
                .missingBlocksInSnapshot(level, menu.getDrivePos(), drive.getSavedFacing(), snapshot);

        if (!missingBlocks.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new BackupDriveMissingBlockPacket(missingBlocks));
        }

        final CompoundTag remaining = CableNetworkManager.get(level)
                .pruneRestoredConnections(level, menu.getDrivePos(), drive.getSavedFacing(), snapshot);

        final int kept = CableNetworkManager.countConnectionsInBackupSnapshot(remaining);
        if (kept <= 0) {
            drive.clearStoredSnapshot();
        } else {
            drive.storeBoundedSnapshot(remaining);
        }

        // * Outputs and transceivers are told apart by what the save holds before and after,
        // * the restore itself only counts connections
        PacketDistributor.sendToPlayer(player, new BackupDriveLoadReportPacket(
                new BackupDriveLoadReportPacket.Tally(
                        loadedSources, summary.missingSources(), moduleCount(connectedBefore)),
                new BackupDriveLoadReportPacket.Tally(
                        Math.max(0, summary.loadedSinks() - before.loadedSinks()),
                        summary.missingSinks(),
                        before.loadedSinks()),
                new BackupDriveLoadReportPacket.Tally(
                        Math.max(0, summary.loadedTransceivers() - before.loadedTransceivers()),
                        summary.missingTransceivers(),
                        before.loadedTransceivers()),
                result.restoredConnections(),
                kept
        ));

        if (result.restoredConnections() <= 0) {
            level.playSound(
                    null,
                    menu.getDrivePos(),
                    SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS,
                    BEACON_VOLUME,
                    1.0F
            );
            return;
        }

        // * Charged for connections that were actually made
        level.playSound(
                null,
                menu.getDrivePos(),
                SoundEvents.BEACON_ACTIVATE,
                SoundSource.BLOCKS,
                BEACON_VOLUME,
                1.0F
        );

        if (!free) {
            menu.consumeCables(Math.min(cost, result.restoredConnections()));
        }
    }
}