package edn.lakeopossmc.drivebysable.client.render;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import edn.lakeopossmc.drivebysable.DriveBySableMod;

// --- THE BULB GLOW OF THE INTERMEDIATE AND ADVANCED CABLE HUBS --- //
public final class CableHubPartialModels {

    private static final String PREFIX = "block/";

    public static final PartialModel INTERMEDIATE_GLOW = of("intermediate_cable_hub/glow");
    public static final PartialModel INTERMEDIATE_GLOW_VERTICAL = of("intermediate_cable_hub/glow_vertical");
    public static final PartialModel ADVANCED_GLOW = of("advanced_cable_hub/glow");
    public static final PartialModel ADVANCED_GLOW_VERTICAL = of("advanced_cable_hub/glow_vertical");

    public static final PartialModel INTERMEDIATE_BULB = of("intermediate_cable_hub/bulb");
    public static final PartialModel INTERMEDIATE_BULB_VERTICAL = of("intermediate_cable_hub/bulb_vertical");
    public static final PartialModel ADVANCED_BULB = of("advanced_cable_hub/bulb");
    public static final PartialModel ADVANCED_BULB_VERTICAL = of("advanced_cable_hub/bulb_vertical");

    public static final PartialModel TYPEWRITER_GLOW = of("cable_typewriter_hub/glow");
    public static final PartialModel TYPEWRITER_BULB = of("cable_typewriter_hub/bulb");

    private CableHubPartialModels() {
    }

    private static PartialModel of(final String name) {
        return PartialModel.of(DriveBySableMod.asResource(PREFIX + name));
    }

    // * Touching the class is enough to register them all
    public static void load() {
    }
}