package geminiclient.gemini.utils;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * 模块按键绑定工具：键码 ↔ 可读名称的完整映射。
 *
 * <p>键码域为 GLFW 键码（与 {@code KeyEvent.key()} 一致），左右 Shift/Ctrl/Alt
 * 本就是不同键码，可分别绑定。鼠标按键用负码编码：{@code -(button + 1)}，
 * 即 MOUSE1(左键)=-1、MOUSE2(右键)=-2、…、MOUSE8=-8。</p>
 */
public final class KeyUtils {

    /** 无法解析的键名返回该值（区别于 0 = 未绑定 / NONE）。 */
    public static final int UNDEFINED = Integer.MIN_VALUE;

    /** 鼠标按钮 0..7 → 负码 -(button + 1)（MOUSE1..MOUSE8）。 */
    public static int mouseButtonToCode(int button) {
        return -(button + 1);
    }

    /** 是否为鼠标键码（负码）。 */
    public static boolean isMouseCode(int code) {
        return code < 0;
    }

    /**
     * 将键码转换为可读名称。鼠标键显示 MOUSE1..MOUSE8，修饰键带左右区分
     * （LSHIFT/RSHIFT/LCTRL/RCTRL/LALT/RALT），其余特殊键有固定名称，
     * 可打印字符回退到 {@code glfwGetKeyName}，未知键显示 K{code}。
     */
    public static String getKeyName(int key) {
        if (key == 0) return "None";
        if (isMouseCode(key)) return "MOUSE" + (-key);

        // 修饰键与特殊键优先于 glfwGetKeyName，保证左右区分与稳定命名
        switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT:   return "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT:  return "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL:return "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT:     return "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT:    return "RALT";
            case GLFW.GLFW_KEY_TAB:          return "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK:    return "CAPS";
            case GLFW.GLFW_KEY_ENTER:        return "ENTER";
            case GLFW.GLFW_KEY_KP_ENTER:     return "KP_ENTER";
            case GLFW.GLFW_KEY_SPACE:        return "SPACE";
            case GLFW.GLFW_KEY_ESCAPE:       return "ESC";
            case GLFW.GLFW_KEY_BACKSPACE:    return "BACKSPACE";
            case GLFW.GLFW_KEY_DELETE:       return "DELETE";
            case GLFW.GLFW_KEY_INSERT:       return "INSERT";
            case GLFW.GLFW_KEY_HOME:         return "HOME";
            case GLFW.GLFW_KEY_END:          return "END";
            case GLFW.GLFW_KEY_PAGE_UP:      return "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN:    return "PGDN";
            case GLFW.GLFW_KEY_UP:           return "UP";
            case GLFW.GLFW_KEY_DOWN:         return "DOWN";
            case GLFW.GLFW_KEY_LEFT:         return "LEFT";
            case GLFW.GLFW_KEY_RIGHT:        return "RIGHT";
            default: break;
        }

        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F24) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }

        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase();
        return "K" + key;
    }

    /**
     * 将键名解析为键码。支持 NONE（清除绑定）、LSHIFT/RSHIFT 等左右修饰键、
     * 方向键、F1-F24、MOUSE1-MOUSE8 及单个字符（A-Z/0-9/空格）。
     * 无法解析时返回 {@link #UNDEFINED}。
     */
    public static int getKeyCode(String name) {
        if (name == null) return UNDEFINED;
        String s = name.trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty()) return UNDEFINED;

        if (s.equals("NONE") || s.equals("UNBOUND") || s.equals("0")) return 0;

        if (s.startsWith("MOUSE")) {
            try {
                int n = Integer.parseInt(s.substring(5));
                if (n >= 1 && n <= 8) return -n; // -(button+1)，button = n-1 → -n
            } catch (NumberFormatException ignored) {
            }
        }

        switch (s) {
            case "LSHIFT":  return GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RSHIFT":  return GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "SHIFT":   return GLFW.GLFW_KEY_LEFT_SHIFT;
            case "LCTRL":
            case "LCONTROL":return GLFW.GLFW_KEY_LEFT_CONTROL;
            case "RCTRL":
            case "RCONTROL":return GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "CTRL":
            case "CONTROL": return GLFW.GLFW_KEY_LEFT_CONTROL;
            case "LALT":    return GLFW.GLFW_KEY_LEFT_ALT;
            case "RALT":    return GLFW.GLFW_KEY_RIGHT_ALT;
            case "ALT":     return GLFW.GLFW_KEY_LEFT_ALT;
            case "TAB":     return GLFW.GLFW_KEY_TAB;
            case "CAPS":
            case "CAPSLOCK":return GLFW.GLFW_KEY_CAPS_LOCK;
            case "ENTER":
            case "RETURN":  return GLFW.GLFW_KEY_ENTER;
            case "SPACE":   return GLFW.GLFW_KEY_SPACE;
            case "ESC":
            case "ESCAPE":  return GLFW.GLFW_KEY_ESCAPE;
            case "BACKSPACE":return GLFW.GLFW_KEY_BACKSPACE;
            case "DELETE":
            case "DEL":     return GLFW.GLFW_KEY_DELETE;
            case "INSERT":
            case "INS":     return GLFW.GLFW_KEY_INSERT;
            case "HOME":    return GLFW.GLFW_KEY_HOME;
            case "END":     return GLFW.GLFW_KEY_END;
            case "PGUP":
            case "PAGEUP":  return GLFW.GLFW_KEY_PAGE_UP;
            case "PGDN":
            case "PAGEDOWN":return GLFW.GLFW_KEY_PAGE_DOWN;
            case "UP":      return GLFW.GLFW_KEY_UP;
            case "DOWN":    return GLFW.GLFW_KEY_DOWN;
            case "LEFT":    return GLFW.GLFW_KEY_LEFT;
            case "RIGHT":   return GLFW.GLFW_KEY_RIGHT;
            default: break;
        }

        if (s.matches("F([1-9]|1[0-9]|2[0-4])")) {
            int n = Integer.parseInt(s.substring(1));
            return GLFW.GLFW_KEY_F1 + n - 1;
        }

        if (s.length() == 1) {
            char c = s.charAt(0);
            if (c >= 'A' && c <= 'Z') return c;
            if (c >= '0' && c <= '9') return c;
            if (c == ' ') return GLFW.GLFW_KEY_SPACE;
        }

        return UNDEFINED;
    }

    private KeyUtils() {
    }
}
