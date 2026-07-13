package edn.lakeopossmc.drivebysable.mixin.compat.tweaked;

import com.getitemfromblock.create_tweaked_controllers.block.TweakedLecternControllerBlock;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.compat.TweakedControllerCableServerHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

// --- LET TWEAKED LECTERN ACT AS CABLE SOURCE --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(TweakedLecternControllerBlock.class)
public abstract class MixinLecternTweakedBlock implements MultiChannelCableSource {
    @Unique
    private static final List<String> DRIVEBYSABLE$CHANNELS = Stream.concat(
            Arrays.stream(TweakedControllerCableServerHandler.AXIS_TO_CHANNEL),
            Arrays.stream(TweakedControllerCableServerHandler.BUTTON_TO_CHANNEL)
    ).toList();

    @Override
    public List<String> cable$getChannels(final Level level, final BlockPos pos) {
        return DRIVEBYSABLE$CHANNELS;
    }

    // * Wrap around channel list on scroll
    @Override
    public String cable$nextChannel(final Level level, final BlockPos pos, final String current, final boolean forward) {
        final int currentIndex = DRIVEBYSABLE$CHANNELS.indexOf(current);
        if (currentIndex == -1) {
            return DRIVEBYSABLE$CHANNELS.getFirst();
        }
        return DRIVEBYSABLE$CHANNELS.get(Math.floorMod(currentIndex + (forward ? 1 : -1), DRIVEBYSABLE$CHANNELS.size()));
    }
}