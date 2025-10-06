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

    public CategoryPanel(ModuleEnum category, int x, int y, int width, int headerHeight) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.renderY = y;
        this.width = width;
        // 初始高度只包含头部
        this.height = headerHeight;

        // 填充模块组件
        int currentY = y + headerHeight + 2;
        for (Module module : Gemini.moduleManager.getModules()) {
            if (module.getModuleEnum()== category) {
                ModuleComponent component = new ModuleComponent(module, this.x, currentY, this.width, 14);
                moduleComponents.add(component);
                currentY += 14; // 组件高度
            }
        }
    }

    public void setRenderPosition(int x, int renderY) {
        // 原始位置 'y' 保持不变，但 'renderY' 用于渲染
        this.x = x;
        this.renderY = renderY;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks,float scrollOffset) {
        // 1. 处理拖拽
        if (isDragging) {
            // 1. Panel 的新 Render Y = 鼠标当前的屏幕 Y - 记录的屏幕偏移量
            double newRenderY = mouseY - dragOffsetY;

            // 2. Panel 的新 Content Y (y) = 新 Render Y - scrollOffset
            this.x = (int) (mouseX - dragOffsetX); // Content X 仍然是 (Screen X - dragOffsetX)
            this.y = (int) (newRenderY - scrollOffset); // 核心修复：反向计算 Content Y
        }

        this.renderY = this.y + (int)scrollOffset;

        // 2. 渲染面板头部（类别名称）
        int headerHeight = 15;
        // 渲染背景矩形 (使用 NeoForge/Minecraft 提供的矩形渲染方法)
        guiGraphics.fill(x, renderY, x + width, renderY + headerHeight, Color.DARK_GRAY.getRGB());

        // 渲染文本
        guiGraphics.drawString(mc.font, category.name(), x + 2, renderY + 3, Color.WHITE.getRGB(), true);

        // 3. 渲染模块列表和组件
        int currentY = this.renderY + headerHeight;
        for (ModuleComponent component : moduleComponents) {
            component.x = this.x;
            component.y = currentY; // ModuleComponent 的 Y 坐标会被更新
            component.render(guiGraphics, mouseX, mouseY, partialTicks);

            currentY += component.getTotalHeight();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float scrollOffset) {
        // 1. 计算鼠标的 Content Y 坐标
        // mouseY (屏幕) - scrollOffset = Adjusted Content Y
        double adjustedMouseY = mouseY - scrollOffset;

        // 检查鼠标是否点击在面板头部 (使用 Adjusted Content Y 和 Content Y: this.y)
        if (mouseX >= x && mouseX <= x + width && adjustedMouseY >= y && adjustedMouseY <= y + 15) {

            // 拖拽启动：
            if (button == 0) {
                isDragging = true;

                // 【关键修复】：计算拖拽偏移量时，使用 Adjusted Content Y
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - (y + scrollOffset);  // 确保 dragOffsetY 是 Content Y 的偏移量

                return true;
            }
        }

        // 传递点击事件给模块组件 (使用 Adjusted Content Y)
        int currentY = this.y + 15;
        for (ModuleComponent component : moduleComponents) {
            component.x = this.x;
            component.y = currentY;

            // 传递 Adjusted Content Y 给子组件
            if (component.mouseClicked(mouseX, adjustedMouseY, button)) {
                return true;
            }

            currentY += component.getTotalHeight();
        }
        return false;
    }

    // 【新增】处理鼠标释放的方法（用于结束拖拽，需要新的签名）
    public boolean mouseReleased(double mouseX, double mouseY, int button, float scrollOffset) {
        if (button == 0 && isDragging) {
            isDragging = false;
            return true;
        }
        // 递归调用子组件的 mouseReleased
        double adjustedMouseY = mouseY - scrollOffset;

        for (ModuleComponent component : moduleComponents) {
            if (component.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        // ... (ModuleComponent 的 mouseReleased 逻辑需要类似 mouseClicked 的迭代和位置设置) ...
        // 为了简化，如果只关注拖拽，这一步可以暂时省略。
        return false;
    }
}