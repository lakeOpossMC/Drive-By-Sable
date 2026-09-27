package edn.lakeopossmc.drivebysable.mixin.compat.simulated;

import dev.simulated_team.simulated.api.IDirectionalAnalogOutput;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import edn.lakeopossmc.drivebysable.compat.ControllerSignalStore;
import edn.lakeopossmc.drivebysable.compat.SimulatedSteeringWheelChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- PUSH STEERING WHEEL ANGLE INTO CABLE CHANNELS --- //
// * Pseudo since mod may not be loaded
// * Read through the wheel's own comparator output
@Pseudo
@Mixin(SteeringWheelBlockEntity.class)
public abstract class MixinSteeringWheelBlockEntity {

    // * Last value sent per channel
    @Unique
    private final int[] drivebysable$cableSignals = {-1, -1};

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void drivebysable$pushCableSignals(final CallbackInfo ci) {
        final BlockEntity self = (BlockEntity) (Object) this;
        final Level level = self.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        final BlockPos pos = self.getBlockPos();
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof final IDirectionalAnalogOutput output)
                || !state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }

        final Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        for (int index = 0; index < drivebysable$cableSignals.length; index++) {
            final String channel = SimulatedSteeringWheelChannels.CHANNELS.get(index);
            final int value = output.getAnalogOutputSignalFrom(
                    state, level, pos, SimulatedSteeringWheelChannels.queryDirection(channel, facing));
            if (value != drivebysable$cableSignals[index]) {
                drivebysable$cableSignals[index] = value;
                ControllerSignalStore.setSignal(level, pos, channel, value);
            }
        }
    }
}