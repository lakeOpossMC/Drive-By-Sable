package edn.lakeopossmc.drivebysable.cable;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// --- WHO IS PART WAY THROUGH PICKING A CONNECTION --- //
public final class CableSelectionTracker {

    // * Without this, Clear All and Cancel happen simultaneously
    private static final long GRACE_TICKS = 5L;

    private static final Set<UUID> SELECTING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> RELEASED = new ConcurrentHashMap<>();

    private CableSelectionTracker() {
    }

    public static void setSelecting(final Player player, final boolean selecting) {
        if (player == null) {
            return;
        }

        final UUID id = player.getUUID();
        if (selecting) {
            RELEASED.remove(id);
            SELECTING.add(id);
            return;
        }

        if (SELECTING.remove(id)) {
            RELEASED.put(id, player.level().getGameTime());
        }
    }

    // * True while a selection is open, and for a few ticks after one closes
    public static boolean isSelecting(final Player player) {
        if (player == null) {
            return false;
        }

        final UUID id = player.getUUID();
        if (SELECTING.contains(id)) {
            return true;
        }

        final Long released = RELEASED.get(id);
        if (released == null) {
            return false;
        }

        if (player.level().getGameTime() - released <= GRACE_TICKS) {
            return true;
        }

        RELEASED.remove(id);
        return false;
    }

    public static void forget(final Player player) {
        if (player != null) {
            SELECTING.remove(player.getUUID());
            RELEASED.remove(player.getUUID());
        }
    }
}