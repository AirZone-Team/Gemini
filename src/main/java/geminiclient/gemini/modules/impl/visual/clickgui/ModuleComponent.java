package geminiclient.gemini.modules.impl.visual.clickgui;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.I18n;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.component.*;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.utils.KeyUtils;
import geminiclient.gemini.utils.animation.SpringAnimation;
import geminiclient.gemini.values.ValueParent;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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

    /** 当前处于"按下按键..."绑定模式的模块行（全局唯一）。 */
    private static ModuleComponent activeBinding = null;

    // ── Modern palette (accent/borders/text shared via ClassicTheme) ──
    private static final int ACCENT_PURPLE  = ClassicTheme.ACCENT;
    private static final int BASE_BG        = new Color(18, 18, 25, 195).getRGB();
    private static final int HOVER_BG       = new Color(34, 34, 44, 220).getRGB();
    private static final int ACTIVE_TINT    = ClassicTheme.ACCENT_TINT;
    private static final int ACTIVE_GLOW    = ClassicTheme.ACCENT_GLOW;
    private static final int TEXT_COLOR     = ClassicTheme.TEXT;
    private static final int TEXT_DIM       = ClassicTheme.TEXT_DIM;
    private static final int ARROW_COLOR    = new Color(140, 140, 155).getRGB();
    private static final int BORDER_BASE    = ClassicTheme.BORDER;
    private static final int BORDER_HOVER   = ClassicTheme.BORDER_HOVER;
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
            ValueComponent component = ValueComponentFactory.create(value, 0, 0, width, componentHeight);
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
            h += component.getTotalHeight();
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
            guiGraphics.text(mc.font, I18n.module(module.getName()), x + 13, renderY + 5, textCol, true);
        } else {
            int textCol = modulateAlpha(TEXT_COLOR, contentAlpha);
            guiGraphics.text(mc.font, I18n.module(module.getName()), x + 7, renderY + 5, textCol, true);
        }

        // ── Keybind pill ───────────────────────────────
        boolean binding = (activeBinding == this);
        String keyName = binding
                ? I18n.tr("Press a key...")
                : (module.key != 0 ? KeyUtils.getKeyName(module.key) : "");
        int[] pill = pillBounds(binding, keyName);
        int pillBg = binding
                ? new Color(139, 92, 246, 45).getRGB()
                : new Color(255, 255, 255, 10).getRGB();
        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, pill[0], pill[1], pill[2], pill[3],
                pill[3] / 2, modulateAlpha(pillBg, contentAlpha));
        if (binding) {
            CustomRoundedRectRenderer.drawRoundedOutline(
                    guiGraphics, pill[0], pill[1], pill[2], pill[3],
                    pill[3] / 2, modulateAlpha(ACCENT_PURPLE, contentAlpha), 1);
        }
        int keyCol = binding ? ACCENT_PURPLE : TEXT_DIM;
        int keyTextCol = modulateAlpha(keyCol, contentAlpha);
        guiGraphics.text(mc.font, keyName, pill[0] + 5, pill[1] + 2, keyTextCol, true);

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
                int compHeight = component.getTotalHeight();

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
        return ClassicTheme.lerpColor(a, b, t);
    }

    /** Multiply alpha channel by a factor 0..1 */
    private int modulateAlpha(int color, float factor) {
        return ClassicTheme.modulateAlpha(color, factor);
    }

    /**
     * Convert a key code (see {@link KeyUtils}) to a short readable name.
     */
    public static String getKeyName(int key) {
        return KeyUtils.getKeyName(key);
    }

    // ── Keybind pill ────────────────────────────────────

    /** 键名 pill 的边界 [x, y, w, h]，渲染与命中测试共用。 */
    private int[] pillBounds(boolean binding, String keyName) {
        int nameWidth = mc.font.width(keyName);
        int pillW = Math.max(16, nameWidth + 10);
        int pillRight = x + width - (allValueComponents.isEmpty() ? 8 : 24);
        return new int[]{pillRight - pillW, y + 3, pillW, height - 6};
    }

    private boolean isOverPill(double mouseX, double mouseY) {
        int[] pill = pillBounds(activeBinding == this,
                activeBinding == this ? I18n.tr("Press a key...")
                        : (module.key != 0 ? KeyUtils.getKeyName(module.key) : ""));
        return mouseX >= pill[0] && mouseX <= pill[0] + pill[2]
                && mouseY >= pill[1] && mouseY <= pill[1] + pill[3];
    }

    /** 是否有模块行正在等待按键。 */
    public static boolean hasActiveBinding() {
        return activeBinding != null;
    }

    /** 转发按键给绑定中的模块行；已消费返回 true。 */
    public static boolean dispatchKeyPress(int key) {
        return activeBinding != null && activeBinding.keyPressed(key);
    }

    /** 绑定模式下的按键捕获：Esc 取消，其余键立即绑定（键码域见 {@link KeyUtils}）。 */
    private boolean keyPressed(int key) {
        if (activeBinding != this) {
            return false;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            activeBinding = null;
            return true;
        }
        module.key = key;
        activeBinding = null;
        Gemini.fileSystem.saveConfig();
        return true;
    }

    // ── Input ───────────────────────────────────────────

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isModuleHeaderHovered(mouseX, mouseY)) {
            if (isOverPill(mouseX, mouseY)) {
                if (button == 0) {
                    // 左键：进入/切换绑定模式
                    if (activeBinding != this) {
                        activeBinding = this;
                    }
                } else if (button == 1) {
                    // 右键：清除绑定
                    module.key = 0;
                    if (activeBinding == this) {
                        activeBinding = null;
                    }
                    Gemini.fileSystem.saveConfig();
                }
                return true;
            }
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
                currentY += component.getTotalHeight();
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
                currentY += component.getTotalHeight();
            }
        }
        return false;
    }
}
