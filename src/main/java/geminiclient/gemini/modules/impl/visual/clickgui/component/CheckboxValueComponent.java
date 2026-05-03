package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.CheckboxValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class CheckboxValueComponent extends ValueComponent {

    // 统一的颜色主题 - 使用黑灰色调
    private static final int ACCENT_COLOR = new Color(220, 220, 220).getRGB();
    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB();
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB();
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    private final List<BoolValueComponent> subComponents = new ArrayList<>();
    private boolean isExpanded = false;

    public CheckboxValueComponent(CheckboxValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);

        for (BoolValue boolValue : value.boolValues) {
            this.subComponents.add(new BoolValueComponent(boolValue, 0, 0, width, 14));
        }
    }

    public int getExpandedListHeight() {
        if (!isExpanded) {
            return 0;
        }
        int total = 0;
        for (BoolValueComponent component : subComponents) {
            total += component.height;
        }
        return total;
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        CheckboxValue checkboxValue = (CheckboxValue) this.value;

        boolean isHovered = isHovered(mouseX, mouseY);
        int bgColor = isHovered ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        guiGraphics.text(mc.font, checkboxValue.getName(), x + 3, y + 3, TEXT_COLOR, true);

        String symbol = isExpanded ? "▼" : "▶";
        int arrowColor = (isExpanded || isHovered) ? ACCENT_COLOR : TEXT_COLOR;
        guiGraphics.text(mc.font, symbol, x + width - 10, y + 3, arrowColor, true);

        if (isExpanded) {
            guiGraphics.fill(x, y + height - 1, x + width, y + height, ACCENT_COLOR);
            int currentY = y + height;

            for (BoolValueComponent component : subComponents) {
                component.x = x;
                component.y = currentY;
                component.width = width;
                component.render(guiGraphics, mouseX, mouseY, partialTicks);
                currentY += component.height;
            }
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