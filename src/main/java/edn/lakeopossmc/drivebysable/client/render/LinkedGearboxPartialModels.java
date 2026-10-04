package edn.lakeopossmc.drivebysable.client.render;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

// --- MOVING PIECES OF THE LINKED GEARBOX --- //
public final class LinkedGearboxPartialModels {

    public static final PartialModel COG = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath("create", "block/cogwheel"));

    private LinkedGearboxPartialModels() {
    }

    public static void load() {
    }
}