package edn.lakeopossmc.drivebysable.mixin.compat.tweaked;

import com.getitemfromblock.create_tweaked_controllers.packet.TweakedLinkedControllerPacketBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- EXPOSE THE PRECISION FLAG FROM THE TWEAKED PACKET BASE CLASS --- //
// * Pseudo since the mod may not be loaded
@Pseudo
@Mixin(TweakedLinkedControllerPacketBase.class)
public interface MixinTweakedControllerPacketBaseAccessor {
    @Accessor("useFullPrecision")
    boolean drivebysable$getUseFullPrecision();
}
