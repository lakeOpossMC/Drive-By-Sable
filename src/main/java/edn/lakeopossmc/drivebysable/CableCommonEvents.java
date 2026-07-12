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

// --- SHARED SERVER SIDE EVENT HOOKS --- //
public final class CableCommonEvents {
    private CableCommonEvents() {
    }

    // * Flush graph rebuilds and tick controller compat
    public static void onLevelTick(final LevelTickEvent.Post event) {
        final Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        CableNetworkManager.get(level).flushPendingGraphRebuild(level);
        LinkedControllerCableServerHandler.tick(level);
        TweakedControllerCableServerHandler.tick(level);
    }

    private static final Direction[] DIRECTIONS = Direction.values();

    //#region // --- REDSTONE PROPAGATION INTO NETWORK --- //
    public static void onNeighborNotify(final BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof final ServerLevel level)) {
            return;
        }

        final CableNetworkManager manager = CableNetworkManager.get(level);
        final BlockPos pos = event.getPos();

        // * Read strongest signal out of the source itself
        if (manager.hasSinks(pos, CableNetworkManager.WORLD_CHANNEL)) {
            final BlockState state = level.getBlockState(pos);
            if (state.isSignalSource()) {
                int maxSignal = 0;
                for (final Direction direction : DIRECTIONS) {
                    final int signal = state.getSignal(level, pos, direction);
                    if (signal > maxSignal) {
                        maxSignal = signal;
                    }
                }
                CableNetworkManager.trySetSignalAt(level, pos, CableNetworkManager.WORLD_CHANNEL, maxSignal);
            }
        }

        // * Same but for each notified neighbor
        for (final Direction notifiedSide : event.getNotifiedSides()) {
            final BlockPos neighborPos = pos.relative(notifiedSide);
            if (!manager.hasSinks(neighborPos, CableNetworkManager.WORLD_CHANNEL)) {
                continue;
            }
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
    //#endregion

    // * Drop connections when a source is mined
    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof final ServerLevel level) {
            final ServerPlayer player = event.getPlayer() instanceof final ServerPlayer serverPlayer ? serverPlayer : null;
            CableNetworkManager.get(level).removeAllFromSourceInternal(player, level, event.getPos());
        }
    }
}