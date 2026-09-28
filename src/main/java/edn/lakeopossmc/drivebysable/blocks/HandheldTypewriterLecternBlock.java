package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.redstone.link.controller.LecternControllerBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.block.IBE;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.CableItems;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.compat.CableTypewriterHubServerHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

// --- LECTERN WITH A HANDHELD TYPEWRITER CONTROLLER --- //
public class HandheldTypewriterLecternBlock extends LecternBlock
        implements IBE<HandheldTypewriterLecternBlockEntity>, MultiChannelCableSource, SpecialBlockItemRequirement {

    public HandheldTypewriterLecternBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(HAS_BOOK, true));
    }

    @Override
    public Class<HandheldTypewriterLecternBlockEntity> getBlockEntityClass() {
        return HandheldTypewriterLecternBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HandheldTypewriterLecternBlockEntity> getBlockEntityType() {
        return CableBlockEntities.HANDHELD_TYPEWRITER_LECTERN.get();
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return IBE.super.newBlockEntity(pos, state);
    }

    //#region // --- USE AND RETRIEVE --- //
    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hitResult) {
        if (!player.isShiftKeyDown() && LecternControllerBlockEntity.playerInRange(player, level, pos)) {
            if (!level.isClientSide) {
                this.withBlockEntityDo(level, pos, be -> be.tryStartUsing(player));
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                this.replaceWithLectern(state, level, pos);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // * Drops the controller, and clears cable connections unless this is just a state change
    @Override
    public void onRemove(final BlockState state, final Level level, final BlockPos pos,
                         final BlockState newState, final boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                this.withBlockEntityDo(level, pos, be -> be.dropController(state));
            }
            if (level instanceof final ServerLevel serverLevel && !CableNetworkManager.isPendingAssembly(serverLevel, pos)) {
                CableNetworkManager.get(serverLevel).removeAllFromSourceInternal(null, serverLevel, pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    public void replaceLectern(final BlockState lecternState, final Level level, final BlockPos pos, final ItemStack controller) {
        level.setBlockAndUpdate(pos, this.defaultBlockState()
                .setValue(FACING, lecternState.getValue(FACING))
                .setValue(POWERED, lecternState.getValue(POWERED)));
        this.withBlockEntityDo(level, pos, be -> be.setController(controller));
    }

    public void replaceWithLectern(final BlockState state, final Level level, final BlockPos pos) {
        AllSoundEvents.CONTROLLER_TAKE.playOnServer(level, pos);
        level.setBlockAndUpdate(pos, Blocks.LECTERN.defaultBlockState()
                .setValue(FACING, state.getValue(FACING))
                .setValue(POWERED, state.getValue(POWERED)));
    }
    //#endregion

    @Override
    public int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos) {
        return 15;
    }

    @Override
    public ItemStack getCloneItemStack(final BlockState state, final HitResult target, final LevelReader level,
                                      final BlockPos pos, final Player player) {
        return Blocks.LECTERN.getCloneItemStack(state, target, level, pos, player);
    }

    @Override
    public ItemRequirement getRequiredItems(final BlockState state, final BlockEntity blockEntity) {
        final List<ItemStack> requiredItems = new ArrayList<>();
        requiredItems.add(new ItemStack(Blocks.LECTERN));
        if (CableItems.HANDHELD_TYPEWRITER_CONTROLLER != null) {
            requiredItems.add(new ItemStack(CableItems.HANDHELD_TYPEWRITER_CONTROLLER.get()));
        }
        return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, requiredItems);
    }

    //#region // --- CABLE SOURCE --- //
    @Override
    public List<String> cable$getChannels(final Level level, final BlockPos pos) {
        return CableTypewriterHubServerHandler.CHANNELS;
    }

    @Override
    public String cable$nextChannel(final Level level, final BlockPos pos, final String current, final boolean forward) {
        final List<String> channels = CableTypewriterHubServerHandler.CHANNELS;
        final int index = channels.indexOf(current);
        if (index == -1) {
            return channels.getFirst();
        }
        return channels.get(Math.floorMod(index + (forward ? 1 : -1), channels.size()));
    }
    //#endregion
}