package edn.lakeopossmc.drivebysable.ponder;

import com.simibubi.create.foundation.ponder.CreatePonderPlugin;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.items.HandheldTypewriterControllerItem;
import edn.lakeopossmc.drivebysable.items.IntegratedSensorBusItem;
import edn.lakeopossmc.drivebysable.items.MultiChannelCableBusItem;
import edn.lakeopossmc.drivebysable.items.NetworkAnchorItem;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

// --- PONDER PLUGIN ENTRY POINT --- //
public class DriveBySablePonderPlugin extends CreatePonderPlugin {
    public static final ResourceLocation DRIVE_BY_SABLE_TAG =
            ResourceLocation.fromNamespaceAndPath("drivebysable", "main");

    @Override
    public String getModId() {
        return DriveBySableMod.MOD_ID;
    }

    @Override
    public void registerScenes(final PonderSceneRegistrationHelper<ResourceLocation> helper) {
        DriveBySablePonderScenes.register(helper);
    }

    //#region // --- TAG SETUP FOR PONDER INDEX --- //
    // * Skip items tied to a mod that isnt loaded
    @Override
    public void registerTags(final PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(DRIVE_BY_SABLE_TAG)
                .addToIndex()
                .item(CableItems.CABLE_HUB_BLOCK.get(), true, false)
                .register();

        final var tag = helper.addToTag(DRIVE_BY_SABLE_TAG)
                .add(CableItems.CABLE.getId())
                .add(CableItems.CABLE_CUTTER.getId())
                .add(CableItems.CABLE_HUB_BLOCK.getId());

        if (CableItems.INTERMEDIATE_CABLE_HUB_BLOCK != null) {
            tag.add(CableItems.INTERMEDIATE_CABLE_HUB_BLOCK.getId());
        }
        if (CableItems.ADVANCED_CABLE_HUB_BLOCK != null) {
            tag.add(CableItems.ADVANCED_CABLE_HUB_BLOCK.getId());
        }
        if (CableItems.CABLE_TYPEWRITER_HUB != null) {
            tag.add(CableItems.CABLE_TYPEWRITER_HUB.getId());
        }

        // * Toggled entries follow their extension config, PonderTagRefresh reloads Ponder
        appliedExtensionMask = currentExtensionMask();
        if (CableItems.HANDHELD_TYPEWRITER_CONTROLLER != null && HandheldTypewriterControllerItem.isExtensionEnabled()) {
            tag.add(CableItems.HANDHELD_TYPEWRITER_CONTROLLER.getId());
        }
        if (MultiChannelCableBusItem.isExtensionEnabled()) {
            tag.add(CableItems.MULTI_CHANNEL_CABLE_BUS.getId());
        }
        if (IntegratedSensorBusItem.isExtensionEnabled()) {
            tag.add(CableItems.INTEGRATED_SENSOR_BUS.getId());
        }

        tag.add(CableItems.BACKUP_DRIVE.getId());

        if (NetworkAnchorItem.isExtensionEnabled()) {
            tag.add(CableItems.NETWORK_ANCHOR.getId());
        }
    }
    //#endregion

    //#region // --- EXTENSION TOGGLE TRACKING --- //
    // * Which toggles the current tag contents were built from, -1 until the first registration
    private static int appliedExtensionMask = -1;

    public static int currentExtensionMask() {
        return (MultiChannelCableBusItem.isExtensionEnabled() ? 1 : 0)
                | (IntegratedSensorBusItem.isExtensionEnabled() ? 2 : 0)
                | (NetworkAnchorItem.isExtensionEnabled() ? 4 : 0)
                | (HandheldTypewriterControllerItem.isExtensionEnabled() ? 8 : 0);
    }

    // * True when a toggle changed since the tag was last built
    public static boolean isTagStale() {
        return appliedExtensionMask != -1 && appliedExtensionMask != currentExtensionMask();
    }
    //#endregion
}