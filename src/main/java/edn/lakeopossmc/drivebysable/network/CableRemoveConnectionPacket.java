package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.CableSounds;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// --- CLIENT ASKS SERVER TO DROP A CONNECTION --- //
public record CableRemoveConnectionPacket(BlockPos source, BlockPos sink, Direction direction, String channel) implements CustomPacketPayload {
    public static final Type<CableRemoveConnectionPacket> TYPE = new Type<>(DriveBySableMod.asResource("wire_remove_connection"));
    public static final StreamCodec<ByteBuf, CableRemoveConnectionPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CableRemoveConnectionPacket::source,
            BlockPos.STREAM_CODEC, CableRemoveConnectionPacket::sink,
            ByteBufCodecs.VAR_INT, packet -> packet.direction().get3DDataValue(),
            ByteBufCodecs.STRING_UTF8, CableRemoveConnectionPacket::channel,
            (source, sink, direction, channel) -> new CableRemoveConnectionPacket(source, sink, Direction.from3DDataValue(direction), channel)
    );

    @Override
    public Type<CableRemoveConnectionPacket> type() {
        return TYPE;
    }

    // * Refund a cable on success then resync
    public static void handle(final CableRemoveConnectionPacket payload, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }

        if (CableNetworkManager.removeConnection(player.level(), payload.source(), payload.sink(), payload.direction(), payload.channel())) {
            if (CableConfig.CONFIG.shouldConsumeCables.get() && !player.hasInfiniteMaterials()) {
                final ItemStack cable = new ItemStack(CableItems.CABLE.get());
                if (!player.addItem(cable)) {
                    player.drop(cable, false);
                }
            }
            player.level().playSound(null, payload.sink(), CableSounds.PLUG_OUT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        CableNetworkFullSyncPacket.sendTo(player);
    }
}