package edn.lakeopossmc.drivebysable;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.item.ItemDescription;
import edn.lakeopossmc.drivebysable.network.CablePackets;
import edn.lakeopossmc.drivebysable.ponder.DriveBySablePonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(DriveBySableMod.MOD_ID)
public class DriveBySableMod {
    public static final String MOD_ID = "drivebysable";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DriveBySableMod(final IEventBus modEventBus, final ModContainer modContainer, final Dist dist) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CableConfig.CONFIG_SPEC);
        if (dist == Dist.CLIENT) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
        CableBlocks.register(modEventBus);
        CableBlockEntities.register(modEventBus);
        CableItems.register(modEventBus);
        if (!ModList.get().isLoaded("simulated")) {
            CableCreativeTabs.register(modEventBus);
        }
        CableSounds.register(modEventBus);
        modEventBus.addListener(CablePackets::register);
        modEventBus.addListener(this::clientSetup);

        NeoForge.EVENT_BUS.addListener(CableCommonEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(CableCommonEvents::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(CableCommonEvents::onBlockBreak);
    }

    private void clientSetup(final FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("simulated")) {
            event.enqueueWork(CableSimulatedTab::register);
        }

        event.enqueueWork(() -> PonderIndex.addPlugin(new DriveBySablePonderPlugin()));
    }

    public static ResourceLocation asResource(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
