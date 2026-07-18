package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.modules.impl.visual.clickgui.ClassicTheme;
import geminiclient.gemini.utils.animation.SpringAnimation;
import geminiclient.gemini.values.impl.IntValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class IntValueComponent extends ValueComponent {

    private boolean isDragging = false;

    private final SpringAnimation hoverSpring = SpringAnimation.smooth();

    public IntValueComponent(IntValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        IntValue intValue = (IntValue) this.value;

        if (isDragging) {
            updateValueFromMouse(mouseX);
        }

        boolean hovered = isHovered(mouseX, mouseY);
        hoverSpring.setTarget(hovered ? 1.0f : 0.0f);
        hoverSpring.update(partialTicks);
        float hoverT = hoverSpring.getValue();

        ClassicTheme.drawRow(guiGraphics, x, y, width, height, hoverT);

        // Label (left) + current value (right)
        guiGraphics.text(mc.font, intValue.getName(), x + 7, y + 3, ClassicTheme.TEXT, true);
        String valueText = String.valueOf(intValue.getValue());
        guiGraphics.text(mc.font, valueText, x + width - 7 - mc.font.width(valueText), y + 3,
                ClassicTheme.TEXT_DIM, true);

        // Slider (drag mapping matches updateValueFromMouse: x+6 .. x+width-6,
        // inset so the round handle stays inside the row at both extremes)
        float range = intValue.getMax() - intValue.getMin();
        float fraction = range == 0 ? 0 : (intValue.getValue() - intValue.getMin()) / range;
        ClassicTheme.drawSlider(guiGraphics, x + 6, y + height - 5, width - 12, fraction,
                isDragging || hovered);
    }

    private void updateValueFromMouse(double mouseX) {
        IntValue intValue = (IntValue) this.value;

        float MIN_VALUE = intValue.getMin();
        float MAX_VALUE = intValue.getMax();
        float range = MAX_VALUE - MIN_VALUE;

        float renderWidth = width - 12;
        float relativeMouseX = (float) (mouseX - (x + 6));
        float percent = relativeMouseX / renderWidth;

        percent = Math.max(0.0f, Math.min(1.0f, percent));

        int newValue = Math.round(MIN_VALUE + range * percent);

        intValue.setValue(newValue);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isVisible() && isHovered(mouseX, mouseY) && button == 0) {
            isDragging = true;
            updateValueFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isVisible() && button == 0 && isDragging) {
            isDragging = false;
            return true;
        }
        return false;
    }
}
