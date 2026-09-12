package edn.lakeopossmc.drivebysable.items;

import edn.lakeopossmc.drivebysable.CableConfig;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

// --- CONFIG GATED MULTI CHANNEL CABLE BUS --- //
// * Disabling the extension has to make the item unobtainable
public class MultiChannelCableBusItem extends BlockItem {

    public MultiChannelCableBusItem(final Block block, final Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isEnabled(final FeatureFlagSet enabledFeatures) {
        return super.isEnabled(enabledFeatures) && isExtensionEnabled();
    }

    public static boolean isExtensionEnabled() {
        try {
            return CableConfig.CONFIG.multiChannelCableBus.get();
        } catch (final Exception e) {
            return true;
        }
    }
}