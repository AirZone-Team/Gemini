package geminiclient.gemini.modules.impl.visual.clickgui;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.component.*;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.utils.animation.SpringAnimation;
import geminiclient.gemini.values.ValueParent;
import geminiclient.gemini.values.impl.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class ModuleComponent {
    public final Module module;
    public int x, y, width, height;
    private boolean isExpanded = false;
    private final List<ValueComponent> allValueComponents = new ArrayList<>();

    // ── Modern palette ──────────────────────────────────
    private static final int ACCENT_PURPLE  = new Color(139, 92, 246).getRGB();
    private static final int BASE_BG        = new Color(18, 18, 25, 195).getRGB();
    private static final int HOVER_BG       = new Color(34, 34, 44, 220).getRGB();
    private static final int ACTIVE_TINT    = new Color(139, 92, 246, 14).getRGB();
    private static final int ACTIVE_GLOW    = new Color(139, 92, 246, 25).getRGB();
    private static final int TEXT_COLOR     = new Color(230, 230, 242).getRGB();
    private static final int TEXT_DIM       = new Color(150, 155, 170).getRGB();
    private static final int ARROW_COLOR    = new Color(140, 140, 155).getRGB();
    private static final int BORDER_BASE    = new Color(255, 255, 255, 8).getRGB();
    private static final int BORDER_HOVER   = new Color(255, 255, 255, 22).getRGB();
    private static final int BORDER_ACTIVE  = new Color(139, 92, 246, 120).getRGB();
    private static final int DOT_COLOR      = new Color(195, 205, 220).getRGB();
    private static final int DOT_GLOW       = new Color(139, 92, 246, 60).getRGB();

    private static final int CORNER_RADIUS = 5;

    // ── Spring states (cached each frame) ───────────────
    private final SpringAnimation hoverSpring  = SpringAnimation.smooth();
    private final SpringAnimation expandSpring = SpringAnimation.bouncy();

    private float expandProgress = 0.0f;  // cached spring value for getTotalHeight()
    private float hoverProgress  = 0.0f;  // cached spring value for render
    private float contentAlpha   = 1.0f;  // fade from parent panel

    // ── Hit test ────────────────────────────────────────

    public boolean isModuleHeaderHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    // ── Construction ────────────────────────────────────

    public ModuleComponent(Module module, int x, int y, int width, int height) {
        this.module = module;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        int componentHeight = 16;
        for (ValueParent value : module.getValues()) {
            ValueComponent component = null;

            if (value instanceof BoolValue) {
                component = new BoolValueComponent((BoolValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof FloatValue) {
                component = new FloatValueComponent((FloatValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof IntValue) {
                component = new IntValueComponent((IntValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof ListValue) {
                component = new ListValueComponent((ListValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof FloatRangeValue) {
                component = new FloatRangeValueComponent((FloatRangeValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof IntRangeValue) {
                component = new IntRangeValueComponent((IntRangeValue) value, 0, 0, width, componentHeight);
            } else if (value instanceof CheckboxValue) {
                component = new CheckboxValueComponent((CheckboxValue) value,0,0,width,componentHeight);
            } else if (value instanceof ColorValue) {
                component = new ColorValueComponent((ColorValue) value, 0, 0, width, componentHeight);
            }

            if (component != null) {
                this.allValueComponents.add(component);
            }
        }
    }

    // ── Animation tick (called by CategoryPanel before any layout) ──

    /** Advance the expand spring — call BEFORE getTotalHeight() */
    public void advanceAnimation(float partialTicks) {
        expandSpring.setTarget(isExpanded ? 1.0f : 0.0f);
        expandSpring.update(partialTicks);
        expandProgress = expandSpring.getValue();
    }

    /** Advance the hover spring — call once per frame */
    public void advanceHover(float partialTicks, int mouseX, int mouseY) {
        boolean hovered = isModuleHeaderHovered(mouseX, mouseY);
        hoverSpring.setTarget(hovered ? 1.0f : 0.0f);
        hoverSpring.update(partialTicks);
        hoverProgress = hoverSpring.getValue();
    }

    // ── Height calculations ─────────────────────────────

    /**
     * @return total animated height (header + value components × easeOutCubic)
     */
    public int getTotalHeight() {
        int totalHeight = this.height;
        float eased = SpringAnimation.easeOutCubic(expandProgress);
        if (eased > 0.005f) {
            totalHeight += (int) (computeContentHeight() * eased);
        }
        return totalHeight;
    }

    /** Compute the raw content height of all visible value components (no animation). */
    private int computeContentHeight() {
        int h = 0;
        for (ValueComponent component : getVisibleValueComponents()) {
            h += component.height;
            if (component instanceof ListValueComponent listComp) {
                h += listComp.getExpandedListHeight();
            } else if (component instanceof CheckboxValueComponent checkboxComp) {
                h += checkboxComp.getExpandedListHeight();
            }
        }
        return h;
    }

    private List<ValueComponent> getVisibleValueComponents() {
        return allValueComponents.stream()
                .filter(ValueComponent::isVisible)
                .collect(Collectors.toList());
    }

    // ── Render ──────────────────────────────────────────

    /**
     * Render with alpha fade from parent panel.
     */
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                        float partialTicks, float alpha) {
        this.contentAlpha = alpha;
        render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // ── Hover ────────────────────────────────────
        float easedHover = SpringAnimation.easeInOutCubic(hoverProgress);

        // ── Hover lift: float up 1.5px ───────────────
        int liftY = (int) (easedHover * 1.5f);
        int renderY = y - liftY;

        // ── Interpolated background ──────────────────
        int bgColor = modulateAlpha(
                lerpColor(BASE_BG, HOVER_BG, easedHover),
                contentAlpha);

        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, x, renderY, width, height,
                CORNER_RADIUS, bgColor);

        // ── Dynamic border ───────────────────────────
        int borderColor;
        if (module.enabled) {
            borderColor = modulateAlpha(BORDER_ACTIVE, contentAlpha);
        } else if (easedHover > 0.01f) {
            borderColor = modulateAlpha(
                    lerpColor(BORDER_BASE, BORDER_HOVER, easedHover),
                    contentAlpha);
        } else {
            borderColor = modulateAlpha(BORDER_BASE, contentAlpha);
        }
        CustomRoundedRectRenderer.drawRoundedOutline(
                guiGraphics, x, renderY, width, height,
                CORNER_RADIUS, borderColor, 1);

        // ── Enabled: dot indicator + background lift ─
        if (module.enabled) {
            int activeTint = modulateAlpha(ACTIVE_TINT, contentAlpha);
            CustomRoundedRectRenderer.drawRoundedRect(
                    guiGraphics, x, renderY, width, height,
                    CORNER_RADIUS, activeTint);

            // Dot
            int dotX = x + 7;
            int dotY = renderY + height / 2;
            int dotR = 3;

            int dotGlow  = modulateAlpha(DOT_GLOW, contentAlpha);
            int dotColor = modulateAlpha(DOT_COLOR, contentAlpha);
            guiGraphics.fill(dotX - dotR - 1, dotY - dotR - 1,
                    dotX + dotR + 1, dotY + dotR + 1, dotGlow);
            guiGraphics.fill(dotX - 1, dotY - 1, dotX + 1, dotY + 1, dotColor);

            int textCol = modulateAlpha(TEXT_COLOR, contentAlpha);
            guiGraphics.text(mc.font, module.getName(), x + 13, renderY + 5, textCol, true);
        } else {
            int textCol = modulateAlpha(TEXT_COLOR, contentAlpha);
            guiGraphics.text(mc.font, module.getName(), x + 7, renderY + 5, textCol, true);
        }

        // ── Keybind display ─────────────────────────────
        if (module.key != 0) {
            String keyName = getKeyName(module.key);
            int keyCol = modulateAlpha(TEXT_DIM, contentAlpha);
            int keyX = x + width - (allValueComponents.isEmpty() ? 8 : 24);
            guiGraphics.text(mc.font, keyName, keyX - mc.font.width(keyName), renderY + 5, keyCol, true);
        }

        // ── Expand arrow ────────────────────────────────
        if (!allValueComponents.isEmpty()) {
            String symbol = isExpanded ? "▼" : "▶";
            int arrowColor = isExpanded || easedHover > 0.5f ? ACCENT_PURPLE : ARROW_COLOR;
            int arrowCol = modulateAlpha(arrowColor, contentAlpha);
            guiGraphics.text(mc.font, symbol, x + width - 14, renderY + 5, arrowCol, true);
        }

        // ── Value components ──────────────────────────
        float easedExpand = SpringAnimation.easeOutCubic(expandProgress);
        if (easedExpand > 0.005f) {
            int contentHeight    = computeContentHeight();
            int visibleContentH  = (int) (contentHeight * easedExpand);

            // ── ⑥ Scissor prevents overflow ────────────
            guiGraphics.enableScissor(x, y + height,
                    x + width, y + height + visibleContentH);

            int currentY = y + height;
            int drawn = 0;
            for (ValueComponent component : getVisibleValueComponents()) {
                int compHeight = component.height;
                if (component instanceof ListValueComponent lc) compHeight += lc.getExpandedListHeight();
                else if (component instanceof CheckboxValueComponent cc) compHeight += cc.getExpandedListHeight();

                if (drawn + compHeight <= 0) { drawn += compHeight; currentY += compHeight; continue; }
                if (drawn >= visibleContentH) break;

                int visibleComp = Math.min(compHeight, visibleContentH - drawn);

                component.x = x;
                component.y = currentY;
                component.width = width;
                component.render(guiGraphics, mouseX, mouseY, partialTicks);

                drawn  += visibleComp;
                currentY += compHeight;
            }

            guiGraphics.disableScissor();
        }
    }

    // ── Color helpers ───────────────────────────────────

    private int lerpColor(int a, int b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int aa = (a >> 24) & 0xFF, ra = (a >> 16) & 0xFF,
            ga = (a >> 8) & 0xFF,  ba = a & 0xFF;
        int ab = (b >> 24) & 0xFF, rb = (b >> 16) & 0xFF,
            gb = (b >> 8) & 0xFF,  bb = b & 0xFF;
        return (clamp8((int)(aa + (ab - aa) * t)) << 24)
             | (clamp8((int)(ra + (rb - ra) * t)) << 16)
             | (clamp8((int)(ga + (gb - ga) * t)) << 8)
             |  clamp8((int)(ba + (bb - ba) * t));
    }

    /** Multiply alpha channel by a factor 0..1 */
    private int modulateAlpha(int color, float factor) {
        if (factor >= 1.0f) return color;
        int a = (color >> 24) & 0xFF;
        int newA = clamp8((int) (a * factor));
        return (newA << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp8(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Convert a GLFW key code to a short readable name.
     */
    public static String getKeyName(int key) {
        if (key == 0) return "None";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase();
        // Manual mapping for non-printable keys
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT)  return "LSHIFT";
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return "RSHIFT";
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL)  return "LCTRL";
        if (key == GLFW.GLFW_KEY_RIGHT_CONTROL) return "RCTRL";
        if (key == GLFW.GLFW_KEY_LEFT_ALT)  return "LALT";
        if (key == GLFW.GLFW_KEY_RIGHT_ALT) return "RALT";
        if (key == GLFW.GLFW_KEY_TAB)       return "TAB";
        if (key == GLFW.GLFW_KEY_CAPS_LOCK) return "CAPS";
        if (key == GLFW.GLFW_KEY_ENTER)     return "ENTER";
        if (key == GLFW.GLFW_KEY_SPACE)     return "SPACE";
        if (key == GLFW.GLFW_KEY_ESCAPE)    return "ESC";
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12)
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        return "K" + key;
    }

    // ── Input ───────────────────────────────────────────

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isModuleHeaderHovered(mouseX, mouseY)) {
            if (button == 0) {
                module.toggle();
                return true;
            } else if (button == 1) {
                isExpanded = !isExpanded;
                return true;
            }
        }

        if (isExpanded) {
            int currentY = y + height;
            for (ValueComponent component : getVisibleValueComponents()) {
                component.x = x; component.y = currentY; component.width = width;
                if (component.mouseClicked(mouseX, mouseY, button)) return true;
                int h = component.height;
                if (component instanceof ListValueComponent lc) h += lc.getExpandedListHeight();
                if (component instanceof CheckboxValueComponent cc) h += cc.getExpandedListHeight();
                currentY += h;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isExpanded) {
            int currentY = y + height;
            for (ValueComponent component : getVisibleValueComponents()) {
                component.x = x; component.y = currentY; component.width = width;
                if (component.mouseReleased(mouseX, mouseY, button)) return true;
                int h = component.height;
                if (component instanceof ListValueComponent lc) h += lc.getExpandedListHeight();
                if (component instanceof CheckboxValueComponent cc) h += cc.getExpandedListHeight();
                currentY += h;
            }
        }
        return false;
    }
}
