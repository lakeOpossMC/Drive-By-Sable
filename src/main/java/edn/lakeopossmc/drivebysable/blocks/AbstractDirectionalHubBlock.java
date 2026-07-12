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

// --- THIS IS A SHARED MAIN CLASS FOR THE CABLE HUB AND ADVANCED CABLE HUB --- //
// * Shared shape logic is handled here
// * Shared channel logic is handled here
// * Subclasses will pass channel list and item to check for when right-clicked
public abstract class AbstractDirectionalHubBlock extends FaceAttachedHorizontalDirectionalBlock implements MultiChannelCableSource, IWrenchable, IBE<CableHubBlockEntity> {
    //#region // --- SHAPE DEFS FOR ROTATION --- //
    // * Shape North (vertical)
    protected static final VoxelShape NORTH_AABB = Shapes.or(
            Block.box(0.0, 0.0, 14.0, 16.0, 16.0, 16.0),
            Block.box(1.0, 1.0, 9.0, 15.0, 15.0, 14.0)
    );
    // * Shape South (vertical)
    protected static final VoxelShape SOUTH_AABB = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0),
            Block.box(1.0, 1.0, 2.0, 15.0, 15.0, 7.0)
    );
    // * Shape West (vertical)
    protected static final VoxelShape WEST_AABB = Shapes.or(
            Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(9.0, 1.0, 1.0, 14.0, 15.0, 15.0)
    );
    // * Shape East (vertical)
    protected static final VoxelShape EAST_AABB = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 16.0),
            Block.box(2.0, 1.0, 1.0, 7.0, 15.0, 15.0)
    );
    // * Shape Upwards Facing (default)
    protected static final VoxelShape UP_AABB = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),
            Block.box(1.0, 2.0, 1.0, 15.0, 7.0, 15.0)
    );
    // * Shape Downwards Facing
    protected static final VoxelShape DOWN_AABB = Shapes.or(
            Block.box(0.0, 14.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(1.0, 9.0, 1.0, 15.0, 14.0, 15.0)
    );
    //#endregion

    //#region // --- ATTACH PROPERTIES TO THIS CLASS --- //
    protected AbstractDirectionalHubBlock(final Properties properties) {
        // * Default vanilla properties
        super(properties);
        // * Add Facing and Face
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.FLOOR));
    }
    //#endregion
    //#region // --- ADD PROPS TO BLOCKSTATE DEF --- //
    // * Create blockstate def that includes appropriate props
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }
    //#endregion
    //#region // --- MAPCODEC --- //
    // * Minecraft uses this to know how to save the block in world
    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return MapCodec.unit(() -> this);
    }
    //#endregion

    //#region // --- FIND CORRECT STATE DURING PLACEMENT --- //
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // * Loop for all look directions
        for (Direction direction : context.getNearestLookingDirections()) {
            BlockState blockstate;
            // * If we're looking at a rotation on the y-axis
            if (direction.getAxis() == Direction.Axis.Y) {
                // * Attach to floor or ceiling and match the player's look dir
                blockstate = this.defaultBlockState()
                        .setValue(FACE, direction == Direction.UP ? AttachFace.CEILING : AttachFace.FLOOR)
                        .setValue(FACING, context.getHorizontalDirection());
            } else {
                // * Attach to wall at player's look dir
                blockstate = this.defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, direction.getOpposite());
            }

            // * Allow placement only if it makes sense
            if (blockstate.canSurvive(context.getLevel(), context.getClickedPos())) {
                return blockstate;
            }
        }
        return null;
    }
    //#endregion
    //#region // --- CORRECT BOUNDING BOX WHEN PLACED --- //
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // * Find our current attached face
        return switch (state.getValue(FACE)) {
            case FLOOR -> UP_AABB;                  // return Upwards Facing
            case CEILING -> DOWN_AABB;              // return Downwards Facing
            // * Find wall dir
            case WALL -> switch (state.getValue(FACING)) {
                case EAST -> EAST_AABB;             // return East
                case WEST -> WEST_AABB;             // return West
                case SOUTH -> SOUTH_AABB;           // return South
                default -> NORTH_AABB;              // return North
            };
        };
    }
    //#endregion
    //#region // --- MAKE SURE BLOCK STAYS EVEN WHEN BLOCKS AROUND IT ARE DESTROYED --- //
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        // * No matter what, return only the current state
        return state;
    }
    //#endregion
    //#region // --- APPLY BLOCK ROTATION --- //
    @Override
    public BlockState rotate(final BlockState state, final Rotation rotation) {
        // * Find correct rotation based on FACING
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    //#endregion

    //#region // --- CHANNEL DISPLAY LOGIC --- //
    // * Get the list of all channels
    @Override
    public List<String> cable$getChannels(final Level level, final BlockPos pos) {
        // * Get names of all channels associated with this hub
        return channels();
    }
    // * Scroll through the channels
    @Override
    public String cable$nextChannel(final Level level, final BlockPos pos, final String current, final boolean forward) {
        final List<String> channels = channels();
        final int currentIndex = channels.indexOf(current);
        // * Go back to start of list if we reached the end
        if (currentIndex == -1) {
            return channels.getFirst();
        }
        // * Go to next channel in list when scrolling
        return channels.get(Math.floorMod(currentIndex + (forward ? 1 : -1), channels.size()));
    }
    //#endregion
    //#region // --- NETWORK REMOVAL LOGIC --- //
    // * Run if source/hub is destroyed
    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        // * If removed via destroy or movement
        if (!state.is(newState.getBlock()) && level instanceof final ServerLevel serverLevel) {
            // * Kill associated connections
            CableNetworkManager.get(serverLevel).removeAllFromSourceInternal(null, serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    //#endregion

    //#region // --- BLOCK ENTITY LOGIC --- //
    // * Find what code the entity uses
    @Override
    public Class<CableHubBlockEntity> getBlockEntityClass() {
        return CableHubBlockEntity.class;
    }
    // * Find entity entry in register
    @Override
    public BlockEntityType<? extends CableHubBlockEntity> getBlockEntityType() {
        return CableBlockEntities.CABLE_HUB.get();
    }
    //#endregion

    // * Define channel list
    protected abstract List<String> channels();
}