package edn.lakeopossmc.drivebysable;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import edn.lakeopossmc.drivebysable.display.IntegratedSensorBusDisplaySource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// --- DISPLAY LINK SOURCES --- //
public final class CableDisplaySources {
    private static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
            DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, DriveBySableMod.MOD_ID);

    public static final DeferredHolder<DisplaySource, IntegratedSensorBusDisplaySource> INTEGRATED_SENSOR_BUS =
            DISPLAY_SOURCES.register("integrated_sensor_bus", IntegratedSensorBusDisplaySource::new);

    private CableDisplaySources() {
    }

    public static void register(final IEventBus modEventBus) {
        DISPLAY_SOURCES.register(modEventBus);
    }

    // * Bound by block entity type, so the legacy sensor bus block reads the same
    public static void bind() {
        DisplaySource.BY_BLOCK_ENTITY.add(CableBlockEntities.INTEGRATED_SENSOR_BUS.get(), INTEGRATED_SENSOR_BUS.get());
    }
}