package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.Color;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class IntRangeValueComponent extends ValueComponent {

    private boolean isDraggingMin = false;
    private boolean isDraggingMax = false;

    // 统一的颜色主题 - 使用黑灰色调
    private static final int ACCENT_COLOR = new Color(220, 220, 220).getRGB();
    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB();
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB();
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

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

        int bgColor = isHovered(mouseX, mouseY) ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        float absMin = rangeValue.getMin();
        float absMax = rangeValue.getMax();
        float range = absMax - absMin;

        int trackY = y + height - 5;
        int trackX = x + 4;
        int trackWidth = width - 8;

        float minPercent = (rangeValue.getMinValue() - absMin) / range;
        float maxPercent = (rangeValue.getMaxValue() - absMin) / range;

        int minHandleX = (int) (trackX + trackWidth * minPercent);
        int maxHandleX = (int) (trackX + trackWidth * maxPercent);

        guiGraphics.fill(trackX, trackY, trackX + trackWidth, trackY + TRACK_HEIGHT,
                new Color(50, 50, 50, 200).getRGB());

        guiGraphics.fill(minHandleX, trackY, maxHandleX, trackY + TRACK_HEIGHT, ACCENT_COLOR);

        int handleY = trackY - (HANDLE_SIZE - TRACK_HEIGHT) / 2;

        guiGraphics.fill(minHandleX - HANDLE_SIZE / 2, handleY, minHandleX + HANDLE_SIZE / 2, handleY + HANDLE_SIZE,
                isDraggingMin ? ACCENT_COLOR : TEXT_COLOR);

        guiGraphics.fill(maxHandleX - HANDLE_SIZE / 2, handleY, maxHandleX + HANDLE_SIZE / 2, handleY + HANDLE_SIZE,
                isDraggingMax ? ACCENT_COLOR : TEXT_COLOR);

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
            boolean isOverlap = Math.abs(maxHandleX - minHandleX) < 2;

            if (isVerticalHit) {
                if (isOverlap && rangeValue.getMaxValue() == absMax) {
                    if (mouseX >= minHandleX - HANDLE_SIZE && mouseX <= minHandleX + HANDLE_SIZE) {
                        isDraggingMin = true;
                        return true;
                    }
                    if (mouseX >= maxHandleX - HANDLE_SIZE && mouseX <= maxHandleX + HANDLE_SIZE) {
                        isDraggingMax = true;
                        return true;
                    }
                } else {
                    if (mouseX >= maxHandleX - HANDLE_SIZE && mouseX <= maxHandleX + HANDLE_SIZE) {
                        isDraggingMax = true;
                        return true;
                    }
                    if (mouseX >= minHandleX - HANDLE_SIZE && mouseX <= minHandleX + HANDLE_SIZE) {
                        isDraggingMin = true;
                        return true;
                    }
                }

                if (mouseX >= trackX && mouseX <= trackX + trackWidth) {
                    float relativeMouseX = (float) (mouseX - trackX);
                    float percent = relativeMouseX / trackWidth;
                    percent = Math.max(0.0f, Math.min(1.0f, percent));
                    int newValue = Math.round(absMin + range * percent);

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