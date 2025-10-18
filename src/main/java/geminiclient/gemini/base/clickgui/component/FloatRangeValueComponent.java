package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.impl.FloatRangeValue;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class FloatRangeValueComponent extends ValueComponent {

    private boolean isDraggingMin = false;
    private boolean isDraggingMax = false;

    // 统一的颜色主题
    private static final int ACCENT_COLOR = new Color(230, 70, 180).getRGB(); // 调整: 略暗洋红色
    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB(); // 优化: 略不那么黑，透明度高
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB(); // 优化: 略浅的半透明黑
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    // 【风格常量修改】：更厚实的轨道和更大的手柄
    private static final int TRACK_HEIGHT = 2; // 轨道高度
    private static final int HANDLE_SIZE = 6; // 手柄尺寸 (正方形)

    public FloatRangeValueComponent(FloatRangeValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        FloatRangeValue rangeValue = (FloatRangeValue) this.value;

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

        // B. 渲染选中范围 (Min 手柄到 Max 手柄之间) - 主题色
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
        String minVal = String.format("%.2f", rangeValue.getMinValue());
        String maxVal = String.format("%.2f", rangeValue.getMaxValue());

        String displayString = String.format("%s: %s/%s", this.value.getName(), minVal, maxVal);

        guiGraphics.drawString(mc.font, displayString, x + 3, y + 2, TEXT_COLOR, true);
    }

    private void updateValueFromMouse(double mouseX) {
        FloatRangeValue rangeValue = (FloatRangeValue) this.value;

        float absMin = rangeValue.getMin();
        float absMax = rangeValue.getMax();
        float range = absMax - absMin;

        float trackX = x + 4;
        float trackWidth = width - 8;

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

            float trackX = x + 4;
            float trackWidth = width - 8;
            float minPercent = (rangeValue.getMinValue() - absMin) / range;
            float maxPercent = (rangeValue.getMaxValue() - absMin) / range;

            int minHandleX = (int) (trackX + trackWidth * minPercent);
            int maxHandleX = (int) (trackX + trackWidth * maxPercent);

            int sliderY = y + height - 5;
            int clickTolerance = 5;

            boolean isVerticalHit = mouseY >= sliderY - clickTolerance && mouseY <= sliderY + clickTolerance;
            boolean isOverlap = Math.abs(maxHandleX - minHandleX) < 2;

            if (isVerticalHit) {
                if (isOverlap && rangeValue.getMaxValue() == absMax) {
                    // 先检测最小值手柄
                    if (mouseX >= minHandleX - HANDLE_SIZE && mouseX <= minHandleX + HANDLE_SIZE) {
                        isDraggingMin = true;
                        return true;
                    }
                    // 然后检测最大值手柄
                    if (mouseX >= maxHandleX - HANDLE_SIZE && mouseX <= maxHandleX + HANDLE_SIZE) {
                        isDraggingMax = true;
                        return true;
                    }
                } else {
                    // 默认顺序：先检测最大值手柄
                    if (mouseX >= maxHandleX - HANDLE_SIZE && mouseX <= maxHandleX + HANDLE_SIZE) {
                        isDraggingMax = true;
                        return true;
                    }
                    // 然后检测最小值手柄
                    if (mouseX >= minHandleX - HANDLE_SIZE && mouseX <= minHandleX + HANDLE_SIZE) {
                        isDraggingMin = true;
                        return true;
                    }
                }

                // 如果点击在轨道上但没有点击到手柄，可以添加直接跳转功能（可选）
                if (mouseX >= trackX && mouseX <= trackX + trackWidth) {
                    // 计算点击位置对应的值
                    float relativeMouseX = (float) (mouseX - trackX);
                    float percent = relativeMouseX / trackWidth;
                    percent = Math.max(0.0f, Math.min(1.0f, percent));
                    int newValue = Math.round(absMin + range * percent);

                    // 判断点击位置更靠近哪个手柄，或者设置两个手柄
                    float distanceToMin = Math.abs(newValue - rangeValue.getMinValue());
                    float distanceToMax = Math.abs(newValue - rangeValue.getMaxValue());

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