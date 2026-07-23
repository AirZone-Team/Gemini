package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.ClickGui;
import geminiclient.gemini.modules.impl.visual.clickgui.AbstractClickGuiScreen;
import geminiclient.gemini.modules.impl.visual.clickgui.SearchFilterModel;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.component.Md3ModuleComponent;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.component.Md3Overlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Material Design 3 ClickGui rendered as a draggable floating pane.
 *
 * <p>The layout follows the desktop MD3 pattern: a top app bar, persistent
 * navigation rail, expressive section header, then a vertically scrolling
 * collection of filled cards.</p>
 */
public class MD3ClickGuiScreen extends AbstractClickGuiScreen implements Md3Overlay.Host {

    private static final int DEFAULT_W = 720;
    private static final int DEFAULT_H = 440;
    private static final int MIN_W = 520;
    private static final int MIN_H = 320;

    private static final int APP_BAR_H = 56;
    private static final int SEARCH_HEIGHT = 40;
    private static final int HERO_HEIGHT = 78;
    private static final int SECTION_HEADER_H = 26;
    private static final int CONTENT_PADDING = 16;
    private static final int GAP = 12;
    private static final int ROW_SPACING = 8;
    private static final float SCROLL_SPEED = 28f;
    private static final long OPEN_KEY_IGNORE_MS = 180;

    private final Md3SearchBar searchBar;
    private final Md3NavigationRail rail;
    private final Md3HeroCard heroCard;
    private final List<Md3ModuleComponent> allModules = new ArrayList<>();
    private final List<Md3ModuleComponent> visibleRows = new ArrayList<>();

    private Md3Overlay openOverlay;

    private int winX, winY, winW, winH;
    private int renderWinY;
    private boolean dragging;
    private double dragOffsetX, dragOffsetY;

    private final Md3Anim scrollAnim = Md3Anim.mediumAnim();
    private float scrollTarget;
    private float scrollCurrent;
    private float maxScroll;
    private final Md3Anim openAnim = new Md3Anim(280);

    private long ignoreKeyCharsUntil;

    // Cached layout coordinates.
    private int contentX, contentWidth;
    private int sectionHeaderY;
    private int listX, listY, listWidth, listBottom;

    public MD3ClickGuiScreen() {
        super(Component.literal("Gemini ClickGUI"));
        searchBar = new Md3SearchBar(0, 0, 0, SEARCH_HEIGHT);
        rail = new Md3NavigationRail(0, 0);
        heroCard = new Md3HeroCard(0, 0, 0, HERO_HEIGHT);
        for (Module module : Gemini.moduleManager.getModules()) {
            allModules.add(new Md3ModuleComponent(module, this));
        }
        ignoreKeyCharsUntil = System.currentTimeMillis() + OPEN_KEY_IGNORE_MS;
        openAnim.snap(0f);
        openAnim.setTarget(1f);
    }

    @Override
    protected void init() {
        super.init();
        if (winW == 0 || winH == 0) {
            int viewportLeft = logicalViewportLeft();
            int viewportTop = logicalViewportTop();
            int viewportWidth = logicalViewportRight() - viewportLeft;
            int viewportHeight = logicalViewportBottom() - viewportTop;
            winW = Math.min(DEFAULT_W, viewportWidth);
            winH = Math.min(DEFAULT_H, viewportHeight);
            winX = viewportLeft + (viewportWidth - winW) / 2;
            winY = viewportTop + (viewportHeight - winH) / 2;
        }
    }

    private float scale() {
        return ClickGui.md3Scale();
    }

    private double logicalX(double screenX) {
        double pivotX = this.width / 2.0;
        return pivotX + (screenX - pivotX) / scale();
    }

    private double logicalY(double screenY) {
        double pivotY = this.height / 2.0;
        return pivotY + (screenY - pivotY) / scale();
    }

    private int logicalViewportLeft() {
        return (int) Math.ceil(logicalX(0));
    }

    private int logicalViewportTop() {
        return (int) Math.ceil(logicalY(0));
    }

    private int logicalViewportRight() {
        return (int) Math.floor(logicalX(this.width));
    }

    private int logicalViewportBottom() {
        return (int) Math.floor(logicalY(this.height));
    }

    private void layout() {
        int viewportLeft = logicalViewportLeft();
        int viewportTop = logicalViewportTop();
        int viewportRight = logicalViewportRight();
        int viewportBottom = logicalViewportBottom();
        int viewportWidth = Math.max(1, viewportRight - viewportLeft);
        int viewportHeight = Math.max(1, viewportBottom - viewportTop);

        winW = Math.min(winW, viewportWidth);
        winH = Math.min(winH, viewportHeight);
        winW = Math.max(Math.min(MIN_W, viewportWidth), winW);
        winH = Math.max(Math.min(MIN_H, viewportHeight), winH);
        winX = Math.max(viewportLeft, Math.min(winX, viewportRight - winW));
        winY = Math.max(viewportTop, Math.min(winY, viewportBottom - winH));
        renderWinY = winY + openOffsetY();

        contentX = winX + Md3NavigationRail.WIDTH + CONTENT_PADDING;
        contentWidth = winW - Md3NavigationRail.WIDTH - CONTENT_PADDING * 2;

        rail.x = winX;
        rail.y = renderWinY + APP_BAR_H;
        rail.height = winH - APP_BAR_H;

        int searchX = Math.max(winX + 190, contentX);
        searchBar.x = searchX;
        searchBar.y = renderWinY + (APP_BAR_H - SEARCH_HEIGHT) / 2;
        searchBar.width = Math.max(140, winX + winW - 54 - searchX);
        searchBar.height = SEARCH_HEIGHT;

        heroCard.x = contentX;
        heroCard.y = renderWinY + APP_BAR_H + CONTENT_PADDING;
        heroCard.width = contentWidth;
        heroCard.height = HERO_HEIGHT;

        sectionHeaderY = heroCard.y + HERO_HEIGHT + GAP;
        listX = contentX;
        listY = sectionHeaderY + SECTION_HEADER_H;
        listWidth = contentWidth;
        listBottom = renderWinY + winH - CONTENT_PADDING;
    }

    private int openOffsetY() {
        float progress = openAnim.getValue();
        return (int) ((1.0f - Md3Anim.easeOutCubic(progress)) * 14);
    }

    private void rebuildVisibleRows() {
        String filter = searchBar.getFilterText();
        ModuleEnum category = rail.getSelectedCategory();
        boolean favorites = rail.isFavoritesSelected();

        visibleRows.clear();
        visibleRows.addAll(allModules.stream()
                .filter(row -> searchBar.hasFilter()
                        ? SearchFilterModel.matches(row.module, filter)
                        : (favorites ? row.module.favorite : row.module.getModuleEnum() == category))
                .sorted(Comparator.comparing((Md3ModuleComponent row) -> !row.module.favorite)
                        .thenComparing(row -> row.module.getName()))
                .collect(Collectors.toList()));
    }

    private int layoutRows() {
        int currentY = listY - Math.round(scrollCurrent);
        for (Md3ModuleComponent row : visibleRows) {
            row.x = listX;
            row.y = currentY;
            row.width = listWidth;
            currentY += row.getTotalHeight() + ROW_SPACING;
        }
        return Math.max(0, currentY - (listY - Math.round(scrollCurrent)) - ROW_SPACING);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                   float partialTicks) {
        layout();
        rebuildVisibleRows();

        float openT = Md3Anim.easeOutCubic(openAnim.getValue());
        gui.fill(0, 0, this.width, this.height,
                Md3Theme.withAlpha(0x000000, 0.42f * openT));

        int logicalMouseX = (int) Math.round(logicalX(mouseX));
        int logicalMouseY = (int) Math.round(logicalY(mouseY));
        float guiScale = scale();
        float pivotX = this.width / 2f;
        float pivotY = this.height / 2f;
        var pose = gui.pose();
        pose.pushMatrix();
        pose.translate(pivotX, pivotY);
        pose.scale(guiScale, guiScale);
        pose.translate(-pivotX, -pivotY);
        try {
            // Main floating surface.
            Md3Theme.elevation3(gui, winX, renderWinY, winW, winH, Md3Theme.R_EXTRA_LARGE);
            CustomRoundedRectRenderer.drawRoundedRect(gui, winX, renderWinY, winW, winH,
                    Md3Theme.R_EXTRA_LARGE, Md3Theme.SURFACE_CONTAINER_LOW);

            renderTopAppBar(gui, logicalMouseX, logicalMouseY);
            rail.render(gui, logicalMouseX, logicalMouseY, partialTicks);

            scrollAnim.setTarget(scrollTarget);
            scrollCurrent = scrollAnim.getValue();
            for (Md3ModuleComponent row : visibleRows) {
                row.advanceAnimation(partialTicks);
            }

            int contentHeight = layoutRows();
            maxScroll = Math.max(0, contentHeight - (listBottom - listY));
            scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));

            int enabledCount = (int) visibleRows.stream().filter(row -> row.module.enabled).count();
            heroCard.render(gui, searchBar.hasFilter() ? null : rail.getSelectedCategory(),
                    visibleRows.size(), enabledCount,
                    !searchBar.hasFilter() && rail.isFavoritesSelected());

            renderSectionHeader(gui);
            renderModuleList(gui, logicalMouseX, logicalMouseY, partialTicks);

            if (openOverlay != null) {
                // Modal state layer separates menus/pickers from content without
                // obscuring the destination context.
                gui.fill(winX, renderWinY + APP_BAR_H, winX + winW, renderWinY + winH,
                        Md3Theme.withAlpha(Md3Theme.ON_SURFACE, 0.05f));
                openOverlay.render(gui, logicalMouseX, logicalMouseY, partialTicks);
            }
        } finally {
            pose.popMatrix();
        }
    }

    private void renderTopAppBar(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        int radius = Md3Theme.R_EXTRA_LARGE;
        CustomRoundedRectRenderer.drawRoundedRect(gui, winX + 1, renderWinY + 1,
                winW - 2, APP_BAR_H, radius - 1, Md3Theme.SURFACE_CONTAINER_LOW);
        // Square the lower app-bar corners while preserving the outer top shape.
        gui.fill(winX + 1, renderWinY + radius, winX + winW - 1,
                renderWinY + APP_BAR_H, Md3Theme.SURFACE_CONTAINER_LOW);
        gui.fill(winX + 16, renderWinY + APP_BAR_H - 1, winX + winW - 16,
                renderWinY + APP_BAR_H, Md3Theme.withAlpha(Md3Theme.OUTLINE_VARIANT, 0.55f));

        int brandX = winX + 28;
        int brandY = renderWinY + APP_BAR_H / 2;
        CustomRoundedRectRenderer.drawCircle(gui, brandX, brandY, 32,
                Md3Theme.PRIMARY_CONTAINER);
        Md3RenderUtils.drawSparkle(gui, brandX, brandY, 14, Md3Theme.PRIMARY);

        var titleFont = Md3Fonts.title();
        var labelFont = Md3Fonts.label();
        Md3Fonts.drawText(gui, titleFont, "Gemini", winX + 50, renderWinY + 13,
                Md3Theme.ON_SURFACE);
        Md3Fonts.drawText(gui, labelFont, "CONTROL CENTER", winX + 50, renderWinY + 31,
                Md3Theme.ON_SURFACE_VARIANT);

        searchBar.render(gui, mouseX, mouseY, 0f);

        if (isOverClose(mouseX, mouseY)) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, winX + winW - 46,
                    renderWinY + 8, 40, 40, 20,
                    Md3Theme.hoverState(Md3Theme.ON_SURFACE));
        }
        Md3RenderUtils.drawClose(gui, winX + winW - 26, renderWinY + APP_BAR_H / 2,
                10, Md3Theme.ON_SURFACE_VARIANT);
    }

    private void renderSectionHeader(GuiGraphicsExtractor gui) {
        var titleFont = Md3Fonts.title();
        var labelFont = Md3Fonts.label();
        String heading = searchBar.hasFilter() ? "Matching modules" : "Modules";
        Md3Fonts.drawText(gui, titleFont, heading, listX, sectionHeaderY + 2,
                Md3Theme.ON_SURFACE);

        String count = visibleRows.size() + " shown";
        float countW = Md3Fonts.width(labelFont, count);
        Md3Fonts.drawText(gui, labelFont, count, listX + listWidth - countW,
                sectionHeaderY + 5, Md3Theme.ON_SURFACE_VARIANT);
    }

    private void renderModuleList(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                  float partialTicks) {
        gui.enableScissor(listX, listY, listX + listWidth, listBottom);
        if (visibleRows.isEmpty()) {
            renderEmptyState(gui);
        } else {
            for (Md3ModuleComponent row : visibleRows) {
                if (row.y + row.getTotalHeight() < listY || row.y > listBottom) continue;
                row.render(gui, mouseX, mouseY, partialTicks);
            }
        }
        gui.disableScissor();
    }

    private void renderEmptyState(GuiGraphicsExtractor gui) {
        int centerX = listX + listWidth / 2;
        int centerY = listY + Math.max(50, (listBottom - listY) / 2 - 12);
        CustomRoundedRectRenderer.drawCircle(gui, centerX, centerY - 14, 44,
                Md3Theme.SECONDARY_CONTAINER);

        if (!searchBar.hasFilter() && rail.isFavoritesSelected()) {
            Md3RenderUtils.drawHeartPlusIcon(gui, centerX, centerY - 14,
                    22, Md3Theme.ON_SECONDARY_CONTAINER);
        } else {
            Md3RenderUtils.drawSearchIcon(gui, centerX - 7, centerY - 21, 15,
                    Md3Theme.ON_SECONDARY_CONTAINER);
        }

        var titleFont = Md3Fonts.title();
        var labelFont = Md3Fonts.label();
        String title = searchBar.hasFilter() ? "No modules found" : "No favorites yet";
        String message = searchBar.hasFilter()
                ? "Try a shorter or different module name"
                : "Select the heart on a module to pin it here";
        float titleW = Md3Fonts.width(titleFont, title);
        float messageW = Md3Fonts.width(labelFont, message);
        Md3Fonts.drawText(gui, titleFont, title, centerX - titleW / 2f, centerY + 16,
                Md3Theme.ON_SURFACE);
        Md3Fonts.drawText(gui, labelFont, message, centerX - messageW / 2f, centerY + 34,
                Md3Theme.ON_SURFACE_VARIANT);
    }

    private boolean inWindow(double mouseX, double mouseY) {
        return mouseX >= winX && mouseX <= winX + winW
                && mouseY >= renderWinY && mouseY <= renderWinY + winH;
    }

    private boolean inAppBar(double mouseX, double mouseY) {
        return mouseX >= winX && mouseX <= winX + winW
                && mouseY >= renderWinY && mouseY <= renderWinY + APP_BAR_H;
    }

    private boolean isOverClose(double mouseX, double mouseY) {
        return mouseX >= winX + winW - 50 && mouseX <= winX + winW
                && mouseY >= renderWinY && mouseY <= renderWinY + APP_BAR_H;
    }

    private boolean isOverList(double mouseX, double mouseY) {
        return mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= listY && mouseY <= listBottom;
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean idk) {
        double mouseX = logicalX(mouse.x());
        double mouseY = logicalY(mouse.y());
        int button = mouse.button();

        if (!inWindow(mouseX, mouseY)) {
            if (openOverlay != null) {
                openOverlay = null;
                return true;
            }
            return super.mouseClicked(mouse, idk);
        }

        if (openOverlay != null) {
            if (openOverlay.contains(mouseX, mouseY)) {
                openOverlay.mouseClicked(mouseX, mouseY, button);
            } else {
                openOverlay = null;
            }
            return true;
        }

        if (button == 0 && isOverClose(mouseX, mouseY)) {
            onClose();
            return true;
        }

        if (searchBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && inAppBar(mouseX, mouseY)) {
            dragging = true;
            openAnim.snap(1f);
            dragOffsetX = mouseX - winX;
            dragOffsetY = mouseY - winY;
            return true;
        }

        if (rail.mouseClicked(mouseX, mouseY, button)) {
            resetScroll();
            return true;
        }

        if (isOverList(mouseX, mouseY)) {
            for (Md3ModuleComponent row : visibleRows) {
                if (row.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        return true;
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent mouse) {
        double mouseX = logicalX(mouse.x());
        double mouseY = logicalY(mouse.y());
        int button = mouse.button();

        if (dragging && button == 0) {
            dragging = false;
            return true;
        }

        if (openOverlay != null && openOverlay.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        for (Md3ModuleComponent row : allModules) {
            if (row.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouse);
    }

    @Override
    public boolean mouseDragged(@NotNull MouseButtonEvent mouse, double dx, double dy) {
        if (dragging && mouse.button() == 0) {
            winX = (int) (logicalX(mouse.x()) - dragOffsetX);
            winY = (int) (logicalY(mouse.y()) - dragOffsetY);
            return true;
        }
        return super.mouseDragged(mouse, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hScroll, double vScroll) {
        mouseX = logicalX(mouseX);
        mouseY = logicalY(mouseY);
        if (openOverlay != null) {
            openOverlay = null;
            return true;
        }
        if (isOverList(mouseX, mouseY)) {
            scrollTarget -= (float) (vScroll * SCROLL_SPEED);
            scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent keyCode) {
        int glfwKey = keyCode.input();
        int modifiers = keyCode.modifiers();

        if (glfwKey == GLFW.GLFW_KEY_ESCAPE) {
            if (openOverlay != null) {
                openOverlay = null;
                return true;
            }
            if (searchBar.hasFilter()) {
                searchBar.clear();
                resetScroll();
                return true;
            }
            onClose();
            return true;
        }

        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && (glfwKey == GLFW.GLFW_KEY_K || glfwKey == GLFW.GLFW_KEY_F)) {
            openOverlay = null;
            searchBar.focus();
            return true;
        }

        if (System.currentTimeMillis() < ignoreKeyCharsUntil) {
            boolean isCharKey = (glfwKey >= GLFW.GLFW_KEY_A && glfwKey <= GLFW.GLFW_KEY_Z)
                    || (glfwKey >= GLFW.GLFW_KEY_0 && glfwKey <= GLFW.GLFW_KEY_9)
                    || glfwKey == GLFW.GLFW_KEY_SPACE;
            if (isCharKey) {
                return true;
            }
        }

        if (searchBar.keyPressed(glfwKey, modifiers)) {
            resetScroll();
            return true;
        }

        return super.keyPressed(keyCode);
    }

    private void resetScroll() {
        scrollTarget = 0f;
        scrollCurrent = 0f;
        scrollAnim.snap(0f);
    }

    @Override
    public void openOverlay(Md3Overlay overlay) {
        this.openOverlay = overlay;
    }

    @Override
    public void closeOverlay() {
        this.openOverlay = null;
    }

    @Override
    public int screenWidth() {
        return winW;
    }

    @Override
    public int screenHeight() {
        return winH;
    }

    @Override
    public int rightEdge() {
        return winX + winW;
    }

    @Override
    public int bottomEdge() {
        return renderWinY + winH;
    }
}
