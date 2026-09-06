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
        int loadedSources,
        int missingSources,
        int loadedSinks,
        int missingSinks,
        // * How many connections this load actually made
        int restoredConnections
) implements CustomPacketPayload {

    private static final int DISPLAY_TICKS = 120;

    public static final Type<BackupDriveLoadReportPacket> TYPE =
            new Type<>(DriveBySableMod.asResource("backup_drive_load_report"));

    public static final StreamCodec<ByteBuf, BackupDriveLoadReportPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BackupDriveLoadReportPacket::loadedSources,
                    ByteBufCodecs.VAR_INT, BackupDriveLoadReportPacket::missingSources,
                    ByteBufCodecs.VAR_INT, BackupDriveLoadReportPacket::loadedSinks,
                    ByteBufCodecs.VAR_INT, BackupDriveLoadReportPacket::missingSinks,
                    ByteBufCodecs.VAR_INT, BackupDriveLoadReportPacket::restoredConnections,
                    BackupDriveLoadReportPacket::new
            );

    @Override
    public Type<BackupDriveLoadReportPacket> type() {
        return TYPE;
    }

    private static final int SOURCE_COLOR = 0x7FCDE0;
    private static final int OUTPUT_COLOR = 0xDDC166;

    public static void handle(final BackupDriveLoadReportPacket payload, final IPayloadContext context) {
        final boolean complete = payload.missingSources() == 0 && payload.missingSinks() == 0;
        final List<MutableComponent> lines = new ArrayList<>();

        if (complete && payload.restoredConnections() == 0) {
            lines.add(Component.translatable("drivebysable.backup_drive.load_report.unchanged")
                    .withStyle(ChatFormatting.RED));
            lines.add(present("drivebysable.backup_drive.load_report.sources_present",
                    payload.loadedSources(), SOURCE_COLOR));
            lines.add(present("drivebysable.backup_drive.load_report.outputs_present",
                    payload.loadedSinks(), OUTPUT_COLOR));
            lines.add(Component.translatable("drivebysable.backup_drive.load_report.kept")
                    .withStyle(ChatFormatting.GRAY));

            context.enqueueWork(() -> CableHoverTip.pin(lines, DISPLAY_TICKS));
            return;
        }

        final boolean nothingRestored = payload.restoredConnections() == 0;

        if (complete) {
            lines.add(Component.translatable("drivebysable.backup_drive.load_report.complete")
                    .withStyle(ChatFormatting.GREEN));
        } else if (nothingRestored) {
            lines.add(Component.translatable("drivebysable.backup_drive.load_report.unchanged")
                    .withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable("drivebysable.backup_drive.load_report.partial")
                    .withStyle(ChatFormatting.GOLD));
        }

        lines.add(line("drivebysable.backup_drive.load_report.sources",
                payload.loadedSources(), payload.missingSources(), SOURCE_COLOR));
        lines.add(line("drivebysable.backup_drive.load_report.outputs",
                payload.loadedSinks(), payload.missingSinks(), OUTPUT_COLOR));

        if (!complete) {
            lines.add(Component.translatable("drivebysable.backup_drive.load_report.retry")
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