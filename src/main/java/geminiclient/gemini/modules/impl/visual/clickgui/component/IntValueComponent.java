package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.values.impl.IntValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class IntValueComponent extends ValueComponent {

    private boolean isDragging = false;

    // 统一的颜色主题 - 使用黑灰色调
    private static final int ACCENT_COLOR = new Color(220, 220, 220).getRGB();
    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB();
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB();
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    public IntValueComponent(IntValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        IntValue intValue = (IntValue) this.value;

        if (isDragging) {
            updateValueFromMouse(mouseX);
        }

        int bgColor = isHovered(mouseX, mouseY) ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        float range = intValue.getMax() - intValue.getMin();
        float valuePercent = (intValue.getValue() - intValue.getMin()) / range;

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

        String displayString = String.format("%s: %d", intValue.getName(), intValue.getValue());
        guiGraphics.text(mc.font, displayString, x + 3, y + 2, TEXT_COLOR, true);
    }

    private void updateValueFromMouse(double mouseX) {
        IntValue intValue = (IntValue) this.value;

        float MIN_VALUE = intValue.getMin();
        float MAX_VALUE = intValue.getMax();
        float range = MAX_VALUE - MIN_VALUE;

        float renderWidth = width - 4;
        float relativeMouseX = (float) (mouseX - (x + 2));
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