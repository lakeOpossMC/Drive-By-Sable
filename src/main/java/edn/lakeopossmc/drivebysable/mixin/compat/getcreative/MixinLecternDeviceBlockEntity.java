package edn.lakeopossmc.drivebysable.mixin.compat.getcreative;

import amaryllis.get_creative.appliances.linked_controller.AllLinkedDevices;
import edn.lakeopossmc.drivebysable.mixinducks.LecternCableHubDuck;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

// --- LETS A GET CREATIVE LECTERN REMEMBER ITS BOUND HUB --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(targets = "amaryllis.get_creative.appliances.linked_controller.lectern.LecternDeviceBlockEntity", remap = false)
public abstract class MixinLecternDeviceBlockEntity implements LecternCableHubDuck {
    @Unique
    private static final String DRIVEBYSABLE$HUB_KEY = "DriveBySableHub";

    @Unique
    private BlockPos drivebysable$hubPos;

    @Shadow(remap = false)
    protected int deviceID;

    @Shadow(remap = false)
    public abstract ItemStack createLinkedDevice();

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
        drivebysable$hubPos = compound.contains(DRIVEBYSABLE$HUB_KEY, Tag.TAG_LONG)
                ? BlockPos.of(compound.getLong(DRIVEBYSABLE$HUB_KEY))
                : null;
    }
    //#endregion

    // * Grab hub pos off whatever device gets inserted
    @Inject(method = "setController", at = @At("TAIL"), remap = false)
    private void drivebysable$captureHubFromInsertedDevice(final ItemStack newDevice, final CallbackInfo ci) {
        drivebysable$hubPos = newDevice == null ? null : HubItem.getHubPos(newDevice).orElse(null);
    }

    // * Stamp hub pos onto every device stack the lectern builds
    // * Covers input packets, retrieving the device and dropping it
    @Inject(method = "createLinkedDevice", at = @At("RETURN"), remap = false)
    private void drivebysable$restoreHubOnGeneratedDevice(final CallbackInfoReturnable<ItemStack> cir) {
        final ItemStack device = cir.getReturnValue();
        if (drivebysable$hubPos != null && device != null && !device.isEmpty()) {
            HubItem.putHub(device, drivebysable$hubPos);
        }
    }

    @Override
    public BlockPos drivebysable$getHubPos() {
        return drivebysable$hubPos;
    }

    // * Built fresh from the lectern's stored device id and frequencies
    @Override
    public ItemStack drivebysable$getLecternDevice() {
        return createLinkedDevice();
    }

    // * Looked up from the stored id, no stack built
    @Nullable
    @Override
    public Item drivebysable$getLecternDeviceItem() {
        if (deviceID < 0 || deviceID >= AllLinkedDevices.BY_INDEX.size()) {
            return null;
        }
        return AllLinkedDevices.getDevice(deviceID);
    }

    @Override
    public void drivebysable$setHubPos(final BlockPos hubPos) {
        drivebysable$hubPos = hubPos == null ? null : hubPos.immutable();
    }
}