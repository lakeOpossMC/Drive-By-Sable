package edn.lakeopossmc.drivebysable.compat.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlock;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// --- PORTED RELIANTSHELL/AMMAN FEATURE --- //
/** ComputerCraft API combining flight sensing and 100 Drive-By-Wire outputs. */
public final class IntegratedSensorBusPeripheral implements IDynamicPeripheral {
    private static boolean reportedMainThreadReadSafety;
    private static final Set<String> ADDITIONAL_TYPES = Set.of(
            "altitude_sensor",
            "velocity_sensor",
            "navigation_table",
            "gimbal_sensor"
    );

    private static final String[] METHODS = {
            // Stock Simulated sensor-compatible names.
            "getHeight",                    // 0
            "getAirPressure",               // 1
            "getVelocity",                  // 2
            "getAngles",                    // 3
            "getRelativeAngle",             // 4

            // Expanded flight API.
            "getAltitude",                  // 5
            "getPosition",                  // 6
            "getCoordinates",               // 7
            "getVelocityVector",            // 8
            "getSpeed",                     // 9
            "getGroundSpeed",               // 10
            "getVerticalSpeed",             // 11
            "getGimbal",                    // 12
            "getOrientation",               // 13
            "getPitch",                     // 14
            "getRoll",                      // 15
            "getYaw",                       // 16
            "getHeading",                   // 17
            "getNavigation",                // 18
            "getShip",                      // 19
            "isOnShip",                     // 20
            "getX",                         // 21
            "getY",                         // 22
            "getZ",                         // 23
            "setTarget",                    // 24
            "clearTarget",                  // 25
            "hasTarget",                    // 26
            "getTarget",                    // 27
            "getDistanceToTarget",          // 28
            "getHorizontalDistanceToTarget",// 29
            "getBearingToTarget",           // 30
            "getElevationToTarget",         // 31
            "getAll",                       // 32

            // Gimbal/angular positioning-speed API.
            "getAngularVelocity",           // 33
            "getPitchRate",                 // 34
            "getRollRate",                  // 35
            "getYawRate",                   // 36
            "getAngularSpeed",              // 37

            // Integrated 100-channel DBW hub API.
            "getChannelCount",              // 38
            "setChannel",                   // 39
            "getChannel",                   // 40
            "toggleChannel",                // 41
            "setAllChannels",               // 42
            "clearChannels",                // 43
            "getAllChannels",               // 44
            "setOutput",                    // 45
            "getOutput",                    // 46
            "toggleOutput",                 // 47

            // Complete compatibility with the stock Simulated peripherals.
            "getAnglesRad",                 // 48
            "getRelativeAngleRad",          // 49

            // Safer centered navigation helpers for new autopilot programs.
            "getSignedRelativeAngle",       // 50
            "getSignedRelativeAngleRad",    // 51

            // Cable side inputs, matching the Multi-Channel Cable Bus.
            "getInput",                     // 52
            "getAllInputs",                 // 53
            "setIntercept",                 // 54
            "isIntercepted",                // 55
            "setInterceptAll",              // 56

            // Telemetry channel settings and readback.
            "getMaxSpeed",                  // 57
            "setMaxSpeed",                  // 58
            "getMaxAngle",                  // 59
            "setMaxAngle",                  // 60
            "getAltitudeRange",             // 61
            "setAltitudeRange",             // 62
            "isLocalFrame",                 // 63
            "setLocalFrame",                // 64
            "resetTelemetrySettings",       // 65
            "getTelemetrySignal",           // 66
            "getTelemetrySignals",          // 67
            "getTelemetryChannels",         // 68

            // Group toggles and the editor lock.
            "isSpeedEnabled",               // 69
            "setSpeedEnabled",              // 70
            "isAngleEnabled",               // 71
            "setAngleEnabled",              // 72
            "isAltitudeEnabled",            // 73
            "setAltitudeEnabled",           // 74
            "isSettingsLocked",             // 75
            "setSettingsLocked"             // 76
    };

    // * Event raised on every incoming signal: channel, value, whether claimed
    public static final String INPUT_EVENT = "sensor_bus_input";

    private final IntegratedSensorBusBlockEntity owner;

    // * Attached computers, so an incoming signal can be pushed rather than polled
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    public IntegratedSensorBusPeripheral(IntegratedSensorBusBlockEntity owner) {
        this.owner = owner;
    }

    @Override
    public String getType() {
        return "integrated_flight_sensor";
    }

    @Override
    public Set<String> getAdditionalTypes() {
        return ADDITIONAL_TYPES;
    }

    @Override
    public Object getTarget() {
        return owner;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof IntegratedSensorBusPeripheral sensor && sensor.owner == owner;
    }

    @Override
    public String[] getMethodNames() {
        return METHODS.clone();
    }

    @Override
    public MethodResult callMethod(
            IComputerAccess computer,
            ILuaContext context,
            int method,
            IArguments arguments
    ) throws LuaException {
        return switch (method) {
            /**
             * Every flight-data read is marshalled onto Minecraft's main server thread.
             *
             * ComputerCraft invokes peripheral methods from its own computer worker. Sable's
             * sub-level lookup, pose, velocity, and coordinate-projection APIs are world/physics
             * operations and must never be queried from that worker. Polling those APIs off-thread
             * could race Sable's movement step and make the physical ship appear at its internal
             * shipyard coordinates. DBW-only getters remain direct because they read synchronized
             * byte-array state and never touch a Level or a Sable object.
             */

            // Stock sensor-compatible reads.
            case 0 -> readOnMainThread(context, data -> data.y());
            case 1 -> readOnMainThread(context, data -> data.airPressure());
            case 2 -> readOnMainThread(context, data -> data.localForwardSpeed());
            case 3 -> readOnMainThread(context, data -> stockGimbalAngles(data, false));
            case 4 -> readOnMainThread(context, data -> data.relativeAngle());

            // Expanded sensor reads.
            case 5 -> readOnMainThread(context, data -> data.y());
            case 6, 7 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::positionTable);
            case 8 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::velocityTable);
            case 9 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::speed);
            case 10 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::groundSpeed);
            case 11 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::verticalSpeed);
            case 12, 13 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::gimbalTable);
            case 14 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::pitch);
            case 15 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::roll);
            case 16 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::yaw);
            case 17 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::heading);
            case 18 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::navigationTable);
            case 19 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::shipTable);
            case 20 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::onShip);
            case 21 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::x);
            case 22 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::y);
            case 23 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::z);
            case 24 -> {
                double x = arguments.getFiniteDouble(0);
                double y = arguments.getFiniteDouble(1);
                double z = arguments.getFiniteDouble(2);
                yield context.executeMainThreadTask(() -> {
                    try {
                        return new Object[]{owner.setTargetFromComputer(x, y, z)};
                    } catch (IllegalArgumentException exception) {
                        throw new LuaException(exception.getMessage());
                    }
                });
            }
            case 25 -> context.executeMainThreadTask(
                    () -> new Object[]{owner.clearTarget()}
            );
            case 26 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::hasTarget);
            case 27 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::targetTable);
            case 28 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::distanceToTarget);
            case 29 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::horizontalDistanceToTarget);
            case 30 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::bearingToTarget);
            case 31 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::elevationToTarget);
            case 32 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::allTable);
            case 33 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::angularVelocityTable);
            case 34 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::pitchRate);
            case 35 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::rollRate);
            case 36 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::yawRate);
            case 37 -> readOnMainThread(context, IntegratedSensorBusBlockEntity.FlightData::angularSpeed);

            // DBW control and synchronized DBW-only reads.
            case 38 -> MethodResult.of(IntegratedSensorBusBlock.CHANNEL_COUNT);
            case 39, 45 -> setChannel(context, arguments);
            case 40, 46 -> MethodResult.of(owner.getChannel(readChannel(arguments)));
            case 41, 47 -> toggleChannel(context, arguments);
            case 42 -> setAllChannels(context, arguments);
            case 43 -> context.executeMainThreadTask(() -> {
                owner.clearChannels();
                return new Object[]{true};
            });
            case 44 -> MethodResult.of(owner.getAllChannels());
            case 48 -> readOnMainThread(context, data -> stockGimbalAngles(data, true));
            case 49 -> readOnMainThread(context, data -> Math.toRadians(data.relativeAngle()));
            case 50 -> readOnMainThread(context, data -> centerAngle(data.relativeAngle()));
            case 51 -> readOnMainThread(
                    context,
                    data -> Math.toRadians(centerAngle(data.relativeAngle()))
            );
            case 52 -> MethodResult.of(owner.getBridgedInput(readChannel(arguments)));
            case 53 -> MethodResult.of(owner.getAllInputs());
            case 54 -> setIntercept(context, arguments);
            case 55 -> MethodResult.of(owner.isIntercepted(readChannel(arguments)));
            case 56 -> setInterceptAll(context, arguments);
            case 57 -> MethodResult.of(owner.getMaxSpeed());
            case 58 -> context.executeMainThreadTask(
                    () -> new Object[]{owner.setMaxSpeed((int) arguments.getLong(0))});
            case 59 -> MethodResult.of(owner.getMaxAngle());
            case 60 -> context.executeMainThreadTask(
                    () -> new Object[]{owner.setMaxAngle((int) arguments.getLong(0))});
            case 61 -> MethodResult.of(owner.getRangeLower(), owner.getRangeUpper());
            case 62 -> setAltitudeRange(context, arguments);
            case 63 -> MethodResult.of(owner.isLocalFrame());
            case 64 -> setLocalFrame(context, arguments);
            case 65 -> context.executeMainThreadTask(() -> {
                owner.resetTelemetrySettings();
                return new Object[]{true};
            });
            case 66 -> MethodResult.of(owner.getTelemetrySignal(arguments.getString(0)));
            case 67 -> MethodResult.of(owner.getTelemetrySignals());
            case 68 -> MethodResult.of(IntegratedSensorBusBlockEntity.TELEMETRY_CHANNELS);
            case 69 -> MethodResult.of(owner.isSpeedEnabled());
            case 70 -> setGroup(context, arguments, Group.SPEED);
            case 71 -> MethodResult.of(owner.isAngleEnabled());
            case 72 -> setGroup(context, arguments, Group.ANGLE);
            case 73 -> MethodResult.of(owner.isAltitudeEnabled());
            case 74 -> setGroup(context, arguments, Group.ALTITUDE);
            case 75 -> MethodResult.of(owner.isSettingsLocked());
            case 76 -> setLocked(context, arguments);
            default -> throw new LuaException("Unknown peripheral method index: " + method);
        };
    }

    private MethodResult readOnMainThread(ILuaContext context, FlightRead read) throws LuaException {
        return context.executeMainThreadTask(() -> {
            reportMainThreadReadSafety();
            return new Object[]{read.apply(owner.readFlightDataForScripts())};
        });
    }

    private static synchronized void reportMainThreadReadSafety() {
        if (reportedMainThreadReadSafety) {
            return;
        }
        reportedMainThreadReadSafety = true;
        System.out.println(
                "[drivebysable] Integrated Flight Sensor ComputerCraft "
                        + "flight reads are now executed on the Minecraft server thread."
        );
    }

    private static List<Double> stockGimbalAngles(
            IntegratedSensorBusBlockEntity.FlightData data,
            boolean radians
    ) {
        double xAngle = data.pitch();
        double zAngle = data.roll();
        if (!Double.isFinite(xAngle) || !Double.isFinite(zAngle)) {
            return List.of(0.0, 0.0);
        }
        if (radians) {
            return List.of(Math.toRadians(xAngle), Math.toRadians(zAngle));
        }
        return List.of(xAngle, zAngle);
    }

    @FunctionalInterface
    private interface FlightRead {
        Object apply(IntegratedSensorBusBlockEntity.FlightData data);
    }

    private enum Group { SPEED, ANGLE, ALTITUDE }

    private MethodResult setGroup(ILuaContext context, IArguments arguments, Group group) throws LuaException {
        boolean enabled = arguments.getBoolean(0);
        return context.executeMainThreadTask(() -> {
            switch (group) {
                case SPEED -> owner.setSpeedEnabled(enabled);
                case ANGLE -> owner.setAngleEnabled(enabled);
                case ALTITUDE -> owner.setAltitudeEnabled(enabled);
            }
            return new Object[]{enabled};
        });
    }

    // * Locking shuts the player out of the GUI
    private MethodResult setLocked(ILuaContext context, IArguments arguments) throws LuaException {
        boolean locked = arguments.getBoolean(0);
        return context.executeMainThreadTask(() -> {
            owner.setSettingsLocked(locked);
            return new Object[]{locked};
        });
    }

    private MethodResult setAltitudeRange(ILuaContext context, IArguments arguments) throws LuaException {
        int lower = (int) arguments.getLong(0);
        int upper = (int) arguments.getLong(1);
        return context.executeMainThreadTask(() -> {
            owner.setAltitudeRange(lower, upper);
            return new Object[]{owner.getRangeLower(), owner.getRangeUpper()};
        });
    }

    private MethodResult setLocalFrame(ILuaContext context, IArguments arguments) throws LuaException {
        boolean local = arguments.getBoolean(0);
        return context.executeMainThreadTask(() -> {
            owner.setLocalFrame(local);
            return new Object[]{local};
        });
    }

    // * Returns whatever is already sitting on the channel
    private MethodResult setIntercept(ILuaContext context, IArguments arguments) throws LuaException {
        int channel = readChannel(arguments);
        boolean intercept = arguments.getBoolean(1);
        return context.executeMainThreadTask(() -> new Object[]{owner.setIntercepted(channel, intercept)});
    }

    private MethodResult setInterceptAll(ILuaContext context, IArguments arguments) throws LuaException {
        boolean intercept = arguments.getBoolean(0);
        return context.executeMainThreadTask(() -> {
            owner.setInterceptedAll(intercept);
            return new Object[]{true};
        });
    }

    // * Pushed to every computer attached to this block
    @Override
    public void attach(IComputerAccess computer) {
        computers.add(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
    }

    public void queueInputEvent(int channel, int signal, boolean intercepted) {
        computers.forEach(computer ->
                computer.queueEvent(INPUT_EVENT, computer.getAttachmentName(), channel, signal, intercepted));
    }

    private MethodResult setChannel(ILuaContext context, IArguments arguments) throws LuaException {
        int channel = readChannel(arguments);
        int power = readPower(arguments, 1);
        return context.executeMainThreadTask(() -> new Object[]{owner.setChannel(channel, power)});
    }

    private MethodResult toggleChannel(ILuaContext context, IArguments arguments) throws LuaException {
        int channel = readChannel(arguments);
        return context.executeMainThreadTask(() -> new Object[]{owner.toggleChannel(channel)});
    }

    private MethodResult setAllChannels(ILuaContext context, IArguments arguments) throws LuaException {
        int power = readPower(arguments, 0);
        return context.executeMainThreadTask(() -> new Object[]{owner.setAllChannels(power)});
    }

    private static int readChannel(IArguments arguments) throws LuaException {
        int channel = arguments.getInt(0);
        try {
            IntegratedSensorBusBlockEntity.validateChannel(channel);
        } catch (IllegalArgumentException exception) {
            throw new LuaException(exception.getMessage());
        }
        return channel;
    }

    private static double centerAngle(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped < 0.0) {
            wrapped += 360.0;
        }
        return wrapped >= 180.0 ? wrapped - 360.0 : wrapped;
    }

    private static int readPower(IArguments arguments, int index) throws LuaException {
        Object value = arguments.get(index);
        if (value instanceof Boolean enabled) {
            return enabled ? 15 : 0;
        }
        if (value instanceof Number number) {
            return IntegratedSensorBusBlockEntity.clampPower(number.intValue());
        }
        throw new LuaException("Power must be a boolean or a number from 0 to 15");
    }
}