package edn.lakeopossmc.drivebysable.mixinducks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// --- LETS THE SEAT BLOCK ENTITY ASK ITS BLOCK FOR ONE OUTPUT --- //
// * Implemented on Get Creative's ControlSeatBlock
public interface ControlSeatSignalDuck {
    // * Relative side as Get Creative uses it: UP, DOWN, WEST or EAST
    int drivebysable$getSeatSignal(Level level, BlockPos pos, BlockState state, Direction relativeSide);
}