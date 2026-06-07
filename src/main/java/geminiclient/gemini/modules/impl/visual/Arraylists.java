package geminiclient.gemini.modules.impl.visual;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomFontRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.util.*;

/**
 * Modern HUD module list with acrylic glass styling, staggered entry animations,
 * configurable sort order, compact mode, and fine-grained rainbow color control.
 */
public class Arraylists extends Module {

    // ==================== CONFIGURATION VALUES ====================

    public final BoolValue mainBackground   = new BoolValue("Background",    true);
    public final BoolValue moduleBackground = new BoolValue("Module BG",    true);
    public final BoolValue compactMode      = new BoolValue("Compact",      false);
    public final BoolValue textShadow       = new BoolValue("Text Shadow",  true);
    public final ListValue sortMode         = new ListValue("Sort",
            "Length", new String[]{"Length", "Alphabetical", "Category"});
    public final ListValue rainbowMode      = new ListValue("Rainbow",
            "Sync", new String[]{"Off", "Sync", "Wave", "Gradient"});
    public final ColorValue fontColor       = new ColorValue("Font Color",   0xFFFAFAFA);
    public final ColorValue accentColor     = new ColorValue("Accent Color", 0xFF43E096);
    public final ColorValue backgroundColor = new ColorValue("BG Tint",      0xC0121212);
    public final FloatValue rainbowSpeed      = new FloatValue("Speed",       3500f, 500f, 10000f);
    public final FloatValue rainbowSaturation = new FloatValue("Saturation", 0.8f,  0.1f, 1.0f);
    public final FloatValue rainbowBrightness = new FloatValue("Brightness", 1.0f,  0.1f, 1.0f);

    // ==================== VISUAL THEME CONSTANTS ====================

    private static final int   RADIUS_MAIN          = 8;
    private static final int   RADIUS_MODULE        = 4;
    private static final int   STATUS_BAR_W         = 3;
    private static final int   PADDING_X            = 7;
    private static final int   PADDING_Y            = 5;
    private static final int   LINE_GAP             = 3;
    private static final int   SHADOW_LAYERS        = 4;
    private static final int   SHADOW_SPREAD        = 3;

    // Compact overrides
    private static final int   C_RADIUS_MAIN        = 5;
    private static final int   C_RADIUS_MODULE      = 2;
    private static final int   C_STATUS_BAR_W       = 2;
    private static final int   C_PADDING_X          = 4;
    private static final int   C_PADDING_Y          = 2;
    private static final int   C_LINE_GAP           = 1;
    private static final int   C_SHADOW_LAYERS      = 2;
    private static final int   C_SHADOW_SPREAD      = 1;

    // Animation tuning
    private static final float LERP_SPEED           = 0.14f;
    private static final float LERP_SPEED_FAST      = 0.22f;
    private static final float STAGGER_DELAY_MS     = 40f;
    private static final float ENTRY_DURATION_MS    = 360f;

    // ==================== COLOR UTILITY ====================

    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0xFFFFFF) | (alpha << 24);
    }

    private static int brighten(int c, float amount) {
        int r = Math.min(255, (int)(((c >> 16) & 0xFF) + (255 - ((c >> 16) & 0xFF)) * amount));
        int g = Math.min(255, (int)(((c >> 8)  & 0xFF) + (255 - ((c >> 8)  & 0xFF)) * amount));
        int b = Math.min(255, (int)(( c        & 0xFF) + (255 - ( c        & 0xFF)) * amount));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int c, float amount) {
        int r = (int)(((c >> 16) & 0xFF) * (1f - amount));
        int g = (int)(((c >> 8)  & 0xFF) * (1f - amount));
        int b = (int)(( c        & 0xFF) * (1f - amount));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ==================== EASING FUNCTIONS ====================

    private static float easeOutExpo(float t) {
        return t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
    }

    private static float easeOutBack(float t) {
        final float c1 = 1.70158f;
        final float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    // ==================== CACHED STATUS BAR RENDER STATE ====================

    /**
     * A mutable {@link GuiElementRenderState} for the left accent bar.
     * Created once per module and reconfigured each frame with updated
     * position and colors — avoids per-frame allocations.
     */
    private static class StatusBarState implements GuiElementRenderState {
        private static final RenderPipeline PIPELINE   = RenderPipelines.GUI;
        private static final TextureSetup   NO_TEXTURE = TextureSetup.noTexture();

        private Matrix3x2f pose;
        private int x, y, w, h;
        private int topColor, bottomColor;
        @Nullable private ScreenRectangle scissor;
        @Nullable private ScreenRectangle bounds;

        void configure(Matrix3x2f pose, int x, int y, int w, int h,
                       int topColor, int bottomColor,
                       @Nullable ScreenRectangle scissor) {
            this.pose = pose;
            this.x = x; this.y = y;
            this.w = w; this.h = h;
            this.topColor = topColor;
            this.bottomColor = bottomColor;
            this.scissor = scissor;

            if (w > 0 && h > 0) {
                ScreenRectangle b = new ScreenRectangle(x, y, w, h)
                        .transformMaxBounds(pose);
                this.bounds = scissor != null ? scissor.intersection(b) : b;
            } else {
                this.bounds = null;
            }
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            vc.addVertexWith2DPose(pose, x, y).setColor(topColor);
            vc.addVertexWith2DPose(pose, x, y + h).setColor(bottomColor);
            vc.addVertexWith2DPose(pose, x + w, y + h).setColor(bottomColor);
            vc.addVertexWith2DPose(pose, x + w, y).setColor(topColor);
        }

        @Override public @NonNull RenderPipeline pipeline()     { return PIPELINE; }
        @Override public @NonNull TextureSetup textureSetup()   { return NO_TEXTURE; }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds()      { return bounds; }
    }

    // ==================== PER-MODULE ANIMATION STATE ====================

    private static class ModuleAnimation {
        float displayX    = 150f;
        float currentY    = 0f;
        float targetY     = 0f;
        long  entryStart  = -1L;
        int   sortIndex   = -1;
        /** Cached status bar state — allocated once, reconfigured per frame. */
        StatusBarState barState;
    }

    // ==================== INSTANCE STATE ====================

    private final Map<Module, ModuleAnimation> animMap = new LinkedHashMap<>();
    private float containerHeight = 0f;
    private CustomFontRenderer.GlyphFont customFont;

    // ==================== CONSTRUCTOR ====================

    public Arraylists() {
        super("Arraylists", ModuleEnum.Visual);
        addValue(mainBackground, moduleBackground, compactMode, textShadow,
                sortMode, rainbowMode, fontColor, accentColor, backgroundColor,
                rainbowSpeed, rainbowSaturation, rainbowBrightness);
    }

    // ==================== EVENT HANDLER ====================

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        initFontIfNeeded();

        List<Module> modules = Gemini.moduleManager.getModules();
        if (modules == null || modules.isEmpty()) return;

        List<Module> sorted = processAnimationsAndLayout(modules);
        if (sorted.isEmpty()) return;

        drawUserInterface(event.guiGraphics(), sorted);
    }

    // ==================== FONT INITIALIZATION ====================

    private void initFontIfNeeded() {
        if (customFont != null) return;

        String[] candidates = {"Segoe UI", "Microsoft YaHei", "Arial"};
        Font resolved = new Font("SansSerif", Font.PLAIN, 18);
        for (String name : candidates) {
            Font trial = new Font(name, Font.PLAIN, 18);
            if (trial.getFamily().equalsIgnoreCase(name)) {
                resolved = trial;
                break;
            }
        }
        customFont = CustomFontRenderer.fromAwtFont(resolved);
    }

    // ==================== LAYOUT & ANIMATION ENGINE ====================

    /**
     * Sorts modules by the configured strategy, computes target positions,
     * and advances all per-module animations (staggered entry, exit slide-out,
     * vertical position lerp, container height lerp).
     */
    private List<Module> processAnimationsAndLayout(List<Module> modules) {
        long now = System.currentTimeMillis();

        // ---- Resolve compact mode dimensions ----
        boolean compact  = compactMode.enabled;
        int paddingY     = compact ? C_PADDING_Y     : PADDING_Y;
        int lineGap      = compact ? C_LINE_GAP      : LINE_GAP;
        int lineHeight   = (int) mc.font.lineHeight + lineGap;

        // ---- Collect renderable modules ----
        List<Module> sorted = new ArrayList<>();
        for (Module m : modules) {
            animMap.computeIfAbsent(m, k -> new ModuleAnimation());
            ModuleAnimation a = animMap.get(m);
            if (m.enabled || a.displayX < 149f) {
                sorted.add(m);
            }
        }
        animMap.keySet().retainAll(modules);

        if (sorted.isEmpty()) return sorted;

        // ---- Sort ----
        String sm = sortMode.get();
        switch (sm) {
            case "Alphabetical" ->
                sorted.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
            case "Category" ->
                sorted.sort(Comparator
                        .comparingInt((Module m) -> m.getModuleEnum().ordinal())
                        .thenComparing(m -> m.getName(), String.CASE_INSENSITIVE_ORDER));
            default ->
                // "Length" — descending width creates a stepped visual edge
                sorted.sort(Comparator.comparingInt(
                        (Module m) -> -(int) CustomFontRenderer.stringWidth(mc.font, m.getName())));
        }

        // ---- Compute per-module target Y and animate ----
        float yCursor = paddingY;
        for (int i = 0; i < sorted.size(); i++) {
            Module m = sorted.get(i);
            ModuleAnimation a = animMap.get(m);

            // Detect sort-index change for (re-)stagger
            if (a.sortIndex != i) {
                a.sortIndex = i;
                if (m.enabled) {
                    a.entryStart = now + (long) (i * STAGGER_DELAY_MS);
                }
            }

            // Entry progress (0..1) when enabled; disabled → immediate exit
            float entryProgress = 1f;
            if (m.enabled && a.entryStart >= 0) {
                long elapsed = now - a.entryStart;
                if (elapsed <= 0) {
                    entryProgress = 0f;
                } else {
                    entryProgress = easeOutBack(Math.min(1f, elapsed / ENTRY_DURATION_MS));
                }
            } else if (!m.enabled) {
                a.entryStart = -1L;
            }

            // X-axis: entrance slide-in vs exit slide-out
            float targetX = m.enabled ? 0f : 150f;
            float animTargetX;
            float lerpRate;
            if (m.enabled && entryProgress < 1f) {
                animTargetX = 150f * (1f - entryProgress);
                lerpRate    = LERP_SPEED;
            } else {
                animTargetX = targetX;
                lerpRate    = m.enabled ? LERP_SPEED : LERP_SPEED_FAST;
            }
            a.displayX += (animTargetX - a.displayX) * lerpRate;
            if (Math.abs(animTargetX - a.displayX) < 0.05f) a.displayX = animTargetX;

            // Y-axis: smooth vertical repositioning
            a.targetY = yCursor;
            a.currentY += (a.targetY - a.currentY) * LERP_SPEED;
            if (Math.abs(a.targetY - a.currentY) < 0.05f) a.currentY = a.targetY;

            yCursor += lineHeight;
        }

        // ---- Smooth container height ----
        float targetH = paddingY * 2 + sorted.size() * lineHeight;
        containerHeight += (targetH - containerHeight) * LERP_SPEED;

        return sorted;
    }

    // ==================== RENDER PIPELINE ====================

    private void drawUserInterface(GuiGraphicsExtractor g, List<Module> modules) {
        boolean compact  = compactMode.enabled;
        int paddingX     = compact ? C_PADDING_X     : PADDING_X;
        int barW         = compact ? C_STATUS_BAR_W  : STATUS_BAR_W;

        int originX = 6;
        int originY = 6;

        int maxTextW = modules.stream()
                .mapToInt(m -> (int) CustomFontRenderer.stringWidth(mc.font, m.getName()))
                .max().orElse(0);

        int containerW = barW + maxTextW + paddingX * 3;
        int containerH = (int) containerHeight;

        // ---- Main acrylic glass background ----
        if (mainBackground.enabled) {
            renderAcrylicBackground(g, originX, originY, containerW, containerH, compact);
        }

        // ---- Module items ----
        for (int i = 0; i < modules.size(); i++) {
            renderModuleItem(g, modules.get(i), originX, originY, i, modules.size(), compact);
        }
    }

    // ---- Acrylic glass container background ----

    private void renderAcrylicBackground(GuiGraphicsExtractor g,
                                          int x, int y, int w, int h, boolean compact) {
        int r       = compact ? C_RADIUS_MAIN   : RADIUS_MAIN;
        int shadows = compact ? C_SHADOW_LAYERS : SHADOW_LAYERS;
        int spread  = compact ? C_SHADOW_SPREAD : SHADOW_SPREAD;

        // 1. Multi-layer drop shadow
        for (int i = 0; i < shadows; i++) {
            int alpha = 0x38 - i * 10;
            int off   = spread + i;
            CustomRoundedRectRenderer.drawRoundedRect(g,
                    x + off, y + off, w, h, r + i, withAlpha(0, alpha));
        }

        // 2. Tinted glass fill (vertical gradient)
        int tint = backgroundColor.getColor();
        int tintAlpha  = (tint >> 24) & 0xFF;
        int topTint    = darken(tint, 0.10f);
        int bottomTint = darken(tint, 0.40f);
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(g, x, y, w, h, r,
                withAlpha(topTint, tintAlpha), withAlpha(bottomTint, tintAlpha));

        // 3. Inner top-edge highlight (simulates glass reflection)
        CustomRoundedRectRenderer.drawRoundedRect(g,
                x + 1, y, w - 2, 1, r, 0x14FFFFFF);

        // 4. Subtle outline glow
        CustomRoundedRectRenderer.drawRoundedOutline(g,
                x, y, w, h, r, 0x0CFFFFFF, 1);
    }

    // ---- Single module entry ----

    private void renderModuleItem(GuiGraphicsExtractor g, Module m,
                                   int baseX, int baseY, int idx, int total, boolean compact) {
        ModuleAnimation a = animMap.get(m);
        if (a == null) return;

        int paddingX   = compact ? C_PADDING_X    : PADDING_X;
        int barW       = compact ? C_STATUS_BAR_W : STATUS_BAR_W;
        int lineGap    = compact ? C_LINE_GAP     : LINE_GAP;
        int lineHeight = (int) mc.font.lineHeight + lineGap;
        int modRadius  = compact ? C_RADIUS_MODULE : RADIUS_MODULE;

        String name = m.getName();
        int textW   = (int) CustomFontRenderer.stringWidth(mc.font, name);
        int itemW   = barW + textW + paddingX * 2;
        int itemH   = lineHeight;

        float modX = baseX + paddingX - a.displayX;
        float modY = baseY + a.currentY;

        // Module-level background
        if (moduleBackground.enabled && !compact) {
            CustomRoundedRectRenderer.drawRoundedRectVertGrad(g,
                    (int) modX, (int) modY, itemW, itemH, modRadius,
                    0x28FFFFFF, 0x08FFFFFF);
        }

        // Resolve theme color (rainbow or static accent)
        int baseColor = resolveThemeColor(m, idx, total);
        if (!m.enabled) {
            baseColor = withAlpha(0xFF5B5B, 0xA0);
        }

        // Status indicator bar — uses cached state with per-frame color update
        renderStatusBar(g, a, (int) modX, (int) modY, itemH, barW, m.enabled, idx, total);

        // Module name text
        float textX = modX + barW + paddingX;
        float textY = modY + lineGap / 2f;
        renderModuleName(g, name, textX, textY, baseColor, m.enabled, idx, total);
    }

    // ---- Status bar (left accent indicator) ----

    /**
     * Submits a cached {@link StatusBarState} per module, reconfiguring
     * its position and colors each frame. In rainbow modes the bar itself
     * carries a vertical hue gradient (top → bottom hue offset).
     */
    private void renderStatusBar(GuiGraphicsExtractor g, ModuleAnimation a,
                                  int x, int y, int h, int barW,
                                  boolean enabled, int idx, int total) {
        int topColor, bottomColor;

        if (!enabled) {
            topColor    = withAlpha(0xFF5B5B, 0x50);
            bottomColor = withAlpha(0xFF3A3A, 0x30);
        } else {
            String mode = rainbowMode.get();
            long   speed = (long) rainbowSpeed.getValue();
            float  sat   = rainbowSaturation.getValue();
            float  bri   = rainbowBrightness.getValue();

            switch (mode) {
                case "Sync" -> {
                    float hue = (System.currentTimeMillis() % speed) / (float) speed;
                    topColor    = withAlpha(Color.HSBtoRGB(hue, sat, bri), 0xFF);
                    bottomColor = withAlpha(Color.HSBtoRGB((hue + 0.04f) % 1f, sat * 0.85f, bri * 0.7f), 0xFF);
                }
                case "Wave", "Gradient" -> {
                    float hue = ((System.currentTimeMillis() % speed) / (float) speed
                            + (float) idx / Math.max(1, total)) % 1f;
                    topColor    = withAlpha(Color.HSBtoRGB(hue, sat, bri), 0xFF);
                    bottomColor = withAlpha(Color.HSBtoRGB((hue + 0.04f) % 1f, sat * 0.85f, bri * 0.7f), 0xFF);
                }
                default -> {
                    // "Off" — static accent color gradient
                    int accent = accentColor.getColor();
                    topColor    = withAlpha(brighten(accent, 0.30f), 0xFF);
                    bottomColor = withAlpha(darken(accent, 0.18f), 0xFF);
                }
            }
        }

        // Lazily allocate or reuse cached state
        StatusBarState state = a.barState;
        if (state == null) {
            state = new StatusBarState();
            a.barState = state;
        }
        state.configure(
                new Matrix3x2f(g.pose()),
                x, y, barW, h,
                topColor, bottomColor,
                g.peekScissorStack());
        g.submitGuiElementRenderState(state);
    }

    // ---- Module name text rendering ----

    private void renderModuleName(GuiGraphicsExtractor g, String name,
                                   float x, float y, int baseColor, boolean enabled,
                                   int idx, int total) {
        String mode  = rainbowMode.get();
        long   speed = (long) rainbowSpeed.getValue();
        float  sat   = rainbowSaturation.getValue();
        float  bri   = rainbowBrightness.getValue();
        int    alpha = enabled ? 0xFF : 0x80;

        // Text shadow: offset dark copy behind the main text
        if (textShadow.enabled && enabled) {
            CustomFontRenderer.drawString(g, mc.font, name,
                    x + 1f, y + 1f, withAlpha(0, 0x44));
        }

        switch (mode) {
            case "Gradient" -> {
                int len = Math.max(1, name.length());
                CustomFontRenderer.drawGradientString(g, mc.font, name, x, y,
                        i -> {
                            float hue = ((System.currentTimeMillis() % speed) / (float) speed
                                    + (float) i / len) % 1f;
                            return withAlpha(Color.HSBtoRGB(hue, sat, bri), alpha);
                        },
                        i -> {
                            float hue = ((System.currentTimeMillis() % speed) / (float) speed
                                    + (float) i / len + 0.06f) % 1f;
                            return withAlpha(Color.HSBtoRGB(hue, sat, bri), alpha);
                        });
            }
            case "Wave" -> {
                float hue = ((System.currentTimeMillis() % speed) / (float) speed
                        + (float) idx / Math.max(1, total)) % 1f;
                int c1 = withAlpha(Color.HSBtoRGB(hue, sat, bri), alpha);
                int c2 = withAlpha(Color.HSBtoRGB(hue, sat * 0.85f, bri * 0.75f), alpha);
                CustomFontRenderer.drawGradientString(g, mc.font, name, x, y, c1, c2);
            }
            case "Sync" -> {
                float hue = (System.currentTimeMillis() % speed) / (float) speed;
                int c1 = withAlpha(Color.HSBtoRGB(hue, sat, bri), alpha);
                int c2 = withAlpha(Color.HSBtoRGB(hue, sat * 0.85f, bri * 0.75f), alpha);
                CustomFontRenderer.drawGradientString(g, mc.font, name, x, y, c1, c2);
            }
            default -> {
                // "Off" — static color with subtle vertical gradient
                int c1 = withAlpha(brighten(baseColor, 0.12f), alpha);
                int c2 = withAlpha(darken(baseColor, 0.08f), alpha);
                CustomFontRenderer.drawGradientString(g, mc.font, name, x, y, c1, c2);
            }
        }
    }

    // ---- Theme color resolution (rainbow / accent) ----

    private int resolveThemeColor(Module m, int idx, int total) {
        if (!m.enabled) return 0xFF555555;

        String mode  = rainbowMode.get();
        long   speed = (long) rainbowSpeed.getValue();
        float  sat   = rainbowSaturation.getValue();
        float  bri   = rainbowBrightness.getValue();

        return switch (mode) {
            case "Sync" -> withAlpha(Color.HSBtoRGB(
                    (System.currentTimeMillis() % speed) / (float) speed, sat, bri), 0xFF);
            case "Wave", "Gradient" -> withAlpha(Color.HSBtoRGB(
                    ((System.currentTimeMillis() % speed) / (float) speed
                            + (float) idx / Math.max(1, total)) % 1f, sat, bri), 0xFF);
            default -> accentColor.getColor();
        };
    }
}
