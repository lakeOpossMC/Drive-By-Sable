package edn.lakeopossmc.drivebysable.blocks;

import com.mojang.serialization.MapCodec;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

// --- BLOCK FOR PRESERVING NETWORKS --- //
// * This class is setup for the physical block
// * Only information stored here is pos and block entity
public class NetworkBackupDriveBlock extends Block implements EntityBlock {
    // --- DEF CODEC --- //
    public static final MapCodec<NetworkBackupDriveBlock> CODEC = simpleCodec(NetworkBackupDriveBlock::new);

    // --- BLOCK PROPS --- //
    public NetworkBackupDriveBlock(final Properties properties) {
        super(properties);
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

    // --- MAP BLOCK ENTITY TO CORRECT POS AND LEVEL --- //
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        final Level level,
        final BlockState state,
        final BlockEntityType<T> blockEntityType
    ) {
        if (level.isClientSide() || blockEntityType != CableBlockEntities.BACKUP_DRIVE.get()) {
            return null;
        }

        return (tickLevel, tickPos, tickState, blockEntity) -> NetworkBackupDriveBlockEntity.serverTick(
            tickLevel,
            tickPos,
            tickState,
            (NetworkBackupDriveBlockEntity) blockEntity
        );
    }

    // * Drop connections when block actually replaced
    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof final ServerLevel serverLevel) {
            CableNetworkManager.get(serverLevel).removeAllFromSourceInternal(null, serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
