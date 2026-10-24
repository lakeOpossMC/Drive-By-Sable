package edn.lakeopossmc.drivebysable.client;

import org.lwjgl.glfw.GLFW;

// --- TYPEWRITER CHANNEL KEYS ON ANY KEYBOARD LAYOUT --- //
public final class KeyboardLayout {
    private KeyboardLayout() {
    }

    // * The key code whose channel this key should fire
    public static int channelKey(final int key, final int scanCode) {
        if (key >= GLFW.GLFW_KEY_ESCAPE) {
            return key;
        }
        final String name = GLFW.glfwGetKeyName(key, scanCode);
        if (name == null || name.length() != 1) {
            return key;
        }
        final char character = Character.toLowerCase(name.charAt(0));
        if (character >= 'a' && character <= 'z') {
            return GLFW.GLFW_KEY_A + (character - 'a');
        }
        if (character >= '0' && character <= '9') {
            return GLFW.GLFW_KEY_0 + (character - '0');
        }
        return switch (character) {
            case '`' -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case '-' -> GLFW.GLFW_KEY_MINUS;
            case '=' -> GLFW.GLFW_KEY_EQUAL;
            case '[' -> GLFW.GLFW_KEY_LEFT_BRACKET;
            case ']' -> GLFW.GLFW_KEY_RIGHT_BRACKET;
            case '\\' -> GLFW.GLFW_KEY_BACKSLASH;
            case ';' -> GLFW.GLFW_KEY_SEMICOLON;
            case '\'' -> GLFW.GLFW_KEY_APOSTROPHE;
            case ',' -> GLFW.GLFW_KEY_COMMA;
            case '.' -> GLFW.GLFW_KEY_PERIOD;
            case '/' -> GLFW.GLFW_KEY_SLASH;
            default -> key;
        };
    }
}