package edn.lakeopossmc.drivebysable.mixin.compat;

import com.simibubi.create.content.redstone.link.controller.LecternControllerBlockEntity;
import edn.lakeopossmc.drivebysable.mixinducks.LecternCableHubDuck;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// --- LETS LECTERN REMEMBER ITS BOUND HUB --- //
@Mixin(LecternControllerBlockEntity.class)
public abstract class MixinLecternControllerBlockEntity implements LecternCableHubDuck {
    @Unique
    private static final String DRIVEBYSABLE$HUB_KEY = "DriveBySableHub";

    @Unique
    private BlockPos drivebysable$hubPos;

    //#region // --- SAVE AND LOAD HUB POS --- //
    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void drivebysable$writeHub(
        final CompoundTag compound,
        final HolderLookup.Provider registries,
        final boolean clientPacket,
        final CallbackInfo ci
    ) {
        if (drivebysable$hubPos != null) {
            compound.putLong(DRIVEBYSABLE$HUB_KEY, drivebysable$hubPos.asLong());
        }
    }

    @Inject(method = "writeSafe", at = @At("TAIL"), remap = false)
    private void drivebysable$writeSafeHub(
        final CompoundTag compound,
        final HolderLookup.Provider registries,
        final CallbackInfo ci
    ) {
        if (drivebysable$hubPos != null) {
            compound.putLong(DRIVEBYSABLE$HUB_KEY, drivebysable$hubPos.asLong());
        }
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void drivebysable$readHub(
        final CompoundTag compound,
        final HolderLookup.Provider registries,
        final boolean clientPacket,
        final CallbackInfo ci
    ) {
        drivebysable$hubPos = compound.contains(DRIVEBYSABLE$HUB_KEY) ? BlockPos.of(compound.getLong(DRIVEBYSABLE$HUB_KEY)) : null;
    }
    //#endregion

    // * Grab hub pos off whatever controller gets inserted
    @Inject(method = "setController", at = @At("TAIL"), remap = false)
    private void drivebysable$captureHubFromInsertedController(final ItemStack newController, final CallbackInfo ci) {
        drivebysable$hubPos = newController == null ? null : HubItem.getHubPos(newController).orElse(null);
    }

    // * Stamp hub pos onto controller item when its generated
    @Inject(method = "createLinkedController", at = @At("RETURN"), cancellable = true, remap = false)
    private void drivebysable$restoreHubOnGeneratedController(final CallbackInfoReturnable<ItemStack> cir) {
        if (drivebysable$hubPos == null) {
            return;
        }

        final ItemStack controller = cir.getReturnValue();
        HubItem.putHub(controller, drivebysable$hubPos);
        cir.setReturnValue(controller);
    }

    @Override
    public BlockPos drivebysable$getHubPos() {
        return drivebysable$hubPos;
    }

    @Override
    public void drivebysable$setHubPos(final BlockPos hubPos) {
        drivebysable$hubPos = hubPos == null ? null : hubPos.immutable();
    }
}
