package edn.lakeopossmc.drivebysable;

import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class CableSimulatedTab {

    private static final ResourceLocation SECTION_ID = DriveBySableMod.asResource("drivebysable_section");

    public static void register() {
        addItem(CableItems.CABLE_IO_BUS.get());
        addItem(CableItems.CABLE.get());
        addItem(CableItems.CABLE_CUTTER.get());
        addItem(CableItems.CABLE_HUB_BLOCK.get());
        if (CableItems.ADVANCED_CABLE_HUB_BLOCK != null) {
            addItem(CableItems.ADVANCED_CABLE_HUB_BLOCK.get());
        }
        if (CableItems.CABLE_TYPEWRITER_HUB != null) {
            addItem(CableItems.CABLE_TYPEWRITER_HUB.get());
        }
        addItem(CableItems.BACKUP_DRIVE.get());
    }

    private static void addItem(final Item item) {
        final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        SimulatedRegistrate.TAB_ITEMS.add(() -> item);
        SimulatedRegistrate.ITEM_TO_SECTION.put(itemId, SECTION_ID);
    }

    private CableSimulatedTab() {
    }
}