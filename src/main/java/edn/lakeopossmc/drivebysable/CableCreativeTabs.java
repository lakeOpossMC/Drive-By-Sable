package edn.lakeopossmc.drivebysable;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// --- MAIN CREATIVE TAB FOR THE MOD --- //
public final class CableCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB,
            DriveBySableMod.MOD_ID
    );

    // * Only show hub items tied to a loaded compat mod
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BASE_CREATIVE_TAB = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.drivebysable"))
                    .icon(() -> CableItems.CABLE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(CableItems.CABLE.get());
                        output.accept(CableItems.CABLE_CUTTER.get());
                        output.accept(CableItems.BACKUP_DRIVE.get());
                        output.accept(CableItems.CABLE_HUB_BLOCK.get());
                        if (ModList.get().isLoaded("create_tweaked_controllers")) {
                            output.accept(CableItems.ADVANCED_CABLE_HUB_BLOCK.get());
                        }
                        if (ModList.get().isLoaded("simulated")) {
                            output.accept(CableItems.CABLE_TYPEWRITER_HUB.get());
                        }
                    })
                    .build()
    );

    private CableCreativeTabs() {
    }

    public static void register(final IEventBus modEventBus) {
        CableCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
    }
}
