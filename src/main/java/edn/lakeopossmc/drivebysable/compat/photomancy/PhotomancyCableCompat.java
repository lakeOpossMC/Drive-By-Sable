package edn.lakeopossmc.drivebysable.compat.photomancy;

import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.legacy.LegacyWireCompat;
import net.neoforged.fml.ModList;

// --- REGISTERS OUR OWN PHOTOMANCY BLUEPRINT EVENT --- //
// * Photomancy gates its cable handling behind ModList.isLoaded("drivebywire")
public final class PhotomancyCableCompat {
    public static final String PHOTOMANCY_MOD_ID = "sable_schematic_api";

    private static boolean registered;

    private PhotomancyCableCompat() {
    }

    public static void register() {
        if (registered || !ModList.get().isLoaded(PHOTOMANCY_MOD_ID)) {
            return;
        }

        // * With DBW installed
        if (LegacyWireCompat.legacyModPresent()) {
            DriveBySableMod.LOGGER.info(
                    "[drivebywire-migration] Drive-By-Wire is installed, leaving photomancy blueprint "
                            + "cable handling to it."
            );
            return;
        }

        SableBlueprintEventRegistry.register(
                new PhotomancyCableBlueprintEvent(PhotomancyCableBlueprintEvent.legacyEventId())
        );

        registered = true;
        DriveBySableMod.LOGGER.info("Registered photomancy blueprint cable handling.");
    }
}