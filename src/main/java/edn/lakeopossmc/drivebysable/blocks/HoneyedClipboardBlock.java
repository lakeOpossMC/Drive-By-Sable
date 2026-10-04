package edn.lakeopossmc.drivebysable.blocks;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;

// --- THE HONEYED CLIPBOARD --- //
// * Clicking it only squelches...
public class HoneyedClipboardBlock extends FaceAttachedHorizontalDirectionalBlock
        implements IWrenchable, ProperWaterloggedBlock {

    public static final MapCodec<HoneyedClipboardBlock> CODEC = simpleCodec(HoneyedClipboardBlock::new);

    public HoneyedClipboardBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACE, FACING, WATERLOGGED));
    }

    //#region // --- PLACEMENT --- //
    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        if (state.getValue(FACE) != AttachFace.WALL) {
            state = state.setValue(FACING, state.getValue(FACING).getOpposite());
        }
        return this.withWater(state, context);
    }

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos,
                               final CollisionContext context) {
        return (switch (state.getValue(FACE)) {
            case FLOOR -> AllShapes.CLIPBOARD_FLOOR;
            case CEILING -> AllShapes.CLIPBOARD_CEILING;
            default -> AllShapes.CLIPBOARD_WALL;
        }).get(state.getValue(FACING));
    }

    // * Needs something behind it
    @Override
    public boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
        return !level.getBlockState(pos.relative(getConnectedDirection(state).getOpposite())).canBeReplaced();
    }

    @Override
    public FluidState getFluidState(final BlockState state) {
        return this.fluidState(state);
    }

    @Override
    public BlockState updateShape(final BlockState state, final Direction facing, final BlockState facingState,
                                  final LevelAccessor level, final BlockPos pos, final BlockPos facingPos) {
        this.updateWater(level, state, pos);
        return super.updateShape(state, facing, facingState, level, pos, facingPos);
    }
    //#endregion

    //#region // --- CLICKS --- //
    // * Sneak click picks it up
    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            breakAndCollect(state, level, pos, player);
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.HONEY_BLOCK_STEP, SoundSource.BLOCKS,
                    1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void attack(final BlockState state, final Level level, final BlockPos pos, final Player player) {
        breakAndCollect(state, level, pos, player);
    }

    // * Into the hand when it is free, else anywhere in the inventory
    private void breakAndCollect(final BlockState state, final Level level, final BlockPos pos, final Player player) {
        if (player instanceof FakePlayer || level.isClientSide) {
            return;
        }

        level.destroyBlock(pos, false);
        if (level.getBlockState(pos) == state) {
            return;
        }

        final ItemStack stack = new ItemStack(this);
        final Inventory inventory = player.getInventory();
        if (inventory.getSelected().isEmpty()) {
            inventory.setItem(inventory.selected, stack);
        } else {
            inventory.placeItemBackInInventory(stack);
        }
    }
    //#endregion
}