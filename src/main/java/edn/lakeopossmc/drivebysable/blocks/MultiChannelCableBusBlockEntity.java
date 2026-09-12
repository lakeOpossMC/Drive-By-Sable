package edn.lakeopossmc.drivebysable.blocks;

import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import edn.lakeopossmc.drivebysable.compat.computercraft.ComputerCraftCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// --- HOLDS AND PUBLISHES THE BUS HUNDRED CHANNEL VALUES --- //
public final class MultiChannelCableBusBlockEntity extends BlockEntity {
    private static final String OUTPUTS_NBT_KEY = "Outputs";
    private static final String INPUTS_NBT_KEY = "BridgedInputs";
    private static final String INTERCEPTED_NBT_KEY = "Intercepted";

    // * What a computer asked for
    private final byte[] outputs = new byte[MultiChannelCableBusBlock.CHANNEL_COUNT];

    // * What arrived over a cable on the matching sink channel
    // * Kept apart from the computer values so the stronger one wins
    private final byte[] inputs = new byte[MultiChannelCableBusBlock.CHANNEL_COUNT];

    // * Channels a computer has claimed
    private final boolean[] intercepted = new boolean[MultiChannelCableBusBlock.CHANNEL_COUNT];

    private boolean publishing;

    // * Typed as Object so this block entity never names a Computer Craft type
    // * Null when Computer Craft is absent, which is a supported setup
    private final Object peripheral;

    public MultiChannelCableBusBlockEntity(final BlockPos pos, final BlockState state) {
        super(CableBlockEntities.MULTI_CHANNEL_CABLE_BUS.get(), pos, state);
        this.peripheral = ComputerCraftCompat.newCableBusPeripheral(this);
    }

    public Object getPeripheral() {
        return this.peripheral;
    }

    //#region // --- CHANNEL VALUES --- //
    public synchronized int getChannel(final int channel) {
        validateChannel(channel);
        return Byte.toUnsignedInt(this.outputs[channel - 1]);
    }

    // * What the channel actually emits
    // * An intercepted channel emits only what a computer set
    // * The incoming signal belongs to the script now
    public synchronized int getEffectiveChannel(final int channel) {
        validateChannel(channel);
        final int configured = Byte.toUnsignedInt(this.outputs[channel - 1]);
        if (this.intercepted[channel - 1]) {
            return configured;
        }

        return Math.max(configured, Byte.toUnsignedInt(this.inputs[channel - 1]));
    }

    public synchronized boolean isIntercepted(final int channel) {
        validateChannel(channel);
        return this.intercepted[channel - 1];
    }

    // * Returns the value now sitting on that channel, so a script can act at once
    public int setIntercepted(final int channel, final boolean intercept) {
        validateChannel(channel);

        synchronized (this) {
            if (this.intercepted[channel - 1] == intercept) {
                return getBridgedInput(channel);
            }

            this.intercepted[channel - 1] = intercept;
        }

        // * Taking or releasing a channel changes what it emits
        publish(channel, getEffectiveChannel(channel));
        markChangedAndSync();
        return getBridgedInput(channel);
    }

    public void setInterceptedAll(final boolean intercept) {
        for (int channel = 1; channel <= MultiChannelCableBusBlock.CHANNEL_COUNT; channel++) {
            setIntercepted(channel, intercept);
        }
    }

    public synchronized Map<Integer, Integer> getAllInputs() {
        final Map<Integer, Integer> result = new LinkedHashMap<>(MultiChannelCableBusBlock.CHANNEL_COUNT);
        for (int channel = 1; channel <= this.inputs.length; channel++) {
            result.put(channel, Byte.toUnsignedInt(this.inputs[channel - 1]));
        }

        return result;
    }

    public synchronized int getBridgedInput(final int channel) {
        validateChannel(channel);
        return Byte.toUnsignedInt(this.inputs[channel - 1]);
    }

    // * Called when a cable delivers a signal to this Bus on a sink channel
    public void setBridgedInput(final int channel, final int signal) {
        validateChannel(channel);
        final int clamped = clampPower(signal);

        synchronized (this) {
            if (Byte.toUnsignedInt(this.inputs[channel - 1]) == clamped) {
                return;
            }

            this.inputs[channel - 1] = (byte) clamped;
        }

        // * Pass through happens inside publish only when nobody has claimed it
        publish(channel, getEffectiveChannel(channel));
        markChangedAndSync();

        // * Raised whether or not the channel is intercepted
        ComputerCraftCompat.queueCableBusInput(this, channel, clamped, isIntercepted(channel));
    }

    public int setChannel(final int channel, final int power) {
        validateChannel(channel);
        final int clamped = clampPower(power);
        synchronized (this) {
            this.outputs[channel - 1] = (byte) clamped;
        }

        publish(channel, getEffectiveChannel(channel));
        markChangedAndSync();
        return clamped;
    }

    public int toggleChannel(final int channel) {
        return setChannel(channel, getChannel(channel) > 0 ? 0 : 15);
    }

    public int setAll(final int power) {
        final int clamped = clampPower(power);
        synchronized (this) {
            for (int i = 0; i < this.outputs.length; i++) {
                this.outputs[i] = (byte) clamped;
            }
        }

        publishAll();
        markChangedAndSync();
        return clamped;
    }

    public void clear() {
        setAll(0);
    }

    public synchronized Map<Integer, Integer> getAll() {
        final Map<Integer, Integer> result = new LinkedHashMap<>(MultiChannelCableBusBlock.CHANNEL_COUNT);
        for (int channel = 1; channel <= this.outputs.length; channel++) {
            result.put(channel, Byte.toUnsignedInt(this.outputs[channel - 1]));
        }

        return result;
    }
    //#endregion

    //#region // --- WHAT THE NETWORK ACTUALLY HAS --- //
    public Map<String, Object> getChannelStatus(final int channel) {
        validateChannel(channel);

        final Map<String, Object> status = new LinkedHashMap<>();
        status.put("channel", channel);
        status.put("configuredOutput", getChannel(channel));
        // * What a cable is feeding into this channel, if anything
        status.put("bridgedInput", getBridgedInput(channel));
        status.put("intercepted", isIntercepted(channel));
        status.put("effectiveOutput", getEffectiveChannel(channel));

        if (this.level == null) {
            status.put("publishedOutput", 0);
            status.put("connectionCount", 0);
            status.put("connections", List.of());
            status.put("error", "Cable Bus is not attached to a level");
            return status;
        }

        final CableNetworkManager manager = CableNetworkManager.get(this.level);
        final String channelName = Integer.toString(channel);
        final int publishedOutput = manager.getSourceSignals(this.worldPosition).getOrDefault(channelName, 0);
        final Set<CableNetworkSink> sinks = manager.getNetwork()
                .getOrDefault(this.worldPosition.asLong(), Map.of())
                .getOrDefault(channelName, Set.of());

        final List<Map<String, Object>> connections = new ArrayList<>(sinks.size());
        for (final CableNetworkSink sink : sinks) {
            final BlockPos sinkPos = BlockPos.of(sink.position());
            final Direction face = Direction.from3DDataValue(sink.direction());

            final Map<String, Object> connection = new LinkedHashMap<>();
            connection.put("x", sinkPos.getX());
            connection.put("y", sinkPos.getY());
            connection.put("z", sinkPos.getZ());
            connection.put("face", face.getName());
            connection.put("liveSinkSignal", manager.getSignalAt(sinkPos, face));
            // * Empty for a plain block face, a module name otherwise
            connection.put("sinkChannel", sink.sinkChannel());
            connections.add(connection);
        }

        status.put("publishedOutput", publishedOutput);
        status.put("connectionCount", connections.size());
        status.put("connections", connections);
        status.put("sourceX", this.worldPosition.getX());
        status.put("sourceY", this.worldPosition.getY());
        status.put("sourceZ", this.worldPosition.getZ());
        return status;
    }

    public int getConnectionCount(final int channel) {
        final Object count = getChannelStatus(channel).get("connectionCount");
        return count instanceof final Number number ? number.intValue() : 0;
    }
    //#endregion

    //#region // --- LIFECYCLE --- //
    // * The contributed version used onLoad, which 1.21.1 does not have
    // * clearRemoved is the hook that runs when the block entity joins a level
    @Override
    public void clearRemoved() {
        super.clearRemoved();

        if (this.level != null && !this.level.isClientSide()) {
            CableNetworkManager.get(this.level).queueForPublish(this.worldPosition);
        }
    }

    // * Called from the tick queue, once the level is actually running
    public void publishAllNow() {
        publishAll();
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide()) {
            synchronized (this) {
                for (int channel = 1; channel <= this.outputs.length; channel++) {
                    CableNetworkManager.trySetSignalAt(
                            this.level, this.worldPosition, Integer.toString(channel), 0);
                }
            }
        }

        super.setRemoved();
    }

    @Override
    protected synchronized void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByteArray(OUTPUTS_NBT_KEY, this.outputs.clone());
        tag.putByteArray(INPUTS_NBT_KEY, this.inputs.clone());

        final byte[] claimed = new byte[this.intercepted.length];
        for (int i = 0; i < this.intercepted.length; i++) {
            claimed[i] = (byte) (this.intercepted[i] ? 1 : 0);
        }
        tag.putByteArray(INTERCEPTED_NBT_KEY, claimed);
    }

    @Override
    protected synchronized void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        readChannelArray(tag.getByteArray(OUTPUTS_NBT_KEY), this.outputs);
        readChannelArray(tag.getByteArray(INPUTS_NBT_KEY), this.inputs);

        final byte[] claimed = tag.getByteArray(INTERCEPTED_NBT_KEY);
        for (int i = 0; i < this.intercepted.length; i++) {
            this.intercepted[i] = i < claimed.length && claimed[i] != 0;
        }
    }
    //#endregion

    private void publish(final int channel, final int power) {
        if (this.level == null || this.level.isClientSide() || this.publishing) {
            return;
        }

        this.publishing = true;
        try {
            CableNetworkManager.trySetSignalAt(
                    this.level, this.worldPosition, Integer.toString(channel), power);
        } finally {
            this.publishing = false;
        }
    }

    private void publishAll() {
        if (this.level == null || this.level.isClientSide() || this.publishing) {
            return;
        }

        this.publishing = true;
        try {
            publishAllChannels();
        } finally {
            this.publishing = false;
        }
    }

    private void publishAllChannels() {
        synchronized (this) {
            for (int channel = 1; channel <= this.outputs.length; channel++) {
                CableNetworkManager.trySetSignalAt(
                        this.level,
                        this.worldPosition,
                        Integer.toString(channel),
                        getEffectiveChannel(channel)
                );
            }
        }
    }

    private void markChangedAndSync() {
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            final BlockState state = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    // * Older saves may be shorter, and anything past the end is simply off
    private static void readChannelArray(final byte[] saved, final byte[] target) {
        final int copyLength = Math.min(saved.length, target.length);
        System.arraycopy(saved, 0, target, 0, copyLength);

        for (int i = copyLength; i < target.length; i++) {
            target[i] = 0;
        }

        for (int i = 0; i < target.length; i++) {
            target[i] = (byte) clampPower(Byte.toUnsignedInt(target[i]));
        }
    }

    public static void validateChannel(final int channel) {
        if (channel < 1 || channel > MultiChannelCableBusBlock.CHANNEL_COUNT) {
            throw new IllegalArgumentException(
                    "Channel must be between 1 and " + MultiChannelCableBusBlock.CHANNEL_COUNT);
        }
    }

    public static int clampPower(final int power) {
        return Math.max(0, Math.min(15, power));
    }
}