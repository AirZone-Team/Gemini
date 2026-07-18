package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
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
 * Material Design 3 ClickGui rendered as a draggable floating window.
 *
 * <p>The left navigation rail is icon-only by default and expands on hover
 * to reveal labels, sitting visually above the content area.</p>
 */
public class MD3ClickGuiScreen extends AbstractClickGuiScreen implements Md3Overlay.Host {

    private static final int DEFAULT_W = 720;
    private static final int DEFAULT_H = 440;
    private static final int TITLE_H = 24;
    private static final int SEARCH_HEIGHT = 40;
    private static final int RAIL_COLLAPSED_W = 48;
    private static final int HERO_HEIGHT = 84;
    private static final int GAP = 10;
    private static final int ROW_SPACING = 6;
    private static final int PADDING = 10;
    private static final float SCROLL_SPEED = 24f;
    private static final long OPEN_KEY_IGNORE_MS = 180;

    private final Md3SearchBar searchBar;
    private final Md3NavigationRail rail;
    private final Md3HeroCard heroCard;
    private final List<Md3ModuleComponent> allModules = new ArrayList<>();

    private Md3Overlay openOverlay;

    private int winX, winY, winW, winH;
    /** Render-time window Y including the open-animation rise offset. */
    private int renderWinY;
    private boolean dragging = false;
    private double dragOffsetX, dragOffsetY;

    private final Md3Anim scrollAnim = Md3Anim.mediumAnim();
    private float scrollTarget = 0f;
    private float scrollCurrent = 0f;
    private float maxScroll = 0f;

    /** Window entrance animation (rise + scrim fade), started on open. */
    private final Md3Anim openAnim = new Md3Anim(280);

    // Ignore character keys briefly after opening to avoid echoing the ClickGui hotkey
    private long ignoreKeyCharsUntil = 0;

    // Cached layout
    private int listX, listY, listWidth, listBottom;
    private final List<Md3ModuleComponent> visibleRows = new ArrayList<>();

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
            winW = Math.min(DEFAULT_W, this.width);
            winH = Math.min(DEFAULT_H, this.height);
            winX = (this.width - winW) / 2;
            winY = (this.height - winH) / 4;
        }
    }

    // ── Layout ──────────────────────────────────────────

    private void layout() {
        // Clamp window size to the screen (min 420x280 when the screen allows it)
        winW = Math.min(winW, this.width);
        winH = Math.min(winH, this.height);
        winW = Math.max(Math.min(420, this.width), winW);
        winH = Math.max(Math.min(280, this.height), winH);
        winX = Math.max(0, Math.min(winX, this.width - winW));
        winY = Math.max(0, Math.min(winY, this.height - winH));
        renderWinY = winY + openOffsetY();

        int titleBottom = renderWinY + TITLE_H;

        // Rail sits flush against the left edge of the window
        rail.x = winX;
        rail.y = titleBottom;

        searchBar.x = winX + RAIL_COLLAPSED_W + GAP;
        searchBar.y = titleBottom + PADDING / 2;
        searchBar.width = winW - RAIL_COLLAPSED_W - GAP * 2;
        searchBar.height = SEARCH_HEIGHT;

        int top = searchBar.y + SEARCH_HEIGHT + GAP;
        listX = searchBar.x;
        listWidth = searchBar.width;

        heroCard.x = listX;
        heroCard.y = top;
        heroCard.width = listWidth;
        heroCard.height = HERO_HEIGHT;

        listY = top + HERO_HEIGHT + GAP;
        listBottom = renderWinY + winH - PADDING;
    }

    /** Window rise offset during the entrance animation (0 when settled). */
    private int openOffsetY() {
        float p = openAnim.getValue();
        return (int) ((1.0f - Md3Anim.easeOutCubic(p)) * 12);
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
                .sorted(Comparator.comparing((Md3ModuleComponent r) -> !r.module.favorite)
                        .thenComparing(r -> r.module.getName()))
                .collect(Collectors.toList()));
    }

    private int layoutRows() {
        int currentY = listY - (int) scrollCurrent;
        for (Md3ModuleComponent row : visibleRows) {
            row.x = listX;
            row.y = currentY;
            row.width = listWidth;
            currentY += row.getTotalHeight() + ROW_SPACING;
        }
        return currentY - (listY - (int) scrollCurrent) - ROW_SPACING;
    }

    // ── Render ──────────────────────────────────────────

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        layout();
        rebuildVisibleRows();
        rail.updateHover(mouseX, mouseY);

        float openT = Md3Anim.easeOutCubic(openAnim.getValue());

        // Background scrim (fades in with the entrance animation)
        gui.fill(0, 0, this.width, this.height,
                Md3Theme.withAlpha(0x000000, 0.35f * openT));

        // Window shadow + background
        Md3Theme.elevation2(gui, winX, renderWinY, winW, winH, Md3Theme.R_CARD);
        CustomRoundedRectRenderer.drawRoundedRect(gui, winX, renderWinY, winW, winH,
                Md3Theme.R_CARD, Md3Theme.SURFACE_CONTAINER_LOW);

        // Title bar
        CustomRoundedRectRenderer.drawRoundedRect(gui, winX, renderWinY, winW, TITLE_H,
                Md3Theme.R_CARD, Md3Theme.SURFACE_CONTAINER);
        var titleFont = Md3Fonts.title();
        Md3Fonts.drawText(gui, titleFont, "Gemini ClickGUI",
                winX + PADDING, renderWinY + (TITLE_H - Md3Fonts.lineHeight(titleFont)) / 2f,
                Md3Theme.ON_SURFACE);

        // Scroll animation
        scrollAnim.setTarget(scrollTarget);
        scrollCurrent = scrollAnim.getValue();

        for (Md3ModuleComponent row : visibleRows) {
            row.advanceAnimation(partialTicks);
        }

        int contentHeight = layoutRows();
        maxScroll = Math.max(0, contentHeight - (listBottom - listY));
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));

        // Search + content
        searchBar.render(gui, mouseX, mouseY, partialTicks);
        heroCard.render(gui, searchBar.hasFilter() ? null : rail.getSelectedCategory(),
                visibleRows.size());

        // Module list clipped to content area
        gui.enableScissor(listX, listY, listX + listWidth, listBottom);
        for (Md3ModuleComponent row : visibleRows) {
            if (row.y + row.getTotalHeight() < listY || row.y > listBottom) continue;
            row.render(gui, mouseX, mouseY, partialTicks);
        }
        gui.disableScissor();

        // Scrollbar hint
        if (maxScroll > 4) {
            int trackH = listBottom - listY;
            int thumbH = Math.min(trackH, Math.max(24, (int) (trackH * (trackH / (float) (contentHeight)))));
            int thumbY = listY + (int) ((trackH - thumbH) * (scrollCurrent / maxScroll));
            CustomRoundedRectRenderer.drawRoundedRect(gui,
                    listX + listWidth - 6, thumbY, 4, thumbH, 2,
                    Md3Theme.withAlpha(Md3Theme.OUTLINE, 0.5f));
        }

        // Rail rendered last so the expanded panel overlaps content
        rail.render(gui, mouseX, mouseY, partialTicks);

        // Overlay above everything
        if (openOverlay != null) {
            openOverlay.render(gui, mouseX, mouseY, partialTicks);
        }
    }

    // ── Hit helpers ─────────────────────────────────────

    private boolean inWindow(double mx, double my) {
        return mx >= winX && mx <= winX + winW && my >= renderWinY && my <= renderWinY + winH;
    }

    private boolean inTitleBar(double mx, double my) {
        return mx >= winX && mx <= winX + winW && my >= renderWinY && my <= renderWinY + TITLE_H;
    }

    // ── Input ───────────────────────────────────────────

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent mouse, boolean idk) {
        double mx = mouse.x(), my = mouse.y();
        int button = mouse.button();

        if (button == 0 && inTitleBar(mx, my)) {
            dragging = true;
            // Finish the entrance animation so the window tracks the cursor 1:1
            openAnim.snap(1f);
            dragOffsetX = mx - winX;
            dragOffsetY = my - winY;
            return true;
        }

        if (!inWindow(mx, my)) {
            if (openOverlay != null) {
                openOverlay = null;
                return true;
            }
            return super.mouseClicked(mouse, idk);
        }

        if (openOverlay != null) {
            if (openOverlay.contains(mx, my)) {
                openOverlay.mouseClicked(mx, my, button);
            } else {
                openOverlay = null;
            }
            return true;
        }

        // Search bar (but rail has priority when expanded and under the cursor)
        if (rail.isExpanded() && mx <= winX + rail.getCurrentWidth()) {
            if (rail.mouseClicked(mx, my, button)) {
                scrollTarget = 0f;
                scrollAnim.snap(0f);
                return true;
            }
        }

        if (searchBar.mouseClicked(mx, my, button)) {
            return true;
        }

        // Rail
        if (rail.mouseClicked(mx, my, button)) {
            scrollTarget = 0f;
            scrollAnim.snap(0f);
            return true;
        }

        for (Md3ModuleComponent row : visibleRows) {
            if (row.mouseClicked(mx, my, button)) {
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent mouse) {
        double mx = mouse.x(), my = mouse.y();
        int button = mouse.button();

        if (dragging && button == 0) {
            dragging = false;
            return true;
        }

        if (openOverlay != null) {
            if (openOverlay.mouseReleased(mx, my, button)) {
                return true;
            }
        }

        for (Md3ModuleComponent row : allModules) {
            if (row.mouseReleased(mx, my, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouse);
    }

    @Override
    public boolean mouseDragged(@NotNull MouseButtonEvent mouse, double dx, double dy) {
        if (dragging && mouse.button() == 0) {
            winX = (int) (mouse.x() - dragOffsetX);
            winY = (int) (mouse.y() - dragOffsetY);
            return true;
        }
        return super.mouseDragged(mouse, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hScroll, double vScroll) {
        if (openOverlay != null) {
            openOverlay = null;
            return true;
        }
        if (mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= listY && mouseY <= listBottom) {
            scrollTarget -= (float) (vScroll * SCROLL_SPEED);
            scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent keyCode) {
        int glfwKey = keyCode.input();
        int mods = keyCode.modifiers();

        if (glfwKey == GLFW.GLFW_KEY_ESCAPE) {
            if (openOverlay != null) {
                openOverlay = null;
                return true;
            }
            if (searchBar.hasFilter()) {
                searchBar.clear();
                return true;
            }
            this.onClose();
            return true;
        }

        // For a short period after opening, ignore letter/number keys so the
        // ClickGui hotkey (e.g. 'o') is not typed into the search bar.
        if (System.currentTimeMillis() < ignoreKeyCharsUntil) {
            boolean isCharKey = (glfwKey >= GLFW.GLFW_KEY_A && glfwKey <= GLFW.GLFW_KEY_Z)
                    || (glfwKey >= GLFW.GLFW_KEY_0 && glfwKey <= GLFW.GLFW_KEY_9)
                    || glfwKey == GLFW.GLFW_KEY_SPACE;
            if (isCharKey) {
                return true;
            }
        }

        if (searchBar.keyPressed(glfwKey, mods)) {
            return true;
        }

        return super.keyPressed(keyCode);
    }

    // ── Md3Overlay.Host ─────────────────────────────────

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
