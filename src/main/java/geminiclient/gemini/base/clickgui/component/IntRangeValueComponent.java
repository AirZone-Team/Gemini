package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

// 假设 ValueComponent 是所有值组件的基类
public class IntRangeValueComponent extends ValueComponent {

    private boolean isDraggingMin = false;
    private boolean isDraggingMax = false;

    // 【风格常量】：与 FloatRangeValueComponent 保持一致
    private static final int TRACK_HEIGHT = 1;
    private static final int HANDLE_SIZE = 4;

    public IntRangeValueComponent(IntRangeValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16); // 组件高度为 16
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        IntRangeValue rangeValue = (IntRangeValue) this.value;

        // 1. 如果正在拖拽，根据鼠标位置更新值
        if (isDraggingMin || isDraggingMax) {
            updateValueFromMouse(mouseX);
        }

        int bgColor = new Color(20, 20, 20, 180).getRGB();
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        float absMin = rangeValue.getMin();
        float absMax = rangeValue.getMax();
        float range = absMax - absMin;

        // 2. 渲染滑块轨道和手柄
        int trackY = y + height - 5; // 轨道位置 (位于组件底部上方 5 像素处)
        int trackX = x + 2;
        int trackWidth = width - 4;

        // 计算当前范围的百分比
        float minPercent = (rangeValue.getMinValue() - absMin) / range;
        float maxPercent = (rangeValue.getMaxValue() - absMin) / range;

        // 计算手柄的屏幕位置
        int minHandleX = (int) (trackX + trackWidth * minPercent);
        int maxHandleX = (int) (trackX + trackWidth * maxPercent);

        // A. 渲染基础轨道 (整个绝对范围)
        guiGraphics.fill(trackX, trackY, trackX + trackWidth, trackY + TRACK_HEIGHT, new Color(50, 50, 50, 200).getRGB());

        // B. 渲染选中范围 (Min 手柄到 Max 手柄之间)
        int highlightColor = new Color(0, 150, 255).getRGB(); // 亮蓝色
        guiGraphics.fill(minHandleX, trackY, maxHandleX, trackY + TRACK_HEIGHT, highlightColor);

        // C. 渲染手柄
        int handleY = trackY - (HANDLE_SIZE - TRACK_HEIGHT) / 2; // 使手柄垂直居中于轨道

        // Min Handle
        guiGraphics.fill(minHandleX - HANDLE_SIZE / 2, handleY, minHandleX + HANDLE_SIZE / 2, handleY + HANDLE_SIZE,
                isDraggingMin ? Color.CYAN.getRGB() : Color.WHITE.getRGB());

        // Max Handle
        guiGraphics.fill(maxHandleX - HANDLE_SIZE / 2, handleY, maxHandleX + HANDLE_SIZE / 2, handleY + HANDLE_SIZE,
                isDraggingMax ? Color.CYAN.getRGB() : Color.WHITE.getRGB());

        // 3. 渲染名称和当前值
        String minVal = String.valueOf(rangeValue.getMinValue());
        String maxVal = String.valueOf(rangeValue.getMaxValue());

        String displayString = String.format("%s: %s/%s", this.value.getName(), minVal, maxVal);

        int textColor = isHovered(mouseX, mouseY) ? Color.CYAN.getRGB() : Color.WHITE.getRGB();
        guiGraphics.drawString(mc.font, displayString, x + 2, y + 2, textColor, true);
    }

    private void updateValueFromMouse(double mouseX) {
        IntRangeValue rangeValue = (IntRangeValue) this.value;

        float absMin = rangeValue.getMin();
        float absMax = rangeValue.getMax();
        float range = absMax - absMin;

        float trackX = x + 2;
        float trackWidth = width - 4;

        float relativeMouseX = (float) (mouseX - trackX);
        float percent = relativeMouseX / trackWidth;

        // 确保百分比在 [0, 1] 之间
        percent = Math.max(0.0f, Math.min(1.0f, percent));

        float rawNewValue = absMin + range * percent;

        // 【关键】：将计算出的浮点值四舍五入到最近的整数
        int newValue = Math.round(rawNewValue);

        if (isDraggingMin) {
            rangeValue.setMinValue(newValue);
        } else if (isDraggingMax) {
            rangeValue.setMaxValue(newValue);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isVisible() && button == 0) {
            IntRangeValue rangeValue = (IntRangeValue) this.value;

            float absMin = rangeValue.getMin();
            float absMax = rangeValue.getMax();
            float range = absMax - absMin;

            float trackX = x + 2;
            float trackWidth = width - 4;
            float minPercent = (rangeValue.getMinValue() - absMin) / range;
            float maxPercent = (rangeValue.getMaxValue() - absMin) / range;

            int minHandleX = (int) (trackX + trackWidth * minPercent);
            int maxHandleX = (int) (trackX + trackWidth * maxPercent);

            // 点击检测的垂直范围 (与 FloatRangeValueComponent 相同)
            int sliderY = y + height - 5;
            int clickTolerance = 5;

            boolean isVerticalHit = mouseY >= sliderY - clickTolerance && mouseY <= sliderY + clickTolerance;

            if (isVerticalHit) {
                // 检查是否点击在 Min 手柄附近
                if (mouseX >= minHandleX - HANDLE_SIZE && mouseX <= minHandleX + HANDLE_SIZE) {
                    isDraggingMin = true;
                    return true;
                }

                // 检查是否点击在 Max 手柄附近
                if (mouseX >= maxHandleX - HANDLE_SIZE && mouseX <= maxHandleX + HANDLE_SIZE) {
                    isDraggingMax = true;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isVisible() && button == 0) {
            if (isDraggingMin || isDraggingMax) {
                isDraggingMin = false;
                isDraggingMax = false;
                return true;
            }
        }
        return false;
    }
}