package edn.lakeopossmc.drivebysable.mixin;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// --- POWERED LINKED GEARBOX BREAKS ITS KINETIC CONNECTIONS --- //
@Mixin(value = RotationPropagator.class, remap = false)
public abstract class MixinRotationPropagator {

    @Inject(method = "getRotationSpeedModifier", at = @At("HEAD"), cancellable = true)
    private static void drivebysable$disconnectPoweredLinkedGearbox(
            final KineticBlockEntity from,
            final KineticBlockEntity to,
            final CallbackInfoReturnable<Float> cir
    ) {
        if (LinkedGearboxBlock.isDisconnected(from.getBlockState())
                || LinkedGearboxBlock.isDisconnected(to.getBlockState())) {
            cir.setReturnValue(0.0F);
        }
    }
}