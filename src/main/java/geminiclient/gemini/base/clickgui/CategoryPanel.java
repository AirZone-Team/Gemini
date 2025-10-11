package geminiclient.gemini.base.clickgui;

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
    private final List<ModuleComponent> moduleComponents = new ArrayList<>();

    // 统一的颜色主题
    private static final int ACCENT_COLOR = new Color(0, 0, 0).getRGB(); // 黑色头部背景
    private static final int TEXT_COLOR = Color.WHITE.getRGB();

    public CategoryPanel(ModuleEnum category, int x, int y, int width, int headerHeight) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.renderY = y;
        this.width = width;
        this.height = headerHeight;

        int currentY = y + headerHeight + 2;
        for (Module module : Gemini.moduleManager.getModules()) {
            if (module.getModuleEnum() == category) {
                // ✅ 左右各缩 2 像素（比类别少 4 像素宽）
                ModuleComponent component = new ModuleComponent(module, this.x + 2, currentY, this.width - 4, 14);
                moduleComponents.add(component);
                currentY += 14;
            }
        }
    }

    public void setRenderPosition(int x, int renderY) {
        this.x = x;
        this.renderY = renderY;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, float scrollOffset) {
        // 1. 拖拽逻辑
        if (isDragging) {
            double newRenderY = mouseY - dragOffsetY;
            this.x = (int) (mouseX - dragOffsetX);
            this.y = (int) (newRenderY - scrollOffset);
        }

        this.renderY = this.y + (int) scrollOffset;

        // 2. 渲染类别头部
        int headerHeight = 15;
        guiGraphics.fill(x, renderY, x + width, renderY + headerHeight, ACCENT_COLOR);
        guiGraphics.drawString(mc.font, category.name(), x + 2, renderY + 3, TEXT_COLOR, true);

        // 3. 渲染模块列表（左右各缩 2px）
        int currentY = this.renderY + headerHeight;
        for (ModuleComponent component : moduleComponents) {
            component.x = this.x + 2; // ✅ 左右各缩 2 像素
            component.y = currentY;
            component.width = this.width - 4; // ✅ 宽度减 4
            component.render(guiGraphics, mouseX, mouseY, partialTicks);

            currentY += component.getTotalHeight();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float scrollOffset) {
        double adjustedMouseY = mouseY - scrollOffset;

        // 检查点击类别头部（拖动）
        if (mouseX >= x && mouseX <= x + width && adjustedMouseY >= y && adjustedMouseY <= y + 15) {
            if (button == 0) {
                isDragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - (y + scrollOffset);
                return true;
            }
        }

        // 传递点击事件到模块组件
        int currentY = this.y + 15;
        for (ModuleComponent component : moduleComponents) {
            component.x = this.x + 2; // ✅ 一致性
            component.y = currentY;
            component.width = this.width - 4;

            if (component.mouseClicked(mouseX, adjustedMouseY, button)) {
                return true;
            }

            currentY += component.getTotalHeight();
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button, float scrollOffset) {
        if (button == 0 && isDragging) {
            isDragging = false;
            return true;
        }

        // 传递释放事件给模块组件
        int currentY = this.y + 15;
        for (ModuleComponent component : moduleComponents) {
            component.x = this.x + 2; // ✅ 对齐渲染偏移
            component.y = currentY;
            component.width = this.width - 4;

            if (component.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
            currentY += component.getTotalHeight();
        }

        return false;
    }
}
