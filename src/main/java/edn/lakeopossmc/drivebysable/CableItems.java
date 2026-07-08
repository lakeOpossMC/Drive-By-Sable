package edn.lakeopossmc.drivebysable;

import edn.lakeopossmc.drivebysable.items.CableCutterItem;
import edn.lakeopossmc.drivebysable.items.CableItem;
import edn.lakeopossmc.drivebysable.items.CableTypewriterHubItem;
import edn.lakeopossmc.drivebysable.items.NetworkBackupDriveItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;

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

    @Nullable
    public static final DeferredItem<BlockItem> ADVANCED_CABLE_HUB_BLOCK =
            ModList.get().isLoaded("create_tweaked_controllers")
                ? ITEMS.registerSimpleBlockItem(
                "advanced_cable_hub",
                CableBlocks.ADVANCED_CABLE_HUB)
                : null;

    @Nullable
    public static final DeferredItem<CableTypewriterHubItem> CABLE_TYPEWRITER_HUB =
            ModList.get().isLoaded("simulated")
                    ? ITEMS.register(
                    "cable_typewriter_hub",
                    () -> new CableTypewriterHubItem(CableBlocks.CABLE_TYPEWRITER_HUB.get(), new Item.Properties()))
                    : null;

    private CableItems() {
    }

    public static void register(final IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}