package edn.lakeopossmc.drivebysable.client;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.AdvancedCableHubBlock;
import edn.lakeopossmc.drivebysable.mixin.client.MixinTweakedLinkedControllerClientHandlerAccessor;
import edn.lakeopossmc.drivebysable.network.BindLecternCableHubPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// --- SAME AS LECTERN HANDLER BUT FOR TWEAKED --- //
@EventBusSubscriber(modid = DriveBySableMod.MOD_ID, value = Dist.CLIENT)
public final class ClientAdvancedLecternCableHubHandler {
    private ClientAdvancedLecternCableHubHandler() {
    }

    // * Only fires with a tweaked lectern open and empty hand
    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isServer() || !ModList.get().isLoaded("create_tweaked_controllers")) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        final Player player = event.getEntity();
        if (player == null || player.isSpectator() || !event.getItemStack().isEmpty()) {
            return;
        }

        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof AdvancedCableHubBlock)) {
            return;
        }

        final BlockPos lecternPos = MixinTweakedLinkedControllerClientHandlerAccessor.drivebysable$getLecternPos();
        if (lecternPos == null) {
            return;
        }

        PacketDistributor.sendToServer(new BindLecternCableHubPacket(lecternPos, event.getPos()));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
