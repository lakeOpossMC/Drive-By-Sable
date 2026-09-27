package edn.lakeopossmc.drivebysable.mixin.compat.getcreative;

import edn.lakeopossmc.drivebysable.compat.ControllerSignalStore;
import edn.lakeopossmc.drivebysable.compat.GetCreativeControlSeatChannels;
import edn.lakeopossmc.drivebysable.mixinducks.ControlSeatSignalDuck;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// --- PUSH CONTROL SEAT PITCH AND YAW INTO CABLE CHANNELS --- //
// * Pseudo since mod may not be loaded
// * updateSignal runs every tick while seated, and once more when the passenger leaves
// * Only server side, and only when a value actually changed
@Pseudo
@Mixin(targets = "amaryllis.get_creative.appliances.control_seat.ControlSeatBlockEntity", remap = false)
public abstract class MixinControlSeatBlockEntity {

    // * Last value sent per channel
    @Unique
    private final int[] drivebysable$cableSignals = {-1, -1, -1, -1};

    @Unique
    private boolean drivebysable$cableSignalsPrimed;

    // * Network signals outlive a chunk unload
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void drivebysable$primeCableSignals(final CallbackInfo ci) {
        if (!drivebysable$cableSignalsPrimed) {
            drivebysable$cableSignalsPrimed = true;
            drivebysable$pushCableSignals();
        }
    }

    @Inject(method = "updateSignal", at = @At("TAIL"), remap = false)
    private void drivebysable$onUpdateSignal(final CallbackInfo ci) {
        drivebysable$pushCableSignals();
    }

    @Unique
    private void drivebysable$pushCableSignals() {
        final BlockEntity self = (BlockEntity) (Object) this;
        final Level level = self.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        final BlockPos pos = self.getBlockPos();
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof final ControlSeatSignalDuck seat)) {
            return;
        }

        for (int index = 0; index < drivebysable$cableSignals.length; index++) {
            final int value = seat.drivebysable$getSeatSignal(
                    level, pos, state, GetCreativeControlSeatChannels.RELATIVE_SIDES.get(index));
            if (value != drivebysable$cableSignals[index]) {
                drivebysable$cableSignals[index] = value;
                ControllerSignalStore.setSignal(level, pos, GetCreativeControlSeatChannels.CHANNELS.get(index), value);
            }
        }
    }
}