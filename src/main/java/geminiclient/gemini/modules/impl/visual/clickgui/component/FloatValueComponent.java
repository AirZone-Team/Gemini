package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.values.impl.FloatValue;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.text.DecimalFormat;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class FloatValueComponent extends ValueComponent {

    private boolean isDragging = false;
    private final DecimalFormat decimalFormat = new DecimalFormat("0.00");

    // 统一的颜色主题 - 使用黑灰色调
    private static final int ACCENT_COLOR = new Color(220, 220, 220).getRGB();
    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB();
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB();
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    public FloatValueComponent(FloatValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        FloatValue floatValue = (FloatValue) this.value;

        if (isDragging) {
            updateValueFromMouse(mouseX);
        }

        int bgColor = isHovered(mouseX, mouseY) ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        String displayString = String.format("%s: %s", floatValue.getName(),
                decimalFormat.format(floatValue.getValue()));
        guiGraphics.drawString(mc.font, displayString, x + 3, y + 2, TEXT_COLOR, true);

        float range = floatValue.getMax() - floatValue.getMin();
        float valuePercent = (floatValue.getValue() - floatValue.getMin()) / range;

        int sliderStart = x + 2;
        int sliderWidth = width - 4;
        int filledWidth = (int) (sliderWidth * valuePercent);

        int sliderY = y + height - 3;
        int sliderThickness = 2;
        int handleSize = 4;

        guiGraphics.fill(sliderStart, sliderY, sliderStart + sliderWidth, sliderY + sliderThickness,
                new Color(50, 50, 50).getRGB());

        guiGraphics.fill(sliderStart, sliderY, sliderStart + filledWidth, sliderY + sliderThickness, ACCENT_COLOR);

        guiGraphics.fill(sliderStart + filledWidth - handleSize / 2, sliderY - (handleSize - sliderThickness) / 2,
                sliderStart + filledWidth + handleSize / 2, sliderY + (handleSize + sliderThickness) / 2,
                TEXT_COLOR);
    }

    private void updateValueFromMouse(double mouseX) {
        FloatValue floatValue = (FloatValue) this.value;

        float MIN_VALUE = floatValue.getMin();
        float MAX_VALUE = floatValue.getMax();

        float renderWidth = width - 4;

        float relativeMouseX = (float) (mouseX - (x + 2));

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