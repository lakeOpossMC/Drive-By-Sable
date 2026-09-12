package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.api.schematic.nbt.PartialSafeNBT;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.legacy.LegacyWireCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.client.BackupDriveGoggleClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.createmod.catnip.lang.LangBuilder;

// --- BLOCK ENTITY FOR PRESERVER --- //
// * This block entity stores information for schematics
public class NetworkBackupDriveBlockEntity extends BlockEntity implements PartialSafeNBT, IHaveGoggleInformation {

    // * The region capture the player confirmed in the screen
    @Nullable
    private CompoundTag boundedSnapshot;


    // * The region the player set up in the screen
    private BlockPos regionOffset = BlockPos.ZERO;
    private BlockPos regionSize = DEFAULT_REGION_SIZE;
    private int regionRotation;

    // --- KEY/SYMBOL SETUP --- //
    private static final String CABLE_NETWORK_KEY = "CableNetwork";
    private static final String BOUNDED_SNAPSHOT_KEY = "BoundedSnapshot";

    private static final int SOURCE_COLOR = 0x7FCDE0;
    private static final int OUTPUT_COLOR = 0xDDC166;
    private static final int CABLE_COST_COLOR = 0xFF4444;

    private static final String GOGGLES = "goggles.";

    // * Matches CableNetworkManager
    private static final String CONNECTIONS_TAG = "Connections";
    private static final String REGION_OFFSET_KEY = "RegionOffset";
    private static final String REGION_SIZE_KEY = "RegionSize";
    private static final String REGION_ROTATION_KEY = "RegionRotation";

    private static final BlockPos DEFAULT_REGION_SIZE = new BlockPos(1, 1, 1);
    private static final int RESTORE_RETRY_INTERVAL = 20;

    // --- GET POS AND STATE --- //
    public NetworkBackupDriveBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(CableBlockEntities.BACKUP_DRIVE.get(), pos, blockState);
    }

    //#region // --- HANDLE CONNECTION SAVES IN SCHEMATICS --- //
    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        final SubLevelSchematicSerializationContext context = SubLevelSchematicSerializationContext.getCurrentContext();

        writeRegionData(tag, context);
    }
    //#endregion

    // * Region settings
    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readRegionData(tag);



        // * Absent on drive placed before this existed, so fall back to defaults
        this.regionOffset = tag.contains(REGION_OFFSET_KEY)
                ? BlockPos.of(tag.getLong(REGION_OFFSET_KEY))
                : BlockPos.ZERO;
        this.regionSize = tag.contains(REGION_SIZE_KEY)
                ? BlockPos.of(tag.getLong(REGION_SIZE_KEY))
                : DEFAULT_REGION_SIZE;
        this.regionRotation = tag.getInt(REGION_ROTATION_KEY);

        //#region // --- LEGACY PAYLOAD BECOMES SAVE --- //
        if (this.boundedSnapshot == null && tag.contains(CABLE_NETWORK_KEY, Tag.TAG_COMPOUND)) {
            final CompoundTag legacy = tag.getCompound(CABLE_NETWORK_KEY).copy();

            if (!legacy.isEmpty() && CableNetworkManager.countConnectionsInBackupSnapshot(legacy) > 0) {
                this.boundedSnapshot = legacy;

                DriveBySableMod.LOGGER.info(
                        "[schematic-debug] Adopted a legacy payload at {} as saved data: {} connections.",
                        this.worldPosition,
                        CableNetworkManager.countConnectionsInBackupSnapshot(legacy)
                );
            }
        }
        //#endregion

        //#region // --- DRIVE BY WIRE PAYLOAD BECOMES SAVE --- //
        // * An old Backup Block
        if (this.boundedSnapshot == null) {
            final CompoundTag inherited = LegacyWireCompat.readBackupPayload(tag);

            if (inherited != null) {
                this.boundedSnapshot = inherited;
                onLegacyPayloadAdopted();

                DriveBySableMod.LOGGER.info(
                        "[drivebywire-migration] Adopted a Drive-By-Wire payload at {} as saved data: {} connections.",
                        this.worldPosition,
                        LegacyWireCompat.countConnections(inherited)
                );
            }
        }
        //#endregion

        //#region // --- A PAYLOAD WITH NO REGION TO GO WITH IT --- //
        if (this.boundedSnapshot != null
                && this.regionOffset.equals(BlockPos.ZERO)
                && this.regionSize.equals(DEFAULT_REGION_SIZE)
                && CableNetworkManager.countConnectionsInBackupSnapshot(this.boundedSnapshot) > 0) {
            this.regionFitPending = true;
        }
        //#endregion

        // * If setLevel already ran, this is the point the snapshot becomes known
        queueForBindingIfNeeded();
        tryBindWorldSpaceSnapshot();
    }

    // * Fit the region around what we inherited
    private boolean regionFitPending;

    // * Set while this Drive is holding an inherited payload
    private boolean legacyPayloadAwaitingLoad;

    // * Called once a Drive-By-Wire payload lands on this Drive
    public void onLegacyPayloadAdopted() {
        this.legacyPayloadAwaitingLoad = true;
        this.regionFitPending = true;
        registerLegacyPayload();
        tryFitLegacyRegion();
    }

    // * Tells the network a payload is sitting here unloaded
    private void registerLegacyPayload() {
        if (!this.legacyPayloadAwaitingLoad || this.boundedSnapshot == null) {
            return;
        }

        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        CableNetworkManager.get(this.level).recordLegacyPayloadAwaitingLoad(
                this.worldPosition,
                CableNetworkManager.countConnectionsInBackupSnapshot(this.boundedSnapshot)
        );
    }

    private void queueForBindingIfNeeded() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        if (CableNetworkManager.isPastedCopy(this.boundedSnapshot, this.worldPosition)) {
            CableNetworkManager.get(this.level).queueForBinding(this.worldPosition);
        }
    }

    public void tryBindWorldSpaceSnapshot() {
        if (this.boundedSnapshot == null || this.level == null || this.level.isClientSide()) {
            return;
        }

        // * Only a pasted copy gets pinned
        if (!CableNetworkManager.isPastedCopy(this.boundedSnapshot, this.worldPosition)) {
            return;
        }

        final CompoundTag bound = CableNetworkManager.get(this.level)
                .bindWorldSpaceSnapshot(this.level, this.worldPosition, this.boundedSnapshot);

        // * Not everything has been placed yet, so try again on the next read
        if (bound == null) {
            return;
        }

        this.boundedSnapshot = bound;
        CableNetworkManager.get(this.level).stopWaitingToBind(this.worldPosition);

        DriveBySableMod.LOGGER.info(
                "[drivebywire-migration] Pinned a cross-level snapshot at {} to the sublevels it landed on.",
                this.worldPosition
        );

        setChanged();
        syncToClients();
    }

    public void tryFitLegacyRegion() {
        if (!this.regionFitPending || this.boundedSnapshot == null) {
            return;
        }

        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        // * The player has set a region of their own, leave it be
        if (!this.regionOffset.equals(BlockPos.ZERO) || !this.regionSize.equals(DEFAULT_REGION_SIZE)) {
            this.regionFitPending = false;
            return;
        }

        final CableNetworkManager.SnapshotRegion region = CableNetworkManager.get(this.level)
                .deriveSnapshotRegion(this.level, this.worldPosition, getFacing(), this.boundedSnapshot);

        // * Nothing resolved yet, stay pending and try on the next read
        if (region == null) {
            return;
        }

        this.regionOffset = region.offset();
        this.regionSize = region.size();
        this.regionRotation = 0;
        this.regionFitPending = false;

        DriveBySableMod.LOGGER.info(
                "[drivebywire-migration] Fitted region offset {} size {} around the adopted payload at {}.",
                this.regionOffset,
                this.regionSize,
                this.worldPosition
        );

        setChanged();
        syncToClients();
    }

    @Override
    public void setLevel(final Level level) {
        super.setLevel(level);
        registerLegacyPayload();

        queueForBindingIfNeeded();
        tryBindWorldSpaceSnapshot();
        tryFitLegacyRegion();
    }

    // * The player's region and whatever they saved into it
    private void readRegionData(final CompoundTag tag) {
        if (tag == null) {
            return;
        }

        this.boundedSnapshot = tag.contains(BOUNDED_SNAPSHOT_KEY, Tag.TAG_COMPOUND)
                ? tag.getCompound(BOUNDED_SNAPSHOT_KEY).copy()
                : null;

        if (tag.contains(REGION_OFFSET_KEY)) {
            this.regionOffset = BlockPos.of(tag.getLong(REGION_OFFSET_KEY));
        }
        if (tag.contains(REGION_SIZE_KEY)) {
            this.regionSize = BlockPos.of(tag.getLong(REGION_SIZE_KEY));
        }
        this.regionRotation = Math.floorMod(tag.getInt(REGION_ROTATION_KEY), 4);
    }

    private void writeRegionData(final CompoundTag tag, final SubLevelSchematicSerializationContext context) {
        tag.putLong(REGION_OFFSET_KEY, this.regionOffset.asLong());
        tag.putLong(REGION_SIZE_KEY, this.regionSize.asLong());
        tag.putInt(REGION_ROTATION_KEY, this.regionRotation);

        final boolean placing = context != null
                && context.getType() == SubLevelSchematicSerializationContext.Type.PLACE;
        final CompoundTag boundedToWrite;

        if (this.boundedSnapshot == null) {
            boundedToWrite = new CompoundTag();
        } else if (placing) {
            boundedToWrite = CableNetworkManager.transformBackupSnapshotForPlacement(
                    this.boundedSnapshot.copy(), this.worldPosition, context);
        } else {
            boundedToWrite = this.boundedSnapshot.copy();
        }

        if (!boundedToWrite.isEmpty()) {
            tag.put(BOUNDED_SNAPSHOT_KEY, boundedToWrite);
        }

    }

    //#region // --- GOGGLE TOOLTIP --- //
    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        final boolean locked = hasStoredSnapshot();

        lang(GOGGLES + "title")
                .style(ChatFormatting.WHITE)
                .add(lang(GOGGLES + (locked ? "locked" : "idle")).style(ChatFormatting.GREEN))
                .forGoggles(tooltip);

        if (locked) {
            appendStoredInfo(tooltip);
            return true;
        }

        BackupDriveGoggleClient.appendRegionInfo(
                this.worldPosition,
                this.regionOffset,
                this.regionSize,
                this.regionRotation,
                tooltip
        );
        return true;
    }

    // * What the drive is holding
    private void appendStoredInfo(final List<Component> tooltip) {
        final int sources = CableNetworkManager.countStoredSources(this.boundedSnapshot);
        final int outputs = CableNetworkManager.countConnectionsInBackupSnapshot(this.boundedSnapshot);

        lang(GOGGLES + "sources", number(sources, SOURCE_COLOR)).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        lang(GOGGLES + "outputs", number(outputs, OUTPUT_COLOR)).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);

        if (BackupDriveGoggleClient.showsCableCost()) {
            lang(GOGGLES + "cost", number(outputs, CABLE_COST_COLOR)).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
    }

    private static LangBuilder lang(final String key, final Object... args) {
        return new LangBuilder(DriveBySableMod.MOD_ID).translate(key, args);
    }

    private static Component number(final int amount, final int color) {
        return Component.literal(String.valueOf(amount)).withStyle(style -> style.withColor(color));
    }
    //#endregion

    //#region // --- SCHEMATIC SAFE NBT --- //
    // * For schematicannon so NBT is written to pasted drive
    @Override
    public void writeSafe(final CompoundTag tag, final HolderLookup.Provider registries) {
        writeRegionData(tag, null);
    }
    //#endregion

    //#region // --- CLIENT SYNC --- //
    // * The saved region has to reach the client
    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();

        writeRegionData(tag, null);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // * Pushed whenever the saved data changes
    private void syncToClients() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    //#endregion

    //#region // --- BOUNDED SAVE --- //
    public void writeToItem(final ItemStack stack, final HolderLookup.Provider registries) {
        if (this.boundedSnapshot == null) {
            return;
        }

        saveToItem(stack, registries);
    }

    // * One cable per stored connection
    public int getPendingConnectionCount() {
        tryBindWorldSpaceSnapshot();
        tryFitLegacyRegion();

        if (this.boundedSnapshot == null) {
            return 0;
        }
        if (this.level == null) {
            return getStoredConnectionCount();
        }

        return CableNetworkManager.get(this.level)
                .countPendingConnections(this.level, this.worldPosition, getFacing(), this.boundedSnapshot);
    }

    public int getStoredConnectionCount() {
        if (this.boundedSnapshot == null || !this.boundedSnapshot.contains(CONNECTIONS_TAG, Tag.TAG_LIST)) {
            return 0;
        }
        return this.boundedSnapshot.getList(CONNECTIONS_TAG, Tag.TAG_COMPOUND).size();
    }

    public boolean hasStoredSnapshot() {
        return this.boundedSnapshot != null;
    }

    // * Throws away the save and puts the region back to defaults
    public void clearStoredSnapshot() {
        this.boundedSnapshot = null;
        this.regionOffset = BlockPos.ZERO;
        this.regionSize = DEFAULT_REGION_SIZE;
        this.regionRotation = 0;

        // * Loaded, so it is no longer waiting
        this.legacyPayloadAwaitingLoad = false;
        this.regionFitPending = false;
        if (this.level != null && !this.level.isClientSide()) {
            CableNetworkManager.get(this.level).forgetLegacyPayloadAwaitingLoad(this.worldPosition);
        }
        setChanged();
        syncToClients();
    }

    public void storeBoundedSnapshot(final CompoundTag snapshot) {
        this.boundedSnapshot = snapshot.isEmpty() ? null : snapshot.copy();
        setChanged();
        syncToClients();
    }

    @Nullable
    public CompoundTag getBoundedSnapshot() {
        return this.boundedSnapshot;
    }

    public Direction getSavedFacing() {
        return getFacing();
    }

    public BlockPos getRegionOffset() {
        return this.regionOffset;
    }

    public BlockPos getRegionSize() {
        return this.regionSize;
    }

    public int getRegionRotation() {
        return this.regionRotation;
    }

    public void setRegion(final BlockPos offset, final BlockPos size, final int rotation) {
        this.regionOffset = offset.immutable();
        this.regionSize = size.immutable();
        this.regionRotation = Math.floorMod(rotation, 4);
        setChanged();
        syncToClients();
    }
    //#endregion

    // --- LEFTOVER FROM DRIVEBYWIRE --- //
    // * Probably useless, since the blockstates have been removed from the main block for simplicity
    private Direction getFacing() {
        final BlockState blockState = this.getBlockState();
        return blockState.hasProperty(HorizontalDirectionalBlock.FACING)
                ? blockState.getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
    }
}