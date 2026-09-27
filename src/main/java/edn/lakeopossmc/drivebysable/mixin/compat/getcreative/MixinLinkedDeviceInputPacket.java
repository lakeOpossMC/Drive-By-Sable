package edn.lakeopossmc.drivebysable.mixin.compat.getcreative;

import amaryllis.get_creative.appliances.linked_controller.lectern.LecternDeviceBlockEntity;
import amaryllis.get_creative.appliances.linked_controller.packets.LinkedDeviceInputPacket;
import edn.lakeopossmc.drivebysable.compat.GetCreativeCableServerHandler;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// --- FORWARD GET CREATIVE DEVICE BUTTONS TO CABLES --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(LinkedDeviceInputPacket.class)
public abstract class MixinLinkedDeviceInputPacket {
    @Shadow(remap = false) @Final private List<Integer> activatedButtons;
    @Shadow(remap = false) @Final private boolean press;

    // * Push to the lectern itself
    @Inject(method = "handleLectern", at = @At("RETURN"), remap = false)
    private void drivebysable$handleLectern(
            final ServerPlayer player,
            final LecternDeviceBlockEntity lectern,
            final CallbackInfo ci
    ) {
        if (!lectern.isUsedBy(player) || (player.isSpectator() && press)) {
            return;
        }

        GetCreativeCableServerHandler.receivePressed(
                player.level(), lectern.getBlockPos(), lectern.getDevice(), activatedButtons, press);
    }

    // * Push to hub bound on the device
    // * Also reached from the lectern path, with the lectern's device stack
    @Inject(method = "handleItem", at = @At("RETURN"), remap = false)
    private void drivebysable$handleItem(final ServerPlayer player, final ItemStack device, final CallbackInfo ci) {
        // * Get Creative ignores spectator presses, so do we
        if (player.isSpectator() && press) {
            return;
        }

        HubItem.ifHubPresent(device, pos ->
                GetCreativeCableServerHandler.receivePressed(player.level(), pos, device, activatedButtons, press));
    }
}