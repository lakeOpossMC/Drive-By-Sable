package edn.lakeopossmc.drivebysable.mixin.compat.tracks;

import edn.lakeopossmc.drivebysable.compat.CableRedstoneCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// --- LET SABLE TRACK POWER READ CABLES TOO --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(targets = "dev.qwxon.tracks.content.blocks.sable_track.SableTrackBlockEntity", remap = false)
public abstract class MixinSableTrackBlockEntity {
    // * Swap signal read used for physics
    @Redirect(
        method = "sable$physicsTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"
        ),
        remap = false
    )
    private int drivebysable$useCableForTrackPhysicsSignal(final Level level, final BlockPos pos, final Direction direction) {
        return CableRedstoneCompat.getSignalIncludingReverseCable(level, pos, direction);
    }

    // * Swap signal read used for visuals
    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"
        ),
        remap = false
    )
    private int drivebysable$useCableForTrackVisualSignal(final Level level, final BlockPos pos, final Direction direction) {
        return CableRedstoneCompat.getSignalIncludingReverseCable(level, pos, direction);
    }
}
