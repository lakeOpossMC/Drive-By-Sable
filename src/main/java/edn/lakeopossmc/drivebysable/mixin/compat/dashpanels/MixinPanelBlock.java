package edn.lakeopossmc.drivebysable.mixin.compat.dashpanels;

import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import edn.lakeopossmc.drivebysable.compat.dashpanels.DashPanelCableBridge;
import moth.boxxed.panels.content.panel.PanelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// --- MAKES THE DASH PANEL A DYNAMIC CABLE SOURCE --- //
// * Pseudo since mod may not be loaded
@Pseudo
@Mixin(PanelBlock.class)
public abstract class MixinPanelBlock implements MultiChannelCableSource {

    @Override
    public List<String> cable$getChannels(final Level level, final BlockPos pos) {
        return DashPanelCableBridge.getChannels(level, pos);
    }

    @Override
    public String cable$nextChannel(final Level level, final BlockPos pos, final String current, final boolean forward) {
        return DashPanelCableBridge.nextChannel(level, pos, current, forward);
    }

    // * Clear bridge state when block actually replaced
    @Inject(
            method = "onRemove",
            at = @At("TAIL")
    )
    private void drivebysable$clearCableBridge(
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final BlockState newState,
            final boolean movedByPiston,
            final CallbackInfo ci
    ) {
        if (!state.is(newState.getBlock()) && level instanceof final ServerLevel serverLevel) {
            DashPanelCableBridge.clear(serverLevel, pos);
        }
    }
}