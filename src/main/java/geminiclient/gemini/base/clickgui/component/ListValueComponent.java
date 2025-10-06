package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.List;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class ListValueComponent extends ValueComponent {

    private boolean isExpanded = false;
    // 【修改】将模式高度设为静态常量，方便外部引用
    public static final int MODE_HEIGHT = 12;

    public ListValueComponent(ListValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 14);
    }

    // 【新增】提供公共访问器，供 ModuleComponent 检查状态
    public boolean isExpanded() {
        return isExpanded;
    }

    /**
     * 【新增】计算展开模式列表所需的额外高度。
     */
    public int getExpandedListHeight() {
        if (!isExpanded) {
            return 0;
        }
        ListValue listValue = (ListValue) this.value;
        // 列表高度 = 模式数量 * 每个模式的高度
        return listValue.list.size() * MODE_HEIGHT;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // ... (渲染背景、名称、当前模式代码保持不变) ...

        // 1. 渲染背景
        int bgColor = new Color(20, 20, 20, 180).getRGB();
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 2. 渲染名称和当前模式
        ListValue listValue = (ListValue) this.value;
        String displayString = String.format("%s: [ %s ]", this.value.getName(), listValue.get());
        int textColor = (isHovered(mouseX, mouseY) || isExpanded) ? Color.CYAN.getRGB() : Color.WHITE.getRGB();
        guiGraphics.drawString(mc.font, displayString, x + 2, y + 2, textColor, true);

        // 3. 如果展开，则渲染模式列表
        if (isExpanded) {
            drawModeList(guiGraphics, mouseX, mouseY);
        }
    }

    private void drawModeList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ListValue listValue = (ListValue) this.value;
        List<String> modes = listValue.list;

        int listX = x;
        int listY = y + height; // 从 ValueComponent 底部开始
        int listWidth = width;
        int listHeight = modes.size() * MODE_HEIGHT;

        // 渲染整个列表背景
        guiGraphics.fill(listX, listY, listX + listWidth, listY + listHeight, new Color(10, 10, 10, 240).getRGB());

        int currentY = listY;
        for (String mode : modes) {
            // ... (模式选项的悬停和选中高亮渲染逻辑保持不变) ...
            boolean isModeHovered = mouseX >= listX && mouseX <= listX + listWidth &&
                    mouseY >= currentY && mouseY <= currentY + MODE_HEIGHT;
            boolean isSelected = listValue.is(mode);

            if (isModeHovered || isSelected) {
                guiGraphics.fill(listX, currentY, listX + listWidth, currentY + MODE_HEIGHT,
                        isSelected ? new Color(0, 100, 0, 180).getRGB() : new Color(50, 50, 50, 180).getRGB());
            }

            int modeTextColor = isSelected ? Color.GREEN.getRGB() : Color.WHITE.getRGB();
            guiGraphics.drawString(mc.font, mode, listX + 2, currentY + 2, modeTextColor, true);

            currentY += MODE_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ... (点击逻辑保持不变，确保如果点击到选项，isExpanded = false) ...
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
            if (isHovered(mouseX, mouseY) && (button == 0 || button == 1)) { // 左键
                isExpanded = !isExpanded;
                return true;
            }
        }
        return false;
    }
}