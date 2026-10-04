package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.IntConsumer;

// --- REDSTONE LINK RECEIVER OF A LINKED GEARBOX --- //
public class LinkedGearboxPowerLink extends LinkBehaviour {

    public static final BehaviourType<LinkedGearboxPowerLink> TYPE = new BehaviourType<>();

    private static final String FIRST_KEY = "FrequencyFirst";
    private static final String LAST_KEY = "FrequencyLast";
    private static final String SAVED_FIRST_KEY = "PowerFrequencyFirst";
    private static final String SAVED_LAST_KEY = "PowerFrequencyLast";

    private final ValueBoxTransform first;
    private final ValueBoxTransform second;
    private final IntConsumer signalCallback;

    public LinkedGearboxPowerLink(final SmartBlockEntity be, final Pair<ValueBoxTransform, ValueBoxTransform> slots,
                                  final IntConsumer signalCallback) {
        super(be, slots);
        this.first = slots.getLeft();
        this.second = slots.getRight();
        this.signalCallback = signalCallback;
    }

    public ValueBoxTransform slot(final boolean first) {
        return first ? this.first : this.second;
    }

    public ItemStack stack(final boolean first) {
        return getNetworkKey().get(first).getStack();
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean isListening() {
        return true;
    }

    @Override
    public int getTransmittedStrength() {
        return 0;
    }

    @Override
    public void setReceivedStrength(final int networkPower) {
        if (!newPosition) {
            return;
        }
        signalCallback.accept(networkPower);
    }

    @Override
    public void write(final CompoundTag nbt, final HolderLookup.Provider registries, final boolean clientPacket) {
        final CompoundTag own = new CompoundTag();
        super.write(own, registries, clientPacket);
        for (final String key : own.getAllKeys()) {
            if (FIRST_KEY.equals(key)) {
                nbt.put(SAVED_FIRST_KEY, own.get(key));
            } else if (LAST_KEY.equals(key)) {
                nbt.put(SAVED_LAST_KEY, own.get(key));
            } else if (!nbt.contains(key)) {
                nbt.put(key, own.get(key));
            }
        }
    }

    @Override
    public void read(final CompoundTag nbt, final HolderLookup.Provider registries, final boolean clientPacket) {
        final CompoundTag own = nbt.copy();
        own.put(FIRST_KEY, nbt.getCompound(SAVED_FIRST_KEY));
        own.put(LAST_KEY, nbt.getCompound(SAVED_LAST_KEY));
        super.read(own, registries, clientPacket);
    }
}