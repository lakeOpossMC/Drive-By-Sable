package edn.lakeopossmc.drivebysable.menu;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.content.linked_typewriter.LinkedTypewriterMenuImpl;
import dev.simulated_team.simulated.index.SimBlockEntityTypes;
import dev.simulated_team.simulated.index.SimBlocks;
import edn.lakeopossmc.drivebysable.CableMenus;
import edn.lakeopossmc.drivebysable.compat.HandheldTypewriterData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

// --- LINKED TYPEWRITER MENU FOR THE HANDHELD CONTROLLER --- //
// * Reuses Simulated's own typewriter menu and screen
public class HandheldTypewriterMenu extends LinkedTypewriterMenuImpl {
    public static final BlockPos DETACHED_POS = new BlockPos(0, -2000, 0);

    public HandheldTypewriterMenu(final MenuType<?> type, final int id, final Inventory inv, final RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public HandheldTypewriterMenu(final MenuType<?> type, final int id, final Inventory inv, final LinkedTypewriterBlockEntity typewriter) {
        super(type, id, inv, typewriter);
    }

    public static HandheldTypewriterMenu create(final int id, final Inventory inv, final ItemStack controller) {
        final LinkedTypewriterBlockEntity typewriter = detachedTypewriter();
        typewriter.getTypewriterEntries().addAll(
                HandheldTypewriterData.readEntries(controller, inv.player.registryAccess()).getKeyMap());
        return new HandheldTypewriterMenu(CableMenus.HANDHELD_TYPEWRITER.get(), id, inv, typewriter);
    }

    // * What the client needs to rebuild the same keys
    public static void writeExtraData(final RegistryFriendlyByteBuf buf, final ItemStack controller,
                                      final HolderLookup.Provider registries) {
        buf.writeNbt(HandheldTypewriterData.toMenuTag(controller, registries));
    }

    private static LinkedTypewriterBlockEntity detachedTypewriter() {
        return new LinkedTypewriterBlockEntity(
                SimBlockEntityTypes.LINKED_TYPEWRITER.get(),
                DETACHED_POS,
                SimBlocks.LINKED_TYPEWRITER.getDefaultState());
    }

    @Override
    protected LinkedTypewriterBlockEntity createOnClient(final RegistryFriendlyByteBuf extraData) {
        final LinkedTypewriterBlockEntity typewriter = detachedTypewriter();
        final CompoundTag tag = extraData.readNbt();
        if (tag != null) {
            typewriter.readClient(tag, extraData.registryAccess());
        }
        return typewriter;
    }

    // * Closes if the controller leaves the main hand
    @Override
    public boolean stillValid(final Player player) {
        return HandheldTypewriterData.isController(player.getMainHandItem());
    }
}