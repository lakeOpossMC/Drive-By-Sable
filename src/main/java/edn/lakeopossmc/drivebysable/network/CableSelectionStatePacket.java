package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.CableSelectionTracker;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// --- TELLS THE SERVER A SELECTION IS IN PROGRESS --- //
// * Only so the cutter knows to keep its hands off
public record CableSelectionStatePacket(boolean selecting) implements CustomPacketPayload {

    public static final Type<CableSelectionStatePacket> TYPE =
            new Type<>(DriveBySableMod.asResource("cable_selection_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CableSelectionStatePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> buffer.writeBoolean(packet.selecting()),
                    buffer -> new CableSelectionStatePacket(buffer.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final CableSelectionStatePacket packet, final IPayloadContext context) {
        if (context.player() instanceof final ServerPlayer player) {
            CableSelectionTracker.setSelecting(player, packet.selecting());
        }
    }
}