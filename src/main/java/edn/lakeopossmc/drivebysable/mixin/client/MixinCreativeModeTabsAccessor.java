package edn.lakeopossmc.drivebysable.mixin.client;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// --- LETS THE CACHED TAB PARAMETERS BE CLEARED --- //
@Mixin(CreativeModeTabs.class)
public interface MixinCreativeModeTabsAccessor {

    @Accessor("CACHED_PARAMETERS")
    static void drivebysable$setCachedParameters(final CreativeModeTab.ItemDisplayParameters parameters) {
        throw new AssertionError("Replaced by the mixin processor");
    }
}