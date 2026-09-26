package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.client.BackupDriveMissingBlockHighlight;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

// --- WHERE A LOAD FOUND NOTHING TO CONNECT TO --- //
// * Sent alongside the report, so the empty spots can be called out in the world
public record BackupDriveMissingBlockPacket(
        List<BlockPos> positions
) implements CustomPacketPayload {

    public static final Type<BackupDriveMissingBlockPacket> TYPE =
            new Type<>(DriveBySableMod.asResource("backup_drive_missing_block"));

    public static final StreamCodec<ByteBuf, BackupDriveMissingBlockPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), BackupDriveMissingBlockPacket::positions,
                    BackupDriveMissingBlockPacket::new
            );

    @Override
    public Type<BackupDriveMissingBlockPacket> type() {
        return TYPE;
    }

    public static void handle(final BackupDriveMissingBlockPacket payload, final IPayloadContext context) {
        // * Handed straight to the client
        context.enqueueWork(() -> BackupDriveMissingBlockHighlight.show(payload.positions()));
    }
}