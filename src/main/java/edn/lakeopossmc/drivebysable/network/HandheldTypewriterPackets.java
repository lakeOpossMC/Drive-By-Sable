package edn.lakeopossmc.drivebysable.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// --- HANDHELD TYPEWRITER PACKETS --- //
// * Kept apart from CablePackets, these name Simulated types and only register when Simulated is loaded
public final class HandheldTypewriterPackets {
    private HandheldTypewriterPackets() {
    }

    public static void register(final PayloadRegistrar registrar) {
        registrar
                .playToServer(HandheldTypewriterInputPacket.TYPE, HandheldTypewriterInputPacket.STREAM_CODEC, HandheldTypewriterInputPacket::handle)
                .playToServer(HandheldTypewriterStopLecternPacket.TYPE, HandheldTypewriterStopLecternPacket.STREAM_CODEC, HandheldTypewriterStopLecternPacket::handle)
                .playToServer(HandheldTypewriterBindPacket.TYPE, HandheldTypewriterBindPacket.STREAM_CODEC, HandheldTypewriterBindPacket::handle)
                .playToServer(HandheldTypewriterSavePacket.TYPE, HandheldTypewriterSavePacket.STREAM_CODEC, HandheldTypewriterSavePacket::handle);
    }
}