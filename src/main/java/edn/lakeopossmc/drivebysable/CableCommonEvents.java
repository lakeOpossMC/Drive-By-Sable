package edn.lakeopossmc.drivebysable;

import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.CableSelectionTracker;
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
import edn.lakeopossmc.drivebysable.cable.SubTargetCableEndpoint;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.world.level.block.Block;
import java.util.List;
import net.neoforged.neoforge.event.level.ChunkEvent;
import java.util.HashMap;
import java.util.Map;

// --- SHARED SERVER SIDE EVENT HOOKS --- //
public final class CableCommonEvents {
    private CableCommonEvents() {
    }

    // * Flush graph rebuilds and tick controller compat
    // * Marking the graph dirty lets the next tick push signals again
    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof final ServerLevel level)) {
            return;
        }

        CableNetworkManager.get(level).markDirtyIfChunkInvolved(event.getChunk().getPos());
    }

    // * Otherwise a player who logs out mid-selection stays marked forever
    @SubscribeEvent
    public static void onPlayerLoggedOut(final net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        CableSelectionTracker.forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        final Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        CableNetworkManager.get(level).flushPendingGraphRebuild(level);
        CableNetworkManager.get(level).tickPendingBinds(level);
        CableNetworkManager.get(level).tickPendingPublishes(level);
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

    // * Drop connections when a source is mined/moved by piston
    // * Sublevel assembly moves are remapped instead
    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof final ServerLevel level)) {
            return;
        }

        final BlockPos pos = event.getPos().immutable();
        if (CableNetworkManager.isPendingAssembly(level, pos)) {
            return;
        }

        final ServerPlayer player = event.getPlayer() instanceof final ServerPlayer serverPlayer ? serverPlayer : null;

        final Block brokenBlock = event.getState().getBlock();

        if (brokenBlock instanceof final SubTargetCableEndpoint endpoint) {
            removeVanishedSubTargets(level, pos, player, brokenBlock, endpoint);
            return;
        }

        // * Run immediately so the player refund fires before onRemove
        CableNetworkManager.get(level).removeAllFromSourceInternal(player, level, pos);
    }

    // * Which modules the break actually took
    private static void removeVanishedSubTargets(
            final ServerLevel level,
            final BlockPos pos,
            final ServerPlayer player,
            final Block brokenBlock,
            final SubTargetCableEndpoint endpoint
    ) {
        final MinecraftServer server = level.getServer();
        final List<String> before = List.copyOf(endpoint.cable$getSubTargets(level, pos));

        final Map<String, Integer> connectionsBefore = new HashMap<>();
        for (final String subTarget : before) {
            connectionsBefore.put(subTarget, CableNetworkManager.countConnectionsForSubTarget(level, pos, subTarget));
        }

        if (server == null) {
            CableNetworkManager.get(level).removeAllFromSourceInternal(player, level, pos);
            return;
        }

        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (CableNetworkManager.isPendingAssembly(level, pos)) {
                return;
            }

            if (!level.getBlockState(pos).is(brokenBlock)) {
                CableNetworkManager.get(level).removeAllFromSourceInternal(player, level, pos);
                return;
            }

            if (!(level.getBlockState(pos).getBlock() instanceof final SubTargetCableEndpoint current)) {
                return;
            }

            // * Refund covers exactly that module
            final List<String> remaining = current.cable$getSubTargets(level, pos);
            for (final String subTarget : before) {
                if (remaining.contains(subTarget)) {
                    continue;
                }

                // * Whatever is left is removed here
                final int stillThere = CableNetworkManager.countConnectionsForSubTarget(level, pos, subTarget);
                final int alreadyGone = connectionsBefore.getOrDefault(subTarget, 0) - stillThere;

                if (alreadyGone > 0) {
                    CableNetworkManager.refundCables(player, level, alreadyGone);
                }

                CableNetworkManager.removeAllForSubTarget(player, level, pos, subTarget);
            }
        }));
    }
}