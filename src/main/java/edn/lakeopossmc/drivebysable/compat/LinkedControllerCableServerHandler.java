package edn.lakeopossmc.drivebysable.compat;

import com.mojang.datafixers.util.Pair;

import edn.lakeopossmc.drivebysable.compat.computercraft.ComputerCraftCompat;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

// --- BRIDGES LINKED CONTROLLER BUTTONS TO CABLES --- //
// * Buttons auto release after timeout in case a stop packet is missed
public final class LinkedControllerCableServerHandler {
    public static final String[] KEY_TO_CHANNEL = new String[] {"keyUp", "keyDown", "keyLeft", "keyRight", "keyJump", "keyShift"};
    private static final int TIMEOUT = 30;
    private static final WorldAttached<Map<Pair<BlockPos, Integer>, Integer>> TIMEOUT_MAP = new WorldAttached<>(level -> new HashMap<>());
    private static final WorldAttached<Map<BlockPos, Set<Integer>>> PRESSED_MAP = new WorldAttached<>(level -> new HashMap<>());

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
            final boolean wasPressed = PRESSED_MAP.get(level).getOrDefault(pos, Set.of()).contains(button);

            if (pressed) {
                timeoutMap.put(key, TIMEOUT);
                PRESSED_MAP.get(level).computeIfAbsent(pos, k -> new HashSet<>()).add(button);
            } else {
                timeoutMap.remove(key);
                PRESSED_MAP.get(level).computeIfAbsent(pos, k -> new HashSet<>()).remove(button);
            }

            ComputerCraftCompat.handleCableHubKeyPress(level, pos, button, pressed, wasPressed);
        }
    }

    public static void reset(final Level level, final BlockPos pos) {
        final Map<Pair<BlockPos, Integer>, Integer> timeoutMap = TIMEOUT_MAP.get(level);
        for (int index = 0; index < KEY_TO_CHANNEL.length; index++) {
            ControllerSignalStore.setSignal(level, pos, KEY_TO_CHANNEL[index], 0);
            timeoutMap.remove(Pair.of(pos, index));
            PRESSED_MAP.get(level).computeIfAbsent(pos, k -> new HashSet<>()).remove(index);
            ComputerCraftCompat.handleCableHubKeyPress(level, pos, index, false, false);
        }
    }

    public static Set<Integer> getPressed(final Level level, final BlockPos pos) {
        return PRESSED_MAP.get(level).getOrDefault(pos, Set.of());
    }
}
