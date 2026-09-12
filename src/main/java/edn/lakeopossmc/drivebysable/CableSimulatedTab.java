package edn.lakeopossmc.drivebysable;

import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import edn.lakeopossmc.drivebysable.items.IntegratedSensorBusItem;
import edn.lakeopossmc.drivebysable.items.MultiChannelCableBusItem;
import edn.lakeopossmc.drivebysable.items.NetworkAnchorItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// --- ADD ITEMS TO SIMULATED CREATIVE SECTION --- //
public final class CableSimulatedTab {

    private static final ResourceLocation SECTION_ID = DriveBySableMod.asResource("drivebysable_section");

    // * What this mod put into the shared list, so it can be taken back out
    private static final List<Supplier<Item>> CONTRIBUTED = new ArrayList<>();
    private static final List<ResourceLocation> CONTRIBUTED_IDS = new ArrayList<>();

    // * Safe to call again whenever an extension is switched on or off
    public static void refresh() {
        for (final Supplier<Item> entry : CONTRIBUTED) {
            SimulatedRegistrate.TAB_ITEMS.remove(entry);
        }
        for (final ResourceLocation id : CONTRIBUTED_IDS) {
            SimulatedRegistrate.ITEM_TO_SECTION.remove(id);
        }

        CONTRIBUTED.clear();
        CONTRIBUTED_IDS.clear();
        register();
    }

    // * Skip items tied to a mod that isnt loaded
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
        if (MultiChannelCableBusItem.isExtensionEnabled()) {
            addItem(CableItems.MULTI_CHANNEL_CABLE_BUS.get());
        }
        if (IntegratedSensorBusItem.isExtensionEnabled()) {
            addItem(CableItems.INTEGRATED_SENSOR_BUS.get());
        }

        addItem(CableItems.BACKUP_DRIVE.get());

        if (NetworkAnchorItem.isExtensionEnabled()) {
            addItem(CableItems.NETWORK_ANCHOR.get());
        }
    }

    private static void addItem(final Item item) {
        final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        final Supplier<Item> entry = () -> item;

        SimulatedRegistrate.TAB_ITEMS.add(entry);
        SimulatedRegistrate.ITEM_TO_SECTION.put(itemId, SECTION_ID);

        CONTRIBUTED.add(entry);
        CONTRIBUTED_IDS.add(itemId);
    }

    private CableSimulatedTab() {
    }
}