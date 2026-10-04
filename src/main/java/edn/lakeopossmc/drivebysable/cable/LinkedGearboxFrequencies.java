package edn.lakeopossmc.drivebysable.cable;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// --- LOADED LINKED GEARBOXES --- //
// * Server side only
public final class LinkedGearboxFrequencies {

    // * Frequency compares by item and colour
    public record Key(Frequency first, Frequency last) {
    }

    private static final WorldAttached<Map<Key, Set<BlockPos>>> GROUPS = new WorldAttached<>(level -> new HashMap<>());

    private LinkedGearboxFrequencies() {
    }

    // * Null when both slots are empty
    @Nullable
    public static Key keyOf(@Nullable final LinkBehaviour behaviour) {
        if (behaviour == null) {
            return null;
        }
        final Couple<Frequency> couple = behaviour.getNetworkKey();
        if (couple.getFirst().getStack().isEmpty() && couple.getSecond().getStack().isEmpty()) {
            return null;
        }
        return new Key(couple.getFirst(), couple.getSecond());
    }

    public static void move(final LevelAccessor level, final BlockPos pos, @Nullable final Key from, @Nullable final Key to) {
        final Map<Key, Set<BlockPos>> groups = GROUPS.get(level);
        if (from != null) {
            final Set<BlockPos> members = groups.get(from);
            if (members != null) {
                members.remove(pos);
                if (members.isEmpty()) {
                    groups.remove(from);
                }
            }
        }
        if (to != null) {
            groups.computeIfAbsent(to, ignored -> new LinkedHashSet<>()).add(pos.immutable());
        }
    }

    // * Everything else on this frequency
    public static Set<BlockPos> partners(final LevelAccessor level, final BlockPos pos, @Nullable final Key key) {
        if (key == null) {
            return Set.of();
        }
        final Set<BlockPos> members = GROUPS.get(level).get(key);
        if (members == null || members.size() < 2) {
            return Set.of();
        }
        final Set<BlockPos> partners = new LinkedHashSet<>(members);
        partners.remove(pos);
        return partners;
    }
}