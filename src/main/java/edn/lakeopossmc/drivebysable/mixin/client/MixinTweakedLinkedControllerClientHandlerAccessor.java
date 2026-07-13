package edn.lakeopossmc.drivebysable.mixin.client;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- EXPOSE PRIVATE LECTERN POS FIELD --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(targets = "com.getitemfromblock.create_tweaked_controllers.controller.TweakedLinkedControllerClientHandler", remap = false)
public interface MixinTweakedLinkedControllerClientHandlerAccessor {
    @Accessor("lecternPos")
    static BlockPos drivebysable$getLecternPos() {
        throw new AssertionError();
    }
}
