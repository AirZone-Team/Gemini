package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.impl.FloatValue;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.text.DecimalFormat;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class FloatValueComponent extends ValueComponent {

    private boolean isDragging = false;
    private final DecimalFormat decimalFormat = new DecimalFormat("0.00");

    public FloatValueComponent(FloatValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 14);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        FloatValue floatValue = (FloatValue) this.value;

        if (isDragging) {
            // 在拖拽状态下，持续根据鼠标位置更新值
            updateValueFromMouse(mouseX);
        }

        // 2. 渲染背景
        int bgColor = new Color(20, 20, 20, 180).getRGB();
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 3. 计算滑块位置和长度
        float range = floatValue.getMax() - floatValue.getMin();
        float valuePercent = (floatValue.getValue() - floatValue.getMin()) / range;

        int sliderStart = x + 2;
        int sliderWidth = width - 4;
        int filledWidth = (int) (sliderWidth * valuePercent);

        // 4. 渲染填充条
        guiGraphics.fill(sliderStart, y + height - 3, sliderStart + filledWidth, y + height - 1, Color.GREEN.getRGB());

        // 5. 渲染名称和当前值
        String displayString = String.format("%s: %s", floatValue.getName(), decimalFormat.format(floatValue.getValue()));
        int textColor = isHovered(mouseX, mouseY) ? Color.CYAN.getRGB() : Color.WHITE.getRGB();
        guiGraphics.drawString(mc.font, displayString, x + 2, y + 2, textColor, true);
    }

    private void updateValueFromMouse(double mouseX) {
        FloatValue floatValue = (FloatValue) this.value;

        // 确保我们只在拖拽或首次点击时更新
        // if (isDragging) { // 在 mouseClicked 中调用时不需要 if 检查

        // 使用 floatValue 的 min/max 值
        float MIN_VALUE = floatValue.getMin();
        float MAX_VALUE = floatValue.getMax();

        // 计算鼠标位置占滑块区域的百分比
        float renderWidth = width - 4; // 扣除边距的渲染宽度

        // 计算鼠标在滑块区域内的相对 x 坐标
        float relativeMouseX = (float) (mouseX - (x + 2));

        // 计算百分比
        float percent = relativeMouseX / renderWidth;

        // 限制百分比在 0.0f 到 1.0f 之间
        percent = Math.max(0.0f, Math.min(1.0f, percent));

        // 计算新值
        float newValue = MIN_VALUE + (MAX_VALUE - MIN_VALUE) * percent;

        // 更新 FloatValue (使用您在 FloatValue 中实现的 setter 或直接访问)
        floatValue.setValue(newValue); // 假设您已添加 setValue 方法
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            // 1. 设置拖拽状态
            isDragging = true;

            // 2. 立即更新值（这是必须的，但不需要调用 render）
            updateValueFromMouse(mouseX); // <--- 调用新的值更新方法

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