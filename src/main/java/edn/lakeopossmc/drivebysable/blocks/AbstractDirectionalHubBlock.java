package edn.lakeopossmc.drivebysable.blocks;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

// --- THIS IS A SHARED MAIN CLASS: CLEANS UP DUPLICATE CODE --- //
// * Moved shape logic here so I don't have to type it twice in the block classes
// * Moved some channel logic here for the same reason
// * Subclasses will pass channel list and item to check for when right-clicked
public abstract class AbstractDirectionalHubBlock extends FaceAttachedHorizontalDirectionalBlock implements MultiChannelCableSource, IWrenchable, IBE<CableHubBlockEntity> {
    // --- SHAPE DEFS FOR ROTATION --- //
    protected static final VoxelShape NORTH_AABB = Shapes.or(
            Block.box(0.0, 0.0, 14.0, 16.0, 16.0, 16.0),
            Block.box(1.0, 1.0, 9.0, 15.0, 15.0, 14.0)
    );
    protected static final VoxelShape SOUTH_AABB = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0),
            Block.box(1.0, 1.0, 2.0, 15.0, 15.0, 7.0)
    );
    protected static final VoxelShape WEST_AABB = Shapes.or(
            Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(9.0, 1.0, 1.0, 14.0, 15.0, 15.0)
    );
    protected static final VoxelShape EAST_AABB = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 16.0),
            Block.box(2.0, 1.0, 1.0, 7.0, 15.0, 15.0)
    );
    protected static final VoxelShape UP_AABB = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),
            Block.box(1.0, 2.0, 1.0, 15.0, 7.0, 15.0)
    );
    protected static final VoxelShape DOWN_AABB = Shapes.or(
            Block.box(0.0, 14.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(1.0, 9.0, 1.0, 15.0, 14.0, 15.0)
    );

    // --- ATTACH PROPERTIES TO THIS CLASS --- //
    protected AbstractDirectionalHubBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.FLOOR));
    }

    // --- ADD PROPS TO BLOCKSTATE DEF --- //
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return MapCodec.unit(() -> this);
    }

    // --- FIND CORRECT STATE DURING PLACEMENT --- //
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            BlockState blockstate;
            if (direction.getAxis() == Direction.Axis.Y) {
                blockstate = this.defaultBlockState()
                        .setValue(FACE, direction == Direction.UP ? AttachFace.CEILING : AttachFace.FLOOR)
                        .setValue(FACING, context.getHorizontalDirection());
            } else {
                blockstate = this.defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, direction.getOpposite());
            }

            if (blockstate.canSurvive(context.getLevel(), context.getClickedPos())) {
                return blockstate;
            }
        }
        return null;
    }

    // --- CORRECT BOUNDING BOX WHEN PLACED --- //
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> UP_AABB;
            case CEILING -> DOWN_AABB;
            case WALL -> switch (state.getValue(FACING)) {
                case EAST -> EAST_AABB;
                case WEST -> WEST_AABB;
                case SOUTH -> SOUTH_AABB;
                default -> NORTH_AABB;
            };
        };
    }

    // --- MAKE SURE BLOCK STAYS EVEN WHEN BLOCKS AROUND IT ARE DESTROYED --- //
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        return state;
    }

    @Override
    public BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    // --- CHANNEL LOGIC (MOSTLY UNCHANGED FROM OG DRIVEBYWIRE) --- //
    @Override
    public List<String> cable$getChannels(final Level level, final BlockPos pos) {
        return channels();
    }

    @Override
    public String cable$nextChannel(final Level level, final BlockPos pos, final String current, final boolean forward) {
        final List<String> channels = channels();
        final int currentIndex = channels.indexOf(current);
        if (currentIndex == -1) {
            return channels.getFirst();
        }
        return channels.get(Math.floorMod(currentIndex + (forward ? 1 : -1), channels.size()));
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof final ServerLevel serverLevel) {
            CableNetworkManager.get(serverLevel).removeAllFromSourceInternal(null, serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public Class<CableHubBlockEntity> getBlockEntityClass() {
        return CableHubBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CableHubBlockEntity> getBlockEntityType() {
        return CableBlockEntities.CABLE_HUB.get();
    }

    protected abstract List<String> channels();
}