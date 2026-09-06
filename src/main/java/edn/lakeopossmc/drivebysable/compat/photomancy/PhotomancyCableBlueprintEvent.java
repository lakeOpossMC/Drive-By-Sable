package edn.lakeopossmc.drivebysable.compat.photomancy;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlacedBlock;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEvent;
import dev.rew1nd.sableschematicapi.compat.BlueprintRefTags;
import edn.lakeopossmc.drivebysable.CableBlocks;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;

// --- READS PHOTOMANCY BLUEPRINT CABLE DATA --- //
// * Photomancy only registers its own cable handling when drivebywire is loaded
// * This is our own event against the public blueprint API
public final class PhotomancyCableBlueprintEvent implements SableBlueprintEvent {
    private static final String LEGACY_EVENT_PATH = "drivebywire/wires";

    private static final String CONNECTIONS_KEY = "connections";
    private static final String SOURCE_REF_KEY = "source_ref";
    private static final String SINK_REF_KEY = "sink_ref";
    private static final String DIRECTION_KEY = "direction";
    private static final String CHANNEL_KEY = "channel";

    // * Keys on the snapshot we hand to the Drive
    private static final String SNAPSHOT_CONNECTIONS = "Connections";
    private static final String SNAPSHOT_SOURCE = "Source";
    private static final String SNAPSHOT_SINK = "Sink";
    private static final String SNAPSHOT_DIRECTION = "Direction";
    private static final String SNAPSHOT_CHANNEL = "Channel";
    private static final String SNAPSHOT_FACING = "Facing";
    private static final String SNAPSHOT_VERSION = "SnapshotVersion";

    // * Relative to the Drive
    private static final int RELATIVE_SNAPSHOT_VERSION = 2;
    private static final String NEUTRAL_FACING = "north";

    private final ResourceLocation id;

    public PhotomancyCableBlueprintEvent(final ResourceLocation id) {
        this.id = id;
    }

    public static ResourceLocation legacyEventId() {
        // * Namespaced by photomancy itself
        return ResourceLocation.fromNamespaceAndPath("sable_schematic_api", LEGACY_EVENT_PATH);
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public void onSaveAfterBlocks(final BlueprintSaveSession session, final CompoundTag data) {
        // * Nothing to write
    }

    @Override
    public void onPlaceAfterBlockEntities(final BlueprintPlaceSession session, final CompoundTag data) {
        if (!data.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return;
        }

        final ListTag connections = data.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND);
        if (connections.isEmpty()) {
            return;
        }

        final NetworkBackupDriveBlockEntity drive = findBackupDrive(session);

        // * No Drive means nowhere for this to land
        if (drive == null) {
            DriveBySableMod.LOGGER.warn(
                    "[drivebywire-migration] Photomancy blueprint carried {} cable link(s) but the paste "
                            + "contains no Network Backup Drive to hold them. They were not restored.",
                    connections.size()
            );
            return;
        }

        // * Never over the top of a save already sitting on the Drive
        if (drive.hasStoredSnapshot()) {
            DriveBySableMod.LOGGER.info(
                    "[drivebywire-migration] Drive at {} already holds a save, leaving the blueprint's "
                            + "{} cable link(s) alone.",
                    drive.getBlockPos(),
                    connections.size()
            );
            return;
        }

        final BlockPos drivePos = drive.getBlockPos();
        final ListTag converted = new ListTag();
        int unresolved = 0;

        for (final Tag entry : connections) {
            if (!(entry instanceof final CompoundTag connection)) {
                continue;
            }

            if (!connection.contains(DIRECTION_KEY, Tag.TAG_BYTE)
                    || !connection.contains(CHANNEL_KEY, Tag.TAG_STRING)) {
                unresolved++;
                continue;
            }

            final BlockPos sourcePos = mapRef(session, connection, SOURCE_REF_KEY);
            final BlockPos sinkPos = mapRef(session, connection, SINK_REF_KEY);

            // * An endpoint outside what was pasted
            if (sourcePos == null || sinkPos == null) {
                unresolved++;
                continue;
            }

            final CompoundTag stored = new CompoundTag();
            // * The Drive restores from offsets against itself
            stored.putLong(SNAPSHOT_SOURCE, sourcePos.subtract(drivePos).asLong());
            stored.putLong(SNAPSHOT_SINK, sinkPos.subtract(drivePos).asLong());
            stored.putByte(SNAPSHOT_DIRECTION, connection.getByte(DIRECTION_KEY));
            stored.putString(SNAPSHOT_CHANNEL, connection.getString(CHANNEL_KEY));
            converted.add(stored);
        }

        if (converted.isEmpty()) {
            DriveBySableMod.LOGGER.warn(
                    "[drivebywire-migration] Photomancy blueprint carried {} cable link(s), none of which "
                            + "resolved to a pasted block. Nothing was stored on the Drive at {}.",
                    connections.size(),
                    drivePos
            );
            return;
        }

        final CompoundTag snapshot = new CompoundTag();
        snapshot.put(SNAPSHOT_CONNECTIONS, converted);
        snapshot.putInt(SNAPSHOT_VERSION, RELATIVE_SNAPSHOT_VERSION);
        snapshot.putString(SNAPSHOT_FACING, NEUTRAL_FACING);

        drive.storeBoundedSnapshot(snapshot);
        drive.onLegacyPayloadAdopted();

        DriveBySableMod.LOGGER.info(
                "[drivebywire-migration] Stored {} of {} blueprint cable link(s) on the Drive at {}, "
                        + "awaiting a manual load. {} could not be resolved.",
                converted.size(),
                connections.size(),
                drivePos,
                unresolved
        );
    }

    @Nullable
    private static BlockPos mapRef(
            final BlueprintPlaceSession session,
            final CompoundTag connection,
            final String key
    ) {
        final Optional<BlueprintBlockRef> ref = BlueprintRefTags.read(connection, key);
        return ref.map(session::mapBlock).orElse(null);
    }

    // * The first Drive in the paste wins
    @Nullable
    private static NetworkBackupDriveBlockEntity findBackupDrive(final BlueprintPlaceSession session) {
        for (final BlueprintPlacedBlock placed : session.placedBlocks().blocks()) {
            if (!placed.state().is(CableBlocks.BACKUP_DRIVE.get())) {
                continue;
            }

            if (session.level().getBlockEntity(placed.storagePos())
                    instanceof final NetworkBackupDriveBlockEntity drive) {
                return drive;
            }
        }

        return null;
    }

}