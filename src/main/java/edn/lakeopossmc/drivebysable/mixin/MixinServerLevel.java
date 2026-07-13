package edn.lakeopossmc.drivebysable.mixin;

import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Supplier;

// --- BLENDS CABLE SIGNAL INTO VANILLA REDSTONE READ --- //
@Mixin(ServerLevel.class)
public abstract class MixinServerLevel extends Level {
    protected MixinServerLevel(
        final WritableLevelData levelData,
        final ResourceKey<Level> dimension,
        final RegistryAccess registryAccess,
        final Holder<DimensionType> dimensionTypeRegistration,
        final Supplier<ProfilerFiller> profilerSupplier,
        final boolean isClientSide,
        final boolean isDebug,
        final long biomeZoomSeed,
        final int maxChainedNeighborUpdates
    ) {
        super(
            levelData,
            dimension,
            registryAccess,
            dimensionTypeRegistration,
            profilerSupplier,
            isClientSide,
            isDebug,
            biomeZoomSeed,
            maxChainedNeighborUpdates
        );
    }

    // * Also check cable network on the opposite face
    public int getSignal(final BlockPos pos, final Direction direction) {
        final BlockState state = this.getBlockState(pos);
        int signal = state.getSignal(this, pos, direction);
        if (state.shouldCheckWeakPower(this, pos, direction)) {
            signal = Math.max(signal, this.getDirectSignalTo(pos));
        }

        final BlockPos target = pos.relative(direction.getOpposite());
        return Math.max(signal, CableNetworkManager.get(this).getSignalAt(target, direction));
    }
}
