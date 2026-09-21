package edn.lakeopossmc.drivebysable.mixin.client;

import com.getitemfromblock.create_tweaked_controllers.block.TweakedLecternControllerBlockEntity;
import com.getitemfromblock.create_tweaked_controllers.controller.TweakedLinkedControllerClientHandler;
import com.getitemfromblock.create_tweaked_controllers.item.ModItems;

import edn.lakeopossmc.drivebysable.blocks.AdvancedCableHubBlockEntity;
import edn.lakeopossmc.drivebysable.mixinducks.LecternCableHubDuck;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// --- ENABLE FULL PRECISION WHEN A TWEAKED CONTROLLER IS BOUND TO AN ADVANCED HUB --- //
// * Pseudo since the mod may not be loaded
@Pseudo
@Mixin(targets = "com.getitemfromblock.create_tweaked_controllers.controller.TweakedLinkedControllerClientHandler", remap = false)
public abstract class MixinTweakedLinkedControllerClientHandler {
    @ModifyVariable(method = "tick", at = @At("STORE"), name = "useFullPrec", remap = false)
    private static boolean modifyUseFullPrec(boolean original) {
        // If the original value is true, we don't need to check anything else
        if (original) {
            return true;
        }

        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        BlockPos hubPos = null;

        // Determine the position of the hub, either from the lectern or the held item
        if (TweakedLinkedControllerClientHandler.inLectern()) {
            final BlockPos lecternPos = MixinTweakedLinkedControllerClientHandlerAccessor.drivebysable$getLecternPos();
            if (lecternPos == null) {
                return original;
            }

            final TweakedLecternControllerBlockEntity lectern = (TweakedLecternControllerBlockEntity) player.level()
                    .getBlockEntity(lecternPos);
            if (lectern == null || !(lectern instanceof final LecternCableHubDuck lecternHub)) {
                return original;
            }

            hubPos = lecternHub.drivebysable$getHubPos();
        } else {
            ItemStack heldItem = player.getMainHandItem();
            if (!ModItems.TWEAKED_LINKED_CONTROLLER.isIn(heldItem)) {
                heldItem = player.getOffhandItem();
            }

            if (!ModItems.TWEAKED_LINKED_CONTROLLER.isIn(heldItem)) {
                return original;
            }

            final Optional<BlockPos> hubPosOptional = HubItem.getHubPos(heldItem);
            if (hubPosOptional.isEmpty()) {
                return original;
            }

            hubPos = hubPosOptional.get();
        }

        if (hubPos == null) {
            return original;
        }

        if (!(player.level().getBlockEntity(hubPos) instanceof AdvancedCableHubBlockEntity hub)) {
            return original;
        }
        
        return hub.shouldUseFullPrecision();
    }
}
