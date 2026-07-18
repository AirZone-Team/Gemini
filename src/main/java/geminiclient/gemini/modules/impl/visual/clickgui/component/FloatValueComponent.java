package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.modules.impl.visual.clickgui.ClassicTheme;
import geminiclient.gemini.utils.animation.SpringAnimation;
import geminiclient.gemini.values.impl.FloatValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.text.DecimalFormat;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class FloatValueComponent extends ValueComponent {

    private boolean isDragging = false;
    private final DecimalFormat decimalFormat = new DecimalFormat("0.00");

    private final SpringAnimation hoverSpring = SpringAnimation.smooth();

    public FloatValueComponent(FloatValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        FloatValue floatValue = (FloatValue) this.value;

        if (isDragging) {
            updateValueFromMouse(mouseX);
        }

        boolean hovered = isHovered(mouseX, mouseY);
        hoverSpring.setTarget(hovered ? 1.0f : 0.0f);
        hoverSpring.update(partialTicks);
        float hoverT = hoverSpring.getValue();

        ClassicTheme.drawRow(guiGraphics, x, y, width, height, hoverT);

        // Label (left) + current value (right)
        guiGraphics.text(mc.font, floatValue.getName(), x + 7, y + 3, ClassicTheme.TEXT, true);
        String valueText = decimalFormat.format(floatValue.getValue());
        guiGraphics.text(mc.font, valueText, x + width - 7 - mc.font.width(valueText), y + 3,
                ClassicTheme.TEXT_DIM, true);

        // Slider (drag mapping matches updateValueFromMouse: x+6 .. x+width-6,
        // inset so the round handle stays inside the row at both extremes)
        float range = floatValue.getMax() - floatValue.getMin();
        float fraction = range == 0 ? 0 : (floatValue.getValue() - floatValue.getMin()) / range;
        ClassicTheme.drawSlider(guiGraphics, x + 6, y + height - 5, width - 12, fraction,
                isDragging || hovered);
    }

    private void updateValueFromMouse(double mouseX) {
        FloatValue floatValue = (FloatValue) this.value;

        float MIN_VALUE = floatValue.getMin();
        float MAX_VALUE = floatValue.getMax();

        float renderWidth = width - 12;

        float relativeMouseX = (float) (mouseX - (x + 6));

        float percent = relativeMouseX / renderWidth;

        percent = Math.max(0.0f, Math.min(1.0f, percent));

        float newValue = MIN_VALUE + (MAX_VALUE - MIN_VALUE) * percent;

        floatValue.setValue(newValue);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            isDragging = true;
            updateValueFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDragging) {
            isDragging = false;
            return true;
        }
        return false;
    }
}
