package edn.lakeopossmc.drivebysable.mixinducks;

import net.minecraft.core.BlockPos;

// --- MARKER FOR LECTERN HUB LINK --- //
// * Stores which hub a lectern is bound to
public interface LecternCableHubDuck {
    BlockPos drivebysable$getHubPos();

    void drivebysable$setHubPos(BlockPos hubPos);
}
