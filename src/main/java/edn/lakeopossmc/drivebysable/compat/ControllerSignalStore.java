package edn.lakeopossmc.drivebysable.compat;

import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Map;

// --- BRIDGE FOR CONTROLLER SIGNAL VALUES --- //
// * Wraps network manager calls for compat use
public final class ControllerSignalStore {
    private ControllerSignalStore() {
    }

    public static void setSignal(final Level level, final BlockPos pos, final String channel, final int value) {
        CableNetworkManager.trySetSignalAt(level, pos, channel, value);
    }

    public static void clear(final Level level, final BlockPos pos) {
        CableNetworkManager.get(level).clearSourceSignals(level, pos);
    }

    public static Map<String, Integer> getSignals(final Level level, final BlockPos pos) {
        return CableNetworkManager.get(level).getSourceSignals(pos);
    }
}
