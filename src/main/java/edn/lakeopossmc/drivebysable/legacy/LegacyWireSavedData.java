package edn.lakeopossmc.drivebysable.legacy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// --- READS THE OLD DRIVEBYWIRE_NETWORK FILE --- //
// * Holds the raw tag and nothing else
public final class LegacyWireSavedData extends SavedData {
    private CompoundTag payload = new CompoundTag();

    private LegacyWireSavedData() {
    }

    public static Factory<LegacyWireSavedData> factory() {
        return new Factory<>(LegacyWireSavedData::new, LegacyWireSavedData::load);
    }

    private static LegacyWireSavedData load(final CompoundTag tag, final HolderLookup.Provider registries) {
        final LegacyWireSavedData data = new LegacyWireSavedData();
        data.payload = tag.copy();
        return data;
    }

    // * Empty compound when the world never had DBW installed
    public static CompoundTag read(final ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(factory(), LegacyWireCompat.LEGACY_SAVED_DATA_NAME)
                .payload;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        return this.payload.copy();
    }
}
