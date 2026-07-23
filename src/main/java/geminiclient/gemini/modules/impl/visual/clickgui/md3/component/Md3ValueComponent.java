package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import geminiclient.gemini.values.ValueParent;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Base class for MD3 value controls. Positions are set by the parent
 * {@link Md3ModuleComponent} during layout; height is fixed per control type.
 */
public abstract class Md3ValueComponent {

    private static final int HOVER_LEFT_EXTENSION = 8;
    private static final int HOVER_RIGHT_EXTENSION = 2;

    protected final ValueParent value;
    protected final Md3Overlay.Host overlayHost;

    public int x, y, width, height;

    protected Md3ValueComponent(ValueParent value, int width, int height, Md3Overlay.Host overlayHost) {
        this.value = value;
        this.width = width;
        this.height = height;
        this.overlayHost = overlayHost;
    }

    public ValueParent getValue() {
        return value;
    }

    public boolean isVisible() {
        return value.visibility.get();
    }

    /** Extra height below the base row when expanded (checkbox groups). */
    public int getExtraHeight() {
        return 0;
    }

    public int getTotalHeight() {
        return height + getExtraHeight();
    }

    public abstract void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks);

    public abstract boolean mouseClicked(double mouseX, double mouseY, int button);

    /** Always forwarded (even outside bounds) so drags can end anywhere. */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x - HOVER_LEFT_EXTENSION
                && mouseX <= x + width + HOVER_RIGHT_EXTENSION
                && mouseY >= y && mouseY <= y + height;
    }

    /**
     * MD3 hover state layer for row-style controls: a subtle rounded tint of
     * the content colour drawn over the row while the cursor is inside it.
     * Keeps every clickable row visually consistent.
     */
    protected void drawHoverState(GuiGraphicsExtractor gui, double mouseX, double mouseY) {
        if (isHovered(mouseX, mouseY)) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, x - HOVER_LEFT_EXTENSION, y + 1,
                    width + HOVER_LEFT_EXTENSION + HOVER_RIGHT_EXTENSION, height - 2,
                    Md3Theme.R_CONTROL, Md3Theme.hoverState(Md3Theme.ON_SURFACE));
        }
    }
}
