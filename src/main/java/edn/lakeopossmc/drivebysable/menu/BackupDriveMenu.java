package edn.lakeopossmc.drivebysable.menu;

import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.CableMenus;
import edn.lakeopossmc.drivebysable.blocks.NetworkBackupDriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import edn.lakeopossmc.drivebysable.CableConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// --- BACKUP DRIVE MENU --- //
public class BackupDriveMenu extends AbstractContainerMenu {

    public static final int CABLE_SLOT = 0;

    // * A save can need more than one stack of Cables, so the slot is not held to the stack size
    public static final int MAX_CABLES = 1024;

    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;

    private final Container cableContainer;

    // * Only populated on the client
    private BlockPos syncedOffset = BlockPos.ZERO;
    private BlockPos syncedSize = new BlockPos(1, 1, 1);
    private int syncedRotation;

    private boolean syncedSaved;

    // * Cables a load would spend
    private int syncedCableCost;

    private final DataSlot savedSlot = DataSlot.standalone();
    private final DataSlot cableCostSlot = DataSlot.standalone();
    private final BlockPos drivePos;
    @Nullable
    private final NetworkBackupDriveBlockEntity drive;

    // * Client constructor
    public BackupDriveMenu(final int containerId, final Inventory playerInventory, final RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
        this.syncedOffset = buffer.readBlockPos();
        this.syncedSize = buffer.readBlockPos();
        this.syncedRotation = buffer.readVarInt();
        this.syncedSaved = buffer.readBoolean();
        this.syncedCableCost = buffer.readVarInt();
        this.savedSlot.set(this.syncedSaved ? 1 : 0);
        this.cableCostSlot.set(this.syncedCableCost);
    }

    public static void writeRegion(final RegistryFriendlyByteBuf buffer, final NetworkBackupDriveBlockEntity drive) {

        buffer.writeBlockPos(drive.getRegionOffset());
        buffer.writeBlockPos(drive.getRegionSize());
        buffer.writeVarInt(drive.getRegionRotation());
        buffer.writeBoolean(drive.hasStoredSnapshot());
        buffer.writeVarInt(drive.getPendingConnectionCount());
    }

    public BackupDriveMenu(final int containerId, final Inventory playerInventory, final BlockPos drivePos) {
        super(CableMenus.BACKUP_DRIVE.get(), containerId);
        this.drivePos = drivePos;

        final Level level = playerInventory.player.level();
        this.drive = level.getBlockEntity(drivePos) instanceof final NetworkBackupDriveBlockEntity backupDrive
                ? backupDrive
                : null;

        this.cableContainer = new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() {
                return MAX_CABLES;
            }

            @Override
            public int getMaxStackSize(final ItemStack stack) {
                return MAX_CABLES;
            }
        };

        final Player owner = playerInventory.player;

        addSlot(new Slot(this.cableContainer, 0, 178, 61) {
            @Override
            public boolean mayPlace(final ItemStack stack) {
                return isActive() && stack.is(CableItems.CABLE.get());
            }

            // * Hidden when a load would not charge anything
            @Override
            public boolean isActive() {
                return cablesRequired(owner);
            }

            @Override
            public int getMaxStackSize() {
                return MAX_CABLES;
            }

            @Override
            public int getMaxStackSize(final ItemStack stack) {
                return MAX_CABLES;
            }

            // * Taken out one normal stack at a time, so an oversized stack never leaves the slot
            @Override
            public Optional<ItemStack> tryRemove(final int count, final int decrement, final Player player) {
                return super.tryRemove(Math.min(count, getItem().getMaxStackSize()), decrement, player);
            }
        });

        addPlayerSlots(playerInventory);

        if (this.drive != null) {
            this.syncedOffset = this.drive.getRegionOffset();
            this.syncedSize = this.drive.getRegionSize();
            this.syncedRotation = this.drive.getRegionRotation();
            this.syncedSaved = this.drive.hasStoredSnapshot();
            this.syncedCableCost = this.drive.getPendingConnectionCount();
        }

        addDataSlot(this.savedSlot);
        addDataSlot(this.cableCostSlot);
        if (this.drive != null) {
            this.savedSlot.set(this.drive.hasStoredSnapshot() ? 1 : 0);
            this.cableCostSlot.set(this.drive.getPendingConnectionCount());
        }

    }

    private void addPlayerSlots(final Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 29 + column * 18, 142 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 29 + column * 18, 200));
        }
    }

    public BlockPos getDrivePos() {
        return drivePos;
    }

    @Override
    public void broadcastChanges() {
        if (this.drive != null && !this.drive.isRemoved()) {
            this.savedSlot.set(this.drive.hasStoredSnapshot() ? 1 : 0);
            this.cableCostSlot.set(this.drive.getPendingConnectionCount());
        }
        super.broadcastChanges();
    }

    public BlockPos getRegionOffset() {
        return syncedOffset;
    }

    public BlockPos getRegionSize() {
        return syncedSize;
    }

    public int getRegionRotation() {
        return syncedRotation;
    }

    // * Read on the client from the open packet
    public int getCableCost() {
        return cableCostSlot.get();
    }

    public boolean isSaved() {
        return savedSlot.get() != 0;
    }

    @Nullable
    public NetworkBackupDriveBlockEntity getDrive() {
        return drive;
    }

    public void consumeCables(final int amount) {
        if (amount <= 0) {
            return;
        }

        final ItemStack cables = this.cableContainer.getItem(0);
        cables.shrink(amount);
        if (cables.isEmpty()) {
            this.cableContainer.setItem(0, ItemStack.EMPTY);
        }

        this.cableContainer.setChanged();
        broadcastChanges();
    }

    // * Whether this player would actually pay for a load
    public static boolean cablesRequired(final Player player) {
        return !player.hasInfiniteMaterials() && CableConfig.CONFIG.shouldConsumeCables.get();
    }

    public ItemStack getCableStack() {
        return cableContainer.getItem(0);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();

        if (index == CABLE_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, CABLE_SLOT, CABLE_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    // * A hotbar swap would move the whole oversized stack into the player's inventory
    @Override
    public void clicked(final int slotId, final int button, final ClickType clickType, final Player player) {
        if (clickType == ClickType.SWAP && slotId == CABLE_SLOT) {
            final ItemStack cables = getCableStack();
            if (cables.getCount() > cables.getMaxStackSize()) {
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    // * Give the cables back, split into normal stacks
    @Override
    public void removed(final Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            final ItemStack cables = cableContainer.removeItemNoUpdate(0);
            final List<ItemStack> stacks = new ArrayList<>();
            while (!cables.isEmpty()) {
                stacks.add(cables.split(cables.getMaxStackSize()));
            }
            clearContainer(player, new SimpleContainer(stacks.toArray(new ItemStack[0])));
        }
    }

    @Override
    public boolean stillValid(final Player player) {
        return player.level().getBlockEntity(drivePos) instanceof NetworkBackupDriveBlockEntity;
    }
}