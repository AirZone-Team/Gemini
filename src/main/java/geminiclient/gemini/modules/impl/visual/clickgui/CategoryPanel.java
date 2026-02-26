package geminiclient.gemini.modules.impl.visual.clickgui;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;

public class CategoryPanel implements MinecraftInstance {
    public final ModuleEnum category;
    public int x, y, width, height;
    private int renderY;
    private double dragOffsetX, dragOffsetY;
    private boolean isDragging = false;
    private boolean isExpanded = true;
    private final List<ModuleComponent> moduleComponents = new ArrayList<>();

    // 现代化颜色主题 - 直角设计配色
    private static final int HEADER_COLOR = new Color(30, 33, 45, 240).getRGB();
    private static final int HEADER_HOVER_COLOR = new Color(40, 43, 55, 240).getRGB();
    private static final int HEADER_ACCENT = new Color(65, 105, 225).getRGB();
    private static final int BACKGROUND_COLOR = new Color(25, 28, 38, 220).getRGB();
    private static final int BORDER_COLOR = new Color(50, 55, 70).getRGB();
    private static final int TEXT_COLOR = new Color(240, 240, 245).getRGB();
    private static final int TEXT_SHADOW = new Color(15, 18, 25, 120).getRGB();
    private static final int MODULE_HIGHLIGHT = new Color(45, 50, 65, 180).getRGB();

    // 尺寸常量
    private static final int HEADER_HEIGHT = 20;
    private static final int MODULE_SPACING = 1;
    private static final int BORDER_WIDTH = 1;
    private static final int PADDING = 2;

    public CategoryPanel(ModuleEnum category, int x, int y, int width, int headerHeight) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.renderY = y;
        this.width = width;
        this.height = headerHeight;
        initializeModuleComponents();
    }

    private void initializeModuleComponents() {
        int currentY = y + HEADER_HEIGHT;
        for (Module module : Gemini.moduleManager.getModules()) {
            if (module.getModuleEnum() == category) {
                ModuleComponent component = new ModuleComponent(module, this.x + PADDING, currentY,
                        this.width - PADDING * 2, 16);
                moduleComponents.add(component);
                currentY += 16 + MODULE_SPACING;
            }
        }
    }

    public void setRenderPosition(int x, int renderY) {
        this.x = x;
        this.renderY = renderY;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, float scrollOffset) {
        handleDragging(mouseX, mouseY, scrollOffset);          // 传入 scrollOffset
        this.renderY = this.y + (int) scrollOffset;
        renderPanel(guiGraphics, mouseX, mouseY, partialTicks);
    }

    private void handleDragging(double mouseX, double mouseY, float scrollOffset) {
        if (isDragging) {
            this.x = (int) (mouseX - dragOffsetX);
            this.y = (int) (mouseY - dragOffsetY - scrollOffset);
        }
    }

    private void renderPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // 渲染主面板背景（直角设计）
        renderMainBackground(guiGraphics);

        // 渲染面板头部
        renderPanelHeader(guiGraphics, mouseX, mouseY);

        // 如果展开状态，渲染模块内容
        if (isExpanded && !moduleComponents.isEmpty()) {
            renderModuleContent(guiGraphics, mouseX, mouseY, partialTicks);
        }
    }

    private void renderMainBackground(GuiGraphics guiGraphics) {
        int panelHeight = getPanelHeight();

        // 主背景
        guiGraphics.fill(x, renderY, x + width, renderY + panelHeight, BACKGROUND_COLOR);

        // 边框
        guiGraphics.fill(x, renderY, x + width, renderY + BORDER_WIDTH, BORDER_COLOR); // 上边框
        guiGraphics.fill(x, renderY + panelHeight - BORDER_WIDTH, x + width, renderY + panelHeight, BORDER_COLOR); // 下边框
        guiGraphics.fill(x, renderY, x + BORDER_WIDTH, renderY + panelHeight, BORDER_COLOR); // 左边框
        guiGraphics.fill(x + width - BORDER_WIDTH, renderY, x + width, renderY + panelHeight, BORDER_COLOR); // 右边框
    }

    private void renderPanelHeader(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean isHoveringHeader = isMouseOverHeader(mouseX, mouseY);

        // 头部背景（带悬停效果）
        int headerColor = isHoveringHeader ? HEADER_HOVER_COLOR : HEADER_COLOR;
        guiGraphics.fill(x, renderY, x + width, renderY + HEADER_HEIGHT, headerColor);

        // 头部底部装饰线
        guiGraphics.fill(x, renderY + HEADER_HEIGHT - 2, x + width, renderY + HEADER_HEIGHT, HEADER_ACCENT);

        // 头部顶部边框（与主边框重叠，需要覆盖）
        guiGraphics.fill(x, renderY, x + width, renderY + 1, headerColor);

        // 渲染折叠/展开图标
        renderExpandIcon(guiGraphics);

        // 渲染类别名称
        renderCategoryText(guiGraphics);
    }

    private void renderExpandIcon(GuiGraphics guiGraphics) {
        int iconX = x + 6;
        int iconY = renderY + 7;
        int iconSize = 6;

        if (isExpanded) {
            // 向下箭头（直角风格）
            guiGraphics.fill(iconX, iconY + 2, iconX + iconSize, iconY + 2, TEXT_COLOR); // 水平线
            guiGraphics.fill(iconX + 2, iconY + 4, iconX + iconSize - 2, iconY + 4, TEXT_COLOR); // 缩短的水平线
            guiGraphics.fill(iconX + 1, iconY + 3, iconX + iconSize - 1, iconY + 3, TEXT_COLOR); // 中间水平线
        } else {
            // 向右箭头（直角风格）
            guiGraphics.fill(iconX + 2, iconY, iconX + 2, iconY + iconSize, TEXT_COLOR); // 垂直线
            guiGraphics.fill(iconX + 4, iconY + 2, iconX + 4, iconY + iconSize - 2, TEXT_COLOR); // 缩短的垂直线
            guiGraphics.fill(iconX + 3, iconY + 1, iconX + 3, iconY + iconSize - 1, TEXT_COLOR); // 中间垂直线
        }
    }

    private void renderCategoryText(GuiGraphics guiGraphics) {
        String displayName = getFormattedCategoryName();
        int textWidth = mc.font.width(displayName);
        int textX = x + (width - textWidth) / 2;
        int textY = renderY + 6;

        // 文字阴影
        guiGraphics.drawString(mc.font, displayName, textX + 1, textY + 1, TEXT_SHADOW, false);
        // 主文字
        guiGraphics.drawString(mc.font, displayName, textX, textY, TEXT_COLOR, false);
    }

    private void renderModuleContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        int contentY = renderY + HEADER_HEIGHT;
        int contentHeight = getContentHeight();

        if (contentHeight > 0) {
            // 内容区域分隔线
            guiGraphics.fill(x, contentY, x + width, contentY + 1, BORDER_COLOR);

            // 渲染模块组件
            int currentY = contentY + PADDING;
            for (ModuleComponent component : moduleComponents) {
                component.x = x + PADDING;
                component.y = currentY;
                component.width = width - PADDING * 2;

                // 模块悬停效果
                if (isMouseOverModule(component, mouseX, mouseY)) {
                    guiGraphics.fill(component.x, component.y, component.x + component.width,
                            component.y + component.height, MODULE_HIGHLIGHT);
                }

                component.render(guiGraphics, mouseX, mouseY, partialTicks);
                currentY += component.getTotalHeight() + MODULE_SPACING;
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float scrollOffset) {
        double adjustedMouseY = mouseY - scrollOffset;

        if (isMouseOverHeader(mouseX, adjustedMouseY)) {
            if (button == 0) {
                // 左键拖动
                isDragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - (y + scrollOffset);
                return true;
            } else if (button == 1) {
                // 右键折叠/展开
                isExpanded = !isExpanded;
                return true;
            }
        }

        // 如果展开状态，传递点击事件到模块组件
        if (isExpanded) {
            return handleModuleComponentClicks(mouseX, adjustedMouseY, button);
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button, float scrollOffset) {
        if (button == 0 && isDragging) {
            isDragging = false;
            return true;
        }

        // 传递释放事件给模块组件
        if (isExpanded) {
            int currentY = y + HEADER_HEIGHT + PADDING;
            for (ModuleComponent component : moduleComponents) {
                component.x = x + PADDING;
                component.y = currentY;
                component.width = width - PADDING * 2;

                if (component.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
                currentY += component.getTotalHeight() + MODULE_SPACING;
            }
        }

        return false;
    }

    private boolean handleModuleComponentClicks(double mouseX, double mouseY, int button) {
        int currentY = y + HEADER_HEIGHT + PADDING;
        for (ModuleComponent component : moduleComponents) {
            component.x = x + PADDING;
            component.y = currentY;
            component.width = width - PADDING * 2;

            if (component.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            currentY += component.getTotalHeight() + MODULE_SPACING;
        }
        return false;
    }

    private boolean isMouseOverHeader(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + HEADER_HEIGHT;
    }

    private boolean isMouseOverModule(ModuleComponent component, double mouseX, double mouseY) {
        return mouseX >= component.x && mouseX <= component.x + component.width &&
                mouseY >= component.y && mouseY <= component.y + component.height;
    }

    private int getContentHeight() {
        if (!isExpanded || moduleComponents.isEmpty())
            return 0;

        int height = 0;
        for (ModuleComponent component : moduleComponents) {
            height += component.getTotalHeight() + MODULE_SPACING;
        }
        return height + PADDING * 2 - MODULE_SPACING; // 包含内边距
    }

    private int getPanelHeight() {
        return HEADER_HEIGHT + (isExpanded ? getContentHeight() : 0);
    }

    private String getFormattedCategoryName() {
        String name = category.name();
        if (name.length() <= 1)
            return name;
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}