package edn.lakeopossmc.drivebysable.mixin.compat.tweaked;

import com.getitemfromblock.create_tweaked_controllers.item.TweakedLinkedControllerItem;
import edn.lakeopossmc.drivebysable.mixinducks.TweakedControllerDuck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

// --- MARK TWEAKED CONTROLLER ITEM AS DUCK --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(TweakedLinkedControllerItem.class)
public class MixinTweakedController implements TweakedControllerDuck {
}
