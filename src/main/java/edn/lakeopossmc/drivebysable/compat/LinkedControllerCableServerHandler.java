package edn.lakeopossmc.drivebysable.compat;

import com.mojang.datafixers.util.Pair;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// --- BRIDGES LINKED CONTROLLER BUTTONS TO CABLES --- //
// * Buttons auto release after timeout in case a stop packet is missed
public final class LinkedControllerCableServerHandler {
    public static final String[] KEY_TO_CHANNEL = new String[] {"keyUp", "keyDown", "keyLeft", "keyRight", "keyJump", "keyShift"};
    private static final int TIMEOUT = 30;
    private static final WorldAttached<Map<Pair<BlockPos, Integer>, Integer>> TIMEOUT_MAP = new WorldAttached<>(level -> new HashMap<>());

    private LinkedControllerCableServerHandler() {
    }

    // * Count down held buttons, clear signal once expired
    public static void tick(final Level level) {
        final Iterator<Map.Entry<Pair<BlockPos, Integer>, Integer>> iterator = TIMEOUT_MAP.get(level).entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<Pair<BlockPos, Integer>, Integer> entry = iterator.next();
            final int ttl = entry.getValue() - 1;
            if (ttl <= 0) {
                final Pair<BlockPos, Integer> key = entry.getKey();
                ControllerSignalStore.setSignal(level, key.getFirst(), KEY_TO_CHANNEL[key.getSecond()], 0);
                iterator.remove();
                continue;
            }

            entry.setValue(ttl);
        }
    }

    public static void receivePressed(final Level level, final BlockPos pos, final Collection<Integer> buttons, final boolean pressed) {
        final Map<Pair<BlockPos, Integer>, Integer> timeoutMap = TIMEOUT_MAP.get(level);
        for (final Integer button : buttons) {
            final Pair<BlockPos, Integer> key = Pair.of(pos.immutable(), button);
            ControllerSignalStore.setSignal(level, pos, KEY_TO_CHANNEL[button], pressed ? 15 : 0);
            if (pressed) {
                timeoutMap.put(key, TIMEOUT);
            } else {
                timeoutMap.remove(key);
            }
        }
    }

    public static void reset(final Level level, final BlockPos pos) {
        final Map<Pair<BlockPos, Integer>, Integer> timeoutMap = TIMEOUT_MAP.get(level);
        for (int index = 0; index < KEY_TO_CHANNEL.length; index++) {
            ControllerSignalStore.setSignal(level, pos, KEY_TO_CHANNEL[index], 0);
            timeoutMap.remove(Pair.of(pos, index));
        }
    }
}
