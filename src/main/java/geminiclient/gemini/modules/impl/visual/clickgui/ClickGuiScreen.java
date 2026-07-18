package geminiclient.gemini.modules.impl.visual.clickgui;

import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.animation.SpringAnimation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Classic ClickGui screen: draggable category panels + top search bar.
 */
public class ClickGuiScreen extends AbstractClickGuiScreen {

    private final List<CategoryPanel> categoryPanels = new ArrayList<>();
    private final SearchWidget searchWidget;

    private float scrollOffset = 0.0f;
    private float scrollTarget = 0.0f;
    private final SpringAnimation scrollSpring = SpringAnimation.smooth();
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
        searchWidget = new SearchWidget(10, 8, 140, 22);

        for (ModuleEnum category : ModuleEnum.values()) {
            CategoryPanel panel = new CategoryPanel(category, panelX, panelY, panelWidth, 150);
            categoryPanels.add(panel);
            panelX += panelWidth + 10;
        }
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // ── Smooth scrolling (clamped to the content extent) ──
        float maxScroll = computeMaxScroll();
        scrollTarget = Math.max(-maxScroll, Math.min(0.0f, scrollTarget));
        scrollSpring.setTarget(scrollTarget);
        scrollSpring.update(partialTicks);
        scrollOffset = scrollSpring.getValue();

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

    /** Total scrollable distance: how far the tallest panel extends below the screen. */
    private float computeMaxScroll() {
        int contentBottom = 0;
        for (CategoryPanel panel : categoryPanels) {
            contentBottom = Math.max(contentBottom, panel.getBottomY());
        }
        return Math.max(0.0f, contentBottom - (this.height - 12));
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
        scrollTarget -= (float) (vScroll * SCROLL_SPEED);
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
}
