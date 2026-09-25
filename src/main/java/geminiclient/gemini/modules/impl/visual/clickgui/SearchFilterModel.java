package geminiclient.gemini.modules.impl.visual.clickgui;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.base.I18n;
import geminiclient.gemini.modules.Module;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.Locale;

/**
 * Render-free search/filter state shared by the classic {@link SearchWidget}
 * and the MD3 search bar. Handles editing keys, typed text, cursor blink
 * timing, and module name matching. Text arrives through
 * {@link #charTyped(CharacterEvent)} so that keyboard layout, shift and IME are
 * resolved by Minecraft itself instead of a hand-rolled key table.
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
     * Editing keys only — backspace. Text comes through {@link #charTyped}.
     *
     * @return true if consumed
     */
    public boolean keyPressed(KeyEvent event) {
        if (!focused) return false;

        if (event.key() == InputConstants.KEY_BACKSPACE) {
            if (!filterText.isEmpty()) {
                filterText = filterText
                        .substring(0, filterText.offsetByCodePoints(filterText.length(), -1));
                cursorTick = 0;
            }
            return true;
        }
        return false;
    }

    /**
     * Append one character that Minecraft has already resolved for the active
     * keyboard layout and shift state.
     *
     * @return true if consumed
     */
    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (!focused || cp < 32 || cp == 127 || filterText.length() >= 32) {
            return false;
        }
        filterText += new String(Character.toChars(cp));
        cursorTick = 0;
        return true;
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
        // 同时匹配英文原名与本地化显示名，保证中英文搜索都能命中
        return module.getName().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))
                || I18n.module(module.getName()).toLowerCase(Locale.ROOT)
                        .contains(filter.toLowerCase(Locale.ROOT));
    }
}
