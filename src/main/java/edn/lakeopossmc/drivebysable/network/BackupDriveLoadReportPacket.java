package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.client.CableHoverTip;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

// --- HOW A LOAD TURNED OUT --- //
public record BackupDriveLoadReportPacket(
        Tally sources,
        Tally outputs,
        // * How many connections this load actually made
        int restoredConnections,
        // * How many are still held in the save for another try
        int keptConnections
) implements CustomPacketPayload {

    private static final int DISPLAY_TICKS = 120;

    public record Tally(int loaded, int missing, int present) {

        public static final StreamCodec<ByteBuf, Tally> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Tally::loaded,
                        ByteBufCodecs.VAR_INT, Tally::missing,
                        ByteBufCodecs.VAR_INT, Tally::present,
                        Tally::new
                );
    }

    public static final Type<BackupDriveLoadReportPacket> TYPE =
            new Type<>(DriveBySableMod.asResource("backup_drive_load_report"));

    public static final StreamCodec<ByteBuf, BackupDriveLoadReportPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Tally.STREAM_CODEC, BackupDriveLoadReportPacket::sources,
                    Tally.STREAM_CODEC, BackupDriveLoadReportPacket::outputs,
                    ByteBufCodecs.VAR_INT, BackupDriveLoadReportPacket::restoredConnections,
                    ByteBufCodecs.VAR_INT, BackupDriveLoadReportPacket::keptConnections,
                    BackupDriveLoadReportPacket::new
            );

    @Override
    public Type<BackupDriveLoadReportPacket> type() {
        return TYPE;
    }

    private static final int SOURCE_COLOR = 0x7FCDE0;
    private static final int OUTPUT_COLOR = 0xDDC166;

    public static void handle(final BackupDriveLoadReportPacket payload, final IPayloadContext context) {
        final Tally sources = payload.sources();
        final Tally outputs = payload.outputs();

        final boolean anythingMissing = sources.missing() > 0 || outputs.missing() > 0;
        final boolean nothingLoaded = payload.restoredConnections() == 0;

        final List<MutableComponent> lines = new ArrayList<>();

        if (nothingLoaded) {
            lines.add(Component.translatable("drivebysable.backup_drive.load_report.unchanged")
                    .withStyle(ChatFormatting.RED));
        } else if (anythingMissing) {
            lines.add(Component.translatable("drivebysable.backup_drive.load_report.partial")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            lines.add(Component.translatable("drivebysable.backup_drive.load_report.complete")
                    .withStyle(ChatFormatting.GREEN));
        }

        // * Each line only earns its place when this load had something to say with it
        if (sources.loaded() > 0 || sources.missing() > 0) {
            lines.add(line("drivebysable.backup_drive.load_report.sources",
                    sources.loaded(), sources.missing(), SOURCE_COLOR));
        }
        if (sources.present() > 0) {
            lines.add(present("drivebysable.backup_drive.load_report.sources_present",
                    sources.present(), SOURCE_COLOR));
        }

        if (outputs.loaded() > 0 || outputs.missing() > 0) {
            lines.add(line("drivebysable.backup_drive.load_report.outputs",
                    outputs.loaded(), outputs.missing(), OUTPUT_COLOR));
        }
        if (outputs.present() > 0) {
            lines.add(present("drivebysable.backup_drive.load_report.outputs_present",
                    outputs.present(), OUTPUT_COLOR));
        }

        if (anythingMissing) {
            lines.add(Component.translatable("drivebysable.backup_drive.load_report.retry")
                    .withStyle(ChatFormatting.GRAY));
        }

        // * Whatever could not go in is still waiting for another try
        if (payload.keptConnections() > 0) {
            lines.add(Component.translatable("drivebysable.backup_drive.load_report.kept")
                    .withStyle(ChatFormatting.GRAY));
        }

        // * Handed to the client
        context.enqueueWork(() -> CableHoverTip.pin(lines, DISPLAY_TICKS));
    }

    // * Color coded per data type
    private static MutableComponent present(final String key, final int count, final int color) {
        return Component.translatable(
                key,
                Component.literal(String.valueOf(count)).withStyle(style -> style.withColor(color))
        ).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent line(final String key, final int loaded, final int missing, final int color) {
        return Component.translatable(
                key,
                Component.literal(String.valueOf(loaded)).withStyle(style -> style.withColor(color)),
                Component.literal(String.valueOf(missing))
                        .withStyle(missing > 0 ? ChatFormatting.RED : ChatFormatting.GRAY)
        ).withStyle(ChatFormatting.WHITE);
    }
}