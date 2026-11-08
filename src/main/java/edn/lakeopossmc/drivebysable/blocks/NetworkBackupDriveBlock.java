package edn.lakeopossmc.drivebysable.blocks;

import com.mojang.serialization.MapCodec;
import edn.lakeopossmc.drivebysable.cable.CableDataLostSound;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import edn.lakeopossmc.drivebysable.menu.BackupDriveMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType;

// --- BLOCK FOR PRESERVING NETWORKS --- //
// * This class is setup for the physical block
// * Only information stored here is pos and block entity
public class NetworkBackupDriveBlock extends Block implements EntityBlock, SpecialBlockItemRequirement {
    // --- DEF CODEC --- //
    public static final MapCodec<NetworkBackupDriveBlock> CODEC = simpleCodec(NetworkBackupDriveBlock::new);

    // --- BLOCK PROPS --- //
    public NetworkBackupDriveBlock(final Properties properties) {
        super(properties);
    }

    // * Right click opens the bounds editor
    @Override
    protected InteractionResult useWithoutItem(
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final Player player,
            final BlockHitResult hit
    ) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof final NetworkBackupDriveBlockEntity drive)
                || !(player instanceof final ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        serverPlayer.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new BackupDriveMenu(containerId, inventory, pos),
                        state.getBlock().getName()
                ),
                buffer -> {
                    buffer.writeBlockPos(pos);
                    BackupDriveMenu.writeRegion(buffer, drive);
                }
        );
        return InteractionResult.CONSUME;
    }

    // --- MAPCODEC TO CODEC --- //
    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    // --- DEF BLOCK ENTITY --- //
    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new NetworkBackupDriveBlockEntity(pos, state);
    }

    // --- SCHEMATICANNON ASKS FOR THE DRIVE ONLY --- //
    // * Cables are paid for when the drive is loaded
    @Override
    public ItemRequirement getRequiredItems(final BlockState state, final BlockEntity blockEntity) {
        return new ItemRequirement(ItemUseType.CONSUME, asItem());
    }
    //#endregion

    //#region // --- SAVED DATA SURVIVES BREAKING --- //
    @Override
    protected List<ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
        final List<ItemStack> drops = super.getDrops(state, params);
        if (!(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof final NetworkBackupDriveBlockEntity drive) || !drive.hasStoredSnapshot()) {
            return drops;
        }

        for (final ItemStack drop : drops) {
            if (drop.getItem() == asItem()) {
                drive.writeToItem(drop, params.getLevel().registryAccess());
            }
        }
        return drops;
    }
    //#endregion

    // * Drop connections when block actually replaced
    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof final ServerLevel serverLevel
                && !CableNetworkManager.isPendingAssembly(serverLevel, pos)) {
            CableNetworkManager.get(serverLevel).removeAllFromSourceInternal(null, serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    //#region // --- WRONG TOOL FEEDBACK --- //
    @Override
    public BlockState playerWillDestroy(
            final Level level,
            final BlockPos pos,
            final BlockState state,
            final Player player
    ) {
        if (!level.isClientSide
                && !player.isCreative()
                && !state.canHarvestBlock(level, pos, player)
                && holdsData(level, pos)) {
            CableDataLostSound.play(level, pos);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    private static boolean holdsData(final Level level, final BlockPos pos) {
        return level.getBlockEntity(pos) instanceof final NetworkBackupDriveBlockEntity drive
                && drive.hasStoredSnapshot();
    }

    //#endregion
}