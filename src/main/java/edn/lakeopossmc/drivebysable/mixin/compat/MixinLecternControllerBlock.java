package edn.lakeopossmc.drivebysable.mixin.compat;

import com.simibubi.create.content.redstone.link.controller.LecternControllerBlock;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.compat.LinkedControllerCableServerHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;
import java.util.List;

// --- LET LECTERN CONTROLLER ACT AS CABLE SOURCE --- //
@Mixin(LecternControllerBlock.class)
public abstract class MixinLecternControllerBlock implements MultiChannelCableSource {
    @Unique
    private static final List<String> DRIVEBYSABLE$CHANNELS = Arrays.stream(LinkedControllerCableServerHandler.KEY_TO_CHANNEL).toList();

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