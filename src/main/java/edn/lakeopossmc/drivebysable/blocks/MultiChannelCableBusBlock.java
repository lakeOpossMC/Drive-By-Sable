package edn.lakeopossmc.drivebysable.blocks;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import edn.lakeopossmc.drivebysable.cable.ModuleSinkTarget;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.List;
import java.util.stream.IntStream;

// --- PORTED RELIANTSHELL/AMMAN FEATURE --- //
// * Channels are named "1" through "100"
// * Contributed against Drive-By-Wire and ported to the cable network
public final class MultiChannelCableBusBlock extends BaseEntityBlock
        implements MultiChannelCableSource, ModuleSinkTarget, IWrenchable {

    public static final MapCodec<MultiChannelCableBusBlock> CODEC = simpleCodec(MultiChannelCableBusBlock::new);
    public static final int CHANNEL_COUNT = 100;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final List<String> CHANNELS = IntStream.rangeClosed(1, CHANNEL_COUNT)
            .mapToObj(Integer::toString)
            .toList();

    public MultiChannelCableBusBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockState getRotatedBlockState(final BlockState state, final Direction targetedFace) {
        return state.setValue(FACING, state.getValue(FACING).getClockWise());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MultiChannelCableBusBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    //#region // --- CABLE SOURCE --- //
    // * The channel list is fixed, so the level and position are not needed
    @Override
    public List<String> cable$getChannels(final Level level, final BlockPos pos) {
        return CHANNELS;
    }

    @Override
    public String cable$nextChannel(
            final Level level,
            final BlockPos pos,
            final String current,
            final boolean forward
    ) {
        final int currentIndex = CHANNELS.indexOf(current);
        if (currentIndex < 0) {
            return CHANNELS.getFirst();
        }

        return CHANNELS.get(Math.floorMod(currentIndex + (forward ? 1 : -1), CHANNELS.size()));
    }
    //#endregion

    //#region // --- CABLE SINK --- //
    // * The same hundred channels accept a signal as well as emit one
    @Override
    public List<String> cable$getSinkChannels(final Level level, final BlockPos pos) {
        return CHANNELS;
    }

    @Override
    public boolean cable$applySinkSignal(
            final Level level,
            final BlockPos pos,
            final String channel,
            final int signal
    ) {
        final int index = CHANNELS.indexOf(channel);
        if (index < 0) {
            return false;
        }

        if (!(level.getBlockEntity(pos) instanceof final MultiChannelCableBusBlockEntity bus)) {
            return false;
        }

        bus.setBridgedInput(index + 1, signal);
        return true;
    }
    //#endregion
}