package edn.lakeopossmc.drivebysable.blocks;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerClientHandler;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.index.SimBlockShapes;
import dev.simulated_team.simulated.service.SimMenuService;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.compat.CableTypewriterHubServerHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.List;
import java.util.UUID;

public class CableTypewriterHubBlock extends HorizontalDirectionalBlock
        implements IBE<CableTypewriterHubBlockEntity>, MultiChannelCableSource, IWrenchable {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final MapCodec<CableTypewriterHubBlock> CODEC = simpleCodec(CableTypewriterHubBlock::new);

    public CableTypewriterHubBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter getter,
                               final BlockPos pos, final CollisionContext context) {
        return SimBlockShapes.LINKED_TYPEWRITER.get(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, FACING);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        assert context.getPlayer() != null;
        final var dir = context.getHorizontalDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING,
                context.getPlayer().isShiftKeyDown() ? dir.getOpposite() : dir);
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state,
                                              final Level level, final BlockPos pos,
                                              final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        final ItemStack held = player.getItemInHand(hand);
        final Item linkedController = AllItems.LINKED_CONTROLLER.asItem();

        if (player.getMainHandItem().is(linkedController) || player.getOffhandItem().is(linkedController)) {
            if (level.isClientSide) {
                final ItemStack item = player.getMainHandItem().is(linkedController)
                        ? player.getMainHandItem() : player.getOffhandItem();
                player.displayClientMessage(
                        SimLang.translate("linked_typewriter.linked_controller_copy").component(), true);
                LinkedTypewriterInteractionHandler.sendLinkedControllerData(level, pos, item);
                LinkedControllerClientHandler.MODE = LinkedControllerClientHandler.Mode.IDLE;
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (held.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            final MutableBoolean success = new MutableBoolean(false);

            this.withBlockEntityDo(level, pos, be -> {
                final UUID uuid = player.getUUID();

                if (player.isShiftKeyDown() && be.checkAndStartUsing(uuid)) {
                    if (!level.isClientSide) {
                        displayScreen(be, player);
                    } else {
                        LinkedTypewriterInteractionHandler.setMode(
                                LinkedTypewriterInteractionHandler.Mode.SCREEN_BINDING);
                    }
                    success.setTrue();
                    return;
                }

                if (be.checkAndStartUsing(uuid)) {
                    success.setTrue();
                    return;
                }

                if (be.checkUser(uuid)) {
                    be.disconnectUser();
                    success.setTrue();
                }
            });

            if (success.getValue()) return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    private void displayScreen(final CableTypewriterHubBlockEntity be, final Player player) {
        SimMenuService.INSTANCE.openScreen((ServerPlayer) player, be, be::sendToMenu);
    }

    @Override
    public InteractionResult onSneakWrenched(final BlockState state, final UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        if (level instanceof ServerLevel) {
            this.withBlockEntityDo(level, pos, be -> {
                be.disconnectUser();
                if (context.getPlayer() != null && context.getPlayer().isCreative()) {
                    Block.getDrops(state, (ServerLevel) level, pos, level.getBlockEntity(pos),
                                    context.getPlayer(), context.getItemInHand())
                            .forEach(s -> context.getPlayer().getInventory()
                                    .placeItemBackInInventory(s));
                }
            });
        }
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override
    public boolean hasAnalogOutputSignal(final BlockState state) { return true; }

    @Override
    protected int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public Class<CableTypewriterHubBlockEntity> getBlockEntityClass() {
        return CableTypewriterHubBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CableTypewriterHubBlockEntity> getBlockEntityType() {
        return CableBlockEntities.CABLE_TYPEWRITER_HUB.get();
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state, final Player player) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null
                && !level.isClientSide
                && player.isCreative()
                && blockEntity instanceof final CableTypewriterHubBlockEntity typewriter
                && !typewriter.getTypewriterEntries().getKeyMap().isEmpty()) {
            final ItemStack itemStack = new ItemStack(this);
            typewriter.saveToItem(itemStack, level.registryAccess());
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), itemStack);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public List<String> cable$getChannels() {
        return CableTypewriterHubServerHandler.CHANNELS;
    }

    @Override
    public String cable$nextChannel(final String current, final boolean forward) {
        final List<String> channels = CableTypewriterHubServerHandler.CHANNELS;
        int idx = channels.indexOf(current);
        if (idx == -1) return channels.get(0);
        idx = forward ? (idx + 1) % channels.size()
                : (idx - 1 + channels.size()) % channels.size();
        return channels.get(idx);
    }

    @Override
    public List<ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
        final BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof final CableTypewriterHubBlockEntity typewriter) {
            final ItemStack itemStack = new ItemStack(this);
            typewriter.saveToItem(itemStack, params.getLevel().registryAccess());
            return List.of(itemStack);
        }
        return super.getDrops(state, params);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof final ServerLevel serverLevel) {
            CableNetworkManager.get(serverLevel).removeAllFromSourceInternal(null, serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}