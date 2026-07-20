package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import geminiclient.gemini.modules.impl.visual.ClickGui;
import geminiclient.gemini.values.ValueParent;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared MD3 single-thumb slider logic. Subclasses provide the value
 * mapping (fraction <-> typed value) and the value label formatting.
 */
public abstract class Md3SliderComponent extends Md3ValueComponent {

    private boolean dragging = false;

    protected Md3SliderComponent(ValueParent value, int width, Md3Overlay.Host host) {
        super(value, width, 44, host);
    }

    protected abstract float getFraction();

    protected abstract void setFromFraction(float fraction);

    protected abstract String formatValue();

    @Override
    public int getTotalHeight() {
        return ClickGui.md3SliderHeight();
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        height = getTotalHeight();
        if (dragging) {
            updateFromMouse(mouseX);
        }

        var bodyFont = Md3Fonts.body();
        var labelFont = Md3Fonts.label();

        // Label (left) + current value (right)
        Md3Fonts.drawText(gui, bodyFont, value.getName(), x, y + 2, Md3Theme.ON_SURFACE);
        String valueText = formatValue();
        float vw = Md3Fonts.width(labelFont, valueText);
        Md3Fonts.drawText(gui, labelFont, valueText, x + width - vw, y + 4,
                Md3Theme.ON_SURFACE_VARIANT);

        // Slider track
        int sliderY = y + height - 14;
        int thumbX = Md3RenderUtils.drawSlider(gui, x, sliderY, width, getFraction(),
                dragging || isHovered(mouseX, mouseY));

        // Value chip above thumb while dragging
        if (dragging) {
            Md3RenderUtils.drawValueChip(gui, thumbX, sliderY - 28, valueText);
        }
    }

    private void updateFromMouse(double mouseX) {
        float fraction = (float) (mouseX - x) / width;
        setFromFraction(Math.max(0f, Math.min(1f, fraction)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            dragging = true;
            updateFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }
}
