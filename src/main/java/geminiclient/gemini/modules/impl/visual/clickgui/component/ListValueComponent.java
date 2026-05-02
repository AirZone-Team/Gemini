package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.List;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class ListValueComponent extends ValueComponent {

    private boolean isExpanded = false;
    public static final int MODE_HEIGHT = 12;

    // 统一的颜色主题 - 使用黑灰色调
    private static final int ACCENT_COLOR = new Color(220, 220, 220).getRGB();
    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB();
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB();
    private static final int TEXT_COLOR = Color.WHITE.getRGB();
    private static final int LIST_BG = new Color(0, 0, 0, 240).getRGB();
    private static final int LIST_HOVER_BG = new Color(30, 30, 30, 200).getRGB();

    public ListValueComponent(ListValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16);
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public int getExpandedListHeight() {
        if (!isExpanded) {
            return 0;
        }
        ListValue listValue = (ListValue) this.value;
        return listValue.list.size() * MODE_HEIGHT;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        ListValue listValue = (ListValue) this.value;

        int bgColor = isHovered(mouseX, mouseY) ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        if (isExpanded) {
            guiGraphics.fill(x, y + height - 1, x + width, y + height, ACCENT_COLOR);
        }

        String displayString = String.format("%s: %s", this.value.getName(), listValue.get());
        guiGraphics.drawString(mc.font, displayString, x + 3, y + 2, TEXT_COLOR, true);

        if (isExpanded) {
            drawModeList(guiGraphics, mouseX, mouseY);
        }
    }

    private void drawModeList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ListValue listValue = (ListValue) this.value;
        List<String> modes = listValue.list;

        int listX = x;
        int listY = y + height;
        int listWidth = width;

        guiGraphics.fill(listX, listY, listX + listWidth, listY + modes.size() * MODE_HEIGHT, LIST_BG);

        int currentY = listY;
        for (String mode : modes) {
            boolean isModeHovered = mouseX >= listX && mouseX <= listX + listWidth &&
                    mouseY >= currentY && mouseY <= currentY + MODE_HEIGHT;
            boolean isSelected = listValue.is(mode);

            if (isModeHovered || isSelected) {
                int highlightColor = isSelected ? ACCENT_COLOR : LIST_HOVER_BG;
                guiGraphics.fill(listX, currentY, listX + listWidth, currentY + MODE_HEIGHT, highlightColor);
            }

            int modeTextColor = isSelected ? TEXT_COLOR : new Color(170, 170, 170).getRGB();
            guiGraphics.drawString(mc.font, mode, listX + 5, currentY + 2, modeTextColor, true);

            currentY += MODE_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ListValue listValue = (ListValue) this.value;

        if (isVisible()) {
            if (isExpanded) {
                int listX = x;
                int listY = y + height;
                int listWidth = width;
                List<String> modes = listValue.list;

                for (int i = 0; i < modes.size(); i++) {
                    int modeY = listY + i * MODE_HEIGHT;
                    if (mouseX >= listX && mouseX <= listX + listWidth &&
                            mouseY >= modeY && mouseY <= modeY + MODE_HEIGHT) {

                        listValue.setMode(modes.get(i));
                        return true;
                    }
                }
            }

            if (isHovered(mouseX, mouseY) && (button == 0 || button == 1)) {
                isExpanded = !isExpanded;
                return true;
            }
        }
        return false;
    }
}