package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.List;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class ListValueComponent extends ValueComponent {

    private boolean isExpanded = false;
    public static final int MODE_HEIGHT = 12;

    // 统一的颜色主题
    private static final int ACCENT_COLOR = new Color(255, 51, 153).getRGB(); // 亮洋红色
    private static final int BASE_BG = new Color(15, 15, 15, 200).getRGB();
    private static final int HOVER_BG = new Color(30, 30, 30, 200).getRGB();
    private static final int LIST_BG = new Color(5, 5, 5, 240).getRGB();
    private static final int LIST_HOVER_BG = new Color(40, 40, 40, 180).getRGB();
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    public ListValueComponent(ListValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 14);
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

        // 1. 渲染主组件背景 (悬停时有轻微变化)
        int bgColor = isHovered(mouseX, mouseY) ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 如果展开，在主组件底部渲染主题色高亮线
        if (isExpanded) {
            guiGraphics.fill(x, y + height - 1, x + width, y + height, ACCENT_COLOR);
        }

        // 2. 渲染名称和当前模式
        String displayString = String.format("%s: [ %s ]", this.value.getName(), listValue.get());
        guiGraphics.drawString(mc.font, displayString, x + 3, y + 2, TEXT_COLOR, true);

        // 3. 如果展开，则渲染模式列表
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

        // 渲染整个列表背景 (更深的颜色)
        guiGraphics.fill(listX, listY, listX + listWidth, listY + modes.size() * MODE_HEIGHT, LIST_BG);

        int currentY = listY;
        for (String mode : modes) {
            boolean isModeHovered = mouseX >= listX && mouseX <= listX + listWidth &&
                    mouseY >= currentY && mouseY <= currentY + MODE_HEIGHT;
            boolean isSelected = listValue.is(mode);

            if (isModeHovered || isSelected) {
                // 悬停使用浅灰色，选中时使用主题色
                int highlightColor = isSelected ? ACCENT_COLOR : LIST_HOVER_BG;
                guiGraphics.fill(listX + 1, currentY, listX + listWidth - 1, currentY + MODE_HEIGHT, highlightColor);
            }

            // 文本颜色：选中白色，未选中略灰
            int modeTextColor = isSelected ? TEXT_COLOR : new Color(170, 170, 170).getRGB();
            guiGraphics.drawString(mc.font, mode, listX + 5, currentY + 2, modeTextColor, true);

            currentY += MODE_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ListValue listValue = (ListValue) this.value;

        if (isVisible()) {
            // 1. 如果列表已展开，处理点击选项
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

            // 2. 处理主组件点击 (切换展开状态)
            if (isHovered(mouseX, mouseY) && (button == 0 || button == 1)) {
                isExpanded = !isExpanded;
                return true;
            }
        }
        return false;
    }
}