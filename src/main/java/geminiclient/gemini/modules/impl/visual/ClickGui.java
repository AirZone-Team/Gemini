package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.modules.impl.visual.clickgui.CategoryPanel;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

// 假设您可以使用 Java AWT Color 或其他颜色类


public class ClickGui extends Module {

    // ClickGui 本身通常也是一个 Module
    public ClickGui() {
        super("ClickGui", ModuleEnum.Visual);
        this.key = 79;
        // 通常 ClickGui 模块默认禁用，通过按键或命令开启
    }

    // 内部类或单独的类来实现 GUI 屏幕
    public static class ClickGuiScreen extends Screen {

        // 渲染组件列表
        private final List<CategoryPanel> categoryPanels = new ArrayList<>();

        // 【新增】滚动偏移量，用于控制整个界面的 Y 轴位置
        private float scrollOffset = 0.0f;
        private final float SCROLL_SPEED = 15.0f; // 每次滚动的像素量

        public float getScrollOffset() {
            return this.scrollOffset;
        }

        public ClickGuiScreen() {
            // Screen 的构造函数需要一个标题 Component
            super(Component.literal("Faith's ClickGUI"));
            int panelX = 10;
            int panelY = 10;
            int panelWidth = 100;

            for (ModuleEnum category : ModuleEnum.values()) {
                CategoryPanel panel = new CategoryPanel(category, panelX, panelY, panelWidth, 150);
                categoryPanels.add(panel);
                panelX += panelWidth + 10;
            }
        }

        // 覆盖 Screen 的 render 方法进行渲染
        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            // 渲染背景
//            this.renderBackground(guiGraphics,mouseX,mouseY,partialTicks);

            // 渲染所有面板
            // 渲染所有面板
            for (CategoryPanel panel : categoryPanels) {
                // 【关键修改】：应用滚动偏移量到面板的 Y 坐标
                panel.setRenderPosition(panel.x, panel.y + (int)scrollOffset);
                panel.render(guiGraphics, mouseX, mouseY, partialTicks,scrollOffset);
            }

//            super.render(guiGraphics, mouseX, mouseY, partialTicks);
        }

        // 覆盖鼠标点击事件
        @Override
        public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean idk) {
            for (CategoryPanel panel : categoryPanels) {
                // 传递原始的屏幕 Y 坐标 (mouseY) 和当前的滚动偏移量
                if (panel.mouseClicked(mouse.x(),mouse.y(),mouse.button(), scrollOffset)) {
                    return true;
                }
            }
            return super.mouseClicked(mouse,idk);
        }

        // 覆盖鼠标释放事件
        @Override
        public boolean mouseReleased(@NotNull MouseButtonEvent mouse) {
            for (CategoryPanel panel : categoryPanels) {
                // 传递原始的屏幕 Y 坐标 (mouseY) 和当前的滚动偏移量
                if (panel.mouseReleased(mouse.x(), mouse.y(), mouse.button(), scrollOffset)) {
                    return true;
                }
            }
            return super.mouseReleased(mouse);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double hScroll, double vScroll) {
            // vScroll 为垂直滚动量，正值通常代表向下滚动 (取决于系统设置，但 Minecraft 通常是正值向下)

            // 1. 更新滚动偏移量
            scrollOffset -= (float) (vScroll * SCROLL_SPEED);

            // 2. 【可选但推荐】限制滚动范围
            // 防止用户滚动到顶部太远（例如，限制 scrollOffset 最小为 0）
//            if (scrollOffset > 0) {
//                scrollOffset = 0;
//            }

            // 3. 【可选】计算最大滚动限制
            // 你需要计算所有面板的总高度和屏幕高度的差值，来设置一个最大负值限制。
            // 这里我们暂时只设置最小限制，防止顶部被滚出屏幕。
            return true;
        }


            // 覆盖按键事件
        @Override
        public boolean keyPressed(KeyEvent keyCode) {
            // 按下 ESC 键关闭 GUI
            if (keyCode.input() == 256) { // 256 是 KeyCode for ESC
                this.onClose();
                return true;
            }
            return super.keyPressed(keyCode);
        }

        // 当屏幕关闭时调用
        @Override
        public void onClose() {
            // 关闭屏幕
            if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
            // 禁用 ClickGui 模块（如果它在按键时自动开启）
            Gemini.moduleManager.getModules().stream()
                    .filter(m -> m instanceof ClickGui)
                    .findFirst()
                    .ifPresent(m -> m.setEnabled(false));
        }

        // 覆盖 ShouldPauseGame，通常 ClickGui 不暂停游戏
        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // 当 ClickGui 模块启用时，打开 GUI 屏幕
    @Override
    public void onEnabled() {
        super.onEnabled();
        if (mc.screen == null) {
            mc.setScreen(new ClickGuiScreen());
        }
    }

    // 当 ClickGui 模块禁用时，如果屏幕还开着就关闭
    @Override
    public void onDisabled() {
        super.onDisabled();
        Gemini.fileSystem.saveConfig();
        if (mc.screen instanceof ClickGuiScreen) {
            mc.setScreen(null);
        }
    }
}