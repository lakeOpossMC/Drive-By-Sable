package edn.lakeopossmc.drivebysable.mixin.compat.toolgun;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import edn.lakeopossmc.drivebysable.compat.toolgun.ToolgunStructureMove;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// --- CONNECTIONS TRAVEL WITH A CONTAINER PICKUP --- //
@Pseudo
@Mixin(targets = "com.enxv.aeronauticsstructuretool.PortableStructureContainerItem", remap = false)
public abstract class MixinPortableStructureContainer {

    @Inject(method = "captureVehicle", at = @At("HEAD"), require = 0, expect = 0)
    private void drivebysable$beginVehicleMove(
            final ServerLevel level,
            final ServerPlayer player,
            final ItemStack stack,
            final ServerSubLevel containing,
            final List<ServerSubLevel> expected,
            final long expectedBlocks,
            final double progressTicks,
            final CallbackInfo ci
    ) {
        ToolgunStructureMove.begin(level);
    }

    @Inject(method = "captureVehicle", at = @At("RETURN"), require = 0, expect = 0)
    private void drivebysable$endVehicleMove(
            final ServerLevel level,
            final ServerPlayer player,
            final ItemStack stack,
            final ServerSubLevel containing,
            final List<ServerSubLevel> expected,
            final long expectedBlocks,
            final double progressTicks,
            final CallbackInfo ci
    ) {
        ToolgunStructureMove.end();
    }

    @Inject(method = "captureIntoItem", at = @At("HEAD"), require = 0, expect = 0)
    private void drivebysable$beginStructureMove(
            final ServerLevel level,
            final UseOnContext context,
            final ItemStack stack,
            final CallbackInfo ci
    ) {
        ToolgunStructureMove.begin(level);
    }

    @Inject(method = "captureIntoItem", at = @At("RETURN"), require = 0, expect = 0)
    private void drivebysable$endStructureMove(
            final ServerLevel level,
            final UseOnContext context,
            final ItemStack stack,
            final CallbackInfo ci
    ) {
        ToolgunStructureMove.end();
    }
}