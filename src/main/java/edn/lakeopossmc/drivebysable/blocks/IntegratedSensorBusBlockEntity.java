package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.compat.computercraft.ComputerCraftCompat;
import edn.lakeopossmc.drivebysable.client.NavigationTargetClientState;
import edn.lakeopossmc.drivebysable.network.NavigationTargetSyncPacket;
import edn.lakeopossmc.drivebysable.network.SensorBusSettingsSyncPacket;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;
import java.util.Locale;
import java.util.Map;

// --- PORTED RELIANTSHELL/AMMAN FEATURE --- //
// * Stores all sensor data, also handles what gets sent to goggle display
// * Some comments written before the port are left behind so I can understand stuff
public final class IntegratedSensorBusBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private static final double RAD_TO_DEG = 180.0 / Math.PI;
    private static final String OUTPUTS_NBT_KEY = "DbwOutputs";
    private static final String INPUTS_NBT_KEY = "BridgedInputs";
    private static final String INTERCEPTED_NBT_KEY = "Intercepted";
    private static final String MAX_SPEED_NBT_KEY = "MaxSpeed";
    private static final String MAX_ANGLE_NBT_KEY = "MaxAngle";
    private static final String RANGE_LOWER_NBT_KEY = "RangeLower";
    private static final String RANGE_UPPER_NBT_KEY = "RangeUpper";
    private static final String LOCAL_FRAME_NBT_KEY = "LocalFrame";
    private static final String SPEED_ENABLED_NBT_KEY = "SpeedEnabled";
    private static final String ANGLE_ENABLED_NBT_KEY = "AngleEnabled";
    private static final String ALTITUDE_ENABLED_NBT_KEY = "AltitudeEnabled";
    private static final String SETTINGS_LOCKED_NBT_KEY = "SettingsLocked";

    public static final int MAX_SPEED_MIN = 1;
    public static final int MAX_SPEED_MAX = 50;
    public static final int MAX_SPEED_DEFAULT = 10;
    public static final int MAX_ANGLE_MIN = 0;
    public static final int MAX_ANGLE_MAX = 90;
    public static final int MAX_ANGLE_DEFAULT = 45;
    public static final int RANGE_MIN = -64;
    public static final int RANGE_MAX = 320;

    // * Named alongside the numeric channels rather than taking slots from them
    public static final String CHANNEL_ALTITUDE = "altitude";
    public static final List<String> SPEED_CHANNELS = List.of(
            "speed_x_pos", "speed_x_neg", "speed_y_pos", "speed_y_neg", "speed_z_pos", "speed_z_neg");
    public static final List<String> ANGLE_CHANNELS = List.of(
            "angle_x_pos", "angle_x_neg", "angle_y_pos", "angle_y_neg", "angle_z_pos", "angle_z_neg");
    public static final List<String> ALTITUDE_CHANNELS = List.of(CHANNEL_ALTITUDE);

    public static final Map<String, String> CHANNEL_TO_LANG_KEY = Map.ofEntries(
            Map.entry("speed_x_pos", "drivebysable.sensor_bus.channel.speed_x_pos"),
            Map.entry("speed_x_neg", "drivebysable.sensor_bus.channel.speed_x_neg"),
            Map.entry("speed_y_pos", "drivebysable.sensor_bus.channel.speed_y_pos"),
            Map.entry("speed_y_neg", "drivebysable.sensor_bus.channel.speed_y_neg"),
            Map.entry("speed_z_pos", "drivebysable.sensor_bus.channel.speed_z_pos"),
            Map.entry("speed_z_neg", "drivebysable.sensor_bus.channel.speed_z_neg"),
            Map.entry("altitude", "drivebysable.sensor_bus.channel.altitude"),
            Map.entry("angle_x_pos", "drivebysable.sensor_bus.channel.angle_x_pos"),
            Map.entry("angle_x_neg", "drivebysable.sensor_bus.channel.angle_x_neg"),
            Map.entry("angle_y_pos", "drivebysable.sensor_bus.channel.angle_y_pos"),
            Map.entry("angle_y_neg", "drivebysable.sensor_bus.channel.angle_y_neg"),
            Map.entry("angle_z_pos", "drivebysable.sensor_bus.channel.angle_z_pos"),
            Map.entry("angle_z_neg", "drivebysable.sensor_bus.channel.angle_z_neg")
    );

    public static final List<String> TELEMETRY_CHANNELS = Stream.of(
            SPEED_CHANNELS, ALTITUDE_CHANNELS, ANGLE_CHANNELS
    ).flatMap(List::stream).toList();
    private static final double MAX_TARGET_COORDINATE = 30_000_000.0;
    private static boolean reportedDbwPlotUnload;

    // * Typed as Object so this block entity never names a Computer Craft type
    private final Object peripheral;
    private final byte[] outputs = new byte[IntegratedSensorBusBlock.CHANNEL_COUNT];

    // * What arrived over a cable on the matching sink channel
    // * Kept apart from the computer values so the stronger one wins
    private final byte[] inputs = new byte[IntegratedSensorBusBlock.CHANNEL_COUNT];

    // * Channels a computer has claimed
    private final boolean[] intercepted = new boolean[IntegratedSensorBusBlock.CHANNEL_COUNT];

    private boolean publishing;

    //#region // --- TELEMETRY CHANNEL SETTINGS --- //
    private int maxSpeed = MAX_SPEED_DEFAULT;
    private int maxAngle = MAX_ANGLE_DEFAULT;
    private int rangeLower = RANGE_MIN;
    private int rangeUpper = RANGE_MAX;

    // * True reads the sublevel's own right/up/forward, false reads the world axes
    private boolean localFrame = true;

    // * A disabled group is hidden everywhere
    private boolean speedEnabled = true;
    private boolean angleEnabled = true;
    private boolean altitudeEnabled = true;

    // * Set by a computer to stop a player editing these from the GUI
    private boolean settingsLocked;

    public synchronized boolean isSpeedEnabled() {
        return speedEnabled;
    }

    public synchronized boolean isAngleEnabled() {
        return angleEnabled;
    }

    public synchronized boolean isAltitudeEnabled() {
        return altitudeEnabled;
    }

    public synchronized boolean isSettingsLocked() {
        return settingsLocked;
    }

    public void setSpeedEnabled(boolean enabled) {
        synchronized (this) {
            speedEnabled = enabled;
        }
        onGroupToggled(SPEED_CHANNELS);
    }

    public void setAngleEnabled(boolean enabled) {
        synchronized (this) {
            angleEnabled = enabled;
        }
        onGroupToggled(ANGLE_CHANNELS);
    }

    public void setAltitudeEnabled(boolean enabled) {
        synchronized (this) {
            altitudeEnabled = enabled;
        }
        onGroupToggled(ALTITUDE_CHANNELS);
    }

    // * Only a computer can set this
    public void setSettingsLocked(boolean locked) {
        synchronized (this) {
            settingsLocked = locked;
        }
        markPersistentChanged();
        sendSettingsToClients();
    }

    // * Turning a group off disables signal output entirely
    private void onGroupToggled(List<String> channels) {
        for (String channel : channels) {
            synchronized (this) {
                telemetrySignals.remove(channel);
            }
            publishNamed(channel, 0);
        }
        markPersistentChanged();
        sendSettingsToClients();
    }

    private void sendSettingsToClients() {
        Level currentLevel = getLevel();
        if (currentLevel == null || currentLevel.isClientSide()) {
            return;
        }

        SensorBusSettingsSyncPacket payload = new SensorBusSettingsSyncPacket(
                worldPosition, isSpeedEnabled(), isAngleEnabled(), isAltitudeEnabled(),
                isSettingsLocked(), isLocalFrame());

        for (net.minecraft.world.entity.player.Player player : currentLevel.players()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, payload);
            }
        }
    }

    // * Applied on the client from the sync payload
    public void applySyncedSettings(
            boolean speed,
            boolean angle,
            boolean altitude,
            boolean locked,
            boolean local
    ) {
        synchronized (this) {
            speedEnabled = speed;
            angleEnabled = angle;
            altitudeEnabled = altitude;
            settingsLocked = locked;
            localFrame = local;
        }
    }

    public synchronized boolean isChannelEnabled(String channel) {
        if (SPEED_CHANNELS.contains(channel)) {
            return speedEnabled;
        }
        if (ANGLE_CHANNELS.contains(channel)) {
            return angleEnabled;
        }
        if (ALTITUDE_CHANNELS.contains(channel)) {
            return altitudeEnabled;
        }
        return true;
    }

    // * What the channel picker and the peripheral are allowed to see
    public synchronized List<String> getEnabledTelemetryChannels() {
        return TELEMETRY_CHANNELS.stream().filter(this::isChannelEnabled).toList();
    }

    // * Only republished when a value actually changes
    private final Map<String, Integer> telemetrySignals = new LinkedHashMap<>();

    public synchronized int getMaxSpeed() {
        return maxSpeed;
    }

    public int setMaxSpeed(int value) {
        int clamped = Math.max(MAX_SPEED_MIN, Math.min(MAX_SPEED_MAX, value));
        synchronized (this) {
            maxSpeed = clamped;
        }
        markPersistentChanged();
        return clamped;
    }

    public synchronized int getMaxAngle() {
        return maxAngle;
    }

    public int setMaxAngle(int value) {
        int clamped = Math.max(MAX_ANGLE_MIN, Math.min(MAX_ANGLE_MAX, value));
        synchronized (this) {
            maxAngle = clamped;
        }
        markPersistentChanged();
        return clamped;
    }

    public synchronized int getRangeLower() {
        return rangeLower;
    }

    public synchronized int getRangeUpper() {
        return rangeUpper;
    }

    public void setAltitudeRange(int lower, int upper) {
        int clampedLower = Math.max(RANGE_MIN, Math.min(RANGE_MAX, lower));
        int clampedUpper = Math.max(RANGE_MIN, Math.min(RANGE_MAX, upper));
        synchronized (this) {
            rangeLower = clampedLower;
            rangeUpper = clampedUpper;
        }
        markPersistentChanged();
    }

    public synchronized boolean isLocalFrame() {
        return localFrame;
    }

    public void setLocalFrame(boolean local) {
        synchronized (this) {
            localFrame = local;
        }
        markPersistentChanged();

        // * The goggle readout labels its speeds with this, so the client needs it
        sendSettingsToClients();
    }

    public void resetTelemetrySettings() {
        synchronized (this) {
            maxSpeed = MAX_SPEED_DEFAULT;
            maxAngle = MAX_ANGLE_DEFAULT;
            rangeLower = RANGE_MIN;
            rangeUpper = RANGE_MAX;
            localFrame = true;
        }
        markPersistentChanged();
    }

    public synchronized Map<String, Integer> getTelemetrySignals() {
        return new LinkedHashMap<>(telemetrySignals);
    }

    public synchronized int getTelemetrySignal(String channel) {
        return isChannelEnabled(channel) ? telemetrySignals.getOrDefault(channel, 0) : 0;
    }
    //#endregion

    private boolean hasTarget;
    private double targetX;
    private double targetY;
    private double targetZ;

    // Transient samples used to estimate gimbal/angular positioning speed in degrees per second
    private long lastOrientationSampleTick = Long.MIN_VALUE;
    private double lastPitch;
    private double lastRoll;
    private double lastYaw;
    private double pitchRate;
    private double rollRate;
    private double yawRate;
    private double angularSpeed;

    /**
     * A ComputerCraft main-thread task may already be queued when Sable begins removing a
     * sub-level. The peripheral still holds this block-entity object after setRemoved(), so a
     * late read must be rejected before it can call logicalPose() on a deleted Rapier body.
     * Native Rapier panics cannot be recovered with a Java try/catch.
     */
    private volatile boolean flightReadsBlocked = true;

    // A Lua program commonly asks for several fields in the same tick. Reuse one Sable sample
    // so one polling loop does not perform a native pose lookup for every peripheral method.
    private Level cachedFlightDataLevel;
    private long cachedFlightDataTick = Long.MIN_VALUE;
    private FlightData cachedFlightData = FlightData.unavailable();

    public IntegratedSensorBusBlockEntity(BlockPos pos, BlockState state) {
        super(CableBlockEntities.INTEGRATED_SENSOR_BUS.get(), pos, state);
        this.peripheral = ComputerCraftCompat.newSensorBusPeripheral(this);
    }

    public Object getPeripheral() {
        return peripheral;
    }

    /**
     * Sets a world-space navigation target without touching the DBW network.
     *
     * <p>Target changes used to send a full neighbour/block update from inside a Sable
     * sub-level. On physicalized ships that could make Sable treat a harmless navigation
     * edit like a structure update. The target is now persisted and synced with the
     * client-only update flag, while all DBW output state is left completely untouched.</p>
     */
    public boolean setTarget(double x, double y, double z) {
        validateTargetCoordinate("x", x);
        validateTargetCoordinate("y", y);
        validateTargetCoordinate("z", z);

        if (hasTarget
                && Double.compare(targetX, x) == 0
                && Double.compare(targetY, y) == 0
                && Double.compare(targetZ, z) == 0) {
            return false;
        }

        hasTarget = true;
        targetX = x;
        targetY = y;
        targetZ = z;
        invalidateFlightDataCache();
        markTargetChangedAndSync();
        return true;
    }

    public boolean clearTarget() {
        if (!hasTarget) {
            return false;
        }
        hasTarget = false;
        targetX = 0.0;
        targetY = 0.0;
        targetZ = 0.0;
        invalidateFlightDataCache();
        markTargetChangedAndSync();
        return true;
    }

    public synchronized int getChannel(int channel) {
        validateChannel(channel);
        return Byte.toUnsignedInt(outputs[channel - 1]);
    }

    public int setChannel(int channel, int power) {
        validateChannel(channel);
        int clamped = clampPower(power);
        synchronized (this) {
            outputs[channel - 1] = (byte) clamped;
        }
        publish(channel, getEffectiveChannel(channel));
        markPersistentChanged();
        return clamped;
    }

    //#region // --- CABLE SIDE INPUTS --- //
    // * What the channel actually emits
    // * An intercepted channel emits only what a computer set
    // * The incoming signal belongs to the script now
    public synchronized int getEffectiveChannel(int channel) {
        validateChannel(channel);
        int configured = Byte.toUnsignedInt(outputs[channel - 1]);
        if (intercepted[channel - 1]) {
            return configured;
        }
        return Math.max(configured, Byte.toUnsignedInt(inputs[channel - 1]));
    }

    public synchronized int getBridgedInput(int channel) {
        validateChannel(channel);
        return Byte.toUnsignedInt(inputs[channel - 1]);
    }

    public synchronized Map<Integer, Integer> getAllInputs() {
        Map<Integer, Integer> result = new LinkedHashMap<>(inputs.length);
        for (int channel = 1; channel <= inputs.length; channel++) {
            result.put(channel, Byte.toUnsignedInt(inputs[channel - 1]));
        }
        return result;
    }

    public synchronized boolean isIntercepted(int channel) {
        validateChannel(channel);
        return intercepted[channel - 1];
    }

    // * Returns the value now sitting on the channel, so a script can act at once
    public int setIntercepted(int channel, boolean intercept) {
        validateChannel(channel);
        synchronized (this) {
            if (intercepted[channel - 1] == intercept) {
                return getBridgedInput(channel);
            }
            intercepted[channel - 1] = intercept;
        }
        publish(channel, getEffectiveChannel(channel));
        markPersistentChanged();
        return getBridgedInput(channel);
    }

    public void setInterceptedAll(boolean intercept) {
        for (int channel = 1; channel <= IntegratedSensorBusBlock.CHANNEL_COUNT; channel++) {
            setIntercepted(channel, intercept);
        }
    }

    // * Called when a cable delivers a signal to this block on a sink channel
    public void setBridgedInput(int channel, int signal) {
        validateChannel(channel);
        int clamped = clampPower(signal);
        synchronized (this) {
            if (Byte.toUnsignedInt(inputs[channel - 1]) == clamped) {
                return;
            }
            inputs[channel - 1] = (byte) clamped;
        }

        publish(channel, getEffectiveChannel(channel));
        markPersistentChanged();

        // * Raised whether or not the channel is claimed
        ComputerCraftCompat.queueSensorBusInput(this, channel, clamped, isIntercepted(channel));
    }
    //#endregion

    public int toggleChannel(int channel) {
        return setChannel(channel, getChannel(channel) > 0 ? 0 : 15);
    }

    public int setAllChannels(int power) {
        int clamped = clampPower(power);
        synchronized (this) {
            for (int index = 0; index < outputs.length; index++) {
                outputs[index] = (byte) clamped;
            }
        }
        publishAllChannels();
        markPersistentChanged();
        return clamped;
    }

    public void clearChannels() {
        setAllChannels(0);
    }

    public synchronized Map<Integer, Integer> getAllChannels() {
        Map<Integer, Integer> result = new LinkedHashMap<>(outputs.length);
        for (int channel = 1; channel <= outputs.length; channel++) {
            result.put(channel, Byte.toUnsignedInt(outputs[channel - 1]));
        }
        return result;
    }

    public boolean setTargetFromComputer(double x, double y, double z) {
        return setTarget(x, y, z);
    }

    public synchronized FlightData readFlightData() {
        Level currentLevel = level;
        if (!canReadFlightData(currentLevel)) {
            return FlightData.unavailable();
        }

        long gameTime = currentLevel.getGameTime();
        if (cachedFlightDataLevel == currentLevel && cachedFlightDataTick == gameTime) {
            return cachedFlightData;
        }

        Vec3 localPosition = Vec3.atCenterOf(worldPosition);
        Vec3 globalPosition = localPosition;
        Vec3 globalVelocity = new Vec3(0.0, 0.0, 0.0);
        Object subLevel = null;
        Object pose = null;

        /**
         * Resolve containment before requesting any pose data. This lookup is Java-side, but a
         * sub-level may remain registered briefly after Sable marks it removed, so the returned
         * object must be lifecycle-checked before logicalPose(). The old code instead called
         * projectOutOfSubLevel/getVelocity first, allowing those convenience methods to reach
         * Rapier with a stale body key while the ship was unloading.
         *
         * Sable Companion is embedded inside Sable as a nested runtime JAR, but it is not
         * published as a normal Maven dependency. Reflection keeps this project compilable
         * while still using the real Sable 2.x API in-game.
         */
        Object helper = getSableHelper();
        if (helper != null) {
            subLevel = invokeQuietly(helper, "getContaining", this);
            if (subLevel == null) {
                subLevel = invokeQuietly(helper, "getContaining", currentLevel, worldPosition);
            }

            if (subLevel != null) {
                if (!isSubLevelUsable(subLevel) || !canReadFlightData(currentLevel)) {
                    return cacheFlightData(currentLevel, gameTime, FlightData.unavailable());
                }

                // Fetch the current pose once. Repeated logicalPose() calls widen the race
                // window while Sable removes the corresponding native Rapier body.
                pose = invokeQuietly(subLevel, "logicalPose");
                if (pose == null || !isSubLevelUsable(subLevel)
                        || !canReadFlightData(currentLevel)) {
                    return cacheFlightData(currentLevel, gameTime, FlightData.unavailable());
                }

                Vec3 projected = transformPosition(pose, localPosition);
                if (projected == null) {
                    return cacheFlightData(currentLevel, gameTime, FlightData.unavailable());
                }
                globalPosition = projected;

                /**
                 * Do not call Sable's getVelocity helper here. In the Sable 2.x stack that helper asks
                 * for logicalPose() again and then reaches through the native physics handle,
                 * recreating the exact unload race this guard is meant to close. Sable already
                 * retains lastPose() as ordinary Java-side transform data, so derive the sensor's
                 * point velocity from the current and previous tick poses instead.
                 */
                Object lastPose = invokeQuietly(subLevel, "lastPose");
                Vec3 previousPosition = transformPosition(lastPose, localPosition);
                if (previousPosition != null) {
                    globalVelocity = globalPosition.subtract(previousPosition).scale(20.0);
                }
            }
        }

        boolean onShip = subLevel != null;
        String shipId = onShip ? stringify(invokeQuietly(subLevel, "getUniqueId")) : "";
        String shipName = onShip ? stringify(invokeQuietly(subLevel, "getName")) : "";

        Vector3d forward = new Vector3d(0.0, 0.0, -1.0);
        Vector3d right = new Vector3d(1.0, 0.0, 0.0);
        Vector3d up = new Vector3d(0.0, 1.0, 0.0);
        Vector3d localVelocity = new Vector3d(globalVelocity.x, globalVelocity.y, globalVelocity.z);

        Quaterniondc orientation = getOrientationFromPose(pose);
        if (orientation != null) {
            orientation.transform(forward);
            orientation.transform(right);
            orientation.transform(up);
            orientation.transformInverse(localVelocity);
        }

        double heading = normalizeHeading(Math.atan2(forward.x, -forward.z) * RAD_TO_DEG);
        double yaw = wrapDegrees(heading);

        // Match Simulated's GimbalSensorBlockEntity exactly. Controllers written for the
        // stock sensor expect these X/Z tilt angles and signs, not generic Euler pitch/roll.
        double pitch = 0.0;
        double roll = 0.0;
        if (orientation != null) {
            Vector3d localDown = new Vector3d(0.0, -1.0, 0.0);
            orientation.transformInverse(localDown);
            double xAngle = localDown.y() < 0.0 || localDown.z() * localDown.z() > 0.001
                    ? Math.atan2(localDown.z(), -localDown.y())
                    : 0.0;
            double zAngle = localDown.y() < 0.0 || localDown.x() * localDown.x() > 0.001
                    ? Math.atan2(localDown.x(), -localDown.y())
                    : 0.0;
            if (Double.isFinite(xAngle) && Double.isFinite(zAngle)) {
                pitch = xAngle * RAD_TO_DEG;
                roll = zAngle * RAD_TO_DEG;
            }
        }

        AngularRates rates = updateAngularRates(gameTime, pitch, roll, yaw);

        double speed = globalVelocity.length();
        double groundSpeed = Math.hypot(globalVelocity.x, globalVelocity.z);
        double airPressure = getAirPressure(currentLevel, globalPosition);

        NavigationTargetClientState.TargetState clientTarget = currentLevel.isClientSide
                ? NavigationTargetClientState.get(currentLevel, worldPosition)
                : null;
        boolean effectiveHasTarget = clientTarget != null ? clientTarget.hasTarget() : hasTarget;
        double effectiveTargetX = clientTarget != null ? clientTarget.x() : targetX;
        double effectiveTargetY = clientTarget != null ? clientTarget.y() : targetY;
        double effectiveTargetZ = clientTarget != null ? clientTarget.z() : targetZ;

        double distanceToTarget = 0.0;
        double horizontalDistanceToTarget = 0.0;
        double bearingToTarget = 0.0;
        double elevationToTarget = 0.0;
        double relativeAngle = 0.0;

        if (effectiveHasTarget) {
            double dx = effectiveTargetX - globalPosition.x;
            double dy = effectiveTargetY - globalPosition.y;
            double dz = effectiveTargetZ - globalPosition.z;
            horizontalDistanceToTarget = Math.hypot(dx, dz);
            distanceToTarget = Math.sqrt(dx * dx + dy * dy + dz * dz);
            bearingToTarget = normalizeHeading(Math.atan2(dx, -dz) * RAD_TO_DEG);
            elevationToTarget = Math.atan2(dy, horizontalDistanceToTarget) * RAD_TO_DEG;

            /**
             * Reproduce Simulated's NavTableBlockEntity.updateCurrentAngle() convention.
             * The stock table transforms the target direction into ship-local space,
             * applies the table's UP-facing rotation, and then uses atan2(z, x).
             */
            relativeAngle = stockNavigationAngle(dx, dy, dz, orientation);
        }

        String dimension = currentLevel.dimension().location().toString();

        FlightData result = new FlightData(
                true,
                onShip,
                shipId,
                shipName,
                dimension,
                gameTime,
                globalPosition.x,
                globalPosition.y,
                globalPosition.z,
                globalVelocity.x,
                globalVelocity.y,
                globalVelocity.z,
                speed,
                groundSpeed,
                globalVelocity.y,
                localVelocity.x,
                localVelocity.y,
                -localVelocity.z,
                pitch,
                roll,
                yaw,
                heading,
                rates.pitchRate(),
                rates.rollRate(),
                rates.yawRate(),
                rates.angularSpeed(),
                airPressure,
                effectiveHasTarget,
                effectiveTargetX,
                effectiveTargetY,
                effectiveTargetZ,
                distanceToTarget,
                horizontalDistanceToTarget,
                bearingToTarget,
                elevationToTarget,
                relativeAngle
        );
        return cacheFlightData(currentLevel, gameTime, result);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        FlightData data = readFlightData();
        if (!data.available()) {
            return false;
        }

        // * Laid out on the client, where the font is available for the columns
        edn.lakeopossmc.drivebysable.client.SensorBusGoggleClient.appendSensorInfo(this, data, tooltip);
        return true;
    }

    private static Component line(String label, String value) {
        return Component.literal("  " + label + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.AQUA));
    }

    private static String format(double value, String suffix) {
        return String.format(Locale.ROOT, "%.2f %s", value, suffix);
    }

    private static String signed(double value, String suffix) {
        return String.format(Locale.ROOT, "%+.2f %s", value, suffix);
    }

    private static String angleAndRate(double angle, double rate) {
        return String.format(Locale.ROOT, "%+.2f°  (%+.2f°/s)", angle, rate);
    }

    private synchronized AngularRates updateAngularRates(long gameTime, double pitch, double roll, double yaw) {
        if (lastOrientationSampleTick == Long.MIN_VALUE || gameTime < lastOrientationSampleTick) {
            lastOrientationSampleTick = gameTime;
            lastPitch = pitch;
            lastRoll = roll;
            lastYaw = yaw;
            pitchRate = 0.0;
            rollRate = 0.0;
            yawRate = 0.0;
            angularSpeed = 0.0;
        } else if (gameTime > lastOrientationSampleTick) {
            double elapsedSeconds = (gameTime - lastOrientationSampleTick) / 20.0;
            if (elapsedSeconds > 0.0) {
                pitchRate = wrapDegrees(pitch - lastPitch) / elapsedSeconds;
                rollRate = wrapDegrees(roll - lastRoll) / elapsedSeconds;
                yawRate = wrapDegrees(yaw - lastYaw) / elapsedSeconds;
                angularSpeed = Math.sqrt(
                        pitchRate * pitchRate + rollRate * rollRate + yawRate * yawRate
                );
            }
            lastOrientationSampleTick = gameTime;
            lastPitch = pitch;
            lastRoll = roll;
            lastYaw = yaw;
        }
        return new AngularRates(pitchRate, rollRate, yawRate, angularSpeed);
    }

    // * The contributed version used onLoad, which 1.21.1 does not have
    @Override
    public void clearRemoved() {
        super.clearRemoved();
        synchronized (this) {
            flightReadsBlocked = false;
            resetFlightRuntimeState();
        }

        if (level != null && !level.isClientSide()) {
            CableNetworkManager.get(level).queueForPublish(worldPosition);
        }
    }

    // * Called from the tick queue, once the level is actually running
    public void publishAllNow() {
        publishAllChannels();
    }

    @Override
    public void setRemoved() {
        /**
         * Treat removal as a hard lifecycle boundary. Do not publish zeroes here.
         * Sable clears block entities after its plot holder has started disappearing;
         * a DBW write from this callback therefore targets a nonexistent plot and can
         * terminate the dedicated server. Drive By Wire observes the block/source removal
         * through the normal block lifecycle, so teardown must remain write-free.
         */
        synchronized (this) {
            flightReadsBlocked = true;
            resetFlightRuntimeState();
        }
        super.setRemoved();
    }

    //#region // --- TELEMETRY CHANNEL OUTPUT --- //
    // * What a script is allowed to see
    public FlightData readFlightDataForScripts() {
        FlightData data = readFlightData();
        if (!data.available()) {
            return data;
        }

        boolean speed = isSpeedEnabled();
        boolean angle = isAngleEnabled();
        boolean altitude = isAltitudeEnabled();
        if (speed && angle && altitude) {
            return data;
        }

        return new FlightData(
                data.available(),
                data.onShip(),
                data.shipId(),
                data.shipName(),
                data.dimension(),
                data.gameTime(),
                data.x(),
                altitude ? data.y() : 0.0,
                data.z(),
                speed ? data.velocityX() : 0.0,
                speed ? data.velocityY() : 0.0,
                speed ? data.velocityZ() : 0.0,
                speed ? data.speed() : 0.0,
                speed ? data.groundSpeed() : 0.0,
                speed ? data.verticalSpeed() : 0.0,
                speed ? data.localRightSpeed() : 0.0,
                speed ? data.localUpSpeed() : 0.0,
                speed ? data.localForwardSpeed() : 0.0,
                angle ? data.pitch() : 0.0,
                angle ? data.roll() : 0.0,
                angle ? data.yaw() : 0.0,
                angle ? data.heading() : 0.0,
                angle ? data.pitchRate() : 0.0,
                angle ? data.rollRate() : 0.0,
                angle ? data.yawRate() : 0.0,
                angle ? data.angularSpeed() : 0.0,
                data.airPressure(),
                data.hasTarget(),
                data.targetX(),
                data.targetY(),
                data.targetZ(),
                data.distanceToTarget(),
                data.horizontalDistanceToTarget(),
                data.bearingToTarget(),
                data.elevationToTarget(),
                data.relativeAngle()
        );
    }

    private static final int AVERAGE_SPEED_SAMPLES = 40;
    private final double[] speedSamples = new double[AVERAGE_SPEED_SAMPLES];
    private int speedSampleCount;
    private int speedSampleCursor;

    public synchronized double getAverageSpeed() {
        if (speedSampleCount == 0) {
            return 0.0;
        }

        double total = 0.0;
        for (int index = 0; index < speedSampleCount; index++) {
            total += speedSamples[index];
        }
        return total / speedSampleCount;
    }

    private synchronized void sampleSpeed(double speed) {
        speedSamples[speedSampleCursor] = speed;
        speedSampleCursor = (speedSampleCursor + 1) % AVERAGE_SPEED_SAMPLES;
        speedSampleCount = Math.min(speedSampleCount + 1, AVERAGE_SPEED_SAMPLES);
    }

    public void tickTelemetry() {
        if (level == null || level.isClientSide || flightReadsBlocked || isRemoved()) {
            return;
        }

        FlightData data = readFlightData();
        sampleSpeed(data.available() ? data.speed() : 0.0);
        Map<String, Integer> next = computeTelemetrySignals(data);

        for (Map.Entry<String, Integer> entry : next.entrySet()) {
            Integer previous;
            synchronized (this) {
                previous = telemetrySignals.put(entry.getKey(), entry.getValue());
            }

            if (previous == null || !previous.equals(entry.getValue())) {
                publishNamed(entry.getKey(), entry.getValue());
            }
        }
    }

    private Map<String, Integer> computeTelemetrySignals(FlightData data) {
        Map<String, Integer> result = new LinkedHashMap<>(TELEMETRY_CHANNELS.size());

        if (!data.available()) {
            for (String channel : TELEMETRY_CHANNELS) {
                result.put(channel, 0);
            }
            return result;
        }

        // * A disabled group is simply not computed, and never republished

        boolean local = isLocalFrame();
        double vx = local ? data.localRightSpeed() : data.velocityX();
        double vy = local ? data.localUpSpeed() : data.velocityY();
        double vz = local ? data.localForwardSpeed() : data.velocityZ();

        if (isSpeedEnabled()) {
            int speedLimit = getMaxSpeed();
            putSplit(result, "speed_x_pos", "speed_x_neg", vx, speedLimit);
            putSplit(result, "speed_y_pos", "speed_y_neg", vy, speedLimit);
            putSplit(result, "speed_z_pos", "speed_z_neg", vz, speedLimit);
        }

        if (isAltitudeEnabled()) {
            result.put(CHANNEL_ALTITUDE, altitudeSignal(data.y()));
        }

        if (!isAngleEnabled()) {
            return result;
        }

        int angleLimit = getMaxAngle();
        // * pitch is tilt about X and roll is tilt about Z, matching the Gimbal Sensor
        // * Yaw is signed deviation from north rather than a tilt
        putSplit(result, "angle_x_pos", "angle_x_neg", data.pitch(), angleLimit);
        putSplit(result, "angle_y_pos", "angle_y_neg", data.yaw(), angleLimit);
        putSplit(result, "angle_z_pos", "angle_z_neg", data.roll(), angleLimit);
        return result;
    }

    // * One direction per channel
    private static void putSplit(Map<String, Integer> into, String positive, String negative, double value, int limit) {
        into.put(positive, scaleMagnitude(Math.max(value, 0.0), limit));
        into.put(negative, scaleMagnitude(Math.max(-value, 0.0), limit));
    }

    private static int scaleMagnitude(double magnitude, int limit) {
        if (magnitude <= 0.0) {
            return 0;
        }

        if (limit <= 0) {
            return 15;
        }

        return clampPower((int) Math.round(15.0 * magnitude / limit));
    }

    private int altitudeSignal(double worldY) {
        int lower = getRangeLower();
        int upper = getRangeUpper();

        // * An empty range is a threshold rather than a division by zero
        if (lower == upper) {
            return worldY >= upper ? 15 : 0;
        }

        double fraction = (worldY - lower) / (double) (upper - lower);
        return clampPower((int) Math.round(15.0 * Math.max(0.0, Math.min(1.0, fraction))));
    }

    private void publishNamed(String channel, int power) {
        if (level == null || level.isClientSide || flightReadsBlocked || isRemoved() || publishing) {
            return;
        }

        publishing = true;
        try {
            CableNetworkManager.trySetSignalAt(level, worldPosition, channel, power);
        } finally {
            publishing = false;
        }
    }
    //#endregion

    private void publish(int channel, int power) {
        if (level == null || level.isClientSide || flightReadsBlocked || isRemoved() || publishing) {
            return;
        }

        publishing = true;
        try {
            tryPublish(channel, power);
        } finally {
            publishing = false;
        }
    }

    private void publishAllChannels() {
        if (level == null || level.isClientSide || flightReadsBlocked || isRemoved()) {
            return;
        }
        synchronized (this) {
            for (int channel = 1; channel <= outputs.length; channel++) {
                if (!tryPublish(channel, getEffectiveChannel(channel))) {
                    break;
                }
            }
        }
    }

    /**
     * Shields the server from the narrow Sable teardown race where DBW reaches a plot
     * after its holder has been removed. Other runtime failures are still surfaced.
     */
    private boolean tryPublish(int channel, int power) {
        try {
            CableNetworkManager.trySetSignalAt(
                    level,
                    worldPosition,
                    Integer.toString(channel),
                    power
            );
            return true;
        } catch (RuntimeException exception) {
            if (!isMissingPlotHolder(exception)) {
                throw exception;
            }
            synchronized (this) {
                flightReadsBlocked = true;
                resetFlightRuntimeState();
            }
            if (!reportedDbwPlotUnload) {
                reportedDbwPlotUnload = true;
                System.err.println(
                        "[drivebysable] Suppressed a DBW write while a "
                                + "Sable plot was unloading; the Integrated Flight Sensor "
                                + "was detached without updating the deleted plot holder."
                );
            }
            return false;
        }
    }

    private static boolean isMissingPlotHolder(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (current instanceof UnsupportedOperationException
                    && message != null
                    && message.toLowerCase(Locale.ROOT).contains("nonexistent plot holder")) {
                return true;
            }
        }
        return false;
    }

    /** Persist a DBW change without broadcasting a physical block update. */
    private void markPersistentChanged() {
        setChanged();
    }

    /**
     * Persists the target and synchronizes it through a dedicated custom payload.
     *
     * <p>Never send a vanilla block-entity packet manually from a Sable sub-level. Those packets
     * carry the sensor's shipyard BlockPos and bypass Sable's normal sub-level packet routing.
     * The dedicated payload contains plain navigation data only and cannot be interpreted as a
     * block, chunk, structure, entity, or ship-transform update.</p>
     */
    private void markTargetChangedAndSync() {
        /**
         * Navigation is runtime avionics state, not structure state. Do not call setChanged()
         * from a physicalized Sable sub-level: dirtying the storage chunk for a transient
         * waypoint can race sub-level movement/saving. The dedicated navigation payload keeps
         * the Pilot Helmet synchronized without touching the ship's chunk or transform.
         *
         * A target therefore intentionally resets when the world/server is restarted.
         */
        sendNavigationTarget();
    }

    // * Was a class of its own upstream
    // * Values only, never a block or ship updat,
    private void sendNavigationTarget() {
        final Level currentLevel = getLevel();
        if (currentLevel == null || currentLevel.isClientSide()) {
            return;
        }

        final NavigationTargetSyncPacket payload = createNavigationTargetPayload();
        for (final net.minecraft.world.entity.player.Player player : currentLevel.players()) {
            if (player instanceof final net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, payload);
            }
        }
    }

    synchronized NavigationTargetSyncPacket createNavigationTargetPayload() {
        return new NavigationTargetSyncPacket(
                worldPosition.asLong(),
                hasTarget,
                targetX,
                targetY,
                targetZ
        );
    }

    private static double stockNavigationAngle(
            double dx,
            double dy,
            double dz,
            @Nullable Quaterniondc shipOrientation
    ) {
        Vector3d direction = new Vector3d(dx, dy, dz);
        if (direction.lengthSquared() <= 1.0E-12) {
            return 0.0;
        }
        direction.normalize();

        // SimMathUtils.rotateQuat(vector, quaternion) applies the inverse rotation.
        if (shipOrientation != null) {
            shipOrientation.transformInverse(direction);
        }

        // A normal navigation table placed on a floor has FACING=UP.
        Vector3f tableLocal = new Vector3f(
                (float) direction.x,
                (float) direction.y,
                (float) direction.z
        );
        new Quaternionf(net.minecraft.core.Direction.UP.getRotation())
                .conjugate()
                .transform(tableLocal);

        return normalizeHeading(Math.atan2(tableLocal.z, tableLocal.x) * RAD_TO_DEG);
    }

    private static void validateTargetCoordinate(String axis, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Target " + axis + " must be finite");
        }
        if (Math.abs(value) > MAX_TARGET_COORDINATE) {
            throw new IllegalArgumentException(
                    "Target " + axis + " must be between -"
                            + (long) MAX_TARGET_COORDINATE + " and "
                            + (long) MAX_TARGET_COORDINATE
            );
        }
    }

    // * Older saves may be shorter, and anything past the end is simply off
    private static void readChannelArray(byte[] saved, byte[] target) {
        int copyLength = Math.min(saved.length, target.length);
        System.arraycopy(saved, 0, target, 0, copyLength);
        for (int index = copyLength; index < target.length; index++) {
            target[index] = 0;
        }
        for (int index = 0; index < target.length; index++) {
            target[index] = (byte) clampPower(Byte.toUnsignedInt(target[index]));
        }
    }

    public static void validateChannel(int channel) {
        if (channel < 1 || channel > IntegratedSensorBusBlock.CHANNEL_COUNT) {
            throw new IllegalArgumentException(
                    "Channel must be between 1 and " + IntegratedSensorBusBlock.CHANNEL_COUNT
            );
        }
    }

    public static int clampPower(int power) {
        return Math.max(0, Math.min(15, power));
    }

    private static Object getSableHelper() {
        try {
            Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
            return sableClass.getField("HELPER").get(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Vec3 invokeVec3(Object target, String methodName, Object... arguments) {
        Object value = invokeQuietly(target, methodName, arguments);
        return value instanceof Vec3 vec3 ? vec3 : null;
    }

    private boolean canReadFlightData(@Nullable Level currentLevel) {
        if (currentLevel == null || flightReadsBlocked || isRemoved()) {
            return false;
        }
        if (!currentLevel.hasChunkAt(worldPosition)) {
            return false;
        }

        // A detached peripheral may retain the old owner object after its chunk/sub-level was
        // removed. Verify that this exact block entity is still the live object at the position.
        return currentLevel.getBlockEntity(worldPosition) == this;
    }

    private synchronized FlightData cacheFlightData(Level currentLevel, long gameTime, FlightData data) {
        cachedFlightDataLevel = currentLevel;
        cachedFlightDataTick = gameTime;
        cachedFlightData = data;
        return data;
    }

    private synchronized void invalidateFlightDataCache() {
        cachedFlightDataLevel = null;
        cachedFlightDataTick = Long.MIN_VALUE;
        cachedFlightData = FlightData.unavailable();
    }

    private synchronized void resetFlightRuntimeState() {
        invalidateFlightDataCache();
        lastOrientationSampleTick = Long.MIN_VALUE;
        lastPitch = 0.0;
        lastRoll = 0.0;
        lastYaw = 0.0;
        pitchRate = 0.0;
        rollRate = 0.0;
        yawRate = 0.0;
        angularSpeed = 0.0;
    }

    /**
     * Reject a sub-level when its implementation exposes an unload/removal lifecycle flag.
     * Sable versions differ in which of these methods exist, so absent methods are ignored.
     */
    private static boolean isSubLevelUsable(Object subLevel) {
        if (booleanMethodEquals(subLevel, "isRemoved", true)
                || booleanMethodEquals(subLevel, "isUnloading", true)
                || booleanMethodEquals(subLevel, "isUnloaded", true)
                || booleanMethodEquals(subLevel, "isClosed", true)
                || booleanMethodEquals(subLevel, "isInvalid", true)) {
            return false;
        }
        return !booleanMethodEquals(subLevel, "isLoaded", false);
    }

    private static boolean booleanMethodEquals(Object target, String methodName, boolean expected) {
        Object value = invokeQuietly(target, methodName);
        return value instanceof Boolean bool && bool == expected;
    }

    /** Transform a ship-local block position with the already-fetched pose. */
    private static Vec3 transformPosition(Object pose, Vec3 localPosition) {
        Vec3 direct = invokeVec3(pose, "transformPosition", localPosition);
        if (direct != null) {
            return direct;
        }

        Vector3d mutable = new Vector3d(localPosition.x, localPosition.y, localPosition.z);
        Object transformed = invokeQuietly(pose, "transformPosition", mutable);
        if (transformed instanceof org.joml.Vector3dc vector) {
            return new Vec3(vector.x(), vector.y(), vector.z());
        }

        // Some APIs mutate the supplied vector and return void/null. Only accept that fallback
        // if the method exists; otherwise returning the unchanged local vector would be wrong.
        if (hasCompatibleMethod(pose, "transformPosition", mutable)) {
            return new Vec3(mutable.x, mutable.y, mutable.z);
        }
        return null;
    }

    private static boolean hasCompatibleMethod(Object target, String methodName, Object... arguments) {
        if (target == null) {
            return false;
        }
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName)
                    && method.getParameterCount() == arguments.length
                    && parametersAccept(method.getParameterTypes(), arguments)) {
                return true;
            }
        }
        return false;
    }

    private static Quaterniondc getOrientationFromPose(Object pose) {
        if (pose == null) {
            return null;
        }
        Object orientation = invokeQuietly(pose, "orientation");
        return orientation instanceof Quaterniondc quaternion ? quaternion : null;
    }

    private static double getAirPressure(Level currentLevel, Vec3 globalPosition) {
        try {
            Class<?> pressureClass = Class.forName(
                    "dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData"
            );
            Object result = invokeCompatible(
                    null,
                    pressureClass,
                    "getAirPressure",
                    currentLevel,
                    new Vector3d(globalPosition.x, globalPosition.y, globalPosition.z)
            );
            return result instanceof Number number ? number.doubleValue() : 0.0;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return 0.0;
        }
    }

    private static Object invokeQuietly(Object target, String methodName, Object... arguments) {
        if (target == null) {
            return null;
        }
        try {
            return invokeCompatible(target, target.getClass(), methodName, arguments);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invokeCompatible(
            Object target,
            Class<?> ownerClass,
            String methodName,
            Object... arguments
    ) throws ReflectiveOperationException {
        for (Method method : ownerClass.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != arguments.length) {
                continue;
            }
            if (target == null && !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!parametersAccept(method.getParameterTypes(), arguments)) {
                continue;
            }
            return method.invoke(target, arguments);
        }
        throw new NoSuchMethodException(ownerClass.getName() + "." + methodName);
    }

    private static boolean parametersAccept(Class<?>[] parameterTypes, Object[] arguments) {
        for (int index = 0; index < parameterTypes.length; index++) {
            Object argument = arguments[index];
            Class<?> parameterType = wrapPrimitive(parameterTypes[index]);
            if (argument == null) {
                if (parameterTypes[index].isPrimitive()) {
                    return false;
                }
            } else if (!parameterType.isAssignableFrom(argument.getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static String stringify(Object value) {
        return value == null ? "" : value.toString();
    }

    @Override
    protected synchronized void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeTargetData(tag);
        tag.putByteArray(OUTPUTS_NBT_KEY, outputs.clone());
        tag.putByteArray(INPUTS_NBT_KEY, inputs.clone());

        byte[] claimed = new byte[intercepted.length];
        for (int index = 0; index < intercepted.length; index++) {
            claimed[index] = (byte) (intercepted[index] ? 1 : 0);
        }
        tag.putByteArray(INTERCEPTED_NBT_KEY, claimed);

        tag.putInt(MAX_SPEED_NBT_KEY, maxSpeed);
        tag.putInt(MAX_ANGLE_NBT_KEY, maxAngle);
        tag.putInt(RANGE_LOWER_NBT_KEY, rangeLower);
        tag.putInt(RANGE_UPPER_NBT_KEY, rangeUpper);
        tag.putBoolean(LOCAL_FRAME_NBT_KEY, localFrame);
        tag.putBoolean(SPEED_ENABLED_NBT_KEY, speedEnabled);
        tag.putBoolean(ANGLE_ENABLED_NBT_KEY, angleEnabled);
        tag.putBoolean(ALTITUDE_ENABLED_NBT_KEY, altitudeEnabled);
        tag.putBoolean(SETTINGS_LOCKED_NBT_KEY, settingsLocked);
    }

    @Override
    protected synchronized void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readTargetData(tag);

        readChannelArray(tag.getByteArray(OUTPUTS_NBT_KEY), outputs);
        readChannelArray(tag.getByteArray(INPUTS_NBT_KEY), inputs);

        maxSpeed = tag.contains(MAX_SPEED_NBT_KEY)
                ? Math.max(MAX_SPEED_MIN, Math.min(MAX_SPEED_MAX, tag.getInt(MAX_SPEED_NBT_KEY)))
                : MAX_SPEED_DEFAULT;
        maxAngle = tag.contains(MAX_ANGLE_NBT_KEY)
                ? Math.max(MAX_ANGLE_MIN, Math.min(MAX_ANGLE_MAX, tag.getInt(MAX_ANGLE_NBT_KEY)))
                : MAX_ANGLE_DEFAULT;
        rangeLower = tag.contains(RANGE_LOWER_NBT_KEY)
                ? Math.max(RANGE_MIN, Math.min(RANGE_MAX, tag.getInt(RANGE_LOWER_NBT_KEY)))
                : RANGE_MIN;
        rangeUpper = tag.contains(RANGE_UPPER_NBT_KEY)
                ? Math.max(RANGE_MIN, Math.min(RANGE_MAX, tag.getInt(RANGE_UPPER_NBT_KEY)))
                : RANGE_MAX;
        localFrame = !tag.contains(LOCAL_FRAME_NBT_KEY) || tag.getBoolean(LOCAL_FRAME_NBT_KEY);
        speedEnabled = !tag.contains(SPEED_ENABLED_NBT_KEY) || tag.getBoolean(SPEED_ENABLED_NBT_KEY);
        angleEnabled = !tag.contains(ANGLE_ENABLED_NBT_KEY) || tag.getBoolean(ANGLE_ENABLED_NBT_KEY);
        altitudeEnabled = !tag.contains(ALTITUDE_ENABLED_NBT_KEY) || tag.getBoolean(ALTITUDE_ENABLED_NBT_KEY);
        settingsLocked = tag.getBoolean(SETTINGS_LOCKED_NBT_KEY);

        byte[] claimed = tag.getByteArray(INTERCEPTED_NBT_KEY);
        for (int index = 0; index < intercepted.length; index++) {
            intercepted[index] = index < claimed.length && claimed[index] != 0;
        }
    }

    private synchronized void writeTargetData(CompoundTag tag) {
        tag.putBoolean("HasTarget", hasTarget);
        if (hasTarget) {
            tag.putDouble("TargetX", targetX);
            tag.putDouble("TargetY", targetY);
            tag.putDouble("TargetZ", targetZ);
        }
    }

    private synchronized void readTargetData(CompoundTag tag) {
        hasTarget = tag.getBoolean("HasTarget");
        if (hasTarget) {
            targetX = tag.getDouble("TargetX");
            targetY = tag.getDouble("TargetY");
            targetZ = tag.getDouble("TargetZ");
        } else {
            targetX = 0.0;
            targetY = 0.0;
            targetZ = 0.0;
        }
    }

    /**
     * Network updates intentionally contain only navigation data. DBW outputs remain
     * server-authoritative and are never reloaded or republished because a target changed.
     */
    @Override
    public synchronized CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeTargetData(tag);

        // * Carried on the initial chunk sync too
        tag.putBoolean(SPEED_ENABLED_NBT_KEY, speedEnabled);
        tag.putBoolean(ANGLE_ENABLED_NBT_KEY, angleEnabled);
        tag.putBoolean(ALTITUDE_ENABLED_NBT_KEY, altitudeEnabled);
        tag.putBoolean(SETTINGS_LOCKED_NBT_KEY, settingsLocked);
        tag.putBoolean(LOCAL_FRAME_NBT_KEY, localFrame);
        return tag;
    }

    /**
     * Initial chunk synchronization still uses the normal update tag. Dynamic target changes use
     * NavigationTargetSyncPacket and never manufacture a block-entity packet.
     */

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double normalizeHeading(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = normalizeHeading(degrees);
        return wrapped >= 180.0 ? wrapped - 360.0 : wrapped;
    }

    private record AngularRates(double pitchRate, double rollRate, double yawRate, double angularSpeed) {}

    public record FlightData(
            boolean available,
            boolean onShip,
            String shipId,
            String shipName,
            String dimension,
            long gameTime,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            double speed,
            double groundSpeed,
            double verticalSpeed,
            double localRightSpeed,
            double localUpSpeed,
            double localForwardSpeed,
            double pitch,
            double roll,
            double yaw,
            double heading,
            double pitchRate,
            double rollRate,
            double yawRate,
            double angularSpeed,
            double airPressure,
            boolean hasTarget,
            double targetX,
            double targetY,
            double targetZ,
            double distanceToTarget,
            double horizontalDistanceToTarget,
            double bearingToTarget,
            double elevationToTarget,
            double relativeAngle
    ) {
        static FlightData unavailable() {
            return new FlightData(
                    false, false, "", "", "", 0L,
                    0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0,
                    0.0,
                    false, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0
            );
        }

        public Map<String, Object> positionTable() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("x", x);
            result.put("y", y);
            result.put("z", z);
            result.put("altitude", y);
            result.put("dimension", dimension);
            return result;
        }

        public Map<String, Object> velocityTable() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("x", velocityX);
            result.put("y", velocityY);
            result.put("z", velocityZ);
            result.put("speed", speed);
            result.put("groundSpeed", groundSpeed);
            result.put("verticalSpeed", verticalSpeed);
            result.put("forward", localForwardSpeed);
            result.put("right", localRightSpeed);
            result.put("up", localUpSpeed);
            return result;
        }

        public Map<String, Object> angularVelocityTable() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pitch", pitchRate);
            result.put("roll", rollRate);
            result.put("yaw", yawRate);
            result.put("speed", angularSpeed);
            result.put("units", "degrees_per_second");
            return result;
        }

        public Map<String, Object> gimbalTable() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pitch", pitch);
            result.put("roll", roll);
            result.put("yaw", yaw);
            result.put("heading", heading);
            result.put("pitchRate", pitchRate);
            result.put("rollRate", rollRate);
            result.put("yawRate", yawRate);
            result.put("angularSpeed", angularSpeed);
            return result;
        }

        public Map<String, Object> shipTable() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("onShip", onShip);
            result.put("id", shipId);
            result.put("name", shipName);
            return result;
        }

        public Map<String, Object> targetTable() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("active", hasTarget);
            if (hasTarget) {
                result.put("x", targetX);
                result.put("y", targetY);
                result.put("z", targetZ);
                result.put("distance", distanceToTarget);
                result.put("horizontalDistance", horizontalDistanceToTarget);
                result.put("bearing", bearingToTarget);
                result.put("elevation", elevationToTarget);
                result.put("relativeAngle", relativeAngle);
            }
            return result;
        }

        public Map<String, Object> navigationTable() {
            Map<String, Object> result = positionTable();
            result.put("heading", heading);
            result.put("onShip", onShip);
            result.put("shipId", shipId);
            result.put("shipName", shipName);
            result.put("target", targetTable());
            return result;
        }

        public Map<String, Object> allTable() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", available);
            result.put("position", positionTable());
            result.put("velocity", velocityTable());
            result.put("gimbal", gimbalTable());
            result.put("angularVelocity", angularVelocityTable());
            result.put("navigation", navigationTable());
            result.put("ship", shipTable());
            result.put("target", targetTable());
            result.put("altitude", y);
            result.put("airPressure", airPressure);
            result.put("speed", speed);
            result.put("groundSpeed", groundSpeed);
            result.put("verticalSpeed", verticalSpeed);
            result.put("gameTime", gameTime);
            return result;
        }
    }
}