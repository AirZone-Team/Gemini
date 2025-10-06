package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.impl.IntValue;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class IntValueComponent extends ValueComponent {

    private boolean isDragging = false;

    public IntValueComponent(IntValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 14);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        IntValue intValue = (IntValue) this.value;

        // 1. 处理拖拽逻辑
        if (isDragging) {
            updateValueFromMouse(mouseX);
        }

        // 2. 渲染背景
        int bgColor = new Color(20, 20, 20, 180).getRGB();
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 3. 计算滑块位置和长度
        float range = intValue.getMax() - intValue.getMin();
        // 使用浮点数计算百分比，但操作的是整数值
        float valuePercent = (intValue.getValue() - intValue.getMin()) / range;

        int sliderStart = x + 2;
        int sliderWidth = width - 4;
        int filledWidth = (int) (sliderWidth * valuePercent);

        // 4. 渲染填充条
        guiGraphics.fill(sliderStart, y + height - 3, sliderStart + filledWidth, y + height - 1, Color.ORANGE.getRGB());

        // 5. 渲染名称和当前值
        String displayString = String.format("%s: %d", intValue.getName(), intValue.getValue());
        int textColor = isHovered(mouseX, mouseY) ? Color.CYAN.getRGB() : Color.WHITE.getRGB();
        guiGraphics.drawString(mc.font, displayString, x + 2, y + 2, textColor, true);
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

        // 计算新值，并四舍五入到最近的整数
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