package geminiclient.gemini.base.clickgui.component;

import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.CheckboxValue;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static geminiclient.gemini.base.MinecraftInstance.mc;

/**
 * CheckboxValueComponent 负责渲染 CheckboxValue。
 * 它作为一个可展开的头部，显示 CheckboxValue 的名称，
 * 展开后显示所有子 BoolValue (使用 BoolValueComponent 渲染)。
 * * 注意：由于 CheckboxValue 继承自 ValueParent，但没有显式的 'value' 字段来代表自身状态，
 * 这里的逻辑是将其作为一个容器，处理展开/收起和子项的交互。
 * 如果您希望头部能够点击切换状态，您需要确保 CheckboxValue 内部有一个主 BoolValue 属性（例如：defaultValue），或者让它继承 BoolValue。
 * (根据原始文件，它没有显式的主状态，但为了实现开关功能，我默认它**不实现**头部开关，**只实现展开/收起**，除非您的 CheckboxValue 修正稿提供了主状态。)
 * * 如果您希望它能开关，请参考 BoolValueComponent 的逻辑并修改 CheckboxValue。
 * 在此版本中，**左键点击头部将展开/收起**，**右键点击头部无操作**，子项则使用 BoolValueComponent 的交互。
 */
public class CheckboxValueComponent extends ValueComponent {

    // 统一的颜色主题 (与 BoolValueComponent 保持一致)
    private static final int ACCENT_COLOR = new Color(230, 70, 180).getRGB(); // 调整: 略暗洋红色
    private static final int BASE_BG = new Color(18, 18, 18, 230).getRGB(); // 优化: 略不那么黑，透明度高
    private static final int HOVER_BG = new Color(30, 30, 30, 230).getRGB(); // 优化: 略浅的半透明黑
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    // 用于管理子 BoolValue 的组件列表
    private final List<BoolValueComponent> subComponents = new ArrayList<>();
    private boolean isExpanded = false;

    public CheckboxValueComponent(CheckboxValue value, int x, int y, int width, int height) {
        super(value, x, y, width, 16); // 头部默认高度为 14

        // 实例化所有子 BoolValue 的组件
        for (BoolValue boolValue : value.boolValues) {
            // 使用 BoolValueComponent 渲染子项
            this.subComponents.add(new BoolValueComponent(boolValue, 0, 0, width, 14));
        }
    }

    /**
     * 获取展开时子列表的总高度。
     */
    public int getExpandedListHeight() {
        if (!isExpanded) {
            return 0;
        }
        // 计算所有子组件的总高度
        int total = 0;
        for (BoolValueComponent component : subComponents) {
            total += component.height; // 每个 BoolValueComponent 的高度是 14
        }
        return total;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        CheckboxValue checkboxValue = (CheckboxValue) this.value;

        // 1. 渲染头部背景
        boolean isHovered = isHovered(mouseX, mouseY);
        int bgColor = isHovered ? HOVER_BG : BASE_BG;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 2. 渲染名称
        guiGraphics.drawString(mc.font, checkboxValue.getName(), x + 3, y + 3, TEXT_COLOR, true);

        // 3. 渲染展开箭头
        String symbol = isExpanded ? "▼" : "▶";
        int arrowColor = (isExpanded || isHovered) ? ACCENT_COLOR : TEXT_COLOR;
        guiGraphics.drawString(mc.font, symbol, x + width - 10, y + 3, arrowColor, true);

        // 4. 渲染子组件 (如果已展开)
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
        // 1. 检查是否点击了头部 (展开/收起)
        if (isHovered(mouseX, mouseY)) {
            if (button == 0 || button == 1) { // 左键或右键都可以用来展开/收起，保持与 ModuleComponent 右键展开的逻辑一致
                isExpanded = !isExpanded;
                return true;
            }
        }

        // 2. 传递点击事件给展开的子组件
        if (isExpanded) {
            int currentY = y + height;

            for (BoolValueComponent component : subComponents) {
                // 必须更新组件坐标以便 isHovered 正确判断
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