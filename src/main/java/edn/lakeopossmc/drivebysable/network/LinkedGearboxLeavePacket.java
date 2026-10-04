package edn.lakeopossmc.drivebysable.network;

import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.CableSounds;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlockEntity;
import edn.lakeopossmc.drivebysable.cable.LinkedGearboxLinks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// --- CLIENT ASKS SERVER TO UPDATE NETWORK --- //
public record LinkedGearboxLeavePacket(BlockPos gearbox) implements CustomPacketPayload {
    public static final Type<LinkedGearboxLeavePacket> TYPE = new Type<>(DriveBySableMod.asResource("linked_gearbox_leave"));
    public static final StreamCodec<ByteBuf, LinkedGearboxLeavePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LinkedGearboxLeavePacket::gearbox,
            LinkedGearboxLeavePacket::new
    );

    @Override
    public Type<LinkedGearboxLeavePacket> type() {
        return TYPE;
    }

    // * Same rules as removing a connection
    public static void handle(final LinkedGearboxLeavePacket payload, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }

        if (!CableConfig.cableCanDisconnect(player)
                && !player.getMainHandItem().is(CableItems.CABLE_CUTTER.get())) {
            CableNetworkFullSyncPacket.sendTo(player);
            return;
        }

        if (!player.level().isLoaded(payload.gearbox())
                || !LinkedGearboxLinks.isGearbox(player.level(), payload.gearbox())) {
            CableNetworkFullSyncPacket.sendTo(player);
            return;
        }

        final int refund = LinkedGearboxLinks.leaveNetwork(player.level(), payload.gearbox());
        if (player.level().getBlockEntity(payload.gearbox()) instanceof final LinkedGearboxBlockEntity gearbox) {
            gearbox.clearLinkingFrequency();
        }
        if (refund > 0 && CableConfig.CONFIG.shouldConsumeCables.get() && !player.hasInfiniteMaterials()) {
            final ItemStack cables = new ItemStack(CableItems.CABLE.get(), refund);
            if (!player.addItem(cables)) {
                player.drop(cables, false);
            }
        }
        player.level().playSound(null, payload.gearbox(), CableSounds.PLUG_OUT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);

        CableNetworkFullSyncPacket.sendTo(player);
    }
}