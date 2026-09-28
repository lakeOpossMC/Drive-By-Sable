package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.HandheldTypewriterLecternBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// --- CLIENT STOPS USING A HANDHELD TYPEWRITER LECTERN --- //
public record HandheldTypewriterStopLecternPacket(BlockPos lecternPos) implements CustomPacketPayload {

    public static final Type<HandheldTypewriterStopLecternPacket> TYPE =
            new Type<>(DriveBySableMod.asResource("handheld_typewriter_stop_lectern"));

    public static final StreamCodec<ByteBuf, HandheldTypewriterStopLecternPacket> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(HandheldTypewriterStopLecternPacket::new, HandheldTypewriterStopLecternPacket::lecternPos);

    @Override
    public Type<HandheldTypewriterStopLecternPacket> type() {
        return TYPE;
    }

    public static void handle(final HandheldTypewriterStopLecternPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof final ServerPlayer player
                    && player.level().getBlockEntity(packet.lecternPos()) instanceof final HandheldTypewriterLecternBlockEntity lectern) {
                lectern.tryStopUsing(player);
            }
        });
    }
}