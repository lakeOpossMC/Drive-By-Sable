package edn.lakeopossmc.drivebysable;

import edn.lakeopossmc.drivebysable.items.CableCutterItem;
import edn.lakeopossmc.drivebysable.items.CableItem;
import edn.lakeopossmc.drivebysable.items.CableTypewriterHubItem;
import edn.lakeopossmc.drivebysable.items.NetworkBackupDriveItem;
import edn.lakeopossmc.drivebysable.items.NetworkAnchorItem;
import edn.lakeopossmc.drivebysable.legacy.LegacyWireCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;

// --- REGISTERS ALL ITEMS --- //
public final class CableItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DriveBySableMod.MOD_ID);

    public static final DeferredItem<CableItem> CABLE = ITEMS.register("cable", () -> new CableItem(new Item.Properties()));
    public static final DeferredItem<CableCutterItem> CABLE_CUTTER = ITEMS.register(
            "cable_cutter",
            () -> new CableCutterItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> CABLE_IO_BUS = ITEMS.registerSimpleItem("cable_io_bus");
    public static final DeferredItem<Item> INCOMPLETE_CABLE_IO_BUS = ITEMS.registerSimpleItem("incomplete_cable_io_bus");
    public static final DeferredItem<NetworkBackupDriveItem> BACKUP_DRIVE = ITEMS.register(
            "backup_drive",
            () -> new NetworkBackupDriveItem(CableBlocks.BACKUP_DRIVE.get(), new Item.Properties())
    );
    public static final DeferredItem<BlockItem> CABLE_HUB_BLOCK = ITEMS.registerSimpleBlockItem("cable_hub", CableBlocks.CABLE_HUB);
    public static final DeferredItem<BlockItem> NETWORK_ANCHOR = ITEMS.register(
            "network_anchor",
            () -> new NetworkAnchorItem(CableBlocks.NETWORK_ANCHOR.get(), new Item.Properties())
    );

    // * Null when tweaked controllers isnt loaded
    @Nullable
    public static final DeferredItem<BlockItem> ADVANCED_CABLE_HUB_BLOCK =
            ModList.get().isLoaded("create_tweaked_controllers")
                    ? ITEMS.registerSimpleBlockItem(
                    "advanced_cable_hub",
                    CableBlocks.ADVANCED_CABLE_HUB)
                    : null;

    // * Null when simulated isnt loaded
    @Nullable
    public static final DeferredItem<CableTypewriterHubItem> CABLE_TYPEWRITER_HUB =
            ModList.get().isLoaded("simulated")
                    ? ITEMS.register(
                    "cable_typewriter_hub",
                    () -> new CableTypewriterHubItem(CableBlocks.CABLE_TYPEWRITER_HUB.get(), new Item.Properties()))
                    : null;

    private CableItems() {
    }

    //#region // --- LEGACY ALIASES --- //
    private static void addLegacyAliases() {
        alias(LegacyWireCompat.LEGACY_WIRE, "cable");
        alias(LegacyWireCompat.LEGACY_WIRE_CUTTER, "cable_cutter");
        alias(LegacyWireCompat.LEGACY_BACKUP_BLOCK, "backup_drive");
        alias(LegacyWireCompat.LEGACY_CONTROLLER_HUB, "cable_hub");

        // * Only registered when tweaked controllers is loaded
        if (ADVANCED_CABLE_HUB_BLOCK != null) {
            alias(LegacyWireCompat.LEGACY_TWEAKED_CONTROLLER_HUB, "advanced_cable_hub");
        }
    }

    private static void alias(final String legacyPath, final String currentPath) {
        ITEMS.addAlias(
                ResourceLocation.fromNamespaceAndPath(LegacyWireCompat.LEGACY_MOD_ID, legacyPath),
                ResourceLocation.fromNamespaceAndPath(DriveBySableMod.MOD_ID, currentPath)
        );
    }
    //#endregion

    public static void register(final IEventBus modEventBus) {
        addLegacyAliases();
        ITEMS.register(modEventBus);
    }
}