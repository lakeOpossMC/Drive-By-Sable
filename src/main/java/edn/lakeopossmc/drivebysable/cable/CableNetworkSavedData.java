package edn.lakeopossmc.drivebysable.cable;

import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.legacy.LegacyWireCompat;
import edn.lakeopossmc.drivebysable.legacy.LegacyWireSavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// --- PERSISTS CABLE NETWORK PER LEVEL --- //
public final class CableNetworkSavedData extends SavedData {
    private static final String DATA_NAME = "drivebysable_network";

    // * Marks a level as already checked
    private static final String LEGACY_IMPORTED_KEY = "LegacyWireImported";

    private final CableNetworkManager manager;
    private boolean legacyImported;

    private CableNetworkSavedData() {
        this.manager = new CableNetworkManager(this::setDirty);
    }

    public static Factory<CableNetworkSavedData> factory() {
        return new Factory<>(CableNetworkSavedData::new, CableNetworkSavedData::load);
    }

    // * Fetch or create then bind to this level
    public static CableNetworkManager get(final ServerLevel level) {
        final CableNetworkSavedData data = level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
        data.importLegacyNetwork(level);
        data.manager.attachLevel(level);
        return data.manager;
    }

    private static CableNetworkSavedData load(final CompoundTag tag, final HolderLookup.Provider registries) {
        final CableNetworkSavedData data = new CableNetworkSavedData();
        data.manager.load(tag);
        data.legacyImported = tag.getBoolean(LEGACY_IMPORTED_KEY);
        return data;
    }

    //#region // --- DRIVE BY WIRE IMPORT --- //
    // * DBW wrote the whole graph into its own file under a different name
    private void importLegacyNetwork(final ServerLevel level) {
        if (this.legacyImported) {
            return;
        }

        // * Claim it before doing anything
        this.legacyImported = true;
        setDirty();

        // * If DBW is still installed
        if (LegacyWireCompat.legacyModPresent()) {
            DriveBySableMod.LOGGER.info(
                    "[drivebywire-migration] Drive-By-Wire is installed, leaving its network in {} alone.",
                    level.dimension().location()
            );
            return;
        }

        final CompoundTag legacy = LegacyWireSavedData.read(level);
        final int inherited = LegacyWireCompat.countConnections(legacy);
        if (inherited == 0) {
            return;
        }

        // * Anything the player already built in this level wins on collision
        final int adopted = this.manager.mergeSavedConnections(level, legacy);

        DriveBySableMod.LOGGER.info(
                "[drivebywire-migration] Imported {} of {} Drive-By-Wire connections into {}.",
                adopted,
                inherited,
                level.dimension().location()
        );
    }
    //#endregion

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        final CompoundTag saved = this.manager.save(tag);
        saved.putBoolean(LEGACY_IMPORTED_KEY, this.legacyImported);
        return saved;
    }
}