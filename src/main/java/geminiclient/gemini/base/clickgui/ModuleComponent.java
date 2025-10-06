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

    // 统一的颜色主题 (与其它组件保持一致)
    private static final int ACCENT_COLOR = new Color(255, 51, 153).getRGB(); // 亮洋红色
    private static final int BASE_BG = new Color(15, 15, 15, 200).getRGB(); // 统一的深色背景
    private static final int HOVER_BG = new Color(30, 30, 30, 200).getRGB(); // 悬停背景
    private static final int TEXT_COLOR = Color.WHITE.getRGB(); // 统一白色文本

    // 【新增】判断鼠标是否在模块头部的方法
    protected boolean isModuleHeaderHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public ModuleComponent(Module module, int x, int y, int width, int height) {
        this.module = module;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height; // 默认高度 (模块头部)

        // ... (ValueComponent 实例化逻辑保持不变)
        int componentHeight = 14;
        for (ValueParent value : module.getValues()) {
            ValueComponent component = null;

            if (value instanceof BoolValue) {
                component = new BoolValueComponent((BoolValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof FloatValue) {
                component = new FloatValueComponent((FloatValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof IntValue) {
                component = new IntValueComponent((IntValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof ListValue) {
                component = new ListValueComponent((ListValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof FloatRangeValue) {
                component = new FloatRangeValueComponent((FloatRangeValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof IntRangeValue) {
                component = new IntRangeValueComponent((IntRangeValue) value, 0, 0, width, componentHeight);
            }

            if (component != null) {
                this.allValueComponents.add(component);
            }
        }
    }

    public int getTotalHeight() {
        // ... (getTotalHeight 逻辑保持不变)
        int totalHeight = this.height;

        if (isExpanded) {
            for (ValueComponent component : getVisibleValueComponents()) {
                totalHeight += component.height;

                if (component instanceof ListValueComponent listComp) {
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

    // 【删除 @Override】
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        boolean isHovered = isModuleHeaderHovered(mouseX, mouseY);

        // 1. 渲染模块背景
        int bgColor;
        if (module.enabled) {
            // 启用：使用暗主题色
            bgColor = new Color(178, 36, 114, 200).getRGB();

            // 悬停时使用亮主题色
            if (isHovered) {
                bgColor = ACCENT_COLOR;
            }
        } else {
            // 未启用：深黑背景
            bgColor = isHovered ? HOVER_BG : BASE_BG;
        }

        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 2. 启用时，在左侧添加主题色高亮条
        if (module.enabled) {
            guiGraphics.fill(x, y, x + 1, y + height, ACCENT_COLOR);
        }

        // 3. 渲染模块名称
        guiGraphics.drawString(mc.font, module.getName(), x + 3, y + 3, TEXT_COLOR, true);

        // 4. 渲染展开箭头 (如果有值组件)
        if (!allValueComponents.isEmpty()) {
            String symbol = isExpanded ? "▼" : "▶";

            // 悬停或展开时使用主题色高亮箭头
            int arrowColor = (isExpanded || isHovered) ? ACCENT_COLOR : TEXT_COLOR;

            guiGraphics.drawString(mc.font, symbol, x + width - 10, y + 3, arrowColor, true);
        }

        // 5. 渲染值组件 (如果已展开)
        if (isExpanded) {
            int currentY = y + height;

            for (ValueComponent component : getVisibleValueComponents()) {
                component.x = x;
                component.y = currentY;
                component.width = width;
                component.render(guiGraphics, mouseX, mouseY, partialTicks);

                int componentRenderHeight = component.height;
                if (component instanceof ListValueComponent listComp) {
                    componentRenderHeight += listComp.getExpandedListHeight();
                }
                currentY += componentRenderHeight;
            }
        }
    }

    // 【删除 @Override】
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查是否点击了模块名称部分
        if (isModuleHeaderHovered(mouseX, mouseY)) {
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
            int currentY = y + height;

            for (ValueComponent component : getVisibleValueComponents()) {

                component.x = x;
                component.y = currentY;

                if (component.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }

                int componentInteractionHeight = component.height;
                if (component instanceof ListValueComponent listComp) {
                    componentInteractionHeight += listComp.getExpandedListHeight();
                }
                currentY += componentInteractionHeight;
            }
        }
        return false;
    }

    // 【删除 @Override】
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isExpanded) {
            int currentY = y + height;
            for (ValueComponent component : getVisibleValueComponents()) {

                // 必须更新组件坐标
                component.x = x;
                component.y = currentY;

                if (component.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }

                // 更新 Y 坐标
                int componentInteractionHeight = component.height;
                if (component instanceof ListValueComponent listComp) {
                    componentInteractionHeight += listComp.getExpandedListHeight();
                }
                currentY += componentInteractionHeight;
            }
        }
        return false;
    }
}