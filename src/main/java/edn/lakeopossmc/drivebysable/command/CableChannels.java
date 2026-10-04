package edn.lakeopossmc.drivebysable.command;

import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager.IncomingConnection;
import edn.lakeopossmc.drivebysable.cable.LinkedGearboxLinks;
import edn.lakeopossmc.drivebysable.cable.graph.CableNetworkNode.CableNetworkSink;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// --- THE CHANNELS ON ONE ENDPOINT --- //
public final class CableChannels {
    private CableChannels() {
    }

    //#region // --- WHAT THE COMMANDS SEE OF THE NETWORK --- //
    // * Linked Gearbox links are left out of everything here
    public static Map<String, List<CableNetworkSink>> connections(final CableNetworkManager manager, final BlockPos pos) {
        final Map<String, List<CableNetworkSink>> channels = new LinkedHashMap<>();
        manager.getConnections(pos).forEach((channel, sinks) -> {
            final List<CableNetworkSink> kept = new ArrayList<>(sinks.size());
            for (final CableNetworkSink sink : sinks) {
                if (!LinkedGearboxLinks.isStressLink(channel, sink.sinkChannel())) {
                    kept.add(sink);
                }
            }
            if (!kept.isEmpty()) {
                channels.put(channel, kept);
            }
        });
        return channels;
    }

    public static List<IncomingConnection> incoming(final CableNetworkManager manager, final BlockPos pos) {
        final List<IncomingConnection> kept = new ArrayList<>();
        for (final IncomingConnection incoming : manager.getIncoming(pos)) {
            if (!LinkedGearboxLinks.isStressLink(incoming.channel(), incoming.sinkChannel())) {
                kept.add(incoming);
            }
        }
        return kept;
    }

    public static boolean isSource(final CableNetworkManager manager, final BlockPos pos) {
        return manager.isSource(pos) && !connections(manager, pos).isEmpty();
    }

    public static boolean isOutput(final CableNetworkManager manager, final BlockPos pos) {
        return manager.isOutput(pos) && !incoming(manager, pos).isEmpty();
    }

    public static boolean isEndpoint(final CableNetworkManager manager, final BlockPos pos) {
        return isSource(manager, pos) || isOutput(manager, pos);
    }

    // * True when the block also has gearbox links
    public static boolean hasGearboxLinks(final CableNetworkManager manager, final BlockPos pos) {
        for (final Map.Entry<String, List<CableNetworkSink>> entry : manager.getConnections(pos).entrySet()) {
            for (final CableNetworkSink sink : entry.getValue()) {
                if (LinkedGearboxLinks.isStressLink(entry.getKey(), sink.sinkChannel())) {
                    return true;
                }
            }
        }
        for (final IncomingConnection incoming : manager.getIncoming(pos)) {
            if (LinkedGearboxLinks.isStressLink(incoming.channel(), incoming.sinkChannel())) {
                return true;
            }
        }
        return false;
    }
    //#endregion

    public static Map<String, List<CableNetworkSink>> sending(
            final ServerLevel level,
            final CableNetworkManager manager,
            final CableEndpoint endpoint
    ) {
        final Map<String, List<CableNetworkSink>> channels = new LinkedHashMap<>();
        connections(manager, endpoint.pos()).forEach((channel, sinks) -> {
            if (belongsTo(level, endpoint, channel)) {
                channels.put(channel, List.copyOf(sinks));
            }
        });
        return channels;
    }

    public static Map<String, List<IncomingConnection>> receiving(
            final ServerLevel level,
            final CableNetworkManager manager,
            final CableEndpoint endpoint
    ) {
        final Map<String, List<IncomingConnection>> channels = new LinkedHashMap<>();
        for (final IncomingConnection incoming : incoming(manager, endpoint.pos())) {
            if (endpoint.hasModule()
                    && !SourceModules.receivedBy(level, endpoint.pos(), incoming, endpoint.module())) {
                continue;
            }
            final String name = incoming.isModule() ? incoming.sinkChannel() : incoming.direction().getName();
            channels.computeIfAbsent(name, ignored -> new ArrayList<>()).add(incoming);
        }
        return channels;
    }

    public static int outputCount(final Map<String, List<CableNetworkSink>> sending) {
        final List<Long> seen = new ArrayList<>();
        for (final List<CableNetworkSink> sinks : sending.values()) {
            for (final CableNetworkSink sink : sinks) {
                if (!seen.contains(sink.position())) {
                    seen.add(sink.position());
                }
            }
        }
        return seen.size();
    }

    public static int sourceCount(final Map<String, List<IncomingConnection>> receiving) {
        final List<Long> seen = new ArrayList<>();
        for (final List<IncomingConnection> incoming : receiving.values()) {
            for (final IncomingConnection connection : incoming) {
                if (!seen.contains(connection.source().asLong())) {
                    seen.add(connection.source().asLong());
                }
            }
        }
        return seen.size();
    }

    public static int sourceCount(final List<IncomingConnection> incoming) {
        return sourceCount(Map.of("", incoming));
    }

    private static boolean belongsTo(final ServerLevel level, final CableEndpoint endpoint, final String channel) {
        if (endpoint.hasModule()) {
            return SourceModules.sendsFrom(level, endpoint.pos(), channel, endpoint.module());
        }
        return true;
    }
}