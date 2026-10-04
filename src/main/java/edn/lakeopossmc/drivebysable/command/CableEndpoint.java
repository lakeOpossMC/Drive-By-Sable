package edn.lakeopossmc.drivebysable.command;

import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager.IncomingConnection;
import edn.lakeopossmc.drivebysable.cable.SubTargetCableEndpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// --- ONE END OF A CONNECTION --- //
public record CableEndpoint(BlockPos pos, String module, boolean source) {

    public boolean hasModule() {
        return !module.isEmpty();
    }

    // * What the block sends from
    private static List<CableEndpoint> sourcesAt(
            final Level level,
            final CableNetworkManager manager,
            final BlockPos pos
    ) {
        // * Read through CableChannels, which leaves Linked Gearbox links out
        final Set<String> channels = CableChannels.connections(manager, pos).keySet();
        if (channels.isEmpty()) {
            return List.of();
        }

        if (!(level.getBlockState(pos).getBlock() instanceof final SubTargetCableEndpoint endpoint)) {
            return List.of(new CableEndpoint(pos, "", true));
        }

        // * Channels belonging to the same part collapse into one endpoint
        final Set<String> modules = new LinkedHashSet<>();
        boolean bare = false;
        for (final String channel : channels) {
            final String module = endpoint.cable$subTargetForChannel(level, pos, channel);
            if (module == null || module.isEmpty()) {
                bare = true;
            } else {
                modules.add(module);
            }
        }

        final List<CableEndpoint> endpoints = new ArrayList<>(modules.size() + 1);
        for (final String module : modules) {
            endpoints.add(new CableEndpoint(pos, module, true));
        }
        if (bare || modules.isEmpty()) {
            endpoints.add(new CableEndpoint(pos, "", true));
        }
        return endpoints;
    }

    // * What drives the block
    private static List<CableEndpoint> outputsAt(
            final Level level,
            final CableNetworkManager manager,
            final BlockPos pos
    ) {
        final List<IncomingConnection> incoming = CableChannels.incoming(manager, pos);
        if (incoming.isEmpty()) {
            return List.of();
        }

        final Set<String> modules = new LinkedHashSet<>();
        boolean face = false;
        for (final IncomingConnection connection : incoming) {
            if (connection.isModule()) {
                // * Named by the part it lands on
                modules.add(SourceModules.moduleForSinkChannel(level, pos, connection.sinkChannel()));
            } else {
                face = true;
            }
        }

        final List<CableEndpoint> endpoints = new ArrayList<>(modules.size() + 1);
        for (final String module : modules) {
            endpoints.add(new CableEndpoint(pos, module, false));
        }
        if (face) {
            endpoints.add(new CableEndpoint(pos, "", false));
        }
        return endpoints;
    }

    // * Everything on this block for the side being asked about
    public static List<CableEndpoint> at(
            final Level level,
            final CableNetworkManager manager,
            final BlockPos pos,
            final boolean sources
    ) {
        return sources ? sourcesAt(level, manager, pos) : outputsAt(level, manager, pos);
    }
}