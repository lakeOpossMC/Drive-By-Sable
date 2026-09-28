package edn.lakeopossmc.drivebysable.network;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.simulated_team.simulated.content.blocks.redstone.AbstractLinkedReceiverBlockEntity;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries.KeyboardEntry;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.compat.HandheldTypewriterData;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

// --- CLIENT BINDS A KEY TO THE REDSTONE LINK IT CLICKED --- //
// * Same idea as the Linked Controller's bind mode, saved as a typewriter entry on the held controller
public record HandheldTypewriterBindPacket(BlockPos linkPos, int key, boolean offHand) implements CustomPacketPayload {

    public static final Type<HandheldTypewriterBindPacket> TYPE =
            new Type<>(DriveBySableMod.asResource("handheld_typewriter_bind"));

    public static final StreamCodec<ByteBuf, HandheldTypewriterBindPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HandheldTypewriterBindPacket::linkPos,
            ByteBufCodecs.VAR_INT, HandheldTypewriterBindPacket::key,
            ByteBufCodecs.BOOL, HandheldTypewriterBindPacket::offHand,
            HandheldTypewriterBindPacket::new
    );

    // * Generous, the client already checked it was in reach when the link was clicked
    private static final double MAX_DISTANCE_SQR = 64 * 64;

    @Override
    public Type<HandheldTypewriterBindPacket> type() {
        return TYPE;
    }

    public static void handle(final HandheldTypewriterBindPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof final ServerPlayer player) || player.isSpectator() || !player.mayBuild()) {
                return;
            }
            if (player.position().distanceToSqr(Vec3.atCenterOf(packet.linkPos())) > MAX_DISTANCE_SQR) {
                return;
            }

            final ItemStack controller = player.getItemInHand(packet.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!HandheldTypewriterData.isController(controller)) {
                return;
            }

            final Couple<Frequency> frequency = frequencyAt(player.level(), packet.linkPos());
            if (frequency == null) {
                return;
            }

            final LinkedTypewriterEntries entries = HandheldTypewriterData.readEntries(controller, player.registryAccess());
            entries.setKey(packet.key(), new KeyboardEntry(frequency.getFirst(), frequency.getSecond(), packet.key(), BlockPos.ZERO));
            HandheldTypewriterData.writeEntries(controller, entries, player.registryAccess());
        });
    }

    @Nullable
    public static Couple<Frequency> frequencyAt(final Level level, final BlockPos pos) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof final AbstractLinkedReceiverBlockEntity receiver) {
            return receiver.getFrequency();
        }
        final LinkBehaviour link = BlockEntityBehaviour.get(level, pos, LinkBehaviour.TYPE);
        return link == null ? null : link.getNetworkKey();
    }
}