package edn.lakeopossmc.drivebysable.mixin.compat;

import dev.simulated_team.simulated.network.packets.linked_typewriter.TypewriterSaveKeyToItemPacket;
import edn.lakeopossmc.drivebysable.CableItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TypewriterSaveKeyToItemPacket.class)
public abstract class MixinTypewriterSaveKeyToItemPacket {

    @Redirect(method = "handle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean drivebysable$allowOurItem(final ItemStack stack, final Item item) {
        return stack.is(item) || stack.is(CableItems.CABLE_TYPEWRITER_HUB.get());
    }
}