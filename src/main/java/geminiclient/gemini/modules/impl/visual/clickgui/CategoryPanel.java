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

    // 现代化颜色主题 - 使用黑灰色调
    private static final int HEADER_COLOR = new Color(30, 33, 45, 240).getRGB();
    private static final int HEADER_HOVER_COLOR = new Color(40, 43, 55, 240).getRGB();
    private static final int ACCENT_COLOR = new Color(220, 220, 220).getRGB(); // 亮灰色作为强调色
    private static final int BACKGROUND_COLOR = new Color(25, 28, 38, 220).getRGB();
    private static final int BORDER_COLOR = new Color(60, 65, 75).getRGB();
    private static final int TEXT_COLOR = new Color(240, 240, 245).getRGB();
    private static final int TEXT_SHADOW = new Color(15, 18, 25, 120).getRGB();
    private static final int MODULE_HIGHLIGHT = new Color(255, 255, 255, 20).getRGB();

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
        handleDragging(mouseX, mouseY, scrollOffset);
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
        renderMainBackground(guiGraphics);
        renderPanelHeader(guiGraphics, mouseX, mouseY);
        if (isExpanded && !moduleComponents.isEmpty()) {
            renderModuleContent(guiGraphics, mouseX, mouseY, partialTicks);
        }
    }

    private void renderMainBackground(GuiGraphics guiGraphics) {
        int panelHeight = getPanelHeight();
        guiGraphics.fill(x, renderY, x + width, renderY + panelHeight, BACKGROUND_COLOR);
        guiGraphics.fill(x, renderY, x + width, renderY + BORDER_WIDTH, BORDER_COLOR);
        guiGraphics.fill(x, renderY + panelHeight - BORDER_WIDTH, x + width, renderY + panelHeight, BORDER_COLOR);
        guiGraphics.fill(x, renderY, x + BORDER_WIDTH, renderY + panelHeight, BORDER_COLOR);
        guiGraphics.fill(x + width - BORDER_WIDTH, renderY, x + width, renderY + panelHeight, BORDER_COLOR);
    }

    private void renderPanelHeader(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean isHoveringHeader = isMouseOverHeader(mouseX, mouseY);
        int headerColor = isHoveringHeader ? HEADER_HOVER_COLOR : HEADER_COLOR;
        guiGraphics.fill(x, renderY, x + width, renderY + HEADER_HEIGHT, headerColor);
        // 头部底部细线，使用强调色
        guiGraphics.fill(x, renderY + HEADER_HEIGHT - 1, x + width, renderY + HEADER_HEIGHT, ACCENT_COLOR);
        renderExpandIcon(guiGraphics);
        renderCategoryText(guiGraphics);
    }

    private void renderExpandIcon(GuiGraphics guiGraphics) {
        int iconX = x + 6;
        int iconY = renderY + 7;
        int iconSize = 6;
        int iconColor = ACCENT_COLOR;

        if (isExpanded) {
            guiGraphics.fill(iconX, iconY + 2, iconX + iconSize, iconY + 2, iconColor);
            guiGraphics.fill(iconX + 2, iconY + 4, iconX + iconSize - 2, iconY + 4, iconColor);
            guiGraphics.fill(iconX + 1, iconY + 3, iconX + iconSize - 1, iconY + 3, iconColor);
        } else {
            guiGraphics.fill(iconX + 2, iconY, iconX + 2, iconY + iconSize, iconColor);
            guiGraphics.fill(iconX + 4, iconY + 2, iconX + 4, iconY + iconSize - 2, iconColor);
            guiGraphics.fill(iconX + 3, iconY + 1, iconX + 3, iconY + iconSize - 1, iconColor);
        }
    }

    private void renderCategoryText(GuiGraphics guiGraphics) {
        String displayName = getFormattedCategoryName();
        int textWidth = mc.font.width(displayName);
        int textX = x + (width - textWidth) / 2;
        int textY = renderY + 6;
        guiGraphics.drawString(mc.font, displayName, textX + 1, textY + 1, TEXT_SHADOW, false);
        guiGraphics.drawString(mc.font, displayName, textX, textY, TEXT_COLOR, false);
    }

    private void renderModuleContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        int contentY = renderY + HEADER_HEIGHT;
        int contentHeight = getContentHeight();

        if (contentHeight > 0) {
            guiGraphics.fill(x, contentY, x + width, contentY + 1, BORDER_COLOR);

            int currentY = contentY + PADDING;
            for (ModuleComponent component : moduleComponents) {
                component.x = x + PADDING;
                component.y = currentY;
                component.width = width - PADDING * 2;

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
                isDragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - (y + scrollOffset);
                return true;
            } else if (button == 1) {
                isExpanded = !isExpanded;
                return true;
            }
        }

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
        return height + PADDING * 2 - MODULE_SPACING;
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