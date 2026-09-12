package edn.lakeopossmc.drivebysable.items;

import edn.lakeopossmc.drivebysable.CableConfig;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.fml.ModList;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

// --- CONFIG GATED INTEGRATED SENSOR BUS --- //
// * Disabling the extension has to make the item unobtainable
public class IntegratedSensorBusItem extends BlockItem {

    public static final String SIMULATED_MOD_ID = "simulated";

    public IntegratedSensorBusItem(final Block block, final Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isEnabled(final FeatureFlagSet enabledFeatures) {
        return super.isEnabled(enabledFeatures) && isExtensionEnabled();
    }

    public static boolean isExtensionEnabled() {
        // * Not a config decision. Without Simulated there is nothing to enable
        if (!ModList.get().isLoaded(SIMULATED_MOD_ID)) {
            return false;
        }

        try {
            return CableConfig.CONFIG.integratedSensorBus.get();
        } catch (final Exception e) {
            return true;
        }
    }
}