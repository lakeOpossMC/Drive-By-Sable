package edn.lakeopossmc.drivebysable.cable.graph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

// --- ONE SINK POSITION IN THE NETWORK --- //
// * Tracks each source input feeding this sink face
public class CableNetworkNode {
    private final Map<InputKey, Integer> inputs = new HashMap<>();
    private final long position;
    private final int direction;

    public CableNetworkNode(final BlockPos pos, final Direction direction) {
        this(pos.asLong(), direction.get3DDataValue());
    }

    public CableNetworkNode(final long position, final int direction) {
        this.position = position;
        this.direction = direction;
    }

    // * Zero signal just clears the input
    public boolean setInput(final InputKey key, final int signal) {
        if (signal <= 0) {
            return inputs.remove(key) != null;
        }

        final Integer previous = inputs.put(key, signal);
        return previous == null || previous != signal;
    }

    public boolean isEmpty() {
        return inputs.isEmpty();
    }

    // * Strongest input wins, like vanilla redstone
    public int getSignal() {
        return inputs.values().stream().max(Comparator.naturalOrder()).orElse(0);
    }

    public long getPosition() {
        return position;
    }

    public int getDirection() {
        return direction;
    }

    public record InputKey(long sourcePos, String channel) {
    }

    public record CableNetworkSink(long position, int direction) {
        public static CableNetworkSink of(final BlockPos pos, final Direction direction) {
            return new CableNetworkSink(pos.asLong(), direction.get3DDataValue());
        }
    }
}
