package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// --- SERVER SENDS WHOLE NETWORK TO A PLAYER --- //
public record CableNetworkFullSyncPacket(CompoundTag network) implements CustomPacketPayload {
    public static final Type<CableNetworkFullSyncPacket> TYPE = new Type<>(DriveBySableMod.asResource("wire_network_full_sync"));
    public static final StreamCodec<ByteBuf, CableNetworkFullSyncPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.COMPOUND_TAG, CableNetworkFullSyncPacket::network,
        CableNetworkFullSyncPacket::new
    );

    @Override
    public Type<CableNetworkFullSyncPacket> type() {
        return TYPE;
    }

    public static void sendTo(final ServerPlayer player) {
        final CompoundTag tag = CableNetworkManager.get(player.serverLevel()).save(new CompoundTag());
        PacketDistributor.sendToPlayer(player, new CableNetworkFullSyncPacket(tag));
    }

    public static void handle(final CableNetworkFullSyncPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> CableNetworkManager.get(context.player().level()).load(payload.network()));
    }
}
