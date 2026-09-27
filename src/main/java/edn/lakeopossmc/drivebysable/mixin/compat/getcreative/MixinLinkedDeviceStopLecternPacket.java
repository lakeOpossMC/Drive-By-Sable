package edn.lakeopossmc.drivebysable.mixin.compat.getcreative;

import amaryllis.get_creative.appliances.linked_controller.lectern.LecternDeviceBlockEntity;
import amaryllis.get_creative.appliances.linked_controller.packets.LinkedDeviceStopLecternPacket;
import edn.lakeopossmc.drivebysable.compat.GetCreativeCableServerHandler;
import edn.lakeopossmc.drivebysable.mixinducks.LecternCableHubDuck;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- CLEAR CABLE SIGNALS WHEN A GET CREATIVE LECTERN STOPS --- //
// * Resets the lectern itself and any bound hub
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(LinkedDeviceStopLecternPacket.class)
public class MixinLinkedDeviceStopLecternPacket {
    // * Only reset if this player was actually the one using it
    @Inject(method = "handleLectern", at = @At("HEAD"), remap = false)
    private void drivebysable$handleLectern(
            final ServerPlayer player,
            final LecternDeviceBlockEntity lectern,
            final CallbackInfo ci
    ) {
        if (!lectern.isUsedBy(player)) {
            return;
        }

        GetCreativeCableServerHandler.reset(player.level(), lectern.getBlockPos());

        if (lectern instanceof final LecternCableHubDuck lecternHub) {
            final BlockPos hubPos = lecternHub.drivebysable$getHubPos();
            if (hubPos != null) {
                GetCreativeCableServerHandler.reset(player.level(), hubPos);
            }
        }
    }
}