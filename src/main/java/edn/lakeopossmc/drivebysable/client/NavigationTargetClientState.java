package edn.lakeopossmc.drivebysable.client;

import edn.lakeopossmc.drivebysable.network.NavigationTargetSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Client-side cache keyed by dimension and the real sensor position. */
public final class NavigationTargetClientState {
    private static final ConcurrentMap<String, TargetState> TARGETS = new ConcurrentHashMap<>();

    private NavigationTargetClientState() {}

    public static void accept(Level level, NavigationTargetSyncPacket payload) {
        if (level == null || payload == null) {
            return;
        }
        TARGETS.put(
                key(level, payload.sensorPosition()),
                new TargetState(payload.hasTarget(), payload.x(), payload.y(), payload.z())
        );
    }

    public static TargetState get(Level level, BlockPos sensorPosition) {
        if (level == null || sensorPosition == null) {
            return null;
        }
        return TARGETS.get(key(level, sensorPosition.asLong()));
    }

    private static String key(Level level, long sensorPosition) {
        return level.dimension().location() + "|" + sensorPosition;
    }

    public record TargetState(boolean hasTarget, double x, double y, double z) {}
}