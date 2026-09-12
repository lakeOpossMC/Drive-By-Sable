package edn.lakeopossmc.drivebysable.blocks;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import edn.lakeopossmc.drivebysable.cable.ChannelGroupedCableSource;
import edn.lakeopossmc.drivebysable.cable.ModuleSinkTarget;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import edn.lakeopossmc.drivebysable.menu.IntegratedSensorBusMenu;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.List;
import java.util.stream.Stream;
import java.util.stream.IntStream;

// --- PORTED RELIANTSHELL/AMMAN FEATURE --- //
// * I made changes here so that this works without CC being necessary
// * Features feel a little crazy overpowered in game
// * However, without changes this block is useless when CC is not installed
public final class IntegratedSensorBusBlock extends BaseEntityBlock
        implements MultiChannelCableSource, ModuleSinkTarget, ChannelGroupedCableSource, IWrenchable {
    public static final MapCodec<IntegratedSensorBusBlock> CODEC = simpleCodec(IntegratedSensorBusBlock::new);
    public static final int CHANNEL_COUNT = MultiChannelCableBusBlock.CHANNEL_COUNT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final List<String> CHANNELS = IntStream.rangeClosed(1, CHANNEL_COUNT)
            .mapToObj(Integer::toString)
            .toList();

    public IntegratedSensorBusBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        return state.setValue(FACING, state.getValue(FACING).getClockWise());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    private static final List<String> ALL_SOURCE_CHANNELS = Stream.concat(
            CHANNELS.stream(),
            IntegratedSensorBusBlockEntity.TELEMETRY_CHANNELS.stream()
    ).toList();

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        // * Telemetry has to be sampled, unlike everything else here which is pushed
        if (level.isClientSide()) {
            return null;
        }

        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof IntegratedSensorBusBlockEntity sensor) {
                sensor.tickTelemetry();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            net.minecraft.world.level.Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof IntegratedSensorBusBlockEntity sensor)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        // * A computer has taken these settings over, so the screen does not open
        if (sensor.isSettingsLocked()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("drivebysable.invalid_op.sensor_locked")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.CONSUME;
        }

        serverPlayer.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new IntegratedSensorBusMenu(containerId, inventory, pos),
                        state.getBlock().getName()
                ),
                buffer -> {
                    buffer.writeBlockPos(pos);
                    IntegratedSensorBusMenu.writeSettings(buffer, sensor);
                }
        );
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IntegratedSensorBusBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public List<String> cable$getChannels(net.minecraft.world.level.Level level, BlockPos pos) {
        return availableChannels(level, pos);
    }

    // * One list, so the quick select menu and scrolling can never disagree
    private static List<String> availableChannels(net.minecraft.world.level.Level level, BlockPos pos) {
        // * A disabled telemetry group disappears from the picker entirely
        if (level.getBlockEntity(pos) instanceof IntegratedSensorBusBlockEntity sensor) {
            return Stream.concat(CHANNELS.stream(), sensor.getEnabledTelemetryChannels().stream()).toList();
        }

        return ALL_SOURCE_CHANNELS;
    }

    @Override
    public String cable$nextChannel(net.minecraft.world.level.Level level, BlockPos pos, String current, boolean forward) {
        // * Cycles the same list the picker shows, telemetry channels included
        List<String> channels = availableChannels(level, pos);
        if (channels.isEmpty()) {
            return current;
        }

        int currentIndex = channels.indexOf(current);
        if (currentIndex < 0) {
            return channels.getFirst();
        }

        return channels.get(Math.floorMod(currentIndex + (forward ? 1 : -1), channels.size()));
    }

    //#region // --- CHANNEL GROUPS --- //
    public static final String GROUP_MAIN = "main";
    public static final String GROUP_SPEED = "speed";
    public static final String GROUP_ANGLE = "angle";
    public static final String GROUP_ALTITUDE = "altitude";

    @Override
    public List<String> cable$getChannelGroups(net.minecraft.world.level.Level level, BlockPos pos) {
        List<String> groups = new java.util.ArrayList<>(4);
        groups.add(GROUP_MAIN);

        // * A disabled group is not offered at all, so Ctrl scrolling skips it
        if (level.getBlockEntity(pos) instanceof IntegratedSensorBusBlockEntity sensor) {
            if (sensor.isSpeedEnabled()) {
                groups.add(GROUP_SPEED);
            }
            if (sensor.isAngleEnabled()) {
                groups.add(GROUP_ANGLE);
            }
            if (sensor.isAltitudeEnabled()) {
                groups.add(GROUP_ALTITUDE);
            }
        }

        return List.copyOf(groups);
    }

    @Override
    public List<String> cable$getGroupChannels(net.minecraft.world.level.Level level, BlockPos pos, String group) {
        if (!cable$getChannelGroups(level, pos).contains(group)) {
            return List.of();
        }

        return switch (group) {
            case GROUP_SPEED -> IntegratedSensorBusBlockEntity.SPEED_CHANNELS;
            case GROUP_ANGLE -> IntegratedSensorBusBlockEntity.ANGLE_CHANNELS;
            case GROUP_ALTITUDE -> IntegratedSensorBusBlockEntity.ALTITUDE_CHANNELS;
            default -> CHANNELS;
        };
    }

    // * One channel is not worth a menu
    @Override
    public boolean cable$groupAllowsQuickSelect(net.minecraft.world.level.Level level, BlockPos pos, String group) {
        return !GROUP_ALTITUDE.equals(group);
    }

    @Override
    public String cable$getChannelGroupLangKey(String group) {
        return "drivebysable.sensor_bus.group." + group;
    }
    //#endregion

    //#region // --- CABLE SINK --- //
    // * The same hundred channels accept a signal as well as emit one
    // * So the block bridges without a computer: channel 1 maps to channel 1
    @Override
    public List<String> cable$getSinkChannels(net.minecraft.world.level.Level level, BlockPos pos) {
        return CHANNELS;
    }

    @Override
    public boolean cable$applySinkSignal(
            net.minecraft.world.level.Level level,
            BlockPos pos,
            String channel,
            int signal
    ) {
        int index = CHANNELS.indexOf(channel);
        if (index < 0) {
            return false;
        }

        if (!(level.getBlockEntity(pos) instanceof IntegratedSensorBusBlockEntity sensor)) {
            return false;
        }

        sensor.setBridgedInput(index + 1, signal);
        return true;
    }
    //#endregion
}