package geminiclient.gemini.modules.impl.visual.clickgui;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.utils.animation.SpringAnimation;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static geminiclient.gemini.base.MinecraftInstance.mc;

/**
 * Search bar widget for filtering modules by name (classic mode).
 *
 * <p>Renders a rounded search field with a leading icon, placeholder text and
 * a violet focus ring. All text-input logic lives in {@link SearchFilterModel}
 * so it can be shared with the MD3 search bar.</p>
 */
public class SearchWidget {

    private final SearchFilterModel model = new SearchFilterModel();

    private final int x, y, width, height;

    private static final int RADIUS = 6;
    private static final int ICON_X = 7;
    private static final int TEXT_X = 20;

    private final SpringAnimation focusSpring = SpringAnimation.smooth();

    public SearchWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        model.tick();

        focusSpring.setTarget(model.isFocused() ? 1.0f : 0.0f);
        focusSpring.update(partialTicks);
        float focusT = focusSpring.getValue();

        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, x, y, width, height, RADIUS, ClassicTheme.ROW_BG);

        // Border: brightens on hover, violet while focused
        boolean hovered = isHovered(mouseX, mouseY);
        int borderCol = ClassicTheme.lerpColor(
                hovered ? ClassicTheme.BORDER_HOVER : ClassicTheme.BORDER,
                ClassicTheme.ACCENT, focusT);
        CustomRoundedRectRenderer.drawRoundedOutline(
                guiGraphics, x, y, width, height, RADIUS, borderCol, 1);

        // Leading search icon (violet; text starts after it)
        guiGraphics.text(mc.font, "⌕", x + ICON_X, y + (height - 9) / 2,
                ClassicTheme.modulateAlpha(ClassicTheme.ACCENT, 0.55f + 0.45f * focusT), true);

        String filterText = model.getFilterText();
        String display = filterText.isEmpty() ? "Search modules..." : filterText;
        int textColor = filterText.isEmpty() ? ClassicTheme.TEXT_DIM : ClassicTheme.TEXT;
        int textX = x + TEXT_X;
        guiGraphics.text(mc.font, display, textX, y + (height - 9) / 2, textColor, true);

        // Blinking caret while focused (only once there is input — otherwise
        // it would overlap the placeholder)
        if (model.isFocused() && !filterText.isEmpty() && model.isCursorVisible()) {
            int cx = textX + mc.font.width(filterText) + 1;
            guiGraphics.fill(cx, y + 5, cx + 1, y + height - 5, ClassicTheme.ACCENT);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        model.setFocused(isHovered(mouseX, mouseY));
        return model.isFocused();
    }

    /**
     * Handle a key press. Converts GLFW key codes + modifiers to text.
     *
     * @param key GLFW key code
     * @param modifiers GLFW modifier bitmask (from KeyEvent.modifiers())
     * @return true if consumed
     */
    public boolean keyPressed(int key, int modifiers) {
        return model.keyPressed(key, modifiers);
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public String getFilterText() {
        return model.getFilterText();
    }

    public boolean hasFilter() {
        return model.hasFilter();
    }

    public void clear() {
        model.clear();
    }
}
