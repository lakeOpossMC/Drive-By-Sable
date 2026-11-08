package edn.lakeopossmc.drivebysable.display;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity.FlightData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

// --- WHAT A DISPLAY LINK CAN READ OFF THE SENSOR BUS --- //
public class IntegratedSensorBusDisplaySource extends SingleLineDisplaySource {
    private static final String SELECTION_KEY = "SensorBusSelection";
    private static final String LANG = DriveBySableMod.MOD_ID + ".display_source.integrated_sensor_bus.";

    private enum Reading {
        RELATIVE_SPEED_X("relative_speed_x"),
        RELATIVE_SPEED_Y("relative_speed_y"),
        RELATIVE_SPEED_Z("relative_speed_z"),
        GLOBAL_SPEED_X("global_speed_x"),
        GLOBAL_SPEED_Y("global_speed_y"),
        GLOBAL_SPEED_Z("global_speed_z"),
        AVERAGE_SPEED("average_speed"),
        AIR_PRESSURE("air_pressure"),
        HEIGHT("height"),
        X_ANGLE("x_angle"),
        Z_ANGLE("z_angle"),
        Y_ANGLE("y_angle"),
        ANGLE_DELTA("angle_delta"),
        COORDINATES("coordinates"),
        HEADING("heading"),
        TARGET_COORDINATES("target_coordinates"),
        TARGET_DISTANCE("target_distance"),
        TARGET_ETA("target_eta");

        private final String key;

        Reading(final String key) {
            this.key = key;
        }
    }

    private static final Reading[] READINGS = Reading.values();

    @Override
    protected MutableComponent provideLine(final DisplayLinkContext context, final DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof final IntegratedSensorBusBlockEntity bus)) {
            return EMPTY_LINE;
        }

        final FlightData data = bus.readFlightData();
        if (!data.available()) {
            return EMPTY_LINE;
        }

        final int index = context.sourceConfig().getInt(SELECTION_KEY);
        final Reading reading = READINGS[Math.floorMod(index, READINGS.length)];
        return Component.literal(format(bus, data, reading));
    }

    private static String format(final IntegratedSensorBusBlockEntity bus, final FlightData data, final Reading reading) {
        // * A module switched off in the bus's settings reads as off here too
        final boolean speed = bus.isSpeedEnabled();
        final boolean angle = bus.isAngleEnabled();
        final boolean altitude = bus.isAltitudeEnabled();

        return switch (reading) {
            case RELATIVE_SPEED_X -> speed ? speed(data.localRightSpeed()) : off();
            case RELATIVE_SPEED_Y -> speed ? speed(data.localUpSpeed()) : off();
            case RELATIVE_SPEED_Z -> speed ? speed(data.localForwardSpeed()) : off();
            case GLOBAL_SPEED_X -> speed ? speed(data.velocityX()) : off();
            case GLOBAL_SPEED_Y -> speed ? speed(data.velocityY()) : off();
            case GLOBAL_SPEED_Z -> speed ? speed(data.velocityZ()) : off();
            case AVERAGE_SPEED -> speed ? speed(bus.getAverageSpeed()) : off();
            case AIR_PRESSURE -> altitude ? fixed("%.2f%%", data.airPressure() * 100.0) : off();
            case HEIGHT -> altitude ? fixed("%.2f", data.y()) : off();
            case X_ANGLE -> angle ? degrees(data.pitch()) : off();
            case Y_ANGLE -> angle ? degrees(data.yaw()) : off();
            case Z_ANGLE -> angle ? degrees(data.roll()) : off();
            case HEADING -> angle ? degrees(data.heading()) : off();
            case COORDINATES -> blocks(data.x(), data.y(), data.z());
            case TARGET_DISTANCE -> data.hasTarget() ? String.valueOf((int) data.distanceToTarget()) : none();
            case TARGET_COORDINATES -> data.hasTarget() ? blocks(data.targetX(), data.targetY(), data.targetZ()) : none();
            case ANGLE_DELTA -> data.hasTarget() ? degrees(data.relativeAngle()) : none();
            case TARGET_ETA -> data.hasTarget() ? eta(data) : none();
        };
    }

    // * Seconds left at the speed the bus is closing on its target
    private static String eta(final FlightData data) {
        final double dx = data.targetX() - data.x();
        final double dy = data.targetY() - data.y();
        final double dz = data.targetZ() - data.z();
        final double distance = data.distanceToTarget();
        if (distance < 0.5) {
            return "0:00";
        }

        final double closing = (data.velocityX() * dx + data.velocityY() * dy + data.velocityZ() * dz) / distance;
        if (closing < 0.001) {
            return none();
        }

        final Duration left = Duration.ofSeconds((long) (distance / closing));
        if (left.toHours() > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", left.toHours(), left.toMinutesPart(), left.toSecondsPart());
        }
        return String.format(Locale.ROOT, "%d:%02d", left.toMinutesPart(), left.toSecondsPart());
    }

    private static String speed(final double metresPerSecond) {
        return fixed("%.2f m/s", metresPerSecond);
    }

    private static String degrees(final double value) {
        return fixed("%.1f°", value);
    }

    private static String blocks(final double x, final double y, final double z) {
        return String.format(Locale.ROOT, "%d, %d, %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private static String fixed(final String pattern, final double value) {
        return String.format(Locale.ROOT, pattern, value);
    }

    private static String off() {
        return Component.translatable(LANG + "disabled").getString();
    }

    private static String none() {
        return "N/A";
    }

    // * Telemetry moves every tick
    @Override
    public int getPassiveRefreshTicks() {
        return 10;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(final DisplayLinkContext context, final ModularGuiLineBuilder builder,
                                         final boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if (isFirstLine) {
            return;
        }

        final List<Component> options = java.util.Arrays.stream(READINGS)
                .map(reading -> (Component) Component.translatable(LANG + reading.key))
                .toList();
        builder.addSelectionScrollInput(0, 120,
                (input, label) -> input.forOptions(options), SELECTION_KEY);
    }

    @Override
    protected String getTranslationKey() {
        return "integrated_sensor_bus.data";
    }

    @Override
    protected boolean allowsLabeling(final DisplayLinkContext context) {
        return true;
    }
}