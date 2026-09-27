package edn.lakeopossmc.drivebysable.compat;

import net.minecraft.core.Direction;

import java.util.List;
import java.util.Map;

// --- CABLE CHANNELS FOR SIMULATED STEERING WHEELS --- //
// * Never touches a Simulated class
// * Left and right are from the driver's point of view
public final class SimulatedSteeringWheelChannels {
    public static final String STEER_LEFT = "steerLeft";
    public static final String STEER_RIGHT = "steerRight";

    public static final List<String> CHANNELS = List.of(STEER_LEFT, STEER_RIGHT);

    public static final Map<String, String> CHANNEL_TO_LANG_KEY = Map.of(
            STEER_LEFT, "drivebysable.simulated.steering_wheel.steer_left",
            STEER_RIGHT, "drivebysable.simulated.steering_wheel.steer_right"
    );

    private SimulatedSteeringWheelChannels() {
    }

    public static Direction queryDirection(final String channel, final Direction facing) {
        return STEER_LEFT.equals(channel) ? facing.getCounterClockWise() : facing.getClockWise();
    }
}