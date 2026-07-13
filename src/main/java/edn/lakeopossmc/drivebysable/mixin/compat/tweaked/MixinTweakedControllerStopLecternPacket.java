package edn.lakeopossmc.drivebysable.mixin.compat.tweaked;

import com.getitemfromblock.create_tweaked_controllers.block.TweakedLecternControllerBlockEntity;
import com.getitemfromblock.create_tweaked_controllers.packet.TweakedLinkedControllerStopLecternPacket;
import edn.lakeopossmc.drivebysable.compat.TweakedControllerCableServerHandler;
import edn.lakeopossmc.drivebysable.mixinducks.LecternCableHubDuck;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- CLEAR CABLE SIGNALS WHEN TWEAKED CONTROLLER STOPS --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(TweakedLinkedControllerStopLecternPacket.class)
public class MixinTweakedControllerStopLecternPacket {
    // * Reset lectern and any bound hub too
    @Inject(method = "handleLectern", at = @At("RETURN"), remap = false)
    private void drivebysable$handleLectern(
        final ServerPlayer player,
        final TweakedLecternControllerBlockEntity lectern,
        final CallbackInfo ci
    ) {
        TweakedControllerCableServerHandler.reset(player.level(), lectern.getBlockPos());
        if (lectern instanceof final LecternCableHubDuck lecternHub && lecternHub.drivebysable$getHubPos() != null) {
            TweakedControllerCableServerHandler.reset(player.level(), lecternHub.drivebysable$getHubPos());
        }
    }

    // * Reset hub bound to held item
    @Inject(method = "handleItem", at = @At("RETURN"), remap = false)
    private void drivebysable$handleItem(final ServerPlayer player, final ItemStack heldItem, final CallbackInfo ci) {
        HubItem.ifHubPresent(heldItem, pos -> TweakedControllerCableServerHandler.reset(player.level(), pos));
    }
}
