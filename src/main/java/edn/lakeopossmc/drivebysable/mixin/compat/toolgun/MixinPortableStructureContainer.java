package edn.lakeopossmc.drivebysable.mixin.compat.toolgun;

import edn.lakeopossmc.drivebysable.compat.toolgun.ToolgunStructureMove;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- MARKS A CONTAINER PICKUP AS A MOVE --- //
// * Pseudo since the toolgun may not be loaded
@Pseudo
@Mixin(targets = "com.enxv.aeronauticsstructuretool.PortableStructureContainerItem", remap = false)
public abstract class MixinPortableStructureContainer {

    @Inject(method = "captureIntoItem", at = @At("HEAD"), require = 0, expect = 0)
    private void drivebysable$beginStructureMove(
            final ServerLevel level,
            final UseOnContext context,
            final ItemStack stack,
            final CallbackInfo ci
    ) {
        ToolgunStructureMove.begin(level);
    }

    @Inject(method = "captureIntoItem", at = @At("RETURN"), require = 0, expect = 0)
    private void drivebysable$endStructureMove(
            final ServerLevel level,
            final UseOnContext context,
            final ItemStack stack,
            final CallbackInfo ci
    ) {
        ToolgunStructureMove.end();
    }
}