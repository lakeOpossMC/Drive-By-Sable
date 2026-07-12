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
            .playToServer(CableTypewriterHubKeyPacket.TYPE, CableTypewriterHubKeyPacket.STREAM_CODEC, CableTypewriterHubKeyPacket::handle);
    }
}
