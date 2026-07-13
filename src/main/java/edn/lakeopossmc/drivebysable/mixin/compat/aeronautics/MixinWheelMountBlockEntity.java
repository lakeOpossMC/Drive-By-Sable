package edn.lakeopossmc.drivebysable.mixin.compat.aeronautics;

import edn.lakeopossmc.drivebysable.compat.CableRedstoneCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// --- LET WHEEL MOUNT READ CABLES FOR BRAKE AND STEER --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(targets = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity", remap = false)
public abstract class MixinWheelMountBlockEntity {
    // * Swap signal read used for brake physics
    @Redirect(
        method = "sable$physicsTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"
        ),
        remap = false
    )
    private int drivebysable$useCableForBrakeSignal(final Level level, final BlockPos pos, final Direction direction) {
        return CableRedstoneCompat.getSignalIncludingReverseCable(level, pos, direction);
    }

    // * Swap signal read used for client side brake
    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"
        ),
        remap = false
    )
    private int drivebysable$useCableForClientBrakeSignal(final Level level, final BlockPos pos, final Direction direction) {
        return CableRedstoneCompat.getSignalIncludingReverseCable(level, pos, direction);
    }

    // * Swap signal read used for left steer
    @Redirect(
        method = "getSteeringSignal",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I",
            ordinal = 0
        ),
        remap = false
    )
    private int drivebysable$useCableForLeftSteeringSignal(final Level level, final BlockPos pos, final Direction direction) {
        return CableRedstoneCompat.getSignalIncludingReverseCable(level, pos, direction);
    }

    // * Swap signal read used for right steer
    @Redirect(
        method = "getSteeringSignal",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I",
            ordinal = 1
        ),
        remap = false
    )
    private int drivebysable$useCableForRightSteeringSignal(final Level level, final BlockPos pos, final Direction direction) {
        return CableRedstoneCompat.getSignalIncludingReverseCable(level, pos, direction);
    }
}
