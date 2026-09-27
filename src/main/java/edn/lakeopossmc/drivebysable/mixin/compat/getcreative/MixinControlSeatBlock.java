package edn.lakeopossmc.drivebysable.mixin.compat.getcreative;

import amaryllis.get_creative.appliances.control_seat.ControlSeatBlock;
import amaryllis.get_creative.appliances.control_seat.ControlSeatBlockEntity;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.compat.GetCreativeControlSeatChannels;
import edn.lakeopossmc.drivebysable.mixinducks.ControlSeatSignalDuck;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

// --- LET A GET CREATIVE CONTROL SEAT ACT AS CABLE SOURCE --- //
// * Pseudo since mod may not be loaded
// * Two pitch and two yaw channels
@Pseudo
@Mixin(targets = "amaryllis.get_creative.appliances.control_seat.ControlSeatBlock", remap = false)
public abstract class MixinControlSeatBlock implements MultiChannelCableSource, ControlSeatSignalDuck {

    @Shadow(remap = false)
    public abstract Entity getPassenger(EntityGetter level, BlockPos pos);

    @Shadow(remap = false)
    protected abstract int getSignalForSide(ControlSeatBlockEntity seat, Entity passenger, Direction relativeSide, Direction facing);

    //#region // --- CHANNEL LIST --- //
    @Override
    public List<String> cable$getChannels(final Level level, final BlockPos pos) {
        return GetCreativeControlSeatChannels.CHANNELS;
    }

    // * Wrap around channel list on scroll
    @Override
    public String cable$nextChannel(final Level level, final BlockPos pos, final String current, final boolean forward) {
        final List<String> channels = GetCreativeControlSeatChannels.CHANNELS;
        final int currentIndex = channels.indexOf(current);
        if (currentIndex == -1) {
            return channels.getFirst();
        }
        return channels.get(Math.floorMod(currentIndex + (forward ? 1 : -1), channels.size()));
    }
    //#endregion

    // * Goes through Get Creative's own math
    @Override
    public int drivebysable$getSeatSignal(final Level level, final BlockPos pos, final BlockState state, final Direction relativeSide) {
        if (!(level.getBlockEntity(pos) instanceof final ControlSeatBlockEntity seat)) {
            return 0;
        }

        final Entity passenger = this.getPassenger(level, pos);
        if (passenger == null) {
            return 0;
        }

        return this.getSignalForSide(seat, passenger, relativeSide, state.getValue(ControlSeatBlock.FACING));
    }
}