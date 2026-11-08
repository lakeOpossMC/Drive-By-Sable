package edn.lakeopossmc.drivebysable.cable;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// --- CABLES RIDE ALONG WHEN A SUBLEVEL CHANGES DIMENSION --- //
public final class SubLevelDimensionTransfer {

    private static final int WINDOW_TICKS = 200;
    private static final int RETRY_TICKS = 5;

    // * Server thread only
    private static final Map<UUID, Departure> departures = new HashMap<>();
    private static final List<Arrival> arrivals = new ArrayList<>();
    private static int tick;

    public record Carried(long source, String channel, long sink, int direction, String sinkChannel) {
    }

    private record Departure(
            UUID id,
            ResourceKey<Level> dimension,
            ChunkPos chunkMin,
            List<Carried> connections,
            Map<Long, String> endpoints,
            int tick
    ) {
    }

    private record Arrival(ServerLevel level, SubLevel subLevel, int tick) {
    }

    private SubLevelDimensionTransfer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SubLevelDimensionTransfer::onServerTick);
        NeoForge.EVENT_BUS.addListener(SubLevelDimensionTransfer::onServerStopped);
    }

    // * Before the plot is purged, while its blocks are still readable
    public static void departing(final ServerLevel level, final SubLevel subLevel) {
        final LevelPlot plot = subLevel.getPlot();
        final List<Carried> connections = CableNetworkManager.get(level).internalConnectionsIn(plot);
        if (connections.isEmpty()) {
            return;
        }

        final Map<Long, String> endpoints = new HashMap<>();
        for (final Carried connection : connections) {
            endpoints.computeIfAbsent(connection.source(), key -> blockIdAt(level, key));
            endpoints.computeIfAbsent(connection.sink(), key -> blockIdAt(level, key));
        }

        departures.put(subLevel.getUniqueId(), new Departure(
                subLevel.getUniqueId(), level.dimension(), plot.getChunkMin(), connections, endpoints, tick));
    }

    public static void arrived(final ServerLevel level, final SubLevel subLevel) {
        arrivals.add(new Arrival(level, subLevel, tick));
    }

    private static void onServerTick(final ServerTickEvent.Post event) {
        tick++;

        final Iterator<Arrival> pending = arrivals.iterator();
        while (pending.hasNext()) {
            final Arrival arrival = pending.next();
            final int age = tick - arrival.tick();

            if (arrival.subLevel().isRemoved() || age > WINDOW_TICKS) {
                pending.remove();
                continue;
            }

            if (!departures.isEmpty() && age > 0 && age % RETRY_TICKS == 1 && tryAdopt(arrival)) {
                pending.remove();
            }
        }

        departures.values().removeIf(departure -> tick - departure.tick() > WINDOW_TICKS);
    }

    private static void onServerStopped(final ServerStoppedEvent event) {
        departures.clear();
        arrivals.clear();
    }

    private static boolean tryAdopt(final Arrival arrival) {
        final ResourceKey<Level> dimension = arrival.level().dimension();
        final UUID id = arrival.subLevel().getUniqueId();

        final Departure sameId = departures.get(id);
        if (sameId != null && !sameId.dimension().equals(dimension) && adopt(arrival, sameId)) {
            return true;
        }

        for (final Departure departure : List.copyOf(departures.values())) {
            if (departure == sameId || departure.dimension().equals(dimension)) {
                continue;
            }
            if (adopt(arrival, departure)) {
                return true;
            }
        }

        return false;
    }

    private static boolean adopt(final Arrival arrival, final Departure departure) {
        final ServerLevel level = arrival.level();
        final LevelPlot plot = arrival.subLevel().getPlot();
        final ChunkPos chunkMin = plot.getChunkMin();
        final int dx = SectionPos.sectionToBlockCoord(chunkMin.x - departure.chunkMin().x);
        final int dz = SectionPos.sectionToBlockCoord(chunkMin.z - departure.chunkMin().z);

        for (final Map.Entry<Long, String> endpoint : departure.endpoints().entrySet()) {
            final BlockPos moved = BlockPos.of(endpoint.getKey()).offset(dx, 0, dz);
            final LevelChunk chunk = plot.getChunk(plot.toLocal(new ChunkPos(moved)));
            if (!plot.contains(new ChunkPos(moved)) || chunk == null) {
                return false;
            }
            if (!endpoint.getValue().equals(blockId(chunk.getBlockState(moved).getBlock()))) {
                return false;
            }
        }

        departures.remove(departure.id());
        final int carried = CableNetworkManager.get(level).adoptCarried(departure.connections(), dx, dz);

        DriveBySableMod.LOGGER.info(
                "[dimension-transfer] Carried {} connection(s) with sub-level {} from {} to {}.",
                carried,
                arrival.subLevel().getUniqueId(),
                departure.dimension().location(),
                level.dimension().location()
        );
        return true;
    }

    private static String blockIdAt(final ServerLevel level, final long key) {
        return blockId(level.getBlockState(BlockPos.of(key)).getBlock());
    }

    private static String blockId(final net.minecraft.world.level.block.Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }
}