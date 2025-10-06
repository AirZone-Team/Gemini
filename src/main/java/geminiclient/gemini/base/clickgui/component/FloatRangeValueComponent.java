package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.impl.FloatRangeValue;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class FloatRangeValueComponent extends ValueComponent {

    private boolean isDraggingMin = false;
    private boolean isDraggingMax = false;

    // 【风格常量】：与单滑块组件保持一致
    private static final int TRACK_HEIGHT = 1; // 轨道高度
    private static final int HANDLE_SIZE = 4;    // 手柄尺寸 (正方形)

    public FloatRangeValueComponent(FloatRangeValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16); // 稍微增加高度以容纳双滑块
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        FloatRangeValue rangeValue = (FloatRangeValue) this.value;

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
        String minVal = String.format("%.2f", rangeValue.getMinValue());
        String maxVal = String.format("%.2f", rangeValue.getMaxValue());

        String displayString = String.format("%s: %s/%s", this.value.getName(), minVal, maxVal);

        int textColor = isHovered(mouseX, mouseY) ? Color.CYAN.getRGB() : Color.WHITE.getRGB();
        guiGraphics.drawString(mc.font, displayString, x + 2, y + 2, textColor, true);
    }

    private void updateValueFromMouse(double mouseX) {
        FloatRangeValue rangeValue = (FloatRangeValue) this.value;

        float absMin = rangeValue.getMin();
        float absMax = rangeValue.getMax();
        float range = absMax - absMin;

        float trackX = x + 2;
        float trackWidth = width - 4;

        float relativeMouseX = (float) (mouseX - trackX);
        float percent = relativeMouseX / trackWidth;

        percent = Math.max(0.0f, Math.min(1.0f, percent));

        float newValue = absMin + range * percent;

        if (isDraggingMin) {
            rangeValue.setMinValue(newValue);
        } else if (isDraggingMax) {
            rangeValue.setMaxValue(newValue);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isVisible() && button == 0) {
            FloatRangeValue rangeValue = (FloatRangeValue) this.value;

            float absMin = rangeValue.getMin();
            float absMax = rangeValue.getMax();
            float range = absMax - absMin;

            float trackX = x + 2;
            float trackWidth = width - 4;
            float minPercent = (rangeValue.getMinValue() - absMin) / range;
            float maxPercent = (rangeValue.getMaxValue() - absMin) / range;

            int minHandleX = (int) (trackX + trackWidth * minPercent);
            int maxHandleX = (int) (trackX + trackWidth * maxPercent);

            // 点击检测的垂直范围
            int sliderY = y + height - 5;
            int clickTolerance = 5; // 垂直点击容错范围

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