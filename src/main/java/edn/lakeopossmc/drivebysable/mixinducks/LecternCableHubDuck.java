package edn.lakeopossmc.drivebysable.mixinducks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

// --- MARKER FOR LECTERN HUB LINK --- //
// * Stores which hub a lectern is bound to
public interface LecternCableHubDuck {
    BlockPos drivebysable$getHubPos();

    void drivebysable$setHubPos(BlockPos hubPos);

    // * The device sat in the lectern, empty when the lectern can't say
    default ItemStack drivebysable$getLecternDevice() {
        return ItemStack.EMPTY;
    }

    // * Just the device type, cheap enough to ask every frame
    @Nullable
    default Item drivebysable$getLecternDeviceItem() {
        final ItemStack device = this.drivebysable$getLecternDevice();
        return device.isEmpty() ? null : device.getItem();
    }
}