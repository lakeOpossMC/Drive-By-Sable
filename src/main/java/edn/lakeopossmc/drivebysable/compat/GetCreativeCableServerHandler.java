package edn.lakeopossmc.drivebysable.compat;

import com.mojang.datafixers.util.Pair;
import edn.lakeopossmc.drivebysable.blocks.IntermediateCableHubBlockEntity;
import edn.lakeopossmc.drivebysable.compat.computercraft.ComputerCraftCompat;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// --- BRIDGES GET CREATIVE LINKED DEVICES TO CABLES --- //
// * Never touches a Get Creative class
public final class GetCreativeCableServerHandler {
    public static final String MOD_ID = "get_creative";

    //#region // --- CHANNEL LAYOUTS --- //
    // * Order matches the button order Get Creative registers for each device
    public static final String[] MACRO_TO_CHANNEL = new String[] {
            "macroUp",
            "macroLeft",
            "macroDown",
            "macroRight",
            "macroArrowUp",
            "macroArrowLeft",
            "macroArrowDown",
            "macroArrowRight",
            "macroL1",
            "macroL2",
            "macroR1",
            "macroR2",
            "macroJump",
            "macroShift"
    };
    public static final String[] KEYPAD_TO_CHANNEL = new String[] {
            "keypad1",
            "keypad2",
            "keypad3",
            "keypad4",
            "keypad5",
            "keypad6",
            "keypad7",
            "keypad8",
            "keypad9",
            "keypadJump",
            "keypadShift"
    };
    public static final String[] REMOTE_TO_CHANNEL = new String[] {
            "remoteButton"
    };

    // * One channel group per device type
    public static final String GROUP_MACRO = "macro";
    public static final String GROUP_KEYPAD = "keypad";
    public static final String GROUP_REMOTE = "remote";
    public static final List<String> GROUP_ORDER = List.of(GROUP_MACRO, GROUP_KEYPAD, GROUP_REMOTE);

    // * Registry path of each supported device
    private static final Map<String, String> DEVICE_TO_GROUP = Map.of(
            "linked_macro_controller", GROUP_MACRO,
            "linked_keypad", GROUP_KEYPAD,
            "linked_remote", GROUP_REMOTE
    );

    private static final Map<String, List<String>> GROUP_TO_CHANNELS = Map.of(
            GROUP_MACRO, List.of(MACRO_TO_CHANNEL),
            GROUP_KEYPAD, List.of(KEYPAD_TO_CHANNEL),
            GROUP_REMOTE, List.of(REMOTE_TO_CHANNEL)
    );

    // * Macro controller first since it is the full sized device
    public static final List<String> ALL_CHANNELS = buildAllChannels();

    // * Fixed mapping from channel id to its lang key
    public static final Map<String, String> CHANNEL_TO_LANG_KEY = buildLangKeys();
    //#endregion

    private static final int TIMEOUT = 30;
    private static final WorldAttached<Map<Pair<BlockPos, String>, Integer>> TIMEOUT_MAP = new WorldAttached<>(level -> new HashMap<>());
    private static final WorldAttached<Map<BlockPos, Set<String>>> PRESSED_MAP = new WorldAttached<>(level -> new HashMap<>());

    private GetCreativeCableServerHandler() {
    }

    //#region // --- DEVICE LOOKUP --- //
    // * Null for anything that isnt a supported Get Creative device
    @Nullable
    public static String[] channelsFor(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        return channelsFor(itemStack.getItem());
    }

    @Nullable
    public static String[] channelsFor(final Item item) {
        final String group = groupFor(item);
        return group == null ? null : GROUP_TO_CHANNELS.get(group).toArray(new String[0]);
    }

    // * Which channel group a device opens on the hub, null if unsupported
    @Nullable
    public static String groupFor(final ItemStack itemStack) {
        return itemStack == null || itemStack.isEmpty() ? null : groupFor(itemStack.getItem());
    }

    @Nullable
    public static String groupFor(final Item item) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (!MOD_ID.equals(id.getNamespace())) {
            return null;
        }
        return DEVICE_TO_GROUP.get(id.getPath());
    }

    public static List<String> channelsInGroup(final String group) {
        return GROUP_TO_CHANNELS.getOrDefault(group, List.of());
    }

    public static String groupLangKey(final String group) {
        return "drivebysable.get_creative.group." + group;
    }

    public static boolean isSupportedDevice(final ItemStack itemStack) {
        return channelsFor(itemStack) != null;
    }
    //#endregion

    //#region // --- INPUT --- //
    public static void receivePressed(
            final Level level,
            final BlockPos pos,
            final ItemStack device,
            final Collection<Integer> buttons,
            final boolean pressed
    ) {
        final String group = groupFor(device);
        if (group == null) {
            return;
        }

        // * Device types that were never bound to this hub have no open channels
        if (level.getBlockEntity(pos) instanceof final IntermediateCableHubBlockEntity hub && !hub.isGroupOpen(group)) {
            return;
        }

        final String[] channels = channelsFor(device);

        final BlockPos hubPos = pos.immutable();
        final Map<Pair<BlockPos, String>, Integer> timeoutMap = TIMEOUT_MAP.get(level);
        final Set<String> pressedSet = PRESSED_MAP.get(level).computeIfAbsent(hubPos, k -> new HashSet<>());
        for (final Integer button : buttons) {
            if (button == null || button < 0 || button >= channels.length) {
                continue;
            }

            final String channel = channels[button];
            final Pair<BlockPos, String> key = Pair.of(hubPos, channel);
            final boolean wasPressed = pressedSet.contains(channel);
            ControllerSignalStore.setSignal(level, hubPos, channel, pressed ? 15 : 0);

            if (pressed) {
                timeoutMap.put(key, TIMEOUT);
                pressedSet.add(channel);
            } else {
                timeoutMap.remove(key);
                pressedSet.remove(channel);
            }

            ComputerCraftCompat.handleChannelHubPress(level, hubPos, channel, pressed, wasPressed);
        }
    }

    // * Drop every held channel on this hub
    public static void reset(final Level level, final BlockPos pos) {
        final BlockPos hubPos = pos.immutable();
        final Map<Pair<BlockPos, String>, Integer> timeoutMap = TIMEOUT_MAP.get(level);
        final Set<String> pressedSet = PRESSED_MAP.get(level).remove(hubPos);
        for (final String channel : ALL_CHANNELS) {
            ControllerSignalStore.setSignal(level, hubPos, channel, 0);
            timeoutMap.remove(Pair.of(hubPos, channel));
        }

        if (pressedSet != null) {
            for (final String channel : pressedSet) {
                ComputerCraftCompat.handleChannelHubPress(level, hubPos, channel, false, true);
            }
        }
    }

    // * Count down held buttons, clear signal once expired
    public static void tick(final Level level) {
        final Map<Pair<BlockPos, String>, Integer> timeoutMap = TIMEOUT_MAP.get(level);
        if (timeoutMap.isEmpty()) {
            return;
        }

        final Iterator<Map.Entry<Pair<BlockPos, String>, Integer>> iterator = timeoutMap.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<Pair<BlockPos, String>, Integer> entry = iterator.next();
            final int ttl = entry.getValue() - 1;
            if (ttl > 0) {
                entry.setValue(ttl);
                continue;
            }

            final BlockPos hubPos = entry.getKey().getFirst();
            final String channel = entry.getKey().getSecond();
            iterator.remove();
            ControllerSignalStore.setSignal(level, hubPos, channel, 0);

            final Set<String> pressedSet = PRESSED_MAP.get(level).get(hubPos);
            if (pressedSet != null && pressedSet.remove(channel)) {
                ComputerCraftCompat.handleChannelHubPress(level, hubPos, channel, false, true);
            }
        }
    }

    public static Set<String> getPressed(final Level level, final BlockPos pos) {
        return PRESSED_MAP.get(level).getOrDefault(pos, Set.of());
    }
    //#endregion

    //#region // --- BUILDERS --- //
    private static List<String> buildAllChannels() {
        final Set<String> channels = new LinkedHashSet<>();
        channels.addAll(List.of(MACRO_TO_CHANNEL));
        channels.addAll(List.of(KEYPAD_TO_CHANNEL));
        channels.addAll(List.of(REMOTE_TO_CHANNEL));
        return List.copyOf(new ArrayList<>(channels));
    }

    private static Map<String, String> buildLangKeys() {
        final Map<String, String> keys = new HashMap<>();
        for (final String channel : MACRO_TO_CHANNEL) {
            keys.put(channel, "drivebysable.get_creative.macro." + toSnake(channel.substring("macro".length())));
        }
        for (final String channel : KEYPAD_TO_CHANNEL) {
            keys.put(channel, "drivebysable.get_creative.keypad." + toSnake(channel.substring("keypad".length())));
        }
        for (final String channel : REMOTE_TO_CHANNEL) {
            keys.put(channel, "drivebysable.get_creative.remote." + toSnake(channel.substring("remote".length())));
        }
        return Map.copyOf(keys);
    }

    private static String toSnake(final String camel) {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < camel.length(); index++) {
            final char character = camel.charAt(index);
            if (Character.isUpperCase(character) && index > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(character));
        }
        return builder.toString();
    }
    //#endregion
}
