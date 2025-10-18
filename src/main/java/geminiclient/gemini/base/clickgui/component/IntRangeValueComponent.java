package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class IntRangeValueComponent extends ValueComponent {

    private boolean isDraggingMin = false;
    private boolean isDraggingMax = false;

    // 统一的颜色主题
    private static final int ACCENT_COLOR = new Color(255, 51, 153).getRGB(); // 亮洋红色
    private static final int BASE_BG = new Color(0, 0, 0, 220).getRGB(); // 优化: 更深的半透明黑
    private static final int HOVER_BG = new Color(20, 20, 20, 220).getRGB(); // 优化: 略浅的半透明黑
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    // 【风格常量修改】：与 FloatRangeValueComponent 保持一致
    private static final int TRACK_HEIGHT = 2;
    private static final int HANDLE_SIZE = 6;

    public IntRangeValueComponent(IntRangeValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        IntRangeValue rangeValue = (IntRangeValue) this.value;

        if (isDraggingMin || isDraggingMax) {
            updateValueFromMouse(mouseX);
        }

        // 1. 渲染背景
        int bgColor = isHovered(mouseX, mouseY) ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        float absMin = rangeValue.getMin();
        float absMax = rangeValue.getMax();
        float range = absMax - absMin;

        // 2. 渲染滑块轨道和手柄
        int trackY = y + height - 5;
        int trackX = x + 4;
        int trackWidth = width - 8;

        float minPercent = (rangeValue.getMinValue() - absMin) / range;
        float maxPercent = (rangeValue.getMaxValue() - absMin) / range;

        int minHandleX = (int) (trackX + trackWidth * minPercent);
        int maxHandleX = (int) (trackX + trackWidth * maxPercent);

        // A. 渲染基础轨道 (整个绝对范围) - 深灰色
        guiGraphics.fill(trackX, trackY, trackX + trackWidth, trackY + TRACK_HEIGHT,
                new Color(50, 50, 50, 200).getRGB());

        // B. 渲染选中范围 - 主题色
        guiGraphics.fill(minHandleX, trackY, maxHandleX, trackY + TRACK_HEIGHT, ACCENT_COLOR);

        // C. 渲染手柄
        int handleY = trackY - (HANDLE_SIZE - TRACK_HEIGHT) / 2;

        // Min Handle
        guiGraphics.fill(minHandleX - HANDLE_SIZE / 2, handleY, minHandleX + HANDLE_SIZE / 2, handleY + HANDLE_SIZE,
                isDraggingMin ? ACCENT_COLOR : TEXT_COLOR);

        // Max Handle
        guiGraphics.fill(maxHandleX - HANDLE_SIZE / 2, handleY, maxHandleX + HANDLE_SIZE / 2, handleY + HANDLE_SIZE,
                isDraggingMax ? ACCENT_COLOR : TEXT_COLOR);

        // 3. 渲染名称和当前值
        String minVal = String.valueOf(rangeValue.getMinValue());
        String maxVal = String.valueOf(rangeValue.getMaxValue());

        String displayString = String.format("%s: %s/%s", this.value.getName(), minVal, maxVal);

        guiGraphics.drawString(mc.font, displayString, x + 3, y + 2, TEXT_COLOR, true);
    }

    private void updateValueFromMouse(double mouseX) {
        IntRangeValue rangeValue = (IntRangeValue) this.value;

        float absMin = rangeValue.getMin();
        float absMax = rangeValue.getMax();
        float range = absMax - absMin;

        float trackX = x + 4;
        float trackWidth = width - 8;

        float relativeMouseX = (float) (mouseX - trackX);
        float percent = relativeMouseX / trackWidth;

        percent = Math.max(0.0f, Math.min(1.0f, percent));

        float rawNewValue = absMin + range * percent;

        // 将计算出的浮点值四舍五入到最近的整数
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

            float trackX = x + 4;
            float trackWidth = width - 8;
            float minPercent = (rangeValue.getMinValue() - absMin) / range;
            float maxPercent = (rangeValue.getMaxValue() - absMin) / range;

            int minHandleX = (int) (trackX + trackWidth * minPercent);
            int maxHandleX = (int) (trackX + trackWidth * maxPercent);

            int sliderY = y + height - 5;
            int clickTolerance = 5;

            boolean isVerticalHit = mouseY >= sliderY - clickTolerance && mouseY <= sliderY + clickTolerance;

            if (isVerticalHit) {
                // 首先检查最大值手柄（当手柄重叠时，优先选择最大值手柄）
                if (mouseX >= maxHandleX - HANDLE_SIZE && mouseX <= maxHandleX + HANDLE_SIZE) {
                    isDraggingMax = true;
                    return true;
                }

                // 然后检查最小值手柄
                if (mouseX >= minHandleX - HANDLE_SIZE && mouseX <= minHandleX + HANDLE_SIZE) {
                    isDraggingMin = true;
                    return true;
                }

                // 如果点击在轨道上但没有点击到手柄，可以添加直接跳转功能（可选）
                if (mouseX >= trackX && mouseX <= trackX + trackWidth) {
                    // 计算点击位置对应的值
                    float relativeMouseX = (float) (mouseX - trackX);
                    float percent = relativeMouseX / trackWidth;
                    percent = Math.max(0.0f, Math.min(1.0f, percent));
                    int newValue = Math.round(absMin + range * percent);

                    // 判断点击位置更靠近哪个手柄，或者设置两个手柄
                    int distanceToMin = Math.abs(newValue - rangeValue.getMinValue());
                    int distanceToMax = Math.abs(newValue - rangeValue.getMaxValue());

                    if (distanceToMin < distanceToMax) {
                        rangeValue.setMinValue(newValue);
                        isDraggingMin = true;
                    } else {
                        rangeValue.setMaxValue(newValue);
                        isDraggingMax = true;
                    }
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