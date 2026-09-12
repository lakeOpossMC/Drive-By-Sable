package edn.lakeopossmc.drivebysable;

import edn.lakeopossmc.drivebysable.menu.BackupDriveMenu;
import edn.lakeopossmc.drivebysable.menu.IntegratedSensorBusMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// --- MENU TYPES --- //
public final class CableMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, DriveBySableMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<BackupDriveMenu>> BACKUP_DRIVE = MENUS.register(
            "backup_drive",
            () -> IMenuTypeExtension.create(BackupDriveMenu::new)
    );

    public static final DeferredHolder<MenuType<?>, MenuType<IntegratedSensorBusMenu>> INTEGRATED_SENSOR_BUS =
            MENUS.register(
                    "integrated_sensor_bus",
                    () -> IMenuTypeExtension.create(IntegratedSensorBusMenu::new)
            );

    private CableMenus() {
    }

    public static void register(final IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}