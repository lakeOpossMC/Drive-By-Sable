package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.client.NavigationTargetClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// --- PORTED RELIANTSHELL/AMMAN FEATURE --- //
/** Client-bound navigation values only; it contains no block or ship update data. */
public record NavigationTargetSyncPacket(
        long sensorPosition,
        boolean hasTarget,
        double x,
        double y,
        double z
) implements CustomPacketPayload {
    private static boolean reportedReceived;

    public static final Type<NavigationTargetSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DriveBySableMod.MOD_ID,
                    "navigation_target_sync"
            )
    );

    public static final StreamCodec<ByteBuf, NavigationTargetSyncPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public NavigationTargetSyncPacket decode(ByteBuf buffer) {
                    return new NavigationTargetSyncPacket(
                            buffer.readLong(),
                            buffer.readBoolean(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble()
                    );
                }

                @Override
                public void encode(ByteBuf buffer, NavigationTargetSyncPacket payload) {
                    buffer.writeLong(payload.sensorPosition());
                    buffer.writeBoolean(payload.hasTarget());
                    buffer.writeDouble(payload.x());
                    buffer.writeDouble(payload.y());
                    buffer.writeDouble(payload.z());
                }
            };

    @Override
    public Type<NavigationTargetSyncPacket> type() {
        return TYPE;
    }

    public static void handle(NavigationTargetSyncPacket payload, IPayloadContext context) {
        if (context.player() == null) {
            return;
        }
        NavigationTargetClientState.accept(context.player().level(), payload);
        if (!reportedReceived) {
            reportedReceived = true;
            System.out.println(
                    "[drivebysable] Client received navigation-only target "
                            + "payload for the Pilot Helmet HUD."
            );
        }
    }
}