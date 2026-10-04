package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.client.CableHoverTip;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

// --- WHAT A NETWORK ANCHOR JUST STORED --- //
public record NetworkAnchorSavedPacket(
        int status,
        int radius,
        int sources,
        int outputs,
        int outputsOutOfRadius,
        int outputsOnOtherLevel,
        int sourcesOnOtherLevel,
        int missingSources,
        int missingOutputs,
        int transceivers,
        int transceiversOutOfRadius,
        int missingTransceivers
) implements CustomPacketPayload {

    public static final int STATUS_SAVED = 0;
    public static final int STATUS_CLEARED = 1;
    public static final int STATUS_LOADED = 2;

    public static NetworkAnchorSavedPacket saved(
            final int radius,
            final int sources,
            final int outputs,
            final int transceivers,
            final int outputsOutOfRadius,
            final int outputsOnOtherLevel,
            final int sourcesOnOtherLevel,
            final int transceiversOutOfRadius
    ) {
        return new NetworkAnchorSavedPacket(
                STATUS_SAVED, radius, sources, outputs,
                outputsOutOfRadius, outputsOnOtherLevel, sourcesOnOtherLevel, 0, 0,
                transceivers, transceiversOutOfRadius, 0);
    }

    public static NetworkAnchorSavedPacket cleared(
            final int radius,
            final int sources,
            final int outputs,
            final int transceivers
    ) {
        return new NetworkAnchorSavedPacket(
                STATUS_CLEARED, radius, sources, outputs, 0, 0, 0, 0, 0, transceivers, 0, 0);
    }

    // * A load fails when blocks it expected are not there
    public static NetworkAnchorSavedPacket loaded(
            final int radius,
            final int sources,
            final int outputs,
            final int transceivers,
            final int missingSources,
            final int missingOutputs,
            final int missingTransceivers
    ) {
        return new NetworkAnchorSavedPacket(
                STATUS_LOADED, radius, sources, outputs, 0, 0, 0, missingSources, missingOutputs,
                transceivers, 0, missingTransceivers);
    }

    private static final int DISPLAY_TICKS = 60;

    private static final int SOURCE_COLOR = 0x7FCDE0;
    private static final int OUTPUT_COLOR = 0xDDC166;
    private static final int TRANSCEIVER_COLOR = 0x55FFFF;

    public static final Type<NetworkAnchorSavedPacket> TYPE =
            new Type<>(DriveBySableMod.asResource("network_anchor_saved"));

    public static final StreamCodec<ByteBuf, NetworkAnchorSavedPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        VarInt.write(buffer, payload.status());
                        VarInt.write(buffer, payload.radius());
                        VarInt.write(buffer, payload.sources());
                        VarInt.write(buffer, payload.outputs());
                        VarInt.write(buffer, payload.outputsOutOfRadius());
                        VarInt.write(buffer, payload.outputsOnOtherLevel());
                        VarInt.write(buffer, payload.sourcesOnOtherLevel());
                        VarInt.write(buffer, payload.missingSources());
                        VarInt.write(buffer, payload.missingOutputs());
                        VarInt.write(buffer, payload.transceivers());
                        VarInt.write(buffer, payload.transceiversOutOfRadius());
                        VarInt.write(buffer, payload.missingTransceivers());
                    },
                    buffer -> new NetworkAnchorSavedPacket(
                            VarInt.read(buffer),
                            VarInt.read(buffer),
                            VarInt.read(buffer),
                            VarInt.read(buffer),
                            VarInt.read(buffer),
                            VarInt.read(buffer),
                            VarInt.read(buffer),
                            VarInt.read(buffer),
                            VarInt.read(buffer),
                            VarInt.read(buffer),
                            VarInt.read(buffer),
                            VarInt.read(buffer)
                    )
            );

    @Override
    public Type<NetworkAnchorSavedPacket> type() {
        return TYPE;
    }

    public static void handle(final NetworkAnchorSavedPacket payload, final IPayloadContext context) {
        final List<MutableComponent> lines = new ArrayList<>(List.of(
                heading(payload.status()),
                count("drivebysable.network_anchor.saved_radius", payload.radius(), ChatFormatting.GRAY),
                colored("drivebysable.network_anchor.saved_sources", payload.sources(), SOURCE_COLOR),
                colored("drivebysable.network_anchor.saved_outputs", payload.outputs(), OUTPUT_COLOR)
        ));

        // * Only shown when there are transceivers to speak of
        if (payload.transceivers() > 0) {
            lines.add(colored("drivebysable.network_anchor.saved_transceivers",
                    payload.transceivers(), TRANSCEIVER_COLOR));
        }

        addIfAny(lines, "drivebysable.network_anchor.rejected_outputs", payload.outputsOutOfRadius());
        addIfAny(lines, "drivebysable.network_anchor.rejected_outputs_level", payload.outputsOnOtherLevel());
        addIfAny(lines, "drivebysable.network_anchor.rejected_sources", payload.sourcesOnOtherLevel());
        addIfAny(lines, "drivebysable.network_anchor.rejected_transceivers", payload.transceiversOutOfRadius());
        addIfAny(lines, "drivebysable.network_anchor.missing_sources", payload.missingSources());
        addIfAny(lines, "drivebysable.network_anchor.missing_outputs", payload.missingOutputs());
        addIfAny(lines, "drivebysable.network_anchor.missing_transceivers", payload.missingTransceivers());

        context.enqueueWork(() -> CableHoverTip.pin(lines, DISPLAY_TICKS));
    }

    private static MutableComponent heading(final int status) {
        return switch (status) {
            case STATUS_CLEARED -> Component.translatable("drivebysable.network_anchor.cleared")
                    .withStyle(ChatFormatting.RED);
            case STATUS_LOADED -> Component.translatable("drivebysable.network_anchor.loaded")
                    .withStyle(ChatFormatting.GREEN);
            default -> Component.translatable("drivebysable.network_anchor.saved")
                    .withStyle(ChatFormatting.GOLD);
        };
    }

    private static void addIfAny(final List<MutableComponent> lines, final String key, final int value) {
        if (value > 0) {
            lines.add(rejected(key, value));
        }
    }

    private static MutableComponent rejected(final String key, final int value) {
        return Component.translatable(key, Component.literal(String.valueOf(value)).withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent count(final String key, final int value, final ChatFormatting style) {
        return Component.translatable(key, Component.literal(String.valueOf(value)).withStyle(style))
                .withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent colored(final String key, final int value, final int color) {
        return Component.translatable(
                key,
                Component.literal(String.valueOf(value)).withStyle(style -> style.withColor(color))
        ).withStyle(ChatFormatting.WHITE);
    }
}