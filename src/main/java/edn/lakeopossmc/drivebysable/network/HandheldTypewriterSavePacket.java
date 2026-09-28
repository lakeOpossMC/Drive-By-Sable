package edn.lakeopossmc.drivebysable.network;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries.KeyboardEntry;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.compat.HandheldTypewriterData;
import edn.lakeopossmc.drivebysable.menu.HandheldTypewriterMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

// --- SAVES THE TYPEWRITER SCREEN'S KEYS ONTO THE HELD CONTROLLER --- //
public record HandheldTypewriterSavePacket(Map<Integer, KeyboardEntry> keys, boolean clearAll) implements CustomPacketPayload {

    public static final Type<HandheldTypewriterSavePacket> TYPE =
            new Type<>(DriveBySableMod.asResource("handheld_typewriter_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandheldTypewriterSavePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_INT, KeyboardEntry.STREAM_CODEC), HandheldTypewriterSavePacket::keys,
            ByteBufCodecs.BOOL, HandheldTypewriterSavePacket::clearAll,
            HandheldTypewriterSavePacket::new
    );

    @Override
    public Type<HandheldTypewriterSavePacket> type() {
        return TYPE;
    }

    // * Only while this player actually has the handheld screen open
    public static void handle(final HandheldTypewriterSavePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof final ServerPlayer player)
                    || !(player.containerMenu instanceof HandheldTypewriterMenu)) {
                return;
            }

            final ItemStack controller = player.getMainHandItem();
            if (!HandheldTypewriterData.isController(controller)) {
                return;
            }

            final LinkedTypewriterEntries entries = new LinkedTypewriterEntries();
            if (!packet.clearAll()) {
                entries.addAll(packet.keys());
            }
            HandheldTypewriterData.writeEntries(controller, entries, player.registryAccess());
        });
    }
}