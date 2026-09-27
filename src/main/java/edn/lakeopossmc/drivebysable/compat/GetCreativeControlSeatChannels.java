package edn.lakeopossmc.drivebysable.compat;

import net.minecraft.core.Direction;

import java.util.List;
import java.util.Map;

// --- CABLE CHANNELS FOR GET CREATIVE CONTROL SEATS --- //
// * Never touches a Get Creative class
// * Directions are relative to the seat's facing
public final class GetCreativeControlSeatChannels {
    public static final String PITCH_UP = "pitchUp";
    public static final String PITCH_DOWN = "pitchDown";
    public static final String YAW_LEFT = "yawLeft";
    public static final String YAW_RIGHT = "yawRight";

    public static final List<String> CHANNELS = List.of(PITCH_UP, PITCH_DOWN, YAW_LEFT, YAW_RIGHT);

    // * Same order as CHANNELS
    public static final List<Direction> RELATIVE_SIDES = List.of(Direction.UP, Direction.DOWN, Direction.WEST, Direction.EAST);

    public static final Map<String, String> CHANNEL_TO_LANG_KEY = Map.of(
            PITCH_UP, "drivebysable.get_creative.control_seat.pitch_up",
            PITCH_DOWN, "drivebysable.get_creative.control_seat.pitch_down",
            YAW_LEFT, "drivebysable.get_creative.control_seat.yaw_left",
            YAW_RIGHT, "drivebysable.get_creative.control_seat.yaw_right"
    );

    private GetCreativeControlSeatChannels() {
    }
}