package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.impl.FloatValue;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.text.DecimalFormat;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class FloatValueComponent extends ValueComponent {

    private boolean isDragging = false;
    private final DecimalFormat decimalFormat = new DecimalFormat("0.00");

    // 统一的颜色主题
    private static final int ACCENT_COLOR = new Color(255, 51, 153).getRGB(); // 亮洋红色
    private static final int BASE_BG = new Color(0, 0, 0, 220).getRGB(); // 优化: 更深的半透明黑
    private static final int HOVER_BG = new Color(20, 20, 20, 220).getRGB(); // 优化: 略浅的半透明黑
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

        // 1. 渲染背景 (悬停时有轻微变化)
        int bgColor = isHovered(mouseX, mouseY) ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 2. 渲染名称和当前值
        String displayString = String.format("%s: %s", floatValue.getName(),
                decimalFormat.format(floatValue.getValue()));
        guiGraphics.drawString(mc.font, displayString, x + 3, y + 2, TEXT_COLOR, true);

        // 3. 计算滑块位置和长度
        float range = floatValue.getMax() - floatValue.getMin();
        float valuePercent = (floatValue.getValue() - floatValue.getMin()) / range;

        int sliderStart = x + 2;
        int sliderWidth = width - 4;
        int filledWidth = (int) (sliderWidth * valuePercent);

        int sliderY = y + height - 3; // 滑块位于底部
        int sliderThickness = 2; // 增加厚度
        int handleSize = 4;

        // 4. 渲染轨道 (深灰色作为背景)
        guiGraphics.fill(sliderStart, sliderY, sliderStart + sliderWidth, sliderY + sliderThickness,
                new Color(50, 50, 50).getRGB());

        // 5. 渲染填充条 (主题色)
        guiGraphics.fill(sliderStart, sliderY, sliderStart + filledWidth, sliderY + sliderThickness, ACCENT_COLOR);

        // 6. 渲染手柄
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