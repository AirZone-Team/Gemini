package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.modules.impl.visual.clickgui.ClassicTheme;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.utils.animation.SpringAnimation;
import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class IntRangeValueComponent extends ValueComponent {

    private boolean isDraggingMin = false;
    private boolean isDraggingMax = false;

    private final SpringAnimation hoverSpring = SpringAnimation.smooth();

    private static final int TRACK_HEIGHT = 2;
    private static final int HANDLE_SIZE = 6;

    public IntRangeValueComponent(IntRangeValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        IntRangeValue rangeValue = (IntRangeValue) this.value;

        if (isDraggingMin || isDraggingMax) {
            updateValueFromMouse(mouseX);
        }

        boolean hovered = isHovered(mouseX, mouseY);
        hoverSpring.setTarget(hovered ? 1.0f : 0.0f);
        hoverSpring.update(partialTicks);
        float hoverT = hoverSpring.getValue();

        ClassicTheme.drawRow(guiGraphics, x, y, width, height, hoverT);

        float absMin = rangeValue.getMin();
        float absMax = rangeValue.getMax();
        float range = absMax - absMin;

        int trackY = y + height - 5;
        int trackX = x + 4;
        int trackWidth = width - 8;

        float minPercent = range == 0 ? 0 : (rangeValue.getMinValue() - absMin) / range;
        float maxPercent = range == 0 ? 0 : (rangeValue.getMaxValue() - absMin) / range;

        int minHandleX = (int) (trackX + trackWidth * minPercent);
        int maxHandleX = (int) (trackX + trackWidth * maxPercent);
        int cy = trackY + TRACK_HEIGHT / 2;

        // Inactive track + accent segment between the thumbs
        CustomRoundedRectRenderer.drawRoundedRect(guiGraphics, trackX, trackY, trackWidth,
                TRACK_HEIGHT, 1, ClassicTheme.TRACK);
        CustomRoundedRectRenderer.drawRoundedRect(guiGraphics, minHandleX, trackY,
                Math.max(TRACK_HEIGHT, maxHandleX - minHandleX), TRACK_HEIGHT, 1, ClassicTheme.ACCENT);

        ClassicTheme.drawRangeThumb(guiGraphics, minHandleX, cy, isDraggingMin || hovered);
        ClassicTheme.drawRangeThumb(guiGraphics, maxHandleX, cy, isDraggingMax || hovered);

        // Label (left) + current range (right)
        guiGraphics.text(mc.font, this.value.getName(), x + 7, y + 3, ClassicTheme.TEXT, true);
        String rangeText = rangeValue.getMinValue() + " / " + rangeValue.getMaxValue();
        guiGraphics.text(mc.font, rangeText, x + width - 7 - mc.font.width(rangeText), y + 3,
                ClassicTheme.TEXT_DIM, true);
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
