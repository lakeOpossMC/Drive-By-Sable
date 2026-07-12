package edn.lakeopossmc.drivebysable.cable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// --- PERSISTS CABLE NETWORK PER LEVEL --- //
public final class CableNetworkSavedData extends SavedData {
    private static final String DATA_NAME = "drivebysable_network";

    private final CableNetworkManager manager;

    private CableNetworkSavedData() {
        this.manager = new CableNetworkManager(this::setDirty);
    }

    public static Factory<CableNetworkSavedData> factory() {
        return new Factory<>(CableNetworkSavedData::new, CableNetworkSavedData::load);
    }

    // * Fetch or create then bind to this level
    public static CableNetworkManager get(final ServerLevel level) {
        final CableNetworkSavedData data = level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
        data.manager.attachLevel(level);
        return data.manager;
    }

    private static CableNetworkSavedData load(final CompoundTag tag, final HolderLookup.Provider registries) {
        final CableNetworkSavedData data = new CableNetworkSavedData();
        data.manager.load(tag);
        return data;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        return manager.save(tag);
    }
}
