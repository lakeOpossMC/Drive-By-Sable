package edn.lakeopossmc.drivebysable.mixin.compat.simulated;

import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlock;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.compat.SimulatedSteeringWheelChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.List;

// --- LET A STEERING WHEEL ACT AS CABLE SOURCE --- //
// * Pseudo since mod may not be loaded
// * Left and right steering
@Pseudo
@Mixin(SteeringWheelBlock.class)
public abstract class MixinSteeringWheelBlock implements MultiChannelCableSource {

    @Override
    public List<String> cable$getChannels(final Level level, final BlockPos pos) {
        return SimulatedSteeringWheelChannels.CHANNELS;
    }

    // * Wrap around channel list on scroll
    @Override
    public String cable$nextChannel(final Level level, final BlockPos pos, final String current, final boolean forward) {
        final List<String> channels = SimulatedSteeringWheelChannels.CHANNELS;
        final int currentIndex = channels.indexOf(current);
        if (currentIndex == -1) {
            return channels.getFirst();
        }
        return channels.get(Math.floorMod(currentIndex + (forward ? 1 : -1), channels.size()));
    }
}