package edn.lakeopossmc.drivebysable.mixin.compat;

import com.simibubi.create.content.redstone.link.controller.LecternControllerBlockEntity;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerStopLecternPacket;
import edn.lakeopossmc.drivebysable.compat.LinkedControllerCableServerHandler;
import edn.lakeopossmc.drivebysable.mixinducks.LecternCableHubDuck;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- CLEAR CABLE SIGNALS WHEN CONTROLLER STOPS --- //
@Mixin(LinkedControllerStopLecternPacket.class)
public class MixinLinkedControllerStopLecternPacket {
    // * Reset lectern and any bound hub too
    @Inject(method = "handleLectern", at = @At("RETURN"), remap = false)
    private void drivebysable$handleLectern(final ServerPlayer player, final LecternControllerBlockEntity lectern, final CallbackInfo ci) {
        LinkedControllerCableServerHandler.reset(player.level(), lectern.getBlockPos());
        if (lectern instanceof final LecternCableHubDuck lecternHub && lecternHub.drivebysable$getHubPos() != null) {
            LinkedControllerCableServerHandler.reset(player.level(), lecternHub.drivebysable$getHubPos());
        }
    }

    // * Reset hub bound to held item
    @Inject(method = "handleItem", at = @At("RETURN"), remap = false)
    private void drivebysable$handleItem(final ServerPlayer player, final ItemStack heldItem, final CallbackInfo ci) {
        HubItem.ifHubPresent(heldItem, pos -> LinkedControllerCableServerHandler.reset(player.level(), pos));
    }
}
