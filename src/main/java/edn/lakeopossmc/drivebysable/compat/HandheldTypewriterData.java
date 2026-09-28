package edn.lakeopossmc.drivebysable.compat;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import edn.lakeopossmc.drivebysable.CableItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// --- KEY BINDINGS STORED ON A HANDHELD TYPEWRITER CONTROLLER --- //
public final class HandheldTypewriterData {
    private static final String KEYS_KEY = "TypewriterKeys";

    private HandheldTypewriterData() {
    }

    public static boolean isController(final ItemStack stack) {
        return CableItems.HANDHELD_TYPEWRITER_CONTROLLER != null
                && stack != null
                && stack.is(CableItems.HANDHELD_TYPEWRITER_CONTROLLER.get());
    }

    // * Always returns a fresh, detached copy
    public static LinkedTypewriterEntries readEntries(final ItemStack stack, final HolderLookup.Provider registries) {
        final CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        final ListTag keys = tag.getList(KEYS_KEY, Tag.TAG_COMPOUND);
        return LinkedTypewriterEntries.readKeys(registries, keys, BlockPos.ZERO);
    }

    public static void writeEntries(final ItemStack stack, final LinkedTypewriterEntries entries,
                                    final HolderLookup.Provider registries) {
        final CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (entries.getSize() == 0) {
            tag.remove(KEYS_KEY);
        } else {
            tag.put(KEYS_KEY, entries.saveKeys(registries));
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int keyCount(final ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getList(KEYS_KEY, Tag.TAG_COMPOUND)
                .size();
    }

    public static CompoundTag toMenuTag(final ItemStack stack, final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.put("Keys", readEntries(stack, registries).saveKeys(registries));
        return tag;
    }
}