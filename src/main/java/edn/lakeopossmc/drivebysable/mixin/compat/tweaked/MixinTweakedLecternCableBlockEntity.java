package edn.lakeopossmc.drivebysable.mixin.compat.tweaked;

import edn.lakeopossmc.drivebysable.mixinducks.LecternCableHubDuck;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// --- LETS TWEAKED LECTERN REMEMBER ITS BOUND HUB --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(targets = "com.getitemfromblock.create_tweaked_controllers.block.TweakedLecternControllerBlockEntity", remap = false)
public abstract class MixinTweakedLecternCableBlockEntity implements LecternCableHubDuck {
    @Unique
    private static final String DRIVEBYSABLE$HUB_KEY = "DriveBySableHub";

    @Unique
    private BlockPos drivebysable$hubPos;

    @Shadow(remap = false)
    public abstract ItemStack getController();

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

    // * Fall back to controller item hub if tag missing
    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void drivebysable$readHub(
        final CompoundTag compound,
        final HolderLookup.Provider registries,
        final boolean clientPacket,
        final CallbackInfo ci
    ) {
        if (compound.contains(DRIVEBYSABLE$HUB_KEY)) {
            drivebysable$hubPos = BlockPos.of(compound.getLong(DRIVEBYSABLE$HUB_KEY));
        } else {
            final ItemStack controller = getController();
            drivebysable$hubPos = controller == null ? null : HubItem.getHubPos(controller).orElse(null);
        }
    }
    //#endregion

    // * Grab hub pos off whatever controller gets inserted
    @Inject(method = "setController", at = @At("TAIL"), remap = false)
    private void drivebysable$captureHubFromController(final ItemStack newController, final CallbackInfo ci) {
        drivebysable$hubPos = newController == null ? null : HubItem.getHubPos(newController).orElse(null);
    }

    // * Stamp hub pos back onto returned controller
    @Inject(method = "getController", at = @At("RETURN"), cancellable = true, remap = false)
    private void drivebysable$restoreHubOnController(final CallbackInfoReturnable<ItemStack> cir) {
        final ItemStack controller = cir.getReturnValue();
        if (drivebysable$hubPos != null && controller != null && !controller.isEmpty()) {
            HubItem.putHub(controller, drivebysable$hubPos);
            cir.setReturnValue(controller);
        }
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
