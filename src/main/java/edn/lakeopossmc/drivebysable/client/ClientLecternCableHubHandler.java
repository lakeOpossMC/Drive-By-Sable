package edn.lakeopossmc.drivebysable.client;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.CableHubBlock;
import edn.lakeopossmc.drivebysable.mixin.client.MixinLinkedControllerClientHandlerAccessor;
import edn.lakeopossmc.drivebysable.network.BindLecternCableHubPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// --- EMPTY HAND CLICK BINDS LECTERN TO HUB --- //
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID, value = Dist.CLIENT)
public final class ClientLecternCableHubHandler {
    private ClientLecternCableHubHandler() {
    }

    // * Only fires with a lectern controller open and empty hand
    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isServer()) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        final Player player = event.getEntity();
        if (player == null || player.isSpectator() || !event.getItemStack().isEmpty()) {
            return;
        }

        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof CableHubBlock)) {
            return;
        }

        final BlockPos lecternPos = MixinLinkedControllerClientHandlerAccessor.drivebysable$getLecternPos();
        if (lecternPos == null) {
            return;
        }

        PacketDistributor.sendToServer(new BindLecternCableHubPacket(lecternPos, event.getPos()));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
