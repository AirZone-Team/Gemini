package geminiclient.gemini.utils;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 模块按键绑定工具：键名 ↔ 键码的完整映射。
 *
 * <p><b>键码域</b>：{@code InputConstants.KEY_*}。Minecraft 26.3 把窗口/输入后端
 * 从 GLFW 换成了 SDL，因此这套值是 SDL scancode（物理位置码），并且就是
 * {@code KeyEvent.key()} 与 {@code InputConstants.isKeyDown(int)} 使用的域。
 * 26.2 及以前它是 GLFW keysym，两者数值完全不同（例如 D：GLFW 68 → SDL 7）。</p>
 *
 * <p>鼠标按键用负码编码：{@code -(button + 1)}，按钮序号沿用
 * {@link #toModButton(int)} 的键号域，即 MOUSE1(左键)=-1、MOUSE2(右键)=-2、
 * MOUSE3(中键)=-3、…、MOUSE8=-8。该域在 26.3 前后保持不变，因此已存档的鼠标
 * 绑定无需迁移；键盘绑定需要，见 {@link #migrateLegacyGlfwKey(int)}。</p>
 */
public final class KeyUtils {

    /** 无法解析的键名返回该值（区别于 0 = 未绑定 / NONE）。 */
    public static final int UNDEFINED = Integer.MIN_VALUE;

    /** 鼠标按钮（{@link #toModButton(int)} 键号，0 基）→ 负码 -(button + 1)。 */
    public static int mouseButtonToCode(int button) {
        return -(button + 1);
    }

    /**
     * 把 MC 上报的鼠标键号换算成本模块内部键号：0=左、1=右、2=中、3..7=侧键。
     *
     * <p>26.3 起 {@code InputConstants.MOUSE_BUTTON_LEFT/MIDDLE/RIGHT} 是 1/2/3
     * （左中右顺序），而本模块（组件树的 {@code mouseClicked(x, y, button)} 约定、
     * 以及已存档的鼠标键码）沿用旧 GLFW 序 0=左、1=右、2=中。所有拿到
     * {@code MouseButtonEvent.button()} / {@code MouseButtonInfo.button()} 的入口
     * 都应先过一次本函数，内部代码继续按 0/1/2 判断。</p>
     */
    public static int toModButton(int mcButton) {
        return switch (mcButton) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> Math.max(0, mcButton - 1);   // MOUSE_BUTTON_4..8 → 3..7
        };
    }

    /** 是否为鼠标键码（负码）。 */
    public static boolean isMouseCode(int code) {
        return code < 0;
    }

    // 键名 → 键码（InputConstants / SDL scancode 域）
    private static final Map<String, Integer> NAME_TO_KEY = new HashMap<>();
    // 反查表，供 getKeyName 使用
    private static final Map<Integer, String> KEY_TO_NAME = new HashMap<>();

    static {
        // 字母：SDL 的 A..Z 连续
        for (int i = 0; i < 26; i++) {
            bind(String.valueOf((char) ('A' + i)), InputConstants.KEY_A + i);
        }
        // 数字主键盘：1..9 连续，0 排在 9 之后
        for (int i = 1; i <= 9; i++) {
            bind(String.valueOf(i), InputConstants.KEY_1 + (i - 1));
        }
        bind("0", InputConstants.KEY_0);

        // 功能键分两段：F1-F12 与 F13-F24
        for (int i = 1; i <= 12; i++) {
            bind("F" + i, InputConstants.KEY_F1 + (i - 1));
        }
        for (int i = 13; i <= 24; i++) {
            bind("F" + i, InputConstants.KEY_F13 + (i - 13));
        }

        bind("LSHIFT", InputConstants.KEY_LSHIFT);
        bind("RSHIFT", InputConstants.KEY_RSHIFT);
        bind("SHIFT", InputConstants.KEY_LSHIFT);
        bind("LCTRL", InputConstants.KEY_LCONTROL);
        bind("RCTRL", InputConstants.KEY_RCONTROL);
        bind("CTRL", InputConstants.KEY_LCONTROL);
        bind("CONTROL", InputConstants.KEY_LCONTROL);
        bind("LALT", InputConstants.KEY_LALT);
        bind("RALT", InputConstants.KEY_RALT);
        bind("ALT", InputConstants.KEY_LALT);
        bind("LSUPER", InputConstants.KEY_LGUI);
        bind("RSUPER", InputConstants.KEY_RGUI);
        bind("TAB", InputConstants.KEY_TAB);
        bind("CAPS", InputConstants.KEY_CAPSLOCK);
        bind("CAPSLOCK", InputConstants.KEY_CAPSLOCK);
        bind("ENTER", InputConstants.KEY_RETURN);
        bind("RETURN", InputConstants.KEY_RETURN);
        bind("KP_ENTER", InputConstants.KEY_NUMPADENTER);
        bind("SPACE", InputConstants.KEY_SPACE);
        bind("ESC", InputConstants.KEY_ESCAPE);
        bind("ESCAPE", InputConstants.KEY_ESCAPE);
        bind("BACKSPACE", InputConstants.KEY_BACKSPACE);
        bind("DELETE", InputConstants.KEY_DELETE);
        bind("DEL", InputConstants.KEY_DELETE);
        bind("INSERT", InputConstants.KEY_INSERT);
        bind("INS", InputConstants.KEY_INSERT);
        bind("HOME", InputConstants.KEY_HOME);
        bind("END", InputConstants.KEY_END);
        bind("PGUP", InputConstants.KEY_PAGEUP);
        bind("PAGEUP", InputConstants.KEY_PAGEUP);
        bind("PGDN", InputConstants.KEY_PAGEDOWN);
        bind("PAGEDOWN", InputConstants.KEY_PAGEDOWN);
        bind("UP", InputConstants.KEY_UP);
        bind("DOWN", InputConstants.KEY_DOWN);
        bind("LEFT", InputConstants.KEY_LEFT);
        bind("RIGHT", InputConstants.KEY_RIGHT);
        bind("MINUS", InputConstants.KEY_MINUS);
        bind("EQUALS", InputConstants.KEY_EQUALS);
        bind("LBRACKET", InputConstants.KEY_LBRACKET);
        bind("RBRACKET", InputConstants.KEY_RBRACKET);
        bind("BACKSLASH", InputConstants.KEY_BACKSLASH);
        bind("SEMICOLON", InputConstants.KEY_SEMICOLON);
        bind("APOSTROPHE", InputConstants.KEY_APOSTROPHE);
        bind("COMMA", InputConstants.KEY_COMMA);
        bind("PERIOD", InputConstants.KEY_PERIOD);
        bind("SLASH", InputConstants.KEY_SLASH);
        bind("GRAVE", InputConstants.KEY_GRAVE);
        bind("NUMLOCK", InputConstants.KEY_NUMLOCK);
        bind("PRINTSCREEN", InputConstants.KEY_PRINTSCREEN);
        bind("SCROLLLOCK", InputConstants.KEY_SCROLLLOCK);
        bind("PAUSE", InputConstants.KEY_PAUSE);
    }

    private static void bind(String name, int code) {
        String key = name.toUpperCase(Locale.ROOT);
        NAME_TO_KEY.put(key, code);
        KEY_TO_NAME.putIfAbsent(code, key);
    }

    /**
     * 26.2 及以前存档里的 GLFW 键码 → 本模块现在使用的 SDL 键码。
     *
     * <p>一次性迁移用：无法识别的值原样返回（负数鼠标码本就不该进来，调用方需先
     * 过滤）。因为新旧两域在 32..127 上重叠，迁移必须由配置里的域标记把关，
     * 重复套用会损坏键位。</p>
     */
    public static int migrateLegacyGlfwKey(int glfwCode) {
        Integer sdl = GLFW_TO_SDL.get(glfwCode);
        return sdl == null ? glfwCode : sdl;
    }

    /** GLFW keysym → InputConstants(26.3/SDL) 的对照表，覆盖本模块能命名的全部键。 */
    private static final Map<Integer, Integer> GLFW_TO_SDL = new HashMap<>();

    private static void legacy(int glfwCode, String name) {
        Integer sdl = NAME_TO_KEY.get(name);
        if (sdl != null) {
            GLFW_TO_SDL.putIfAbsent(glfwCode, sdl);
        }
    }

    static {
        // GLFW 的 A..Z / 0..9 就是 ASCII 码
        for (int i = 0; i < 26; i++) {
            legacy(GLFW.GLFW_KEY_A + i, String.valueOf((char) ('A' + i)));
        }
        for (int i = 0; i <= 9; i++) {
            legacy(GLFW.GLFW_KEY_0 + i, String.valueOf(i));
        }
        // GLFW F1=290 起连续 24 个；SDL 的 F13-F24 另起一段
        for (int i = 1; i <= 12; i++) {
            legacy(GLFW.GLFW_KEY_F1 + (i - 1), "F" + i);
        }
        for (int i = 13; i <= 24; i++) {
            legacy(GLFW.GLFW_KEY_F1 + (i - 1), "F" + i);
        }
        legacy(GLFW.GLFW_KEY_LEFT_SHIFT, "LSHIFT");
        legacy(GLFW.GLFW_KEY_RIGHT_SHIFT, "RSHIFT");
        legacy(GLFW.GLFW_KEY_LEFT_CONTROL, "LCTRL");
        legacy(GLFW.GLFW_KEY_RIGHT_CONTROL, "RCTRL");
        legacy(GLFW.GLFW_KEY_LEFT_ALT, "LALT");
        legacy(GLFW.GLFW_KEY_RIGHT_ALT, "RALT");
        legacy(GLFW.GLFW_KEY_LEFT_SUPER, "LSUPER");
        legacy(GLFW.GLFW_KEY_RIGHT_SUPER, "RSUPER");
        legacy(GLFW.GLFW_KEY_TAB, "TAB");
        legacy(GLFW.GLFW_KEY_CAPS_LOCK, "CAPS");
        legacy(GLFW.GLFW_KEY_ENTER, "ENTER");
        legacy(GLFW.GLFW_KEY_KP_ENTER, "KP_ENTER");
        legacy(GLFW.GLFW_KEY_SPACE, "SPACE");
        legacy(GLFW.GLFW_KEY_ESCAPE, "ESC");
        legacy(GLFW.GLFW_KEY_BACKSPACE, "BACKSPACE");
        legacy(GLFW.GLFW_KEY_DELETE, "DELETE");
        legacy(GLFW.GLFW_KEY_INSERT, "INSERT");
        legacy(GLFW.GLFW_KEY_HOME, "HOME");
        legacy(GLFW.GLFW_KEY_END, "END");
        legacy(GLFW.GLFW_KEY_PAGE_UP, "PGUP");
        legacy(GLFW.GLFW_KEY_PAGE_DOWN, "PGDN");
        legacy(GLFW.GLFW_KEY_UP, "UP");
        legacy(GLFW.GLFW_KEY_DOWN, "DOWN");
        legacy(GLFW.GLFW_KEY_LEFT, "LEFT");
        legacy(GLFW.GLFW_KEY_RIGHT, "RIGHT");
        legacy(GLFW.GLFW_KEY_MINUS, "MINUS");
        legacy(GLFW.GLFW_KEY_EQUAL, "EQUALS");
        legacy(GLFW.GLFW_KEY_LEFT_BRACKET, "LBRACKET");
        legacy(GLFW.GLFW_KEY_RIGHT_BRACKET, "RBRACKET");
        legacy(GLFW.GLFW_KEY_BACKSLASH, "BACKSLASH");
        legacy(GLFW.GLFW_KEY_SEMICOLON, "SEMICOLON");
        legacy(GLFW.GLFW_KEY_APOSTROPHE, "APOSTROPHE");
        legacy(GLFW.GLFW_KEY_COMMA, "COMMA");
        legacy(GLFW.GLFW_KEY_PERIOD, "PERIOD");
        legacy(GLFW.GLFW_KEY_SLASH, "SLASH");
        legacy(GLFW.GLFW_KEY_GRAVE_ACCENT, "GRAVE");
        legacy(GLFW.GLFW_KEY_NUM_LOCK, "NUMLOCK");
        legacy(GLFW.GLFW_KEY_PRINT_SCREEN, "PRINTSCREEN");
        legacy(GLFW.GLFW_KEY_SCROLL_LOCK, "SCROLLLOCK");
        legacy(GLFW.GLFW_KEY_PAUSE, "PAUSE");
    }

    /**
     * 将键码转换为可读名称。鼠标键显示 MOUSE1..MOUSE8，其余按 {@link #KEY_TO_NAME}
     * 反查（带左右区分的修饰键存的是首选名），查不到则显示 K{code}。
     */
    public static String getKeyName(int key) {
        if (key == 0) return "None";
        if (isMouseCode(key)) return "MOUSE" + (-key);
        String name = KEY_TO_NAME.get(key);
        return name != null ? name : "K" + key;
    }

    /**
     * 将键名解析为键码（SDL/InputConstants 域）。支持 NONE（清除绑定，"0" 同义）、
     * LSHIFT/RSHIFT 等左右修饰键、方向键、F1-F24、字母与 1-9 数字键名；
     * MOUSE1-MOUSE8 解析为负码。无法解析时返回 {@link #UNDEFINED}。
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

        Integer mapped = NAME_TO_KEY.get(s);
        if (mapped != null) return mapped;

        return UNDEFINED;
    }

    private KeyUtils() {
    }
}
