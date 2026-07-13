package edn.lakeopossmc.drivebysable.mixin.compat;

import com.simibubi.create.content.redstone.link.controller.LecternControllerBlockEntity;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerInputPacket;
import edn.lakeopossmc.drivebysable.compat.LinkedControllerCableServerHandler;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// --- FORWARD CONTROLLER BUTTON PRESSES TO CABLES --- //
@Mixin(LinkedControllerInputPacket.class)
public abstract class MixinLinkedControllerInputPacket {
    @Shadow @Final private List<Integer> activatedButtons;
    @Shadow @Final private boolean press;

    // * Push to lectern hub
    @Inject(method = "handleLectern", at = @At("RETURN"), remap = false)
    private void drivebysable$handleLectern(final ServerPlayer player, final LecternControllerBlockEntity lectern, final CallbackInfo ci) {
        LinkedControllerCableServerHandler.receivePressed(player.level(), lectern.getBlockPos(), activatedButtons, press);
    }

    // * Push to hub bound on held item
    @Inject(method = "handleItem", at = @At("RETURN"), remap = false)
    private void drivebysable$handleItem(final ServerPlayer player, final ItemStack heldItem, final CallbackInfo ci) {
        HubItem.ifHubPresent(heldItem, pos -> LinkedControllerCableServerHandler.receivePressed(player.level(), pos, activatedButtons, press));
    }
}
