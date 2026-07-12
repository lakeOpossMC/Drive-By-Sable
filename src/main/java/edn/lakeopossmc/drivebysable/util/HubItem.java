package edn.lakeopossmc.drivebysable.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.function.Consumer;

// --- STORES BOUND HUB POS ON AN ITEM --- //
// * Used by controller items linked to a hub
public final class HubItem {
    private static final String HUB_KEY = "DriveBySableHub";
    private static final String LEGACY_HUB_KEY = "Hub";

    private HubItem() {
    }

    // * Write to both keys for old save compat
    public static void putHub(final ItemStack itemStack, final BlockPos pos) {
        final CompoundTag tag = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putLong(HUB_KEY, pos.asLong());
        tag.putLong(LEGACY_HUB_KEY, pos.asLong());
        itemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // * Fall back to legacy key if new one missing
    public static Optional<BlockPos> getHubPos(final ItemStack itemStack) {
        final CompoundTag tag = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(HUB_KEY, Tag.TAG_LONG)) {
            return Optional.of(BlockPos.of(tag.getLong(HUB_KEY)));
        }

        if (tag.contains(LEGACY_HUB_KEY, Tag.TAG_LONG)) {
            return Optional.of(BlockPos.of(tag.getLong(LEGACY_HUB_KEY)));
        }

        return Optional.empty();
    }

    public static void ifHubPresent(final ItemStack itemStack, final Consumer<BlockPos> consumer) {
        getHubPos(itemStack).ifPresent(consumer);
    }
}
