package edn.lakeopossmc.drivebysable.mixin.compat.tweaked;

import com.getitemfromblock.create_tweaked_controllers.block.TweakedLecternControllerBlockEntity;
import com.getitemfromblock.create_tweaked_controllers.controller.ControllerRedstoneOutput;
import com.getitemfromblock.create_tweaked_controllers.packet.TweakedLinkedControllerButtonPacket;
import edn.lakeopossmc.drivebysable.compat.TweakedControllerCableServerHandler;
import edn.lakeopossmc.drivebysable.mixinducks.LecternCableHubDuck;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// --- FORWARD TWEAKED CONTROLLER BUTTONS TO CABLES --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(TweakedLinkedControllerButtonPacket.class)
public abstract class MixinTweakedControllerButtonPacket {
    @Shadow private short buttonStates;

    // * Push to lectern hub
    @Inject(method = "handleLectern", at = @At("RETURN"), remap = false)
    private void drivebysable$handleLectern(
        final ServerPlayer player,
        final TweakedLecternControllerBlockEntity lectern,
        final CallbackInfo ci
    ) {
        final ControllerRedstoneOutput output = new ControllerRedstoneOutput();
        output.DecodeButtons(buttonStates);
        final List<Boolean> buttons = List.of(output.buttons);
        TweakedControllerCableServerHandler.receiveButton(player.level(), lectern.getBlockPos(), buttons);
        if (lectern instanceof final LecternCableHubDuck lecternHub && lecternHub.drivebysable$getHubPos() != null) {
            TweakedControllerCableServerHandler.receiveButton(player.level(), lecternHub.drivebysable$getHubPos(), buttons);
        }
    }

    // * Push to hub bound on held item
    @Inject(method = "handleItem", at = @At("RETURN"), remap = false)
    private void drivebysable$handleItem(final ServerPlayer player, final ItemStack heldItem, final CallbackInfo ci) {
        HubItem.ifHubPresent(heldItem, pos -> {
            final ControllerRedstoneOutput output = new ControllerRedstoneOutput();
            output.DecodeButtons(buttonStates);
            TweakedControllerCableServerHandler.receiveButton(player.level(), pos, List.of(output.buttons));
        });
    }
}
