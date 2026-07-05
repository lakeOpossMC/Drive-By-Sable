package edn.lakeopossmc.drivebysable.ponder;

import com.simibubi.create.foundation.ponder.CreatePonderPlugin;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

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

    @Override
    public void registerTags(final PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(DRIVE_BY_SABLE_TAG)
                .addToIndex()
                .item(CableItems.CABLE_HUB_BLOCK.get(), true, false)
                .register();

        helper.addToTag(DRIVE_BY_SABLE_TAG)
                .add(CableItems.CABLE.getId())
                .add(CableItems.CABLE_CUTTER.getId())
                .add(CableItems.CABLE_HUB_BLOCK.getId())
                .add(CableItems.ADVANCED_CABLE_HUB_BLOCK.getId())
                .add(CableItems.CABLE_TYPEWRITER_HUB.getId())
                .add(CableItems.BACKUP_DRIVE.getId());
    }
}