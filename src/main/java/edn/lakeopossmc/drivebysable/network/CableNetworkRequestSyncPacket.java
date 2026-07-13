package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// --- CLIENT ASKS SERVER FOR A FULL RESYNC --- //
public record CableNetworkRequestSyncPacket() implements CustomPacketPayload {
    public static final CableNetworkRequestSyncPacket INSTANCE = new CableNetworkRequestSyncPacket();
    public static final Type<CableNetworkRequestSyncPacket> TYPE = new Type<>(DriveBySableMod.asResource("wire_network_request_sync"));
    public static final StreamCodec<ByteBuf, CableNetworkRequestSyncPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<CableNetworkRequestSyncPacket> type() {
        return TYPE;
    }

    public static void handle(final CableNetworkRequestSyncPacket payload, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }

        CableNetworkFullSyncPacket.sendTo(player);
    }
}
