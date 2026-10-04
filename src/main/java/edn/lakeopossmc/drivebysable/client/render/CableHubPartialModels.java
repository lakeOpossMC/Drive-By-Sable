package edn.lakeopossmc.drivebysable.client.render;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import edn.lakeopossmc.drivebysable.DriveBySableMod;

// --- THE BULB GLOW OF THE INTERMEDIATE AND ADVANCED CABLE HUBS --- //
public final class CableHubPartialModels {

    private static final String PREFIX = "block/hub_glow/";

    public static final PartialModel INTERMEDIATE_GLOW = of("intermediate");
    public static final PartialModel INTERMEDIATE_GLOW_VERTICAL = of("intermediate_vertical");
    public static final PartialModel ADVANCED_GLOW = of("advanced");
    public static final PartialModel ADVANCED_GLOW_VERTICAL = of("advanced_vertical");

    private CableHubPartialModels() {
    }

    private static PartialModel of(final String name) {
        return PartialModel.of(DriveBySableMod.asResource(PREFIX + name));
    }

    // * Touching the class is enough to register them all
    public static void load() {
    }
}