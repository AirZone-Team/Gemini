package geminiclient.gemini.base.clickgui;

import geminiclient.gemini.base.clickgui.component.*;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.values.ValueParent;
import geminiclient.gemini.values.impl.*;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class ModuleComponent {
    public final Module module;
    public int x, y, width, height;
    private boolean isExpanded = false;
    private final List<ValueComponent> allValueComponents = new ArrayList<>();

    public ModuleComponent(Module module, int x, int y, int width, int height) {
        this.module = module;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height; // 默认高度 (模块头部)

        // 【关键修改：填充 allValueComponents 列表】
        int componentHeight = 14; // 每个值组件的高度

        // 遍历模块中的所有 ValueParent
        for (ValueParent value : module.getValues()) {
            ValueComponent component = null;

            if (value instanceof BoolValue) {
                // 使用 BoolValueComponent
                component = new BoolValueComponent((BoolValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof FloatValue) {
                // 使用 FloatValueComponent
                component = new FloatValueComponent((FloatValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof IntValue) {
                // 使用 IntValueComponent
                component = new IntValueComponent((IntValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof ListValue) {
                // 使用 ListValueComponent
                component = new ListValueComponent((ListValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof FloatRangeValue) {
                component = new FloatRangeValueComponent((FloatRangeValue) value,0,0,width,componentHeight);
            } else if (value instanceof IntRangeValue) {
                component = new IntRangeValueComponent((IntRangeValue) value,0,0,width,componentHeight);
            }
            // ... 可以添加其他 ValueParent 类型 (如 IntValue, EnumValue 等)

            if (component != null) {
                this.allValueComponents.add(component);
            }
        }
    }

    public int getTotalHeight() {
        int totalHeight = this.height; // 模块头部高度

        if (isExpanded) {
            for (ValueComponent component : getVisibleValueComponents()) {
                // 所有组件的基础高度 (例如 14)
                totalHeight += component.height;

                // 【关键修复】：检查是否为展开的 ListValueComponent
                if (component instanceof ListValueComponent listComp) {
                    // 如果展开，加上列表的额外高度
                    totalHeight += listComp.getExpandedListHeight();
                }
            }
        }
        return totalHeight;
    }

    private List<ValueComponent> getVisibleValueComponents() {
        return allValueComponents.stream()
                .filter(ValueComponent::isVisible)
                .collect(Collectors.toList());
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // 1. 渲染模块背景
        int bgColor = module.enabled ? new Color(0, 150, 0, 200).getRGB() : new Color(30, 30, 30, 200).getRGB();
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 2. 渲染模块名称
        guiGraphics.drawString(mc.font, module.getName(), x + 2, y + 3, Color.WHITE.getRGB(), true);

        // 3. 渲染展开箭头 (如果有值组件)
        if (!allValueComponents.isEmpty()) {
            // 渲染一个箭头或指示符
            String arrow = isExpanded ? "v" : ">";
            guiGraphics.drawString(mc.font, arrow, x + width - 8, y + 3, Color.WHITE.getRGB(), true);
        }

        // 4. 渲染值组件 (如果已展开)
        if (isExpanded) {
            int currentY = y + height;

            for (ValueComponent component : getVisibleValueComponents()) {
                component.x = x;
                component.y = currentY;
                component.width = width;
                component.render(guiGraphics, mouseX, mouseY, partialTicks);

                // 【关键修复】：更新下一个组件的 Y 坐标
                int componentRenderHeight = component.height;
                if (component instanceof ListValueComponent listComp) {
                    // 如果 ListValueComponent 展开，需要加上列表的额外高度
                    componentRenderHeight += listComp.getExpandedListHeight();
                }
                currentY += componentRenderHeight;
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查是否点击了模块名称部分
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            if (button == 0) { // 左键：切换模块状态
                module.toggle();
                return true;
            } else if (button == 1) { // 右键：展开/收起值组件
                isExpanded = !isExpanded;
                return true;
            }
        }

        // 传递点击事件给展开的值组件
        if (isExpanded) {
            int currentY = y + height; // 从 Module Header Content Y 之后开始

            for (ValueComponent component : getVisibleValueComponents()) {

                // 【关键修复】：在点击检测前，必须设置 ValueComponent 的 Content Y 坐标
                component.x = x;
                component.y = currentY; // 将 Content Y 设置给 ValueComponent

                if (component.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }

                // 高度修正：使用动态高度来计算下一个组件的位置
                int componentInteractionHeight = component.height;
                if (component instanceof ListValueComponent listComp) {
                    componentInteractionHeight += listComp.getExpandedListHeight();
                }
                currentY += componentInteractionHeight; // 移动到下一个组件的起始 Content Y
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isExpanded) {
            for (ValueComponent component : getVisibleValueComponents()) {
                if (component.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }
}