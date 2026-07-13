package edn.lakeopossmc.drivebysable.compat;

import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

// --- KEYCODE TO CHANNEL MAP FOR TYPEWRITER HUB --- //
public final class CableTypewriterHubServerHandler {

    public static final Map<Integer, String> KEY_TO_CHANNEL;
    public static final List<String> CHANNELS;
    public static final Map<String, String> CHANNEL_TO_DISPLAY;

    //#region // --- BUILD KEY TABLES --- //
    // * Big flat table of glfw code, channel id, display label
    static {
        final Map<Integer, String> keys = new LinkedHashMap<>();
        final Map<String, String> display = new LinkedHashMap<>();

        final Object[][] entries = {
                {290, "keyF1", "F1"}, {292, "keyF3", "F3"}, {293, "keyF4", "F4"},
                {294, "keyF5", "F5"}, {295, "keyF6", "F6"}, {296, "keyF7", "F7"},
                {297, "keyF8", "F8"}, {298, "keyF9", "F9"}, {299, "keyF10", "F10"},
                {301, "keyF12", "F12"},

                {96,  "keyGrave",      "`"},
                {49,  "key1", "1"}, {50, "key2", "2"}, {51, "key3", "3"},
                {52,  "key4", "4"}, {53, "key5", "5"}, {54, "key6", "6"},
                {55,  "key7", "7"}, {56, "key8", "8"}, {57, "key9", "9"}, {48, "key0", "0"},
                {45,  "keyMinus", "-"}, {61, "keyEqual", "="},
                {259, "keyBackspace", "Backspace"},
                {258, "keyTab", "Tab"},

                {81, "keyQ", "Q"}, {87, "keyW", "W"}, {69, "keyE", "E"}, {82, "keyR", "R"},
                {84, "keyT", "T"}, {89, "keyY", "Y"}, {85, "keyU", "U"}, {73, "keyI", "I"},
                {79, "keyO", "O"}, {80, "keyP", "P"},

                {65, "keyA", "A"}, {83, "keyS", "S"}, {68, "keyD", "D"}, {70, "keyF", "F"},
                {71, "keyG", "G"}, {72, "keyH", "H"}, {74, "keyJ", "J"}, {75, "keyK", "K"},
                {76, "keyL", "L"},

                {90, "keyZ", "Z"}, {88, "keyX", "X"}, {67, "keyC", "C"}, {86, "keyV", "V"},
                {66, "keyB", "B"}, {78, "keyN", "N"}, {77, "keyM", "M"},

                {91,  "keyLeftBracket",  "["},
                {93,  "keyRightBracket", "]"},
                {92,  "keyBackslash",    "\\"},
                {280, "keyCapsLock",     "CapsLock"},
                {59,  "keySemicolon",    ";"},
                {39,  "keyApostrophe",   "'"},
                {257, "keyEnter",        "Enter"},

                {340, "keyLeftShift",  "LeftShift"},
                {44,  "keyComma",      ","},
                {46,  "keyPeriod",     "."},
                {47,  "keySlash",      "/"},
                {344, "keyRightShift", "RightShift"},

                {341, "keyLeftCtrl",  "LeftCtrl"},
                {342, "keyLeftAlt",   "LeftAlt"},
                {343, "keyLeftWin",   "LeftWin"},
                {32,  "keySpace",     "Space"},
                {346, "keyRightAlt",  "RightAlt"},
                {348, "keyMenu",      "Menu"},
                {345, "keyRightCtrl", "RightCtrl"},

                {283, "keyPrintScreen", "PrintScreen"},
                {281, "keyScrollLock",  "ScrollLock"},
                {284, "keyPause",       "Pause"},

                {260, "keyIns",  "Ins"},
                {268, "keyHome", "Home"},
                {266, "keyPgUp", "PgUp"},
                {261, "keyDel",  "Del"},
                {269, "keyEnd",  "End"},
                {267, "keyPgDn", "PgDn"},

                {265, "keyUp", "Up"}, {264, "keyDown", "Down"},
                {263, "keyLeft", "Left"}, {262, "keyRight", "Right"},

                {282, "keyNumLock", "NumLock"},
                {331, "keyKpDivide",   "Kp /"},
                {332, "keyKpMultiply", "Kp *"},
                {333, "keyKpSubtract", "Kp -"},
                {334, "keyKpAdd",      "Kp +"},
                {335, "keyKpEnter",    "Kp Enter"},
                {330, "keyKpDecimal",  "Kp ."},
                {321, "keyKp1", "Kp 1"}, {322, "keyKp2", "Kp 2"}, {323, "keyKp3", "Kp 3"},
                {324, "keyKp4", "Kp 4"}, {325, "keyKp5", "Kp 5"}, {326, "keyKp6", "Kp 6"},
                {327, "keyKp7", "Kp 7"}, {328, "keyKp8", "Kp 8"}, {329, "keyKp9", "Kp 9"},
                {320, "keyKp0", "Kp 0"},
        };

        for (final Object[] entry : entries) {
            final int code = (int) entry[0];
            final String channel = (String) entry[1];
            final String label = (String) entry[2];
            keys.put(code, channel);
            display.put(channel, label);
        }

        KEY_TO_CHANNEL = Collections.unmodifiableMap(keys);
        CHANNELS = List.copyOf(new LinkedHashSet<>(keys.values()));
        CHANNEL_TO_DISPLAY = Collections.unmodifiableMap(display);
    }
    //#endregion

    public static void receiveKey(final ServerLevel level, final BlockPos pos,
                                  final int glfwKey, final boolean press) {
        final String channel = KEY_TO_CHANNEL.get(glfwKey);
        if (channel == null) return;
        CableNetworkManager.trySetSignalAt(level, pos, channel, press ? 15 : 0);
    }

    public static String getDisplayName(final String channel) {
        return CHANNEL_TO_DISPLAY.getOrDefault(channel, channel);
    }

    private CableTypewriterHubServerHandler() {}
}