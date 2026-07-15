package geminiclient.gemini.modules.impl.visual.clickgui;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

/**
 * Search bar widget for filtering modules by name.
 *
 * <p>Renders a glass-styled search input with placeholder text.
 * Text input via keyPressed() with GLFW key codes + modifiers.</p>
 */
public class SearchWidget {

    private String filterText = "";
    private boolean focused = true;

    private final int x, y, width, height;

    private static final int BG_COLOR      = new Color(18, 18, 26, 200).getRGB();
    private static final int BORDER_COLOR  = new Color(255, 255, 255, 18).getRGB();
    private static final int TEXT_COLOR    = new Color(210, 210, 225).getRGB();
    private static final int PLACEHOLDER   = new Color(130, 135, 150, 180).getRGB();
    private static final int CURSOR_COLOR  = new Color(139, 92, 246).getRGB();

    private static final int RADIUS = 6;
    private static final int TEXT_PAD = 8;

    private int cursorTick = 0;

    public SearchWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        cursorTick++;

        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, x, y, width, height, RADIUS, BG_COLOR);

        boolean hovered = isHovered(mouseX, mouseY);
        int borderCol = hovered ? new Color(255, 255, 255, 35).getRGB() : BORDER_COLOR;
        CustomRoundedRectRenderer.drawRoundedOutline(
                guiGraphics, x, y, width, height, RADIUS, borderCol, 1);

        String display = filterText.isEmpty() ? "Search modules..." : filterText;
        int textColor = filterText.isEmpty() ? PLACEHOLDER : TEXT_COLOR;
        guiGraphics.text(mc.font, display, x + TEXT_PAD, y + (height - 9) / 2, textColor, true);

        if (focused && filterText.length() > 0 && (cursorTick / 20) % 2 == 0) {
            int textW = mc.font.width(filterText);
            int cx = x + TEXT_PAD + textW + 2;
            guiGraphics.fill(cx, y + 5, cx + 1, y + height - 5, CURSOR_COLOR);
        }

        // Search icon
        guiGraphics.text(mc.font, "⌕", x + 6, y + (height - 9) / 2,
                new Color(139, 92, 246, 100).getRGB(), true);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        focused = isHovered(mouseX, mouseY);
        return focused;
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

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
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
}
