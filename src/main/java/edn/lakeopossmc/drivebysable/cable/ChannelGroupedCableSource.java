package edn.lakeopossmc.drivebysable.cable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

// --- A SOURCE WHOSE CHANNELS COME IN GROUPS --- //
// * Specifically built for one of the ported blocks
// * Can be used for other blocks later
// * Allows channels to be separated in groups for easier scrolling
public interface ChannelGroupedCableSource {

    // * Group ids in cycle order, empty means the block has nothing to offer
    List<String> cable$getChannelGroups(Level level, BlockPos pos);

    List<String> cable$getGroupChannels(Level level, BlockPos pos, String group);

    // * A group whose channels are not worth listing in a menu
    default boolean cable$groupAllowsQuickSelect(Level level, BlockPos pos, String group) {
        return true;
    }

    // * Translation key for the group display name
    String cable$getChannelGroupLangKey(String group);
}