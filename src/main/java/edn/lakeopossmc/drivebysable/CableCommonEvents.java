package edn.lakeopossmc.drivebysable;

import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.compat.LinkedControllerCableServerHandler;
import edn.lakeopossmc.drivebysable.compat.TweakedControllerCableServerHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Comparator;
import java.util.EnumSet;

public final class CableCommonEvents {
    private CableCommonEvents() {
    }

    public static void onLevelTick(final LevelTickEvent.Post event) {
        final Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        CableNetworkManager.get(level).flushPendingGraphRebuild(level);
        LinkedControllerCableServerHandler.tick(level);
        TweakedControllerCableServerHandler.tick(level);
    }

    public static void onNeighborNotify(final BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof final ServerLevel level)) {
            return;
        }

        final BlockPos pos = event.getPos();
        final BlockState state = level.getBlockState(pos);
        if (state.isSignalSource()) {
            final int maxSignal = EnumSet.allOf(Direction.class)
                    .stream()
                    .map(direction -> state.getSignal(level, pos, direction))
                    .max(Comparator.naturalOrder())
                    .orElse(0);
            CableNetworkManager.trySetSignalAt(level, pos, CableNetworkManager.WORLD_CHANNEL, maxSignal);
        }

        for (final Direction notifiedSide : event.getNotifiedSides()) {
            final BlockPos neighborPos = pos.relative(notifiedSide);
            if (!level.getBlockState(neighborPos).isSignalSource()) {
                CableNetworkManager.trySetSignalAt(
                        level,
                        neighborPos,
                        CableNetworkManager.WORLD_CHANNEL,
                        level.getBestNeighborSignal(neighborPos)
                );
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof final ServerLevel level) {
            final ServerPlayer player = event.getPlayer() instanceof final ServerPlayer serverPlayer ? serverPlayer : null;
            CableNetworkManager.get(level).removeAllFromSourceInternal(player, level, event.getPos());
        }
    }
}