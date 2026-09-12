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
            Map.entry("drivebywiretypewriter.key.space", "keySpace"),
            Map.entry("drivebywiretypewriter.key.apostrophe", "keyApostrophe"),
            Map.entry("drivebywiretypewriter.key.comma", "keyComma"),
            Map.entry("drivebywiretypewriter.key.minus", "keyMinus"),
            Map.entry("drivebywiretypewriter.key.period", "keyPeriod"),
            Map.entry("drivebywiretypewriter.key.slash", "keySlash"),
            Map.entry("drivebywiretypewriter.key.0", "key0"),
            Map.entry("drivebywiretypewriter.key.1", "key1"),
            Map.entry("drivebywiretypewriter.key.2", "key2"),
            Map.entry("drivebywiretypewriter.key.3", "key3"),
            Map.entry("drivebywiretypewriter.key.4", "key4"),
            Map.entry("drivebywiretypewriter.key.5", "key5"),
            Map.entry("drivebywiretypewriter.key.6", "key6"),
            Map.entry("drivebywiretypewriter.key.7", "key7"),
            Map.entry("drivebywiretypewriter.key.8", "key8"),
            Map.entry("drivebywiretypewriter.key.9", "key9"),
            Map.entry("drivebywiretypewriter.key.semicolon", "keySemicolon"),
            Map.entry("drivebywiretypewriter.key.equals", "keyEqual"),
            Map.entry("drivebywiretypewriter.key.a", "keyA"),
            Map.entry("drivebywiretypewriter.key.b", "keyB"),
            Map.entry("drivebywiretypewriter.key.c", "keyC"),
            Map.entry("drivebywiretypewriter.key.d", "keyD"),
            Map.entry("drivebywiretypewriter.key.e", "keyE"),
            Map.entry("drivebywiretypewriter.key.f", "keyF"),
            Map.entry("drivebywiretypewriter.key.g", "keyG"),
            Map.entry("drivebywiretypewriter.key.h", "keyH"),
            Map.entry("drivebywiretypewriter.key.i", "keyI"),
            Map.entry("drivebywiretypewriter.key.j", "keyJ"),
            Map.entry("drivebywiretypewriter.key.k", "keyK"),
            Map.entry("drivebywiretypewriter.key.l", "keyL"),
            Map.entry("drivebywiretypewriter.key.m", "keyM"),
            Map.entry("drivebywiretypewriter.key.n", "keyN"),
            Map.entry("drivebywiretypewriter.key.o", "keyO"),
            Map.entry("drivebywiretypewriter.key.p", "keyP"),
            Map.entry("drivebywiretypewriter.key.q", "keyQ"),
            Map.entry("drivebywiretypewriter.key.r", "keyR"),
            Map.entry("drivebywiretypewriter.key.s", "keyS"),
            Map.entry("drivebywiretypewriter.key.t", "keyT"),
            Map.entry("drivebywiretypewriter.key.u", "keyU"),
            Map.entry("drivebywiretypewriter.key.v", "keyV"),
            Map.entry("drivebywiretypewriter.key.w", "keyW"),
            Map.entry("drivebywiretypewriter.key.x", "keyX"),
            Map.entry("drivebywiretypewriter.key.y", "keyY"),
            Map.entry("drivebywiretypewriter.key.z", "keyZ"),
            Map.entry("drivebywiretypewriter.key.left_bracket", "keyLeftBracket"),
            Map.entry("drivebywiretypewriter.key.backslash", "keyBackslash"),
            Map.entry("drivebywiretypewriter.key.right_bracket", "keyRightBracket"),
            Map.entry("drivebywiretypewriter.key.enter", "keyEnter"),
            Map.entry("drivebywiretypewriter.key.tab", "keyTab"),
            Map.entry("drivebywiretypewriter.key.backspace", "keyBackspace"),
            Map.entry("drivebywiretypewriter.key.delete", "keyDel"),
            Map.entry("drivebywiretypewriter.key.right", "keyRight"),
            Map.entry("drivebywiretypewriter.key.left", "keyLeft"),
            Map.entry("drivebywiretypewriter.key.down", "keyDown"),
            Map.entry("drivebywiretypewriter.key.up", "keyUp"),
            Map.entry("drivebywiretypewriter.key.page_up", "keyPgUp"),
            Map.entry("drivebywiretypewriter.key.page_down", "keyPgDn"),
            Map.entry("drivebywiretypewriter.key.end", "keyEnd"),
            Map.entry("drivebywiretypewriter.key.caps_lock", "keyCapsLock"),
            Map.entry("drivebywiretypewriter.key.left_shift", "keyLeftShift"),
            Map.entry("drivebywiretypewriter.key.left_ctrl", "keyLeftCtrl"),
            Map.entry("drivebywiretypewriter.key.left_alt", "keyLeftAlt"),
            Map.entry("drivebywiretypewriter.key.left_super", "keyLeftWin"),
            Map.entry("drivebywiretypewriter.key.right_shift", "keyRightShift"),
            Map.entry("drivebywiretypewriter.key.right_ctrl", "keyRightCtrl"),
            Map.entry("drivebywiretypewriter.key.right_alt", "keyRightAlt"),
            Map.entry("drivebywiretypewriter.key.menu", "keyMenu")
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