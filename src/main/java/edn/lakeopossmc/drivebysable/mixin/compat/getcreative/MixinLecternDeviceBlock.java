package edn.lakeopossmc.drivebysable.mixin.compat.getcreative;

import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.compat.GetCreativeCableServerHandler;
import edn.lakeopossmc.drivebysable.mixinducks.LecternCableHubDuck;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.List;

// --- LET A GET CREATIVE LECTERN ACT AS CABLE SOURCE --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(targets = "amaryllis.get_creative.appliances.linked_controller.lectern.LecternDeviceBlock", remap = false)
public abstract class MixinLecternDeviceBlock implements MultiChannelCableSource {

    @Override
    public List<String> cable$getChannels(final Level level, final BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof final LecternCableHubDuck lectern)) {
            return List.of();
        }

        final Item device = lectern.drivebysable$getLecternDeviceItem();
        final String group = device == null ? null : GetCreativeCableServerHandler.groupFor(device);
        return group == null ? List.of() : GetCreativeCableServerHandler.channelsInGroup(group);
    }

    // * Wrap around channel list on scroll
    @Override
    public String cable$nextChannel(final Level level, final BlockPos pos, final String current, final boolean forward) {
        final List<String> channels = this.cable$getChannels(level, pos);
        if (channels.isEmpty()) {
            return null;
        }

        final int currentIndex = channels.indexOf(current);
        if (currentIndex == -1) {
            return channels.getFirst();
        }
        return channels.get(Math.floorMod(currentIndex + (forward ? 1 : -1), channels.size()));
    }
}