package geminiclient.gemini.modules.impl.visual.clickgui;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.GlowRenderer;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.animation.SpringAnimation;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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

    // ── Modern material palette ─────────────────────────
    private static final int PANEL_BG      = new Color(13, 14, 20, 200).getRGB();
    private static final int HEADER_BG     = new Color(20, 22, 32, 220).getRGB();
    private static final int HEADER_HOVER  = new Color(28, 30, 42, 230).getRGB();
    private static final int ACCENT_COLOR  = new Color(139, 92, 246).getRGB();
    private static final int TEXT_COLOR    = new Color(235, 235, 245).getRGB();
    private static final int TEXT_DIM      = new Color(155, 160, 175).getRGB();
    private static final int BORDER_BASE   = new Color(255, 255, 255, 15).getRGB();
    private static final int BORDER_HOVER  = new Color(255, 255, 255, 30).getRGB();

    // ── Multi-layer shadow ─────────────────────────────
    private static final int AMBIENT_SHADOW = new Color(0, 0, 0, 16).getRGB();
    private static final int CONTACT_SHADOW = new Color(0, 0, 0, 38).getRGB();

    // ── Layout ──────────────────────────────────────────
    private static final int HEADER_HEIGHT  = 24;
    private static final int MODULE_SPACING = 2;
    private static final int PADDING        = 5;
    private static final int CORNER_RADIUS  = 8;

    // ── Filter text (set by SearchWidget) ──────────────
    private String filterText = "";

    public void setFilter(String filter) {
        this.filterText = filter == null ? "" : filter.toLowerCase();
    }

    // ── Animation ──────────────────────────────────────
    private final SpringAnimation expandSpring = SpringAnimation.bouncy();

    public CategoryPanel(ModuleEnum category, int x, int y, int width, int headerHeight) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.renderY = y;
        this.width = width;
        this.height = headerHeight;
        expandSpring.snap(1.0f);
        initializeModuleComponents();
    }

    private void initializeModuleComponents() {
        int currentY = y + HEADER_HEIGHT;
        for (Module module : Gemini.moduleManager.getModules()) {
            if (module.getModuleEnum() == category) {
                ModuleComponent component = new ModuleComponent(module, this.x + PADDING, currentY,
                        this.width - PADDING * 2, 18);
                moduleComponents.add(component);
                currentY += 18 + MODULE_SPACING;
            }
        }
    }

    public void setRenderPosition(int x, int renderY) {
        this.x = x;
        this.renderY = renderY;
    }

    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                       float partialTicks, float scrollOffset) {
        handleDragging(mouseX, mouseY, scrollOffset);
        this.renderY = this.y + (int) scrollOffset;

        // ── Step 1: Advance ALL animation springs BEFORE any layout math ──
        expandSpring.setTarget(isExpanded ? 1.0f : 0.0f);
        expandSpring.update(partialTicks);
        float rawProgress = expandSpring.getValue();
        float easedProgress = SpringAnimation.easeOutCubic(rawProgress);

        for (ModuleComponent comp : moduleComponents) {
            comp.advanceAnimation(partialTicks);
            comp.advanceHover(partialTicks, mouseX, mouseY);
        }

        // ── Step 2: Layout and render using animated heights ──
        renderPanel(guiGraphics, mouseX, mouseY, partialTicks, easedProgress, rawProgress);
    }

    private void handleDragging(double mouseX, double mouseY, float scrollOffset) {
        if (isDragging) {
            this.x = (int) (mouseX - dragOffsetX);
            this.y = (int) (mouseY - dragOffsetY - scrollOffset);
        }
    }

    private void renderPanel(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                              float partialTicks, float easedProgress, float rawProgress) {
        int fullPanelHeight = getFullPanelHeight();
        int animatedHeight = HEADER_HEIGHT + (int) ((fullPanelHeight - HEADER_HEIGHT) * easedProgress);

        // ── Multi-layer shadow ──────────────────────────
        GlowRenderer.drawDropShadow(guiGraphics, x, renderY, width, animatedHeight,
                2, 24, AMBIENT_SHADOW);
        GlowRenderer.drawDropShadow(guiGraphics, x, renderY, width, animatedHeight,
                1, 8, CONTACT_SHADOW);

        // ── Panel background ────────────────────────────
        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, x, renderY, width, animatedHeight,
                CORNER_RADIUS, PANEL_BG);

        // ── Dynamic border ──────────────────────────────
        boolean headerHovered = isMouseOverHeader(mouseX, mouseY);
        int borderColor = headerHovered ? BORDER_HOVER : BORDER_BASE;
        CustomRoundedRectRenderer.drawRoundedOutline(
                guiGraphics, x, renderY, width, animatedHeight,
                CORNER_RADIUS, borderColor, 1);

        // ── Top glass highlight ─────────────────────────
        int topHighlight = new Color(255, 255, 255, 14).getRGB();
        guiGraphics.fill(x + CORNER_RADIUS, renderY + 1,
                x + width - CORNER_RADIUS, renderY + 2, topHighlight);

        // ── Header ──────────────────────────────────────
        renderPanelHeader(guiGraphics, mouseX, mouseY, headerHovered, rawProgress);

        // ── Module content (scissor-clipped) ────────────
        if (rawProgress > 0.01f && !moduleComponents.isEmpty()) {
            int contentY = renderY + HEADER_HEIGHT;
            int contentAnimatedHeight = (int) (getContentHeight() * easedProgress);

            // ── CRITICAL: scissor clips content during expand/collapse ──
            guiGraphics.enableScissor(x, contentY, x + width, contentY + contentAnimatedHeight);
            renderModuleContent(guiGraphics, mouseX, mouseY, partialTicks, rawProgress);
            guiGraphics.disableScissor();
        }
    }

    private void renderPanelHeader(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                    boolean isHovering, float expandProgress) {
        int headerColor = isHovering ? HEADER_HOVER : HEADER_BG;

        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, x, renderY, width, HEADER_HEIGHT,
                CORNER_RADIUS, headerColor);

        // Separator
        int sepY = renderY + HEADER_HEIGHT;
        guiGraphics.fill(x + CORNER_RADIUS, sepY - 1,
                x + width - CORNER_RADIUS, sepY,
                new Color(255, 255, 255, 10).getRGB());

        renderCategoryText(guiGraphics);

        // Arrow — plain character, no alpha crossfade
        String arrow = isExpanded ? "▼" : "▶";
        int arrowColor = isHovering ? TEXT_COLOR : TEXT_DIM;
        guiGraphics.text(mc.font, arrow, x + 9, renderY + 8, arrowColor, true);
    }

    private void renderCategoryText(GuiGraphicsExtractor guiGraphics) {
        String displayName = getFormattedCategoryName();
        int textWidth = mc.font.width(displayName);
        int textX = x + (width - textWidth) / 2;
        int textY = renderY + 8;
        guiGraphics.text(mc.font, displayName, textX, textY, TEXT_COLOR, true);
    }

    private void renderModuleContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                      float partialTicks, float alpha) {
        int contentY = renderY + HEADER_HEIGHT;
        // Float-up offset: 16px → modules slide up from below as panel opens
        float floatOffset = (1.0f - alpha) * 16.0f;

        int currentY = contentY + PADDING;
        for (ModuleComponent component : moduleComponents) {
            if (!matchesFilter(component)) continue;

            int moduleHeight = component.getTotalHeight();

            component.x = x + PADDING;
            component.y = currentY + (int) floatOffset;
            component.width = width - PADDING * 2;

            component.render(guiGraphics, mouseX, mouseY, partialTicks, alpha);
            currentY += moduleHeight + MODULE_SPACING;
        }
    }

    // ── Layout helpers ──────────────────────────────────

    private int getFullPanelHeight() {
        return HEADER_HEIGHT + getContentHeight();
    }

    /**
     * Returns the FULL content height (no animation scaling).
     * Animation scaling is applied in renderPanel() to get animatedHeight.
     */
    private boolean matchesFilter(ModuleComponent comp) {
        return filterText.isEmpty()
                || comp.module.getName().toLowerCase().contains(filterText);
    }

    private int getContentHeight() {
        if (!isExpanded || moduleComponents.isEmpty())
            return 0;

        int height = 0;
        for (ModuleComponent component : moduleComponents) {
            if (matchesFilter(component))
                height += component.getTotalHeight() + MODULE_SPACING;
        }
        return height + PADDING * 2 - MODULE_SPACING;
    }

    // ── Input handling ──────────────────────────────────

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

    private String getFormattedCategoryName() {
        String name = category.name();
        if (name.length() <= 1) return name;
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
