package geminiclient.gemini.modules.impl.visual.clickgui;

import geminiclient.gemini.modules.Module;
import org.lwjgl.glfw.GLFW;

/**
 * Render-free search/filter state shared by the classic {@link SearchWidget}
 * and the MD3 search bar. Handles text input (GLFW key codes + modifiers),
 * cursor blink timing, and module name matching.
 */
public class SearchFilterModel {

    private String filterText = "";
    private boolean focused = true;
    private int cursorTick = 0;

    public void tick() {
        cursorTick++;
    }

    public boolean isCursorVisible() {
        return (cursorTick / 20) % 2 == 0;
    }

    public void resetCursor() {
        cursorTick = 0;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    /**
     * Handle a key press. Converts GLFW key codes + modifiers to text.
     *
     * @param key GLFW key code
     * @param modifiers GLFW modifier bitmask (from KeyEvent.modifiers())
     * @return true if consumed
     */
    public boolean keyPressed(int key, int modifiers) {
        if (!focused) return false;

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!filterText.isEmpty()) {
                filterText = filterText.substring(0, filterText.length() - 1);
                cursorTick = 0;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            filterText = "";
            focused = false;
            cursorTick = 0;
            return true;
        }

        if (key == GLFW.GLFW_KEY_SPACE) {
            append(' ');
            return true;
        }

        // Convert GLFW key code to character
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            char c = (char) (key - GLFW.GLFW_KEY_A + (shift ? 'A' : 'a'));
            append(c);
            return true;
        }

        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            // Map key number row to actual characters (with shift for symbols)
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            String numberRowShift = ")!@#$%^&*(";
            char c = shift ? numberRowShift.charAt(key - GLFW.GLFW_KEY_0)
                           : (char) ('0' + (key - GLFW.GLFW_KEY_0));
            append(c);
            return true;
        }

        // Common printable keys
        if (key == GLFW.GLFW_KEY_MINUS)        append((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? '_' : '-');
        else if (key == GLFW.GLFW_KEY_EQUAL)   append((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? '+' : '=');
        else if (key == GLFW.GLFW_KEY_LEFT_BRACKET)  append((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? '{' : '[');
        else if (key == GLFW.GLFW_KEY_RIGHT_BRACKET) append((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? '}' : ']');
        else if (key == GLFW.GLFW_KEY_BACKSLASH)     append((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? '|' : '\\');
        else if (key == GLFW.GLFW_KEY_SEMICOLON)     append((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? ':' : ';');
        else if (key == GLFW.GLFW_KEY_APOSTROPHE)    append((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? '"' : '\'');
        else if (key == GLFW.GLFW_KEY_COMMA)         append((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? '<' : ',');
        else if (key == GLFW.GLFW_KEY_PERIOD)        append((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? '>' : '.');
        else if (key == GLFW.GLFW_KEY_SLASH)         append((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? '?' : '/');
        else if (key == GLFW.GLFW_KEY_GRAVE_ACCENT)  append((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? '~' : '`');
        else {
            return false;
        }
        return true;
    }

    private void append(char c) {
        if (filterText.length() >= 32) return;
        filterText += c;
        cursorTick = 0;
    }

    public String getFilterText() {
        return filterText;
    }

    public boolean hasFilter() {
        return !filterText.isEmpty();
    }

    public void clear() {
        filterText = "";
        cursorTick = 0;
    }

    /** Case-insensitive "name contains filter" match used by both GUI modes. */
    public static boolean matches(Module module, String filter) {
        if (filter == null || filter.isEmpty()) return true;
        return module.getName().toLowerCase().contains(filter.toLowerCase());
    }
}
