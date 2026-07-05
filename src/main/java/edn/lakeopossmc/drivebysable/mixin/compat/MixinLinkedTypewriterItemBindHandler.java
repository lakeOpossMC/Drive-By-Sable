package edn.lakeopossmc.drivebysable.mixin.compat;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterItemBindHandler;
import edn.lakeopossmc.drivebysable.CableItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LinkedTypewriterItemBindHandler.class)
public abstract class MixinLinkedTypewriterItemBindHandler {

    @Inject(method = "getHand", at = @At("HEAD"), cancellable = true)
    private static void drivebysable$getHand(final CallbackInfoReturnable<InteractionHand> cir) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        final Item item = CableItems.CABLE_TYPEWRITER_HUB.get();
        if (player.getMainHandItem().is(item)) {
            cir.setReturnValue(InteractionHand.MAIN_HAND);
        } else if (player.getOffhandItem().is(item)) {
            cir.setReturnValue(InteractionHand.OFF_HAND);
        }
    }
}