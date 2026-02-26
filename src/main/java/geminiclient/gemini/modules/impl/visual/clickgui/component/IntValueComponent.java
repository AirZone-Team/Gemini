package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.values.impl.IntValue;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class IntValueComponent extends ValueComponent {

    private boolean isDragging = false;

    // 统一的颜色主题
    private static final int ACCENT_COLOR = new Color(230, 70, 180).getRGB(); // 调整: 略暗洋红色
    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB(); // 优化: 略不那么黑，透明度高
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB(); // 优化: 略浅的半透明黑
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    public IntValueComponent(IntValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        IntValue intValue = (IntValue) this.value;

        // 1. 处理拖拽逻辑
        if (isDragging) {
            updateValueFromMouse(mouseX);
        }

        // 2. 渲染背景 (悬停时有轻微变化)
        int bgColor = isHovered(mouseX, mouseY) ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 3. 计算滑块位置和长度
        float range = intValue.getMax() - intValue.getMin();
        float valuePercent = (intValue.getValue() - intValue.getMin()) / range;

        int sliderStart = x + 2;
        int sliderWidth = width - 4;
        int filledWidth = (int) (sliderWidth * valuePercent);

        int sliderY = y + height - 3;
        int sliderThickness = 2;
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

        // 7. 渲染名称和当前值
        String displayString = String.format("%s: %d", intValue.getName(), intValue.getValue());
        guiGraphics.drawString(mc.font, displayString, x + 3, y + 2, TEXT_COLOR, true);
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