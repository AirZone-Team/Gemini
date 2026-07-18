package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Anim;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3ModuleDescriptions;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import geminiclient.gemini.values.ValueParent;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MD3 module card: elevated rounded card holding the module row (title +
 * supporting text, favorite heart, switch) and, when expanded, an inline
 * tonal container with the module's value components. All transitions use
 * fixed-duration Material standard easing — no spring physics.
 */
public class Md3ModuleComponent {

    public static final int ROW_HEIGHT = 46;
    private static final int PAD_H = 12;
    private static final int VALUE_PADDING = 10;
    private static final int VALUE_SPACING = 3;
    private static final int EXPAND_GAP = 6;

    public final Module module;
    public int x, y, width;

    private final List<Md3ValueComponent> valueComponents = new ArrayList<>();
    private final Md3Overlay.Host overlayHost;

    private boolean expanded = false;

    private final Md3Anim expandAnim = Md3Anim.mediumAnim();
    private final Md3Anim switchAnim = Md3Anim.shortAnim();
    private final Md3Anim favAnim = Md3Anim.shortAnim();
    private final Md3Anim hoverAnim = Md3Anim.shortAnim();

    private boolean switchPressed = false;

    private final List<Md3RenderUtils.Ripple> ripples = new ArrayList<>();

    public Md3ModuleComponent(Module module, Md3Overlay.Host overlayHost) {
        this.module = module;
        this.overlayHost = overlayHost;
        for (ValueParent value : module.getValues()) {
            Md3ValueComponent component = Md3ValueComponentFactory.create(value, width, overlayHost);
            if (component != null) {
                valueComponents.add(component);
            }
        }
        expandAnim.snap(0);
    }

    // ── Animation / layout ──────────────────────────────

    /** Update animation targets; call once per frame before layout. */
    public void advanceAnimation(float partialTicks) {
        expandAnim.setTarget(expanded ? 1.0f : 0.0f);
        switchAnim.setTarget(module.enabled ? 1.0f : 0.0f);
        favAnim.setTarget(module.favorite ? 1.0f : 0.0f);
    }

    private float expandProgress() {
        return expandAnim.getValue();
    }

    public int getTotalHeight() {
        int total = ROW_HEIGHT;
        float p = expandProgress();
        if (p > 0.005f) {
            total += (int) ((computeContentHeight() + EXPAND_GAP) * p);
        }
        return total;
    }

    private int computeContentHeight() {
        List<Md3ValueComponent> visible = getVisibleComponents();
        if (visible.isEmpty()) return 0;
        int h = VALUE_PADDING * 2;
        for (Md3ValueComponent c : visible) {
            h += c.getTotalHeight() + VALUE_SPACING;
        }
        return h - VALUE_SPACING;
    }

    private List<Md3ValueComponent> getVisibleComponents() {
        return valueComponents.stream()
                .filter(Md3ValueComponent::isVisible)
                .collect(Collectors.toList());
    }

    // ── Render ──────────────────────────────────────────

    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        int cardH = getTotalHeight();
        int r = Md3Theme.R_ROW;

        // ── Card: tonal fill + subtle outline instead of heavy shadow ─────────
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, width, cardH, r,
                Md3Theme.SURFACE);
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, width, cardH, r,
                Md3Theme.OUTLINE_VARIANT, 1);

        // Hover state layer over the row zone (animated fade)
        hoverAnim.setTarget(isOverRow(mouseX, mouseY) ? 1.0f : 0.0f);
        float hoverT = hoverAnim.getValue();
        if (hoverT > 0.01f) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, width, cardH, r,
                    Md3Theme.modulateAlpha(Md3Theme.hoverState(Md3Theme.ON_SURFACE), hoverT));
        }

        // Ripples (clipped to the card)
        ripples.removeIf(Md3RenderUtils.Ripple::isFinished);
        if (!ripples.isEmpty()) {
            gui.enableScissor(x, y, x + width, y + cardH);
            for (Md3RenderUtils.Ripple ripple : ripples) {
                ripple.render(gui, width * 0.6f, Md3Theme.ON_SURFACE);
            }
            gui.disableScissor();
        }

        // ── Title + supporting text ─────────────────────
        var titleFont = Md3Fonts.title();
        float titleLh = Md3Fonts.lineHeight(titleFont);
        var labelFont = Md3Fonts.label();
        float labelLh = Md3Fonts.lineHeight(labelFont);

        String description = Md3ModuleDescriptions.get(module.getName());
        String supporting = "Category · " + formatCategory();
        if (!description.isEmpty()) {
            supporting += "  —  " + description;
        }

        float textBlockH = titleLh + 2 + labelLh;
        float titleY = y + (ROW_HEIGHT - textBlockH) / 2f;
        Md3Fonts.drawText(gui, titleFont, module.getName(), x + PAD_H, titleY, Md3Theme.ON_SURFACE);
        Md3Fonts.drawText(gui, labelFont, supporting, x + PAD_H, titleY + titleLh + 2,
                Md3Theme.ON_SURFACE_VARIANT);

        // ── Favorite heart (colour cross-fades with the favourite state) ──
        int cell = 2;
        int heartW = 11 * cell, heartH = 9 * cell;
        int heartX = heartCenterX() - heartW / 2;
        int heartY = y + (ROW_HEIGHT - heartH) / 2;
        boolean favHovered = isOverHeart(mouseX, mouseY);
        float favT = favAnim.getValue();
        int unfavColor = favHovered ? Md3Theme.ON_SURFACE : Md3Theme.ON_SURFACE_VARIANT;
        int heartColor = Md3Theme.lerpColor(unfavColor, Md3Theme.PRIMARY, favT);
        Md3RenderUtils.drawHeart(gui, heartX, heartY, cell, module.favorite,
                heartColor, Md3Theme.SURFACE);

        // ── Switch ──────────────────────────────────────
        int swX = switchX();
        int swY = y + (ROW_HEIGHT - Md3RenderUtils.SWITCH_H) / 2;
        Md3RenderUtils.drawSwitch(gui, swX, swY, switchAnim.getValue(),
                isOverSwitch(mouseX, mouseY), switchPressed);

        // ── Expanded value area (inside the same card) ──
        float p = expandProgress();
        if (p > 0.005f) {
            int contentH = computeContentHeight();
            int visibleH = (int) ((contentH + EXPAND_GAP) * p);
            int areaY = y + ROW_HEIGHT + EXPAND_GAP / 2;

            gui.enableScissor(x, y + ROW_HEIGHT, x + width, y + ROW_HEIGHT + visibleH);
            CustomRoundedRectRenderer.drawRoundedRect(gui, x + 6, areaY, width - 12, contentH,
                    Md3Theme.R_CONTROL, Md3Theme.SURFACE_CONTAINER_LOW);
            CustomRoundedRectRenderer.drawRoundedOutline(gui, x + 6, areaY, width - 12, contentH,
                    Md3Theme.R_CONTROL, Md3Theme.OUTLINE_VARIANT, 1);

            int currentY = areaY + VALUE_PADDING;
            for (Md3ValueComponent component : getVisibleComponents()) {
                layoutComponent(component, currentY);
                component.render(gui, mouseX, mouseY, partialTicks);
                currentY += component.getTotalHeight() + VALUE_SPACING;
            }
            gui.disableScissor();
        }
    }

    private void layoutComponent(Md3ValueComponent component, int compY) {
        component.x = x + 6 + VALUE_PADDING;
        component.y = compY;
        component.width = width - 12 - VALUE_PADDING * 2;
    }

    private String formatCategory() {
        String name = module.getModuleEnum().name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    // ── Hit regions ─────────────────────────────────────

    private int switchX() {
        return x + width - PAD_H - Md3RenderUtils.SWITCH_W;
    }

    private int heartCenterX() {
        return switchX() - 24;
    }

    private boolean isOverRow(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + ROW_HEIGHT;
    }

    private boolean isOverSwitch(double mx, double my) {
        int swX = switchX();
        int swY = y + (ROW_HEIGHT - Md3RenderUtils.SWITCH_H) / 2;
        return mx >= swX - 6 && mx <= swX + Md3RenderUtils.SWITCH_W + 6
                && my >= swY - 6 && my <= swY + Md3RenderUtils.SWITCH_H + 6;
    }

    private boolean isOverHeart(double mx, double my) {
        int cx = heartCenterX();
        int cy = y + ROW_HEIGHT / 2;
        return mx >= cx - 18 && mx <= cx + 18 && my >= cy - 18 && my <= cy + 18;
    }

    // ── Input ───────────────────────────────────────────

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverSwitch(mouseX, mouseY)) {
            switchPressed = true;
            module.toggle();
            return true;
        }
        if (button == 0 && isOverHeart(mouseX, mouseY)) {
            module.favorite = !module.favorite;
            return true;
        }
        if (button == 0 && isOverRow(mouseX, mouseY)) {
            if (!valueComponents.isEmpty()) {
                expanded = !expanded;
                ripples.add(new Md3RenderUtils.Ripple((float) mouseX, (float) mouseY));
            }
            return true;
        }

        // Value components (positions mirror render layout)
        if (expandProgress() > 0.01f) {
            int areaY = y + ROW_HEIGHT + EXPAND_GAP / 2;
            int currentY = areaY + VALUE_PADDING;
            for (Md3ValueComponent component : getVisibleComponents()) {
                layoutComponent(component, currentY);
                if (component.mouseClicked(mouseX, mouseY, button)) return true;
                currentY += component.getTotalHeight() + VALUE_SPACING;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            switchPressed = false;
        }
        for (Md3ValueComponent component : valueComponents) {
            if (component.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return false;
    }
}
