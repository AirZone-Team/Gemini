package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.SearchFilterModel;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * MD3 search bar with a leading icon, plain-language placeholder, focus state
 * and a trailing clear action.
 */
public class Md3SearchBar {

    private final SearchFilterModel model = new SearchFilterModel();
    private final Md3Anim focusAnim = Md3Anim.shortAnim();

    public int x, y, width, height;

    public Md3SearchBar(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        model.setFocused(false);
    }

    public SearchFilterModel getModel() {
        return model;
    }

    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        model.tick();
        int r = height / 2;

        focusAnim.setTarget(model.isFocused() ? 1.0f : 0.0f);
        float focusT = focusAnim.getValue();

        // Tonal search container at elevation 0.
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, width, height, r,
                Md3Theme.SURFACE_CONTAINER_HIGH);

        // Hover state layer
        if (isHovered(mouseX, mouseY) && !model.isFocused()) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, width, height, r,
                    Md3Theme.hoverState(Md3Theme.ON_SURFACE));
        }

        // Focus indicator.
        if (focusT > 0.01f) {
            CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, width, height, r,
                    Md3Theme.modulateAlpha(Md3Theme.PRIMARY, focusT), 1);
        }

        // Leading magnifier icon
        int iconSize = 12;
        int iconX = x + 12;
        int iconY = y + (height - iconSize) / 2;
        Md3RenderUtils.drawSearchIcon(gui, iconX, iconY, iconSize, Md3Theme.ON_SURFACE_VARIANT);

        // Text / placeholder
        String filter = model.getFilterText();
        boolean empty = filter.isEmpty();
        String display = empty ? "Search modules" : filter;
        int textColor = empty ? Md3Theme.ON_SURFACE_VARIANT : Md3Theme.ON_SURFACE;

        var font = Md3Fonts.search();
        float lh = Md3Fonts.lineHeight(font);
        float textX = iconX + iconSize + 8;
        float textY = y + (height - lh) / 2f;
        Md3Fonts.drawText(gui, font, display, textX, textY, textColor);

        // Trailing action / keyboard hint.
        if (!empty) {
            boolean clearHovered = isOverClear(mouseX, mouseY);
            if (clearHovered) {
                CustomRoundedRectRenderer.drawRoundedRect(gui, x + width - 34,
                        y + (height - 28) / 2, 28, 28, 14,
                        Md3Theme.hoverState(Md3Theme.ON_SURFACE));
            }
            Md3RenderUtils.drawClose(gui, x + width - 20, y + height / 2, 9,
                    Md3Theme.ON_SURFACE_VARIANT);
        } else if (!model.isFocused() && width >= 180) {
            var labelFont = Md3Fonts.label();
            String hint = "CTRL K";
            float hintWidth = Md3Fonts.width(labelFont, hint);
            int chipW = Math.round(hintWidth) + 12;
            int chipH = 20;
            int chipX = x + width - chipW - 10;
            int chipY = y + (height - chipH) / 2;
            CustomRoundedRectRenderer.drawRoundedRect(gui, chipX, chipY, chipW, chipH,
                    Md3Theme.R_SMALL, Md3Theme.SURFACE_CONTAINER_HIGHEST);
            Md3Fonts.drawText(gui, labelFont, hint, chipX + 6,
                    chipY + (chipH - Md3Fonts.lineHeight(labelFont)) / 2f,
                    Md3Theme.ON_SURFACE_VARIANT);
        }

        // Cursor
        if (model.isFocused() && !empty && model.isCursorVisible()) {
            float tw = Md3Fonts.width(font, filter);
            int cx = (int) (textX + tw + 2);
            gui.fill(cx, y + (int) ((height - lh) / 2), cx + 1, y + (int) ((height + lh) / 2),
                    Md3Theme.PRIMARY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverClear(mouseX, mouseY) && model.hasFilter()) {
            model.clear();
            model.setFocused(true);
            return true;
        }
        model.setFocused(isHovered(mouseX, mouseY));
        return model.isFocused();
    }

    public boolean keyPressed(int key, int modifiers) {
        return model.keyPressed(key, modifiers);
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private boolean isOverClear(double mouseX, double mouseY) {
        return mouseX >= x + width - 40 && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }

    public void focus() {
        model.setFocused(true);
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
