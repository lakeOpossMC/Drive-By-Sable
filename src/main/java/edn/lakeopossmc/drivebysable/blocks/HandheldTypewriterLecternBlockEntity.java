package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.redstone.link.controller.LecternControllerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.client.HandheldTypewriterClientHandler;
import edn.lakeopossmc.drivebysable.compat.HandheldTypewriterServerHandler;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;
import java.util.UUID;

// --- A LECTERN HOLDING A HANDHELD TYPEWRITER CONTROLLER --- //
public class HandheldTypewriterLecternBlockEntity extends SmartBlockEntity {
    private static final String CONTROLLER_KEY = "Controller";
    private static final String USER_KEY = "User";
    private static final String USING_LECTERN_KEY = "IsUsingLecternController";

    private ItemStack controller = ItemStack.EMPTY;
    private UUID user;
    // * Client only
    private UUID prevUser;
    // * Server only
    private boolean deactivatedThisTick;

    public HandheldTypewriterLecternBlockEntity(final BlockPos pos, final BlockState state) {
        super(CableBlockEntities.HANDHELD_TYPEWRITER_LECTERN.get(), pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
    }

    //#region // --- SAVE AND SYNC --- //
    @Override
    protected void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (!this.controller.isEmpty()) {
            compound.put(CONTROLLER_KEY, this.controller.save(registries));
        }
        if (this.user != null) {
            compound.putUUID(USER_KEY, this.user);
        }
    }

    @Override
    public void writeSafe(final CompoundTag compound, final HolderLookup.Provider registries) {
        super.writeSafe(compound, registries);
        if (!this.controller.isEmpty()) {
            compound.put(CONTROLLER_KEY, this.controller.save(registries));
        }
    }

    @Override
    protected void read(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        this.controller = compound.contains(CONTROLLER_KEY)
                ? ItemStack.parseOptional(registries, compound.getCompound(CONTROLLER_KEY))
                : ItemStack.EMPTY;
        this.user = compound.hasUUID(USER_KEY) ? compound.getUUID(USER_KEY) : null;
    }
    //#endregion

    //#region // --- USER HANDSHAKE --- //
    public ItemStack getController() {
        return this.controller.copy();
    }

    public boolean hasUser() {
        return this.user != null;
    }

    public boolean isUsedBy(final Player player) {
        return this.hasUser() && this.user.equals(player.getUUID());
    }

    public void tryStartUsing(final Player player) {
        if (!this.deactivatedThisTick && !this.hasUser()
                && !LecternControllerBlockEntity.playerIsUsingLectern(player)
                && LecternControllerBlockEntity.playerInRange(player, this.level, this.worldPosition)) {
            this.startUsing(player);
        }
    }

    public void tryStopUsing(final Player player) {
        if (this.isUsedBy(player)) {
            this.stopUsing(player);
        }
    }

    private void startUsing(final Player player) {
        this.user = player.getUUID();
        player.getPersistentData().putBoolean(USING_LECTERN_KEY, true);
        this.sendData();
    }

    // * Drops every channel the lectern or its bound hub was holding
    private void stopUsing(final Player player) {
        this.user = null;
        if (player != null) {
            player.getPersistentData().remove(USING_LECTERN_KEY);
        }
        this.deactivatedThisTick = true;

        if (this.level != null && !this.level.isClientSide) {
            HandheldTypewriterServerHandler.reset(this.level, this.worldPosition);
            HubItem.getHubPos(this.controller).ifPresent(hub -> HandheldTypewriterServerHandler.reset(this.level, hub));
        }
        this.sendData();
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level.isClientSide) {
            // * Only a plain static call here, the client handler owns every client type
            if (FMLEnvironment.dist.isClient()) {
                HandheldTypewriterClientHandler.onLecternTick(this.worldPosition, this.user, this.prevUser);
            }
            this.prevUser = this.user;
            return;
        }

        this.deactivatedThisTick = false;
        if (!(this.level instanceof final ServerLevel serverLevel) || this.user == null) {
            return;
        }

        final Entity entity = serverLevel.getEntity(this.user);
        if (!(entity instanceof final Player player)) {
            this.stopUsing(null);
            return;
        }

        if (!LecternControllerBlockEntity.playerInRange(player, this.level, this.worldPosition)
                || !LecternControllerBlockEntity.playerIsUsingLectern(player)) {
            this.stopUsing(player);
        }
    }
    //#endregion

    //#region // --- CONTROLLER IN AND OUT --- //
    public void setController(final ItemStack newController) {
        if (newController != null) {
            this.controller = newController.copyWithCount(1);
            AllSoundEvents.CONTROLLER_PUT.playOnServer(this.level, this.worldPosition);
            this.setChanged();
            this.sendData();
        }
    }

    public void swapControllers(final ItemStack stack, final Player player, final InteractionHand hand, final BlockState state) {
        final ItemStack newController = stack.copy();
        stack.setCount(0);
        if (player.getItemInHand(hand).isEmpty()) {
            player.setItemInHand(hand, this.getController());
        } else {
            this.dropController(state);
        }
        this.setController(newController);
    }

    public void dropController(final BlockState state) {
        if (this.level instanceof final ServerLevel serverLevel && this.user != null
                && serverLevel.getEntity(this.user) instanceof final Player player) {
            this.stopUsing(player);
        }

        if (this.controller.isEmpty() || this.level == null) {
            return;
        }

        final Direction dir = state.getValue(HandheldTypewriterLecternBlock.FACING);
        final double x = this.worldPosition.getX() + 0.5 + 0.25 * dir.getStepX();
        final double y = this.worldPosition.getY() + 1;
        final double z = this.worldPosition.getZ() + 0.5 + 0.25 * dir.getStepZ();
        final ItemEntity itemEntity = new ItemEntity(this.level, x, y, z, this.getController());
        itemEntity.setDefaultPickUpDelay();
        this.level.addFreshEntity(itemEntity);
        this.controller = ItemStack.EMPTY;
    }
    //#endregion
}