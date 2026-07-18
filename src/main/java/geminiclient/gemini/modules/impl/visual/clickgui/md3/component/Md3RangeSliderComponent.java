package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import geminiclient.gemini.values.ValueParent;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared MD3 dual-thumb range slider logic. Subclasses provide the
 * min/max fraction mapping and label formatting.
 */
public abstract class Md3RangeSliderComponent extends Md3ValueComponent {

    private static final int THUMB_NONE = -1;
    private static final int THUMB_MIN = 0;
    private static final int THUMB_MAX = 1;

    private int draggingThumb = THUMB_NONE;

    protected Md3RangeSliderComponent(ValueParent value, int width, Md3Overlay.Host host) {
        super(value, width, 44, host);
    }

    protected abstract float getMinFraction();

    protected abstract float getMaxFraction();

    protected abstract void setMinFromFraction(float fraction);

    protected abstract void setMaxFromFraction(float fraction);

    protected abstract String formatRange();

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        if (draggingThumb != THUMB_NONE) {
            updateFromMouse(mouseX);
        }

        var bodyFont = Md3Fonts.body();
        var labelFont = Md3Fonts.label();

        // Label (left) + current range (right)
        Md3Fonts.drawText(gui, bodyFont, value.getName(), x, y + 2, Md3Theme.ON_SURFACE);
        String rangeText = formatRange();
        float vw = Md3Fonts.width(labelFont, rangeText);
        Md3Fonts.drawText(gui, labelFont, rangeText, x + width - vw, y + 4,
                Md3Theme.ON_SURFACE_VARIANT);

        // Track
        int sliderY = y + height - 14;
        int trackH = 4;
        int minThumbX = x + Math.round(width * clamp01(getMinFraction()));
        int maxThumbX = x + Math.round(width * clamp01(getMaxFraction()));

        CustomRoundedRectRenderer.drawRoundedRect(gui, x, sliderY - trackH / 2, width, trackH,
                trackH / 2, Md3Theme.SURFACE_CONTAINER_HIGHEST);
        CustomRoundedRectRenderer.drawRoundedRect(gui, minThumbX, sliderY - trackH / 2,
                Math.max(trackH, maxThumbX - minThumbX), trackH, trackH / 2, Md3Theme.PRIMARY);

        // Thumbs
        boolean hovered = isHovered(mouseX, mouseY);
        drawThumb(gui, minThumbX, sliderY, draggingThumb == THUMB_MIN || hovered);
        drawThumb(gui, maxThumbX, sliderY, draggingThumb == THUMB_MAX || hovered);

        // Value chip above the dragged thumb
        if (draggingThumb != THUMB_NONE) {
            int chipX = draggingThumb == THUMB_MIN ? minThumbX : maxThumbX;
            Md3RenderUtils.drawValueChip(gui, chipX, sliderY - 28, rangeText);
        }
    }

    private void drawThumb(GuiGraphicsExtractor gui, int thumbX, int cy, boolean active) {
        int d = active ? 22 : 20;
        CustomRoundedRectRenderer.drawRoundedRect(gui, thumbX - d / 2, cy - d / 2, d, d, d / 2,
                Md3Theme.PRIMARY);
        int core = d - 10;
        CustomRoundedRectRenderer.drawRoundedRect(gui, thumbX - core / 2, cy - core / 2,
                core, core, core / 2, Md3Theme.SURFACE);
    }

    private void updateFromMouse(double mouseX) {
        float fraction = clamp01((float) (mouseX - x) / width);
        if (draggingThumb == THUMB_MIN) {
            setMinFromFraction(fraction);
        } else {
            setMaxFromFraction(fraction);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            // Snap to / drag the nearest thumb
            float fraction = clamp01((float) (mouseX - x) / width);
            float distMin = Math.abs(fraction - clamp01(getMinFraction()));
            float distMax = Math.abs(fraction - clamp01(getMaxFraction()));
            draggingThumb = distMin <= distMax ? THUMB_MIN : THUMB_MAX;
            updateFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingThumb != THUMB_NONE) {
            draggingThumb = THUMB_NONE;
            return true;
        }
        return false;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
