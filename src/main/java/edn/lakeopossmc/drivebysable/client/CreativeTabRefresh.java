package edn.lakeopossmc.drivebysable.client;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.mixin.client.MixinCreativeModeTabsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

// --- REBUILDS THE CREATIVE TABS WHEN AN EXTENSION IS TOGGLED --- //
public final class CreativeTabRefresh {

    private CreativeTabRefresh() {
    }

    public static void onConfigChanged(final ModConfigEvent event) {
        final ModConfig config = event.getConfig();
        if (!DriveBySableMod.MOD_ID.equals(config.getModId())) {
            return;
        }

        // * Config events arrive off the client thread
        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(CreativeTabRefresh::rebuild);
    }

    private static void rebuild() {
        final LocalPlayer player = Minecraft.getInstance().player;

        // * Nothing to rebuild against until a world is up
        if (player == null || player.connection == null) {
            return;
        }

        try {
            if (net.neoforged.fml.ModList.get().isLoaded("simulated")) {
                edn.lakeopossmc.drivebysable.CableSimulatedTab.refresh();
            }

            // * Cleared first, or the call sees matching parameters and returns
            MixinCreativeModeTabsAccessor.drivebysable$setCachedParameters(null);

            CreativeModeTabs.tryRebuildTabContents(
                    player.connection.enabledFeatures(),
                    player.canUseGameMasterBlocks(),
                    player.level().registryAccess()
            );

            // * The search tab keeps its own index of the contents
            final List<ItemStack> searchable = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
            player.connection.searchTrees().updateCreativeTags(searchable);
            player.connection.searchTrees().updateCreativeTooltips(
                    player.level().registryAccess(), searchable);
        } catch (final Exception e) {
            DriveBySableMod.LOGGER.warn("Could not rebuild the creative tabs after a config change", e);
        }
    }
}