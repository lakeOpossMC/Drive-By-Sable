package edn.lakeopossmc.drivebysable.blocks;

import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.CableSounds;
import edn.lakeopossmc.drivebysable.cable.ChannelGroupedCableSource;
import edn.lakeopossmc.drivebysable.compat.GetCreativeCableServerHandler;
import edn.lakeopossmc.drivebysable.util.HubItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

// --- DIRECTIONAL INTERMEDIATE CONTROLLER HUB --- //
// * Get Creative compat, speaks for the Linked Macro Controller, Keypad and Remote
public class IntermediateCableHubBlock extends AbstractDirectionalHubBlock<IntermediateCableHubBlockEntity>
        implements ChannelGroupedCableSource {

    //#region // --- SHAPE DEFS FOR ROTATION --- //
    private static final VoxelShape UP_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0),
            Block.box(2.0, 8.0, 2.0, 14.0, 10.0, 14.0)
    );
    private static final VoxelShape DOWN_SHAPE = Shapes.or(
            Block.box(0.0, 8.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(2.0, 6.0, 2.0, 14.0, 8.0, 14.0)
    );
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 8.0, 16.0, 16.0, 16.0),
            Block.box(2.0, 2.0, 6.0, 14.0, 14.0, 8.0)
    );
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 8.0),
            Block.box(2.0, 2.0, 8.0, 14.0, 14.0, 10.0)
    );
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            Block.box(6.0, 2.0, 2.0, 8.0, 14.0, 14.0)
    );
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0),
            Block.box(8.0, 2.0, 2.0, 10.0, 14.0, 14.0)
    );
    //#endregion

    // --- GET PROPS FROM MAIN --- //
    public IntermediateCableHubBlock(final Properties properties) {
        super(properties);
    }

    //#region // --- CORRECT BOUNDING BOX WHEN PLACED --- //
    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> UP_SHAPE;
            case CEILING -> DOWN_SHAPE;
            case WALL -> switch (state.getValue(FACING)) {
                case EAST -> EAST_SHAPE;
                case WEST -> WEST_SHAPE;
                case SOUTH -> SOUTH_SHAPE;
                default -> NORTH_SHAPE;
            };
        };
    }
    //#endregion

    //#region // --- CHANNELS, ONLY THE OPEN GROUPS --- //
    // * Full list, only used as the fallback for the shared hub logic
    @Override
    protected List<String> channels() {
        return GetCreativeCableServerHandler.ALL_CHANNELS;
    }

    @Override
    public List<String> cable$getChannels(final Level level, final BlockPos pos) {
        return level.getBlockEntity(pos) instanceof final IntermediateCableHubBlockEntity hub
                ? hub.getOpenChannels()
                : List.of();
    }

    @Nullable
    @Override
    public String cable$nextChannel(final Level level, final BlockPos pos, final String current, final boolean forward) {
        final List<String> channels = this.cable$getChannels(level, pos);
        if (channels.isEmpty()) {
            return null;
        }

        final int currentIndex = channels.indexOf(current);
        if (currentIndex == -1) {
            return channels.getFirst();
        }
        return channels.get(Math.floorMod(currentIndex + (forward ? 1 : -1), channels.size()));
    }

    // * Empty until a device is bound
    @Override
    public List<String> cable$getChannelGroups(final Level level, final BlockPos pos) {
        return level.getBlockEntity(pos) instanceof final IntermediateCableHubBlockEntity hub
                ? hub.getOpenGroups()
                : List.of();
    }

    @Override
    public List<String> cable$getGroupChannels(final Level level, final BlockPos pos, final String group) {
        if (!this.cable$getChannelGroups(level, pos).contains(group)) {
            return List.of();
        }
        return GetCreativeCableServerHandler.channelsInGroup(group);
    }

    @Override
    public String cable$getChannelGroupLangKey(final String group) {
        return GetCreativeCableServerHandler.groupLangKey(group);
    }
    //#endregion

    @Override
    public Class<IntermediateCableHubBlockEntity> getBlockEntityClass() {
        return IntermediateCableHubBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends IntermediateCableHubBlockEntity> getBlockEntityType() {
        return CableBlockEntities.INTERMEDIATE_CABLE_HUB.get();
    }

    //#region // --- CHECK FOR GET CREATIVE DEVICE USE --- //
    @Override
    protected ItemInteractionResult useItemOn(
            final ItemStack itemStack,
            final BlockState state,
            final Level level,
            final BlockPos blockPos,
            final Player player,
            final InteractionHand interactionHand,
            final BlockHitResult hitResult
    ) {
        // * Make sure we're looking for the correct item for linking
        final String group = GetCreativeCableServerHandler.groupFor(itemStack);
        if (group == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // * When successful, open the device's channel group, show message and play sound
        if (!level.isClientSide()) {
            HubItem.putHub(itemStack, blockPos);
            if (level.getBlockEntity(blockPos) instanceof final IntermediateCableHubBlockEntity hub) {
                hub.openGroup(group);
            }
            level.playSound(null, blockPos, CableSounds.PLUG_IN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            player.displayClientMessage(Component.literal("Controller connected!"), true);
        }
        // * Return a success trigger
        return ItemInteractionResult.SUCCESS;
    }
    //#endregion
}