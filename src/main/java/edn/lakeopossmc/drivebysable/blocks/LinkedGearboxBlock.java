package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.LinkedGearboxLinks;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;
import net.minecraft.world.ticks.TickPriority;

// --- BLOCK FOR LINKED GEARBOX --- //
public class LinkedGearboxBlock extends DirectionalKineticBlock
        implements IBE<LinkedGearboxBlockEntity>, ICogWheel, MultiChannelCableSource {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty ROTATION = DirectionProperty.create("rotation", Direction.Plane.HORIZONTAL);

    //#region // --- SHAPE DEFS FOR EACH FACING --- //
    private static final VoxelShape UP_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 10.0, 16.0),
            Block.box(2.0, 10.0, 2.0, 14.0, 16.0, 14.0)
    );
    private static final VoxelShape DOWN_SHAPE = Shapes.or(
            Block.box(0.0, 6.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(2.0, 0.0, 2.0, 14.0, 6.0, 14.0)
    );
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 16.0),
            Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 6.0)
    );
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 10.0),
            Block.box(2.0, 2.0, 10.0, 14.0, 14.0, 16.0)
    );
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(6.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(0.0, 2.0, 2.0, 6.0, 14.0, 14.0)
    );
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 10.0, 16.0, 16.0),
            Block.box(10.0, 2.0, 2.0, 16.0, 14.0, 14.0)
    );
    //#endregion

    public LinkedGearboxBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(ROTATION, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(ROTATION, POWERED));
    }

    //#region // --- PLACEMENT --- //
    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        final Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            facing = facing.getOpposite();
        }
        final Direction rotation = facing.getAxis() == Direction.Axis.Y
                ? context.getHorizontalDirection()
                : Direction.NORTH;
        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(ROTATION, rotation)
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }
    //#endregion

    //#region // --- SHAPE --- //
    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> UP_SHAPE;
            case DOWN -> DOWN_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
        };
    }
    //#endregion

    //#region // --- ROTATION --- //
    // * A wrench on either end rolls the block around its own axis
    @Override
    public BlockState getRotatedBlockState(final BlockState originalState, final Direction targetedFace) {
        final Direction facing = originalState.getValue(FACING);
        final Direction.Axis around = targetedFace.getAxis();
        if (facing.getAxis() == around) {
            return originalState.setValue(ROTATION, originalState.getValue(ROTATION).getClockWise());
        }

        final Direction turnedFacing = super.getRotatedBlockState(originalState, targetedFace).getValue(FACING);
        Direction reference = reference(originalState);
        Direction step = facing;
        for (int turns = 0; turns < 4 && step != turnedFacing; turns++) {
            step = step.getClockWise(around);
            reference = reference.getAxis() == around ? reference : reference.getClockWise(around);
        }
        return oriented(originalState, turnedFacing, reference);
    }

    // * Structure and schematic rotation turn the whole block the same way
    @Override
    public BlockState rotate(final BlockState state, final Rotation rotation) {
        return oriented(state, rotation.rotate(state.getValue(FACING)), rotation.rotate(reference(state)));
    }

    @Override
    public BlockState mirror(final BlockState state, final Mirror mirror) {
        return oriented(state, mirror.mirror(state.getValue(FACING)), mirror.mirror(reference(state)));
    }

    // * Which way the model's north side ends up pointing
    private static Direction reference(final BlockState state) {
        final Vec3 north = rotateModelVector(state, new Vec3(0, 0, -1));
        return Direction.getNearest(north.x, north.y, north.z);
    }

    private static BlockState oriented(final BlockState state, final Direction facing, final Direction reference) {
        final BlockState turned = state.setValue(FACING, facing);
        for (final Direction rotation : ROTATION.getPossibleValues()) {
            final BlockState candidate = turned.setValue(ROTATION, rotation);
            if (reference(candidate) == reference) {
                return candidate;
            }
        }
        return turned;
    }
    //#endregion

    //#region // --- REDSTONE --- //
    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                   final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) return;
        updatePower(level, pos);
    }

    public static void updatePower(final Level level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LinkedGearboxBlock)) return;

        final boolean powered = level.hasNeighborSignal(pos)
                || level.getBlockEntity(pos) instanceof final LinkedGearboxBlockEntity gearbox
                && (gearbox.isWirelesslyPowered() || gearbox.isComputerDisabled());
        if (powered == state.getValue(POWERED)) return;

        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);

        relink(level, pos);
    }

    // * Leave the kinetic network and rejoin next tick
    public static void relink(final Level level, final BlockPos pos) {
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof final LinkedGearboxBlockEntity gearbox) {
            gearbox.stopLinkRole();
        }
        if (level.getBlockEntity(pos) instanceof final KineticBlockEntity kbe) {
            // * Anything we were driving goes looking for another source
            kbe.detachKinetics();
            // * Drop our own speed and leave the network
            kbe.removeSource();
        }
        if (level.getBlockState(pos).getBlock() instanceof final LinkedGearboxBlock block) {
            level.scheduleTick(pos, block, 0, TickPriority.EXTREMELY_HIGH);
        }
    }

    @Override
    protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
        super.tick(state, level, pos, random);
        if (level.getBlockEntity(pos) instanceof final KineticBlockEntity kbe) {
            RotationPropagator.handleAdded(level, pos, kbe);
        }
    }

    //#region // --- MODEL SPACE --- //
    // * Roll around the model's own axis, then the blockstate's x and y
    // * Upright, ROTATION is the blockstate's y. On its side, it is the roll
    private static int[] modelRotation(final BlockState state) {
        final int spin = (int) (state.getValue(ROTATION).toYRot() + 180) % 360;
        return switch (state.getValue(FACING)) {
            case UP -> new int[]{0, 0, spin};
            case DOWN -> new int[]{0, 180, (spin + 180) % 360};
            case NORTH -> new int[]{spin, 90, 0};
            case SOUTH -> new int[]{spin, 90, 180};
            case WEST -> new int[]{spin, 90, 270};
            case EAST -> new int[]{spin, 90, 90};
        };
    }

    public static Vec3 rotateModelVector(final BlockState state, final Vec3 vector) {
        final int[] rotation = modelRotation(state);
        final double rollRad = Math.toRadians(rotation[0]);
        final double xRad = Math.toRadians(rotation[1]);
        final double yRad = Math.toRadians(rotation[2]);

        final double x0 = vector.x * Math.cos(rollRad) - vector.z * Math.sin(rollRad);
        final double z0 = vector.x * Math.sin(rollRad) + vector.z * Math.cos(rollRad);

        final double y1 = vector.y * Math.cos(xRad) + z0 * Math.sin(xRad);
        final double z1 = -vector.y * Math.sin(xRad) + z0 * Math.cos(xRad);

        final double x2 = x0 * Math.cos(yRad) - z1 * Math.sin(yRad);
        final double z2 = x0 * Math.sin(yRad) + z1 * Math.cos(yRad);
        return new Vec3(x2, y1, z2);
    }

    public static Vec3 rotateModelPoint(final BlockState state, final Vec3 point) {
        final Vec3 centre = new Vec3(0.5, 0.5, 0.5);
        return rotateModelVector(state, point.subtract(centre)).add(centre);
    }
    //#endregion

    public static boolean isDisconnected(final BlockState state) {
        return state.getBlock() instanceof LinkedGearboxBlock && state.getValue(POWERED);
    }
    //#endregion

    //#region // --- KINETICS --- //
    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // * Cog meshes on the sides (via ICogWheel), shaft only connects on the large base side
    @Override
    public boolean hasShaftTowards(final LevelReader level, final BlockPos pos, final BlockState state, final Direction face) {
        return !state.getValue(POWERED) && face == state.getValue(FACING).getOpposite();
    }

    public static void registerStress(final Block block) {
        BlockStressValues.IMPACTS.register(block,
                () -> CableConfig.CONFIG.linkedGearboxTransmitterStress.get());
        BlockStressValues.CAPACITIES.register(block,
                () -> CableConfig.CONFIG.linkedGearboxLimitTransfer.get()
                        ? CableConfig.CONFIG.linkedGearboxStressPerRpm.get() : 0.0);
    }

    public static void registerTooltip(final Item item, final Block block) {
        TooltipModifier.REGISTRY.register(item, new KineticStats(block));
    }
    //#endregion

    //#region // --- CABLE --- //
    @Override
    public List<String> cable$getChannels(final Level level, final BlockPos pos) {
        return List.of(LinkedGearboxLinks.STRESS_CHANNEL);
    }

    @Override
    public String cable$nextChannel(final Level level, final BlockPos pos, final String current, final boolean forward) {
        return LinkedGearboxLinks.STRESS_CHANNEL;
    }

    @Override
    public void onRemove(final BlockState state, final Level level, final BlockPos pos,
                         final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && !CableNetworkManager.isPendingAssembly(level, pos)) {
            LinkedGearboxLinks.leaveNetwork(level, pos);
            CableNetworkManager.get(level).removeAllFromSourceChannelInternal(level, pos, LinkedGearboxLinks.STRESS_CHANNEL);
            CableNetworkManager.removeAllToModuleSink(level, pos, LinkedGearboxLinks.STRESS_CHANNEL);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    //#endregion

    @Override
    public Class<LinkedGearboxBlockEntity> getBlockEntityClass() {
        return LinkedGearboxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LinkedGearboxBlockEntity> getBlockEntityType() {
        return CableBlockEntities.LINKED_GEARBOX.get();
    }
}