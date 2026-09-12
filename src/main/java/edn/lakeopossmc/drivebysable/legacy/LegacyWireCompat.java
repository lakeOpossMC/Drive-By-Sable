package edn.lakeopossmc.drivebysable.legacy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

// --- READS DATA LEFT BEHIND BY DRIVE BY WIRE --- //
// * Every id, NBT key and quirk that DBW wrote lives here
public final class LegacyWireCompat {
    public static final String LEGACY_MOD_ID = "drivebywire";

    // * Registry paths DBW used
    public static final String LEGACY_BACKUP_BLOCK = "backup_block";
    public static final String LEGACY_CONTROLLER_HUB = "controller_hub";
    public static final String LEGACY_TWEAKED_CONTROLLER_HUB = "tweaked_controller_hub";
    public static final String LEGACY_WIRE = "wire";
    public static final String LEGACY_WIRE_CUTTER = "wire_cutter";

    // * Level saved data file DBW wrote its whole graph into
    public static final String LEGACY_SAVED_DATA_NAME = "drivebywire_network";

    // * Payload key on the old backup block
    public static final String LEGACY_BACKUP_PAYLOAD_KEY = "WireNetwork";

    // * Hub binding written onto lecterns and controller items
    public static final String LEGACY_HUB_KEY = "DriveByWireHub";

    // * Snapshot layout
    private static final String CONNECTIONS_KEY = "Connections";
    private static final String SOURCE_KEY = "Source";
    private static final String SINK_KEY = "Sink";
    private static final String FACING_KEY = "Facing";
    private static final String SNAPSHOT_VERSION_KEY = "SnapshotVersion";
    private static final String OWNER_SUB_LEVEL_KEY = "OwnerSubLevel";
    private static final String SOURCE_OWNER_KEY = "SourceOwnerSubLevel";
    private static final String SINK_OWNER_KEY = "SinkOwnerSubLevel";

    public static final String SOURCE_DRIVE_RELATIVE_KEY = "SourceDriveRelative";
    public static final String SINK_DRIVE_RELATIVE_KEY = "SinkDriveRelative";
    private static final int RELATIVE_SNAPSHOT_VERSION = 2;
    private static final int OWNER_AWARE_SNAPSHOT_VERSION = 3;

    // * The Backup Drive has no FACING property
    private static final String NEUTRAL_FACING = "north";

    private LegacyWireCompat() {
    }

    // * When DBW is still installed
    public static boolean legacyModPresent() {
        return ModList.get().isLoaded(LEGACY_MOD_ID);
    }

    //#region // --- BACKUP PAYLOAD ADOPTION --- //
    @Nullable
    public static CompoundTag readBackupPayload(final CompoundTag tag) {
        if (tag == null || !tag.contains(LEGACY_BACKUP_PAYLOAD_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        final CompoundTag payload = tag.getCompound(LEGACY_BACKUP_PAYLOAD_KEY).copy();
        if (payload.isEmpty() || countConnections(payload) == 0) {
            return null;
        }

        // * An old world may have used the third party Typewriter addon
        LegacyTypewriterCompat.translateChannels(payload);

        return normalizeFacing(payload);
    }

    public static boolean rebaseTemplatePayload(final CompoundTag payload, final BlockPos templatePos) {
        if (payload == null
                || templatePos == null
                || snapshotVersion(payload) < OWNER_AWARE_SNAPSHOT_VERSION
                || countConnections(payload) == 0) {
            return false;
        }

        boolean rebased = false;
        for (final Tag entry : payload.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection)) {
                continue;
            }

            rebased |= rebaseUnownedEndpoint(connection, SOURCE_KEY, SOURCE_OWNER_KEY, templatePos);
            rebased |= rebaseUnownedEndpoint(connection, SINK_KEY, SINK_OWNER_KEY, templatePos);
        }

        if (!rebased) {
            return false;
        }

        payload.putString(FACING_KEY, NEUTRAL_FACING);
        return true;
    }

    // * An end with an owner is already in its sublevel's own space
    private static boolean rebaseUnownedEndpoint(
            final CompoundTag connection,
            final String positionKey,
            final String ownerKey,
            final BlockPos templatePos
    ) {
        if (connection.hasUUID(ownerKey) || !connection.contains(positionKey, Tag.TAG_LONG)) {
            return false;
        }

        rebaseEndpoint(connection, positionKey, templatePos);
        connection.putBoolean(
                SOURCE_KEY.equals(positionKey) ? SOURCE_DRIVE_RELATIVE_KEY : SINK_DRIVE_RELATIVE_KEY, true);
        return true;
    }

    private static void rebaseEndpoint(final CompoundTag connection, final String key, final BlockPos base) {
        if (!connection.contains(key, Tag.TAG_LONG)) {
            return;
        }

        connection.putLong(key, BlockPos.of(connection.getLong(key)).subtract(base).asLong());
    }

    // * The payload as stored on a backup block
    @Nullable
    public static CompoundTag payloadIn(final CompoundTag blockEntityTag) {
        if (blockEntityTag == null || !blockEntityTag.contains(LEGACY_BACKUP_PAYLOAD_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        return blockEntityTag.getCompound(LEGACY_BACKUP_PAYLOAD_KEY);
    }

    public static boolean hasOwnerUuids(final CompoundTag payload) {
        if (payload.hasUUID(OWNER_SUB_LEVEL_KEY)) {
            return true;
        }

        for (final Tag entry : payload.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (entry instanceof final CompoundTag connection
                    && (connection.hasUUID(SOURCE_OWNER_KEY) || connection.hasUUID(SINK_OWNER_KEY))) {
                return true;
            }
        }

        return false;
    }

    public static CompoundTag normalizeFacing(final CompoundTag payload) {
        if (payload.getInt(SNAPSHOT_VERSION_KEY) >= OWNER_AWARE_SNAPSHOT_VERSION) {
            return payload;
        }

        if (payload.contains(FACING_KEY, Tag.TAG_STRING)
                && !NEUTRAL_FACING.equals(payload.getString(FACING_KEY))) {
            payload.putString(FACING_KEY, NEUTRAL_FACING);
        }

        return payload;
    }
    //#endregion

    //#region // --- HUB BINDINGS --- //
    // * Lecterns and controller items carried the bound hub under their own key
    @Nullable
    public static BlockPos readLegacyHub(final CompoundTag tag) {
        if (tag == null || !tag.contains(LEGACY_HUB_KEY, Tag.TAG_LONG)) {
            return null;
        }

        return BlockPos.of(tag.getLong(LEGACY_HUB_KEY));
    }
    //#endregion

    // * Which coordinate space the snapshot is written in
    public static int snapshotVersion(final CompoundTag payload) {
        return payload == null ? 0 : payload.getInt(SNAPSHOT_VERSION_KEY);
    }

    public static boolean isOwnerAware(final CompoundTag payload) {
        return snapshotVersion(payload) >= OWNER_AWARE_SNAPSHOT_VERSION;
    }

    public static int countConnections(final CompoundTag payload) {        if (payload == null || !payload.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
        return 0;
    }

        return payload.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND).size();
    }
}