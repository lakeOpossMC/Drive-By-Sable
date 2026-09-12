package edn.lakeopossmc.drivebysable.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// --- REGISTERS ALL NETWORK PACKETS --- //
public final class CablePackets {
    private CablePackets() {
    }

    public static void register(final RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(CableNetworkFullSyncPacket.TYPE, CableNetworkFullSyncPacket.STREAM_CODEC, CableNetworkFullSyncPacket::handle)
                .playToServer(BindLecternCableHubPacket.TYPE, BindLecternCableHubPacket.STREAM_CODEC, BindLecternCableHubPacket::handle)
                .playToServer(CableAddConnectionPacket.TYPE, CableAddConnectionPacket.STREAM_CODEC, CableAddConnectionPacket::handle)
                .playToServer(CableRemoveConnectionPacket.TYPE, CableRemoveConnectionPacket.STREAM_CODEC, CableRemoveConnectionPacket::handle)
                .playToServer(CableNetworkRequestSyncPacket.TYPE, CableNetworkRequestSyncPacket.STREAM_CODEC, CableNetworkRequestSyncPacket::handle)
                .playToServer(BackupDriveSavePacket.TYPE, BackupDriveSavePacket.STREAM_CODEC, BackupDriveSavePacket::handle)
                .playToServer(BackupDriveRegionPacket.TYPE, BackupDriveRegionPacket.STREAM_CODEC, BackupDriveRegionPacket::handle)
                .playToServer(BackupDriveResetPacket.TYPE, BackupDriveResetPacket.STREAM_CODEC, BackupDriveResetPacket::handle)
                .playToServer(BackupDriveLoadPacket.TYPE, BackupDriveLoadPacket.STREAM_CODEC, BackupDriveLoadPacket::handle)
                .playToClient(BackupDriveLoadReportPacket.TYPE, BackupDriveLoadReportPacket.STREAM_CODEC, BackupDriveLoadReportPacket::handle)
                .playToClient(BackupDriveHighlightPacket.TYPE, BackupDriveHighlightPacket.STREAM_CODEC, BackupDriveHighlightPacket::handle)
                .playToClient(NetworkAnchorSavedPacket.TYPE, NetworkAnchorSavedPacket.STREAM_CODEC, NetworkAnchorSavedPacket::handle)
                .playToClient(NavigationTargetSyncPacket.TYPE, NavigationTargetSyncPacket.STREAM_CODEC, NavigationTargetSyncPacket::handle)
                .playToServer(SensorBusSettingsPacket.TYPE, SensorBusSettingsPacket.STREAM_CODEC, SensorBusSettingsPacket::handle)
                .playToClient(SensorBusSettingsSyncPacket.TYPE, SensorBusSettingsSyncPacket.STREAM_CODEC, SensorBusSettingsSyncPacket::handle)
                .playToServer(CableTypewriterHubKeyPacket.TYPE, CableTypewriterHubKeyPacket.STREAM_CODEC, CableTypewriterHubKeyPacket::handle)
                .playToServer(MovementKeybindsPacket.TYPE, MovementKeybindsPacket.STREAM_CODEC, MovementKeybindsPacket::handle)
                .playToServer(TweakedKeybindsPacket.TYPE, TweakedKeybindsPacket.STREAM_CODEC, TweakedKeybindsPacket::handle);
    }
}