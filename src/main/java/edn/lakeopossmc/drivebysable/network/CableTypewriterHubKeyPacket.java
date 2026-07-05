package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.blocks.CableTypewriterHubBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CableTypewriterHubKeyPacket(BlockPos pos, int glfwKey, boolean press)
        implements CustomPacketPayload {

    public static final Type<CableTypewriterHubKeyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("drivebysable", "typewriter_hub_key"));

    public static final StreamCodec<FriendlyByteBuf, CableTypewriterHubKeyPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public CableTypewriterHubKeyPacket decode(final FriendlyByteBuf buf) {
                    return new CableTypewriterHubKeyPacket(buf.readBlockPos(), buf.readInt(), buf.readBoolean());
                }
                @Override
                public void encode(final FriendlyByteBuf buf, final CableTypewriterHubKeyPacket p) {
                    buf.writeBlockPos(p.pos());
                    buf.writeInt(p.glfwKey());
                    buf.writeBoolean(p.press());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(final CableTypewriterHubKeyPacket packet, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof final ServerPlayer player)) return;
            if (!(player.level() instanceof final ServerLevel level)) return;
            if (!(level.getBlockEntity(packet.pos()) instanceof final CableTypewriterHubBlockEntity be)) return;
            if (!be.checkUser(player.getUUID())) return;

            if (packet.press()) {
                be.pressKey(packet.glfwKey());
            } else {
                be.releaseKey(packet.glfwKey());
            }
        });
    }
}