package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.modules.impl.visual.clickgui.CategoryPanel;
import geminiclient.gemini.modules.impl.visual.clickgui.SearchWidget;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ClickGui extends Module {

    public ClickGui() {
        super("ClickGui", ModuleEnum.Visual);
        this.key = 79;
    }

    public static class ClickGuiScreen extends Screen {

        private final List<CategoryPanel> categoryPanels = new ArrayList<>();
        private final SearchWidget searchWidget;

        private float scrollOffset = 0.0f;
        private static final float SCROLL_SPEED = 15.0f;

        public float getScrollOffset() {
            return this.scrollOffset;
        }

        public ClickGuiScreen() {
            super(Component.literal("Faith's ClickGUI"));
            int panelX = 10;
            int panelY = 44; // below search bar
            int panelWidth = 100;

            // Search bar at top
            searchWidget = new SearchWidget(10, 8, 120, 22);

            for (ModuleEnum category : ModuleEnum.values()) {
                CategoryPanel panel = new CategoryPanel(category, panelX, panelY, panelWidth, 150);
                categoryPanels.add(panel);
                panelX += panelWidth + 10;
            }
        }

        @Override
        public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
            // ── Search bar ──────────────────────────────────
            searchWidget.render(guiGraphics, mouseX, mouseY, partialTicks);

            // ── Apply filter to all panels ──────────────────
            String filter = searchWidget.getFilterText();
            for (CategoryPanel panel : categoryPanels) {
                panel.setFilter(filter);
            }

            // ── Panels ─────────────────────────────────────
            for (CategoryPanel panel : categoryPanels) {
                panel.setRenderPosition(panel.x, panel.y + (int) scrollOffset);
                panel.render(guiGraphics, mouseX, mouseY, partialTicks, scrollOffset);
            }
        }

        @Override
        public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean idk) {
            // Search widget gets first crack
            if (searchWidget.mouseClicked(mouse.x(), mouse.y(), mouse.button())) {
                return true;
            }

            for (CategoryPanel panel : categoryPanels) {
                if (panel.mouseClicked(mouse.x(), mouse.y(), mouse.button(), scrollOffset)) {
                    return true;
                }
            }
            return super.mouseClicked(mouse, idk);
        }

        @Override
        public boolean mouseReleased(@NotNull MouseButtonEvent mouse) {
            for (CategoryPanel panel : categoryPanels) {
                if (panel.mouseReleased(mouse.x(), mouse.y(), mouse.button(), scrollOffset)) {
                    return true;
                }
            }
            return super.mouseReleased(mouse);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double hScroll, double vScroll) {
            scrollOffset -= (float) (vScroll * SCROLL_SPEED);
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent keyCode) {
            int glfwKey = keyCode.input();
            int mods = keyCode.modifiers();

            // ESC closes the GUI (or clears search if filter active)
            if (glfwKey == GLFW.GLFW_KEY_ESCAPE) {
                if (searchWidget.hasFilter()) {
                    searchWidget.clear();
                    return true;
                }
                this.onClose();
                return true;
            }

            // Forward to search widget (includes text input + backspace)
            if (searchWidget.keyPressed(glfwKey, mods)) {
                return true;
            }

            return super.keyPressed(keyCode);
        }

        @Override
        public void onClose() {
            if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
            Gemini.moduleManager.getModules().stream()
                    .filter(m -> m instanceof ClickGui)
                    .findFirst()
                    .ifPresent(m -> m.setEnabled(false));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        if (mc.screen == null) {
            mc.setScreen(new ClickGuiScreen());
        }
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        Gemini.fileSystem.saveConfig();
        if (mc.screen instanceof ClickGuiScreen) {
            mc.setScreen(null);
        }
    }
}
