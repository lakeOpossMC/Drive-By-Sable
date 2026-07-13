package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.CableSounds;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// --- CLIENT ASKS SERVER TO ADD A CONNECTION --- //
public record CableAddConnectionPacket(BlockPos source, BlockPos sink, Direction direction, String channel) implements CustomPacketPayload {
    public static final Type<CableAddConnectionPacket> TYPE = new Type<>(DriveBySableMod.asResource("wire_add_connection"));
    public static final StreamCodec<ByteBuf, CableAddConnectionPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, CableAddConnectionPacket::source,
        BlockPos.STREAM_CODEC, CableAddConnectionPacket::sink,
        ByteBufCodecs.VAR_INT, packet -> packet.direction().get3DDataValue(),
        ByteBufCodecs.STRING_UTF8, CableAddConnectionPacket::channel,
        (source, sink, direction, channel) -> new CableAddConnectionPacket(source, sink, Direction.from3DDataValue(direction), channel)
    );

    @Override
    public Type<CableAddConnectionPacket> type() {
        return TYPE;
    }

    // * Consume a cable on success, else show reason
    public static void handle(final CableAddConnectionPacket payload, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }

        final CableNetworkManager.ConnectionResult result = CableNetworkManager.createConnection(
            player.level(),
            payload.source(),
            payload.sink(),
            payload.direction(),
            payload.channel()
        );
        if (result.isSuccess()) {
            player.level().playSound(null, payload.sink(), CableSounds.PLUG_IN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            if (CableConfig.CONFIG.shouldConsumeCables.get()) player.getItemInHand(InteractionHand.MAIN_HAND).consume(1, player);
            CableNetworkFullSyncPacket.sendTo(player);
            return;
        }

        player.displayClientMessage(Component.literal(result.getDescription()).withStyle(ChatFormatting.RED), true);
    }
}
