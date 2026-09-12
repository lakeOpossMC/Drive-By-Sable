package edn.lakeopossmc.drivebysable.client;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity.FlightData;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

// --- THE CLIENT HALF OF THE SENSOR BUS GOGGLE TOOLTIP --- //
// * Kept apart from the block entity because the column alignment needs a Font
public final class SensorBusGoggleClient {

    private static final String PREFIX = "goggles.sensor_bus.";
    private static final String PREFIX_FULL = DriveBySableMod.MOD_ID + "." + PREFIX;

    private static final int COLUMN_GAP = 8;

    // * Every label that shares the first value column, so they line up together
    private static final String[] VALUE_LABELS = {
            "altitude", "air_pressure", "displaying",
            "speed_x", "speed_y", "speed_z",
            "angle_x", "angle_y", "angle_z",
            "coordinates", "heading", "angle_delta", "average_speed"
    };

    private static final ChatFormatting AXIS_X = ChatFormatting.RED;
    private static final ChatFormatting AXIS_Y = ChatFormatting.GREEN;
    private static final ChatFormatting AXIS_Z = ChatFormatting.BLUE;
    private static final ChatFormatting VALUE = ChatFormatting.AQUA;
    private static final ChatFormatting RATE = ChatFormatting.DARK_GRAY;

    private static final String[] CARDINALS = {
            "north", "north_east", "east", "south_east",
            "south", "south_west", "west", "north_west"
    };

    private SensorBusGoggleClient() {
    }

    private static LangBuilder lang(final String key, final Object... args) {
        return new LangBuilder(DriveBySableMod.MOD_ID).translate(key, args);
    }

    public static void appendSensorInfo(
            final IntegratedSensorBusBlockEntity sensor,
            final FlightData data,
            final List<Component> tooltip
    ) {
        lang(PREFIX + "title").style(ChatFormatting.GOLD).forGoggles(tooltip);

        final int column = widestLabel() + COLUMN_GAP;

        section(tooltip, "altitude_sensor", sensor.isAltitudeEnabled());
        if (sensor.isAltitudeEnabled()) {
            value(tooltip, "altitude", column, unit(data.y(), "m"), VALUE);
            value(tooltip, "air_pressure", column, percent(data.airPressure()), VALUE);
        }

        section(tooltip, "velocity_sensor", sensor.isSpeedEnabled());
        if (sensor.isSpeedEnabled()) {
            value(tooltip, "displaying", column,
                    Component.translatable(PREFIX_FULL + (sensor.isLocalFrame() ? "frame_local" : "frame_global")),
                    VALUE);

            final boolean local = sensor.isLocalFrame();
            value(tooltip, "speed_x", column,
                    unit(local ? data.localRightSpeed() : data.velocityX(), "m/s"), AXIS_X);
            value(tooltip, "speed_y", column,
                    unit(local ? data.localUpSpeed() : data.velocityY(), "m/s"), AXIS_Y);
            value(tooltip, "speed_z", column,
                    unit(local ? data.localForwardSpeed() : data.velocityZ(), "m/s"), AXIS_Z);
        }

        section(tooltip, "gimbal_sensor", sensor.isAngleEnabled());
        if (sensor.isAngleEnabled()) {
            angle(tooltip, "angle_x", column, data.pitch(), data.pitchRate(), AXIS_X);
            angle(tooltip, "angle_y", column, data.yaw(), data.yawRate(), AXIS_Y);
            angle(tooltip, "angle_z", column, data.roll(), data.rollRate(), AXIS_Z);
        }

        appendExtras(sensor, data, tooltip, column);
    }

    //#region // --- THE SHIFT HALF --- //
    private static void appendExtras(
            final IntegratedSensorBusBlockEntity sensor,
            final FlightData data,
            final List<Component> tooltip,
            final int column
    ) {
        final boolean held = Screen.hasShiftDown();

        final Component shift = Component.translatable(PREFIX_FULL + "shift_key")
                .withStyle(held ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY);

        lang(PREFIX + "hold_shift", shift).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip);

        if (!held) {
            return;
        }

        heading(tooltip, "navigation");
        coordinates(tooltip, data, column);
        value(tooltip, "heading", column, heading(data.heading()), VALUE);

        heading(tooltip, "misc");
        value(tooltip, "angle_delta", column, unit(data.angularSpeed(), "\u00b0/s"), VALUE);
        value(tooltip, "average_speed", column, unit(sensor.getAverageSpeed(), "m/s"), VALUE);
    }

    private static void coordinates(final List<Component> tooltip, final FlightData data, final int column) {
        final LangBuilder line = lang(PREFIX + "coordinates").style(ChatFormatting.GRAY);
        pad(line, column - labelWidth("coordinates"));

        line.add(new LangBuilder(DriveBySableMod.MOD_ID)
                .text(number(data.x())).style(AXIS_X));
        line.add(new LangBuilder(DriveBySableMod.MOD_ID).text(", "));
        line.add(new LangBuilder(DriveBySableMod.MOD_ID)
                .text(number(data.y())).style(AXIS_Y));
        line.add(new LangBuilder(DriveBySableMod.MOD_ID).text(", "));
        line.add(new LangBuilder(DriveBySableMod.MOD_ID)
                .text(number(data.z())).style(AXIS_Z));
        line.forGoggles(tooltip, 2);
    }
    //#endregion

    //#region // --- LINE BUILDERS --- //
    private static void section(final List<Component> tooltip, final String key, final boolean enabled) {
        lang(PREFIX + key,
                Component.translatable(PREFIX_FULL + (enabled ? "enabled" : "disabled"))
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip, 1);
    }

    private static void heading(final List<Component> tooltip, final String key) {
        lang(PREFIX + key).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
    }

    private static void value(
            final List<Component> tooltip,
            final String key,
            final int column,
            final Component shown,
            final ChatFormatting colour
    ) {
        final LangBuilder line = lang(PREFIX + key).style(ChatFormatting.GRAY);
        pad(line, column - labelWidth(key));
        line.add(new LangBuilder(DriveBySableMod.MOD_ID).add(shown.copy()).style(colour));
        line.forGoggles(tooltip, 2);
    }

    // * Angle then its rate
    private static void angle(
            final List<Component> tooltip,
            final String key,
            final int column,
            final double degrees,
            final double rate,
            final ChatFormatting colour
    ) {
        final Component shown = unit(degrees, "\u00b0");

        final LangBuilder line = lang(PREFIX + key).style(ChatFormatting.GRAY);
        pad(line, column - labelWidth(key));
        line.add(new LangBuilder(DriveBySableMod.MOD_ID).add(shown.copy()).style(colour));

        final Font font = Minecraft.getInstance().font;
        pad(line, rateColumn() - font.width(shown));

        line.add(new LangBuilder(DriveBySableMod.MOD_ID)
                .add(unit(rate, "\u00b0/s").copy()).style(RATE));
        line.forGoggles(tooltip, 2);
    }
    //#endregion

    //#region // --- FORMATTING --- //
    private static Component unit(final double amount, final String suffix) {
        return Component.literal(number(amount) + suffix);
    }

    private static Component percent(final double fraction) {
        return Component.literal(String.format(Locale.ROOT, "%.2f%%", fraction * 100.0));
    }

    private static String number(final double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    // * Degrees plus the compass point it falls in
    private static Component heading(final double degrees) {
        // * Each point covers 45 degrees, centred on its own bearing
        final int index = (int) Math.floor(((degrees % 360.0) + 360.0) % 360.0 / 45.0 + 0.5) % 8;
        return Component.literal(number(degrees) + "\u00b0 ")
                .append(Component.translatable(PREFIX_FULL + "cardinal." + CARDINALS[index]));
    }
    //#endregion

    //#region // --- COLUMN ALIGNMENT --- //
    private static int widestLabel() {
        final Font font = Minecraft.getInstance().font;
        int widest = 0;
        for (final String key : VALUE_LABELS) {
            widest = Math.max(widest, font.width(Component.translatable(PREFIX_FULL + key)));
        }
        return widest;
    }

    private static int labelWidth(final String key) {
        return Minecraft.getInstance().font.width(Component.translatable(PREFIX_FULL + key));
    }

    // * Enough room for the widest angle before its rate starts
    private static int rateColumn() {
        return Minecraft.getInstance().font.width(Component.literal("-000.00\u00b0")) + COLUMN_GAP;
    }

    private static void pad(final LangBuilder line, final int deficit) {
        if (deficit <= 0) {
            return;
        }

        final Font font = Minecraft.getInstance().font;
        final int plain = Math.max(1, font.width(Component.literal(" ")));
        final int bold = Math.max(plain + 1, font.width(Component.literal(" ").withStyle(ChatFormatting.BOLD)));

        for (int bolds = 0; bolds <= deficit / bold; bolds++) {
            final int remainder = deficit - bolds * bold;
            if (remainder % plain != 0) {
                continue;
            }

            if (bolds > 0) {
                line.add(new LangBuilder(DriveBySableMod.MOD_ID)
                        .text(" ".repeat(bolds)).style(ChatFormatting.BOLD));
            }
            line.add(new LangBuilder(DriveBySableMod.MOD_ID).text(" ".repeat(remainder / plain)));
            return;
        }

        line.add(new LangBuilder(DriveBySableMod.MOD_ID).text(" ".repeat(deficit / plain)));
    }
    //#endregion
}