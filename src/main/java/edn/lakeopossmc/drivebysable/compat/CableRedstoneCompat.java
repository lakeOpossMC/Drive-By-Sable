package edn.lakeopossmc.drivebysable.compat;

import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

// --- VANILLA REDSTONE + CABLE SIGNAL BLEND --- //
public final class CableRedstoneCompat {
    private CableRedstoneCompat() {
    }

    // * Take the strongest of vanilla and both cable sides
    public static int getSignalIncludingReverseCable(
        final Level level,
        final BlockPos queriedPos,
        final Direction queriedDirection
    ) {
        final int baseSignal = level.getSignal(queriedPos, queriedDirection);
        final BlockPos sinkPos = queriedPos.relative(queriedDirection);
        final Direction sinkFace = queriedDirection.getOpposite();
        final CableNetworkManager manager = CableNetworkManager.get(level);
        final int consumerFaceSignal = manager.getSignalAt(sinkPos, sinkFace);
        final int providerFaceSignal = manager.getSignalAt(queriedPos, queriedDirection);
        return Math.max(baseSignal, Math.max(consumerFaceSignal, providerFaceSignal));
    }
}
