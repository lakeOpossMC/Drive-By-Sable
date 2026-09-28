package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.HandheldTypewriterLecternBlockEntity;
import edn.lakeopossmc.drivebysable.compat.HandheldTypewriterData;
import edn.lakeopossmc.drivebysable.compat.HandheldTypewriterServerHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

// --- CLIENT SENDS HANDHELD TYPEWRITER KEY PRESSES --- //
// * Either from the held controller, or from the lectern the player is using
public record HandheldTypewriterInputPacket(List<Integer> keys, boolean press, Optional<BlockPos> lecternPos)
        implements CustomPacketPayload {

    public static final Type<HandheldTypewriterInputPacket> TYPE =
            new Type<>(DriveBySableMod.asResource("handheld_typewriter_input"));

    public static final StreamCodec<ByteBuf, HandheldTypewriterInputPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), HandheldTypewriterInputPacket::keys,
            ByteBufCodecs.BOOL, HandheldTypewriterInputPacket::press,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional), HandheldTypewriterInputPacket::lecternPos,
            HandheldTypewriterInputPacket::new
    );

    @Override
    public Type<HandheldTypewriterInputPacket> type() {
        return TYPE;
    }

    public static void handle(final HandheldTypewriterInputPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof final ServerPlayer player)) {
                return;
            }
            // * Spectators can let go of keys, but never press them
            if (player.isSpectator() && packet.press()) {
                return;
            }

            if (packet.lecternPos().isPresent()) {
                final BlockPos lecternPos = packet.lecternPos().get();
                if (player.level().getBlockEntity(lecternPos) instanceof final HandheldTypewriterLecternBlockEntity lectern
                        && lectern.isUsedBy(player)) {
                    HandheldTypewriterServerHandler.receiveKeys(
                            player, lectern.getController(), lecternPos, packet.keys(), packet.press());
                }
                return;
            }

            ItemStack controller = player.getMainHandItem();
            if (!HandheldTypewriterData.isController(controller)) {
                controller = player.getOffhandItem();
                if (!HandheldTypewriterData.isController(controller)) {
                    return;
                }
            }
            HandheldTypewriterServerHandler.receiveKeys(player, controller, null, packet.keys(), packet.press());
        });
    }
}