package edn.lakeopossmc.drivebysable.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Map;

// --- READS DATA LEFT BEHIND BY DRIVE BY WIRE - TYPEWRITER --- //
// * A third party addon for the DBW mod, no longer updated
public final class LegacyTypewriterCompat {

    public static final String LEGACY_MOD_ID = "drivebywiretypewriter";
    public static final String LEGACY_BLOCK = "typewriter_hub";

    private static final String CHANNEL_KEY = "Channel";
    private static final String CONNECTIONS_KEY = "Connections";

    // * Matched by GLFW key code, so the two tables agree on which physical key each channel stood for
    private static final Map<String, String> CHANNEL_TO_CABLE = Map.ofEntries(
            Map.entry("drivebywire.typewriter.channel.space", "keySpace"),
            Map.entry("drivebywire.typewriter.channel.apostrophe", "keyApostrophe"),
            Map.entry("drivebywire.typewriter.channel.comma", "keyComma"),
            Map.entry("drivebywire.typewriter.channel.minus", "keyMinus"),
            Map.entry("drivebywire.typewriter.channel.period", "keyPeriod"),
            Map.entry("drivebywire.typewriter.channel.slash", "keySlash"),
            Map.entry("drivebywire.typewriter.channel.0", "key0"),
            Map.entry("drivebywire.typewriter.channel.1", "key1"),
            Map.entry("drivebywire.typewriter.channel.2", "key2"),
            Map.entry("drivebywire.typewriter.channel.3", "key3"),
            Map.entry("drivebywire.typewriter.channel.4", "key4"),
            Map.entry("drivebywire.typewriter.channel.5", "key5"),
            Map.entry("drivebywire.typewriter.channel.6", "key6"),
            Map.entry("drivebywire.typewriter.channel.7", "key7"),
            Map.entry("drivebywire.typewriter.channel.8", "key8"),
            Map.entry("drivebywire.typewriter.channel.9", "key9"),
            Map.entry("drivebywire.typewriter.channel.semicolon", "keySemicolon"),
            Map.entry("drivebywire.typewriter.channel.equals", "keyEqual"),
            Map.entry("drivebywire.typewriter.channel.a", "keyA"),
            Map.entry("drivebywire.typewriter.channel.b", "keyB"),
            Map.entry("drivebywire.typewriter.channel.c", "keyC"),
            Map.entry("drivebywire.typewriter.channel.d", "keyD"),
            Map.entry("drivebywire.typewriter.channel.e", "keyE"),
            Map.entry("drivebywire.typewriter.channel.f", "keyF"),
            Map.entry("drivebywire.typewriter.channel.g", "keyG"),
            Map.entry("drivebywire.typewriter.channel.h", "keyH"),
            Map.entry("drivebywire.typewriter.channel.i", "keyI"),
            Map.entry("drivebywire.typewriter.channel.j", "keyJ"),
            Map.entry("drivebywire.typewriter.channel.k", "keyK"),
            Map.entry("drivebywire.typewriter.channel.l", "keyL"),
            Map.entry("drivebywire.typewriter.channel.m", "keyM"),
            Map.entry("drivebywire.typewriter.channel.n", "keyN"),
            Map.entry("drivebywire.typewriter.channel.o", "keyO"),
            Map.entry("drivebywire.typewriter.channel.p", "keyP"),
            Map.entry("drivebywire.typewriter.channel.q", "keyQ"),
            Map.entry("drivebywire.typewriter.channel.r", "keyR"),
            Map.entry("drivebywire.typewriter.channel.s", "keyS"),
            Map.entry("drivebywire.typewriter.channel.t", "keyT"),
            Map.entry("drivebywire.typewriter.channel.u", "keyU"),
            Map.entry("drivebywire.typewriter.channel.v", "keyV"),
            Map.entry("drivebywire.typewriter.channel.w", "keyW"),
            Map.entry("drivebywire.typewriter.channel.x", "keyX"),
            Map.entry("drivebywire.typewriter.channel.y", "keyY"),
            Map.entry("drivebywire.typewriter.channel.z", "keyZ"),
            Map.entry("drivebywire.typewriter.channel.left_bracket", "keyLeftBracket"),
            Map.entry("drivebywire.typewriter.channel.backslash", "keyBackslash"),
            Map.entry("drivebywire.typewriter.channel.right_bracket", "keyRightBracket"),
            Map.entry("drivebywire.typewriter.channel.enter", "keyEnter"),
            Map.entry("drivebywire.typewriter.channel.tab", "keyTab"),
            Map.entry("drivebywire.typewriter.channel.backspace", "keyBackspace"),
            Map.entry("drivebywire.typewriter.channel.delete", "keyDel"),
            Map.entry("drivebywire.typewriter.channel.right", "keyRight"),
            Map.entry("drivebywire.typewriter.channel.left", "keyLeft"),
            Map.entry("drivebywire.typewriter.channel.down", "keyDown"),
            Map.entry("drivebywire.typewriter.channel.up", "keyUp"),
            Map.entry("drivebywire.typewriter.channel.page_up", "keyPgUp"),
            Map.entry("drivebywire.typewriter.channel.page_down", "keyPgDn"),
            Map.entry("drivebywire.typewriter.channel.end", "keyEnd"),
            Map.entry("drivebywire.typewriter.channel.caps_lock", "keyCapsLock"),
            Map.entry("drivebywire.typewriter.channel.left_shift", "keyLeftShift"),
            Map.entry("drivebywire.typewriter.channel.left_ctrl", "keyLeftCtrl"),
            Map.entry("drivebywire.typewriter.channel.left_alt", "keyLeftAlt"),
            Map.entry("drivebywire.typewriter.channel.left_super", "keyLeftWin"),
            Map.entry("drivebywire.typewriter.channel.right_shift", "keyRightShift"),
            Map.entry("drivebywire.typewriter.channel.right_ctrl", "keyRightCtrl"),
            Map.entry("drivebywire.typewriter.channel.right_alt", "keyRightAlt"),
            Map.entry("drivebywire.typewriter.channel.menu", "keyMenu")
    );

    private LegacyTypewriterCompat() {
    }

    public static boolean isLegacyChannel(final String channel) {
        return CHANNEL_TO_CABLE.containsKey(channel);
    }

    // * Unknown channels are handed back untouched
    public static String translateChannel(final String channel) {
        return CHANNEL_TO_CABLE.getOrDefault(channel, channel);
    }

    // * Rewrites every channel in a snapshot or saved network, in place
    public static int translateChannels(final CompoundTag payload) {
        if (payload == null || !payload.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            return 0;
        }

        int translated = 0;
        for (final Tag entry : payload.getList(CONNECTIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(entry instanceof final CompoundTag connection)) {
                continue;
            }

            final String channel = connection.getString(CHANNEL_KEY);
            if (!isLegacyChannel(channel)) {
                continue;
            }

            connection.putString(CHANNEL_KEY, translateChannel(channel));
            translated++;
        }

        return translated;
    }
}