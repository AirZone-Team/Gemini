package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.modules.impl.visual.clickgui.ClassicTheme;
import geminiclient.gemini.utils.animation.SpringAnimation;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.CheckboxValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class CheckboxValueComponent extends ValueComponent {

    private final List<BoolValueComponent> subComponents = new ArrayList<>();
    private boolean isExpanded = false;

    private final SpringAnimation expandSpring = SpringAnimation.snappy();
    private final SpringAnimation hoverSpring = SpringAnimation.smooth();

    public CheckboxValueComponent(CheckboxValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);

        for (BoolValue boolValue : value.boolValues) {
            this.subComponents.add(new BoolValueComponent(boolValue, 0, 0, width, 14));
        }
    }

    /** Animated 0..1 expand progress; drives both height and reveal. */
    private float expandProgress() {
        return SpringAnimation.easeOutCubic(Math.max(0f, Math.min(1f, expandSpring.getValue())));
    }

    private int fullChildrenHeight() {
        int total = 0;
        for (BoolValueComponent component : subComponents) {
            total += component.height;
        }
        return total;
    }

    public int getExpandedListHeight() {
        return (int) (fullChildrenHeight() * expandProgress());
    }

    @Override
    public int getExtraHeight() {
        return getExpandedListHeight();
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        CheckboxValue checkboxValue = (CheckboxValue) this.value;

        expandSpring.setTarget(isExpanded ? 1.0f : 0.0f);
        expandSpring.update(partialTicks);

        boolean hovered = isHovered(mouseX, mouseY);
        hoverSpring.setTarget(hovered ? 1.0f : 0.0f);
        hoverSpring.update(partialTicks);
        float hoverT = hoverSpring.getValue();

        ClassicTheme.drawRow(guiGraphics, x, y, width, height, hoverT);

        guiGraphics.text(mc.font, checkboxValue.getName(), x + 7, y + 4, ClassicTheme.TEXT, true);

        // Chevron mirrors the expanded state, violet while open/hovered
        String chevron = isExpanded ? "▼" : "▶";
        int chevronColor = (isExpanded || hoverT > 0.5f) ? ClassicTheme.ACCENT : ClassicTheme.TEXT_DIM;
        guiGraphics.text(mc.font, chevron, x + width - 12, y + 4, chevronColor, true);

        // Children, revealed with the expand animation
        float progress = expandProgress();
        if (progress > 0.01f) {
            int visibleHeight = (int) (fullChildrenHeight() * progress);
            guiGraphics.enableScissor(x, y + height, x + width, y + height + visibleHeight);
            int currentY = y + height;
            for (BoolValueComponent component : subComponents) {
                component.x = x;
                component.y = currentY;
                component.width = width;
                component.render(guiGraphics, mouseX, mouseY, partialTicks);
                currentY += component.height;
            }
            guiGraphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if (button == 0 || button == 1) {
                isExpanded = !isExpanded;
                return true;
            }
        }

        if (isExpanded) {
            int currentY = y + height;

            for (BoolValueComponent component : subComponents) {
                component.x = x;
                component.y = currentY;

                if (component.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                currentY += component.height;
            }
        }
        return false;
    }
}
