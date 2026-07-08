package edn.lakeopossmc.drivebysable.ponder;

import com.tterrag.registrate.util.entry.ItemProviderEntry;
import edn.lakeopossmc.drivebysable.CableBlocks;
import edn.lakeopossmc.drivebysable.CableItems;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;

public class DriveBySablePonderScenes {
    public static void register(final PonderSceneRegistrationHelper<ResourceLocation> registry) {

        registry.forComponents(CableItems.CABLE_HUB_BLOCK.getId())
                .addStoryBoard("cable_hub1", CableScenes::cableHubIntro,
                        DriveBySablePonderPlugin.DRIVE_BY_SABLE_TAG);
        registry.forComponents(CableItems.CABLE_HUB_BLOCK.getId())
                .addStoryBoard("cable_hub2", CableScenes::cableHubLecternIntro,
                        DriveBySablePonderPlugin.DRIVE_BY_SABLE_TAG);

        if (CableItems.ADVANCED_CABLE_HUB_BLOCK != null) {
            registry.forComponents(CableItems.ADVANCED_CABLE_HUB_BLOCK.getId())
                    .addStoryBoard("advanced_cable_hub1", CableScenes::cableAdvancedHubIntro,
                            DriveBySablePonderPlugin.DRIVE_BY_SABLE_TAG);
            registry.forComponents(CableItems.ADVANCED_CABLE_HUB_BLOCK.getId())
                    .addStoryBoard("advanced_cable_hub2", CableScenes::advancedCableHubLecternIntro,
                            DriveBySablePonderPlugin.DRIVE_BY_SABLE_TAG);
        }

        if (CableItems.CABLE_TYPEWRITER_HUB != null) {
            registry.forComponents(CableItems.CABLE_TYPEWRITER_HUB.getId())
                    .addStoryBoard("cable_typewriter_hub1", CableScenes::cableTypewriterHubIntro,
                            DriveBySablePonderPlugin.DRIVE_BY_SABLE_TAG);
        }
    }
}