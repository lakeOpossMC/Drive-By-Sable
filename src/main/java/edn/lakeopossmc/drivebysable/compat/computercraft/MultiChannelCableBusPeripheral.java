package edn.lakeopossmc.drivebysable.compat.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import edn.lakeopossmc.drivebysable.blocks.MultiChannelCableBusBlock;
import edn.lakeopossmc.drivebysable.blocks.MultiChannelCableBusBlockEntity;

// --- COMPUTER CRAFT API FOR THE MULTI CHANNEL CABLE BUS --- //
// * Lives in the compat package so nothing outside it names a CC type
public final class MultiChannelCableBusPeripheral implements IDynamicPeripheral {
    private static final String[] METHODS = {
            "getChannelCount",
            "setChannel",
            "getChannel",
            "toggleChannel",
            "setAll",
            "clear",
            "getAll",
            "setOutput",
            "getOutput",
            "toggleOutput",
            "getConnectionCount",
            "getChannelStatus",
            "getInput",
            "getAllInputs",
            "setIntercept",
            "isIntercepted",
            "setInterceptAll"
    };

    // * Event raised on every incoming signal: channel, value, whether claimed
    public static final String INPUT_EVENT = "cable_bus_input";

    private final MultiChannelCableBusBlockEntity owner;

    // * Attached computers, so an incoming signal can be pushed rather than polled
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    public MultiChannelCableBusPeripheral(MultiChannelCableBusBlockEntity owner) {
        this.owner = owner;
    }

    @Override
    public String getType() {
        return "multi_channel_cable_bus";
    }

    @Override
    public Object getTarget() {
        return owner;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof MultiChannelCableBusPeripheral hub && hub.owner == owner;
    }

    @Override
    public String[] getMethodNames() {
        return METHODS.clone();
    }

    @Override
    public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments)
            throws LuaException {
        return switch (method) {
            case 0 -> MethodResult.of(MultiChannelCableBusBlock.CHANNEL_COUNT);
            case 1, 7 -> setChannel(context, arguments);
            case 2, 8 -> MethodResult.of(owner.getChannel(readChannel(arguments)));
            case 3, 9 -> toggleChannel(context, arguments);
            case 4 -> setAll(context, arguments);
            case 5 -> context.executeMainThreadTask(() -> {
                owner.clear();
                return new Object[]{true};
            });
            case 6 -> MethodResult.of(owner.getAll());
            case 10 -> getConnectionCount(context, arguments);
            case 11 -> getChannelStatus(context, arguments);
            case 12 -> MethodResult.of(owner.getBridgedInput(readChannel(arguments)));
            case 13 -> MethodResult.of(owner.getAllInputs());
            case 14 -> setIntercept(context, arguments);
            case 15 -> MethodResult.of(owner.isIntercepted(readChannel(arguments)));
            case 16 -> setInterceptAll(context, arguments);
            default -> throw new LuaException("Unknown peripheral method index: " + method);
        };
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

    private MethodResult setAll(ILuaContext context, IArguments arguments) throws LuaException {
        int power = readPower(arguments, 0);
        return context.executeMainThreadTask(() -> new Object[]{owner.setAll(power)});
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

    // * Pushed to every computer attached to this Bus
    public void queueInputEvent(int channel, int signal, boolean intercepted) {
        computers.forEach(computer ->
                computer.queueEvent(INPUT_EVENT, computer.getAttachmentName(), channel, signal, intercepted));
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.add(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
    }

    private MethodResult getConnectionCount(ILuaContext context, IArguments arguments) throws LuaException {
        int channel = readChannel(arguments);
        return context.executeMainThreadTask(() -> new Object[]{owner.getConnectionCount(channel)});
    }

    private MethodResult getChannelStatus(ILuaContext context, IArguments arguments) throws LuaException {
        int channel = readChannel(arguments);
        return context.executeMainThreadTask(() -> new Object[]{owner.getChannelStatus(channel)});
    }

    private static int readChannel(IArguments arguments) throws LuaException {
        int channel = arguments.getInt(0);
        try {
            MultiChannelCableBusBlockEntity.validateChannel(channel);
        } catch (IllegalArgumentException exception) {
            throw new LuaException(exception.getMessage());
        }
        return channel;
    }

    private static int readPower(IArguments arguments, int index) throws LuaException {
        String type = arguments.getType(index);
        if ("boolean".equals(type)) {
            return arguments.getBoolean(index) ? 15 : 0;
        }
        if ("number".equals(type)) {
            return MultiChannelCableBusBlockEntity.clampPower(arguments.getInt(index));
        }
        throw new LuaException("Expected a boolean or a redstone-strength number from 0 to 15");
    }
}