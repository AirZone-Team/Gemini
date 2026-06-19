package geminiclient.gemini.modules.impl.visual;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatRangeValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
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
import java.io.File;
import java.util.*;
import java.util.function.IntFunction;

/**
 * Modern HUD module list with full FDPClient-inspired configuration.
 * Supports multiple color modes, tag display, rect indicators,
 * slide animations, and fine-grained rainbow / hue-range control.
 */
public class Arraylists extends Module {

    // ==================== CONFIGURATION VALUES ====================

    // Display toggles
    public final BoolValue mainBackground   = new BoolValue("Background",    true);
    public final BoolValue moduleBackground = new BoolValue("Module BG",    true);
    public final BoolValue compactMode      = new BoolValue("Compact",      false);
    public final BoolValue textShadow       = new BoolValue("Text Shadow",  true);
    public final ListValue sortMode         = new ListValue("Sort",
            "Length", new String[]{"Length", "Alphabetical", "Category"});

    // Color modes
    public final ListValue colorMode        = new ListValue("Text-Color",
            "Sync", new String[]{"Custom", "Sync", "Wave", "Gradient", "SkyRainbow", "Slowly", "Static", "Fade"});
    public final ListValue tagColorMode     = new ListValue("Tag-Color",
            "Custom", new String[]{"Custom", "Sync", "Wave", "Gradient", "SkyRainbow", "Slowly", "Static", "Fade"});
    public final ListValue rectColorMode    = new ListValue("Rect-Color",
            "Sync", new String[]{"Custom", "Sync", "Wave", "Gradient", "SkyRainbow", "Slowly", "Static", "Fade"});

    // Static colors
    public final ColorValue fontColor       = new ColorValue("Font Color",   0xFFFAFAFA);
    public final ColorValue tagColor        = new ColorValue("Tag Color",    0xFFC3C3C3);
    public final ColorValue rectCustomColor = new ColorValue("Rect Color",   0xFF43E096);
    public final ColorValue backgroundColor = new ColorValue("BG Tint",      0xC0121212);

    // Rainbow control
    public final FloatValue rainbowSpeed      = new FloatValue("Speed",       3500f, 500f, 10000f);
    public final FloatValue rainbowSaturation = new FloatValue("Saturation", 0.8f,  0.1f, 1.0f);
    public final FloatValue rainbowBrightness = new FloatValue("Brightness", 1.0f,  0.1f, 1.0f);
    public final FloatRangeValue rainbowHueRange = new FloatRangeValue("Hue Range", 0.0f, 1.0f, 0.0f, 1.0f);
    // Rect indicator style
    public final ListValue rectMode          = new ListValue("Rect",
            "Left", new String[]{"None", "Left", "Right", "Outline", "Special", "Top"});

    // Background
    public final IntValue backgroundAlpha    = new IntValue("BG-Alpha",   0, 0, 255);
    public final IntValue backgroundExpand   = new IntValue("BG-Expand",  2, 0, 10);

    // Layout
    public final ListValue caseMode          = new ListValue("Case",
            "Normal", new String[]{"Upper", "Normal", "Lower"});
    public final FloatValue spaceValue        = new FloatValue("Space",      0f,   0f, 5f);
    public final FloatValue textYOffset       = new FloatValue("TextY",      1f,   0f, 20f);
    public final FloatValue fontAlphaValue    = new FloatValue("TextAlpha",  1.0f, 0.0f, 1.0f);
    public final ListValue ttfFont           = new ListValue("Font",
            "Default", new String[]{"Default"});

    // Misc
    public final BoolValue noRenderModules   = new BoolValue("NoRenderModules",  false);

    // ==================== VISUAL THEME CONSTANTS ====================

    private static final int   RADIUS_MAIN          = 8;
    private static final int   RADIUS_MODULE        = 4;
    private static final int   STATUS_BAR_W         = 3;
    private static final int   PADDING_X            = 7;
    private static final int   PADDING_Y            = 5;
    private static final int   LINE_GAP             = 3;

    // Compact overrides
    private static final int   C_RADIUS_MAIN        = 5;
    private static final int   C_RADIUS_MODULE      = 2;
    private static final int   C_STATUS_BAR_W       = 2;
    private static final int   C_PADDING_X          = 4;
    private static final int   C_PADDING_Y          = 2;
    private static final int   C_LINE_GAP           = 1;

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

    /** Maps a 0-1 hue into the configured [rainbowHueMin, rainbowHueMax] range. */
    private float mapHue(float hue) {
        float min = rainbowHueRange.getMinValue();
        float max = rainbowHueRange.getMaxValue();
        return min + (hue % 1f) * (max - min);
    }

    /** Compute a rainbow hue from a time-based index (per-module offset). */
    private float baseHue(long speedMs, int offset, int divisor) {
        return ((System.currentTimeMillis() % speedMs) / (float) speedMs
                + (float) offset / Math.max(1, divisor)) % 1f;
    }

    /** Wave: smooth sine oscillation — hue peaks then decays smoothly, no sawtooth jump. */
    private float waveHue(long speedMs, int offset, int divisor) {
        float t = ((System.currentTimeMillis() % speedMs) / (float) speedMs
                + (float) offset / Math.max(1, divisor)) % 1f;
        return (float) (Math.sin(t * Math.PI * 2) * 0.5 + 0.5);
    }

    /** SkyRainbow: smooth sine-wave oscillation instead of abrupt fold back. */
    private float skyRainbowHue(long speedMs, int offset) {
        double v = Math.ceil(System.currentTimeMillis() / (double) speedMs + offset * 109L) / 5.0;
        v %= 360.0;
        double t = v / 360.0;
        return (float) (Math.sin(t * Math.PI * 2) * 0.5 + 0.5);
    }

    /** Slowly: very slow nanoTime-based cycling with per-module offset. */
    private static float slowlyHue(int offset) {
        return (System.nanoTime() / 1.0E9f + offset * -3000000f) / 2f % 1f;
    }

    /** Static: smooth sine-wave oscillation instead of abrupt fold back. */
    private static float staticHue(long speedMs, int offset) {
        float t = ((System.currentTimeMillis() % speedMs) / (float) speedMs
                + (float) offset * 0.05f) % 1f;
        return (float) (Math.sin(t * Math.PI * 2) * 0.5 + 0.5);
    }

    /** Fade: oscillates brightness of a base color, returns a Color. */
    private static int fadeColor(int baseRgb, int index, int total) {
        float[] hsb = new float[3];
        Color.RGBtoHSB((baseRgb >> 16) & 0xFF, (baseRgb >> 8) & 0xFF, baseRgb & 0xFF, hsb);
        float b = Math.abs(((System.currentTimeMillis() % 2000L) / 1000.0f
                + (float) index / Math.max(1, total) * 2f) % 2f - 1f);
        b = 0.5f + 0.5f * b;
        return Color.HSBtoRGB(hsb[0], hsb[1], Math.min(b, 1f));
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
        /** Random hue for "Random" color mode, -1 = not assigned. */
        float randomHue   = -1f;
        boolean wasEnabled = false;
        StatusBarState barState;
    }

    // ==================== INSTANCE STATE ====================

    private final Map<Module, ModuleAnimation> animMap = new LinkedHashMap<>();
    private float containerHeight = 0f;
    private CustomFontRenderer.GlyphFont customFont;
    private String lastSelectedFont = null;
    private final float lastFontSize = 8f;

    // ==================== CONSTRUCTOR ====================

    public Arraylists() {
        super("Arraylists", ModuleEnum.Visual);
        addValue(mainBackground, moduleBackground, compactMode, textShadow,
                sortMode, colorMode, tagColorMode, rectColorMode,
                fontColor, tagColor, rectCustomColor, backgroundColor,
                rainbowSpeed, rainbowSaturation, rainbowBrightness, rainbowHueRange,
                rectMode,
                backgroundAlpha, backgroundExpand, caseMode, spaceValue,
                textYOffset, fontAlphaValue, ttfFont,
                noRenderModules);
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
        String selected = ttfFont.get();
        if (selected.equals(lastSelectedFont)) return;
        lastSelectedFont = selected;

        if ("Default".equals(selected)) {
            customFont = null;
            CustomFontRenderer.setCurrentTtfGlyphFont(null, null);
            return;
        }

        File fontFile = Gemini.fileSystem.getTtfFontFile(selected);
        if (fontFile != null && fontFile.exists()) {
            try {
                customFont = CustomFontRenderer.loadFont(fontFile, 8f);
                CustomFontRenderer.setCurrentTtfGlyphFont(customFont, selected);
                return;
            } catch (Exception e) {
                System.err.println("[Arraylists] Failed to load TTF font '" + selected + "': " + e.getMessage());
            }
        }
        customFont = null;
    }

    private float textWidth(String text) {
        return customFont != null
            ? CustomFontRenderer.stringWidth(customFont, text)
            : CustomFontRenderer.stringWidth(mc.font, text);
    }

    private int textLineHeight(int lineGap) {
        return (int) (customFont != null ? customFont.lineHeight : mc.font.lineHeight) + lineGap;
    }

    // ==================== TEXT TRANSFORMATION ====================

    private String changeCase(String str) {
        return switch (caseMode.get().toLowerCase()) {
            case "upper" -> str.toUpperCase();
            case "lower" -> str.toLowerCase();
            default -> str;
        };
    }

    private String getModuleTag(Module module) {
        return ""; // Reserved — Module.tag not yet available
    }

    private boolean shouldSkipModule(Module m) {
        return noRenderModules.enabled && m.getModuleEnum() == ModuleEnum.Visual;
    }

    // ==================== LAYOUT & ANIMATION ENGINE ====================

    private List<Module> processAnimationsAndLayout(List<Module> modules) {
        long now = System.currentTimeMillis();

        boolean compact  = compactMode.enabled;
        int paddingY     = compact ? C_PADDING_Y     : PADDING_Y;
        int lineGap      = compact ? C_LINE_GAP      : LINE_GAP;
        float extraSpace = spaceValue.getValue();
        int lineHeight   = textLineHeight(lineGap) + (int) extraSpace;

        List<Module> sorted = new ArrayList<>();
        for (Module m : modules) {
            if (shouldSkipModule(m)) continue;
            animMap.computeIfAbsent(m, k -> new ModuleAnimation());
            ModuleAnimation a = animMap.get(m);
            if (m.enabled || a.displayX < 149f) {
                sorted.add(m);
            }
        }
        animMap.keySet().retainAll(modules);

        if (sorted.isEmpty()) return sorted;

        // Sort
        String sm = sortMode.get();
        switch (sm) {
            case "Alphabetical" ->
                sorted.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
            case "Category" ->
                sorted.sort(Comparator
                        .comparingInt((Module m) -> m.getModuleEnum().ordinal())
                        .thenComparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
            default ->
                sorted.sort(Comparator.comparingInt(
                        (Module m) -> -(int) textWidth(m.getName())));
        }

        // Animate per-module positions
        float yCursor = paddingY;
        for (int i = 0; i < sorted.size(); i++) {
            Module m = sorted.get(i);
            ModuleAnimation a = animMap.get(m);

            boolean toggled = a.wasEnabled != m.enabled;

            // Only trigger entry animation when the module itself was just toggled
            if (a.sortIndex != i) {
                a.sortIndex = i;
                if (m.enabled && toggled && a.entryStart < 0) {
                    a.entryStart = now + (long) (i * STAGGER_DELAY_MS);
                }
            }

            // Random hue: assign once when module enables
            if (m.enabled && a.randomHue < 0) {
                a.randomHue = (float) Math.random();
            } else if (!m.enabled) {
                a.randomHue = -1f;
            }

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

            a.targetY = yCursor;
            a.currentY += (a.targetY - a.currentY) * LERP_SPEED;
            if (Math.abs(a.targetY - a.currentY) < 0.05f) a.currentY = a.targetY;
            // Mark toggle complete once Y has settled, so next toggle is detected
            if (Math.abs(a.targetY - a.currentY) < 0.05f) {
                a.wasEnabled = m.enabled;
            }

            yCursor += lineHeight;
        }

        float targetH = paddingY * 2 + sorted.size() * lineHeight;
        containerHeight += (targetH - containerHeight) * LERP_SPEED;

        return sorted;
    }

    // ==================== COLOR RESOLUTION ====================

    /**
     * Resolves a single ARGB color for a module from the given color mode.
     * For "Gradient" mode this returns the Wave-equivalent (single pass);
     * per-char colors are handled separately in text rendering.
     */
    private int resolveColor(String mode, int baseColor, ModuleAnimation anim,
                             int idx, int total, float sat, float bri, int alpha) {
        long speed = (long) rainbowSpeed.getValue();

        int rgb = switch (mode) {
            case "Sync" -> Color.HSBtoRGB(mapHue(baseHue(speed, 0, 1)), sat, bri);
            case "Wave" -> Color.HSBtoRGB(mapHue(waveHue(speed, idx, total)), sat, bri);
            case "Gradient" -> Color.HSBtoRGB(mapHue(baseHue(speed, idx, total)), sat, bri);
            case "SkyRainbow" -> {
                float hue = skyRainbowHue(speed, idx);
                yield Color.HSBtoRGB(hue, sat, bri);
            }
            case "Slowly" -> {
                float hue = slowlyHue(idx);
                yield Color.HSBtoRGB(hue, sat, bri);
            }
            case "Static" -> {
                float hue = staticHue(speed, idx);
                yield Color.HSBtoRGB(hue, sat, bri);
            }
            case "Fade" -> fadeColor(baseColor, idx, total);
            default -> baseColor; // Custom / Off
        };
        return withAlpha(rgb, alpha);
    }

    /** Resolves per-character hue for "Gradient" text mode. */
    private float charHue(int charIdx, int charTotal, int moduleIdx, int moduleTotal) {
        long speed = (long) rainbowSpeed.getValue();
        return mapHue(baseHue(speed, moduleIdx, moduleTotal)
                + (float) charIdx / Math.max(1, charTotal));
    }

    /** Resolves per-character hue with offset for gradient bottom half. */
    private float charHueOffset(int charIdx, int charTotal, int moduleIdx, int moduleTotal, float offset) {
        long speed = (long) rainbowSpeed.getValue();
        return mapHue((baseHue(speed, moduleIdx, moduleTotal)
                + (float) charIdx / Math.max(1, charTotal) + offset) % 1f);
    }

    // ==================== RENDER PIPELINE ====================

    private void drawUserInterface(GuiGraphicsExtractor g, List<Module> modules) {
        boolean compact  = compactMode.enabled;
        int paddingX     = compact ? C_PADDING_X     : PADDING_X;
        int barW         = compact ? C_STATUS_BAR_W  : STATUS_BAR_W;
        int bgExpand     = backgroundExpand.getValue();

        int originX = hudX;
        int originY = hudY;
        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(this);

        // Measure all modules
        int maxTextW = 0;
        for (Module m : modules) {
            String displayName = changeCase(m.getName());
            int w = (int) textWidth(displayName + getModuleTag(m));
            if (w > maxTextW) maxTextW = w;
        }

        int containerW = barW + maxTextW + paddingX * 3 + bgExpand * 2;
        int containerH = (int) containerHeight;

        // ---- Main acrylic glass background ----
        if (mainBackground.enabled) {
            renderAcrylicBackground(g, originX, originY, containerW, containerH, compact);
        }

        // ---- Module items ----
        for (int i = 0; i < modules.size(); i++) {
            renderModuleItem(g, modules.get(i), originX, originY, i, modules.size(),
                    compact, containerW, rightAligned, bgExpand, barW, paddingX);
        }

        Gemini.hudDragManager.registerDragRegion(this, originX, originY, containerW, containerH);
    }

    @Override
    public void renderEditorPlaceholder(GuiGraphicsExtractor g) {
        boolean compact = compactMode.enabled;
        int paddingX    = compact ? C_PADDING_X     : PADDING_X;
        int barW        = compact ? C_STATUS_BAR_W  : STATUS_BAR_W;
        int lineGap     = compact ? C_LINE_GAP      : LINE_GAP;
        int lineHeight  = textLineHeight(lineGap);
        int radius      = compact ? C_RADIUS_MAIN   : RADIUS_MAIN;
        int bgExpand    = backgroundExpand.getValue();

        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(this);
        List<Module> allMods = Gemini.moduleManager.getModules();

        int count = 0;
        int maxTextW = 0;
        for (Module m : allMods) {
            if (shouldSkipModule(m)) continue;
            count++;
            int tw = (int) textWidth(m.getName());
            if (tw > maxTextW) maxTextW = tw;
        }

        int containerW = barW + maxTextW + paddingX * 3 + bgExpand * 2;
        int containerH = paddingY() * 2 + count * lineHeight;
        int originX = hudX;
        int originY = hudY;

        CustomRoundedRectRenderer.drawRoundedOutline(g, originX, originY,
                containerW, containerH, radius, 0xAAFFD700, 2);

        if (!enabled) {
            int i = 0;
            for (Module m : allMods) {
                if (shouldSkipModule(m)) continue;
                String name = changeCase(m.getName());
                int textW = (int) textWidth(name);
                float textX = rightAligned
                        ? originX + containerW - paddingX - textW
                        : originX + barW + paddingX;
                float textY = originY + paddingY() + i * lineHeight + lineGap / 2f;

                if (customFont != null) {
                    CustomFontRenderer.drawString(g, customFont, name, textX, textY, 0x88FFD700);
                } else {
                    CustomFontRenderer.drawString(g, mc.font, name, textX, textY, 0x88FFD700);
                }
                i++;
            }
        }

        Gemini.hudDragManager.registerDragRegion(this, originX, originY, containerW, containerH);
    }

    private int paddingY() {
        return compactMode.enabled ? C_PADDING_Y : PADDING_Y;
    }

    // ---- Acrylic glass container background ----

    private void renderAcrylicBackground(GuiGraphicsExtractor g,
                                          int x, int y, int w, int h, boolean compact) {
        int r = compact ? C_RADIUS_MAIN : RADIUS_MAIN;

        int tint = backgroundColor.getColor();
        int tintAlpha  = (tint >> 24) & 0xFF;
        int topTint    = darken(tint, 0.10f);
        int bottomTint = darken(tint, 0.40f);
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(g, x, y, w, h, r,
                withAlpha(topTint, tintAlpha), withAlpha(bottomTint, tintAlpha));

        CustomRoundedRectRenderer.drawRoundedRect(g,
                x + 1, y, w - 2, 1, r, 0x14FFFFFF);
        CustomRoundedRectRenderer.drawRoundedOutline(g,
                x, y, w, h, r, 0x0CFFFFFF, 1);
    }

    // ---- Single module entry ----

    private void renderModuleItem(GuiGraphicsExtractor g, Module m,
                                   int baseX, int baseY, int idx, int total,
                                   boolean compact, int containerW,
                                   boolean rightAligned, int bgExpand,
                                   int barW, int paddingX) {
        ModuleAnimation a = animMap.get(m);
        if (a == null) return;

        int lineGap    = compact ? C_LINE_GAP      : LINE_GAP;
        int lineHeight = textLineHeight(lineGap);
        int modRadius  = compact ? C_RADIUS_MODULE : RADIUS_MODULE;

        String name = changeCase(m.getName());
        String tag  = getModuleTag(m);
        String fullText = name + tag;
        int textW   = (int) textWidth(fullText);
        int itemW   = barW + textW + paddingX * 2;

        float modX;
        if (rightAligned) {
            modX = baseX + containerW - paddingX - itemW - a.displayX;
        } else {
            modX = baseX + paddingX - a.displayX;
        }
        float modY = baseY + a.currentY;

        // Module background — flush with container edges when main BG is shown
        if (moduleBackground.enabled && !compact) {
            int bgColor = backgroundColor.getColor();
            int bgAlpha = backgroundAlpha.getValue();
            if (bgAlpha > 0) {
                bgColor = withAlpha(bgColor, bgAlpha);
            }
            if (mainBackground.enabled) {
                CustomRoundedRectRenderer.drawRoundedRect(g,
                        baseX, (int) modY,
                        containerW, lineHeight, 0,
                        bgColor);
            } else {
                int bgExpandVal = backgroundExpand.getValue();
                CustomRoundedRectRenderer.drawRoundedRect(g,
                        (int) modX - bgExpandVal, (int) modY,
                        itemW + bgExpandVal * 2, lineHeight, modRadius,
                        bgColor);
            }
        }

        // Resolve theme colors
        float sat = rainbowSaturation.getValue();
        float bri = rainbowBrightness.getValue();
        int alpha = (int) (fontAlphaValue.getValue() * 255);
        if (!m.enabled) alpha = Math.min(alpha, 0x80);

        // Status bar / rect indicator
        renderStatusBar(g, a, (int) modX, (int) modY, lineHeight, barW,
                m, idx, total, sat, bri, alpha);

        // Text position
        float textX;
        if (rightAligned) {
            textX = modX + itemW - paddingX - textW;
        } else {
            textX = modX + barW + paddingX;
        }
        float textY = modY + textYOffset.getValue();

        // Draw module name + tag
        renderModuleText(g, name, tag, textX, textY, m, a, idx, total, sat, bri, alpha);
    }

    // ---- Status bar / rect indicator ----

    private void renderStatusBar(GuiGraphicsExtractor g, ModuleAnimation a,
                                  int x, int y, int h, int barW,
                                  Module m, int idx, int total,
                                  float sat, float bri, int alpha) {
        String rectModeStr = rectMode.get();
        if ("None".equals(rectModeStr)) return;

        int color = resolveColor(rectColorMode.get(), rectCustomColor.getColor(),
                a, idx, total, sat, bri, 0xFF);

        if (!m.enabled) {
            color = withAlpha(0xFF5B5B, 0x50);
        }

        int topColor = withAlpha(brighten(color, 0.30f), 0xFF);
        int bottomColor = withAlpha(darken(color, 0.18f), 0xFF);

        switch (rectModeStr) {
            case "Left" -> {
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
            case "Right" -> {
                StatusBarState state = a.barState;
                if (state == null) {
                    state = new StatusBarState();
                    a.barState = state;
                }
                state.configure(
                        new Matrix3x2f(g.pose()),
                        x + barW + 50, y, barW, h,
                        topColor, bottomColor,
                        g.peekScissorStack());
                g.submitGuiElementRenderState(state);
            }
            case "Outline" -> {
                int thick = 1;
                CustomRoundedRectRenderer.drawRoundedOutline(g,
                        x - thick, y - thick,
                        barW + 52 + thick * 2, h + thick * 2,
                        2, color, thick);
            }
            case "Top", "Special" -> {
                if (idx == 0) {
                    CustomRoundedRectRenderer.drawRoundedRect(g,
                            x, y - 1, barW + 52, 2, 1, color);
                }
            }
        }
    }

    // ---- Module name + tag text rendering ----

    private void renderModuleText(GuiGraphicsExtractor g, String name, String tag,
                                   float x, float y, Module m, ModuleAnimation a,
                                   int idx, int total, float sat, float bri, int alpha) {
        String colorModeStr = colorMode.get();
        String tagModeStr   = tagColorMode.get();
        boolean shadow      = textShadow.enabled && m.enabled;

        String fullText = name + tag;
        int textW = (int) textWidth(fullText);
        int nameW = (int) textWidth(name);

        int tagBaseColor = resolveColor(tagModeStr, tagColor.getColor(),
                a, idx, total, sat, bri, alpha);

        if ("Gradient".equals(colorModeStr)) {
            int len = Math.max(1, name.length());
            int tLen = Math.max(1, fullText.length());

            if (customFont != null) {
                // Gradient: per-char colors for name
                CustomFontRenderer.drawGradientString(g, customFont, name, x, y,
                        ci -> withAlpha(Color.HSBtoRGB(charHue(ci, len, idx, total), sat, bri), alpha),
                        ci -> withAlpha(Color.HSBtoRGB(charHueOffset(ci, len, idx, total, 0.06f), sat, bri), alpha));
                // Tag: uniform resolved color
                CustomFontRenderer.drawString(g, customFont, tag, x + nameW, y, tagBaseColor);
            } else {
                IntFunction<Integer> topFunc = ci -> withAlpha(Color.HSBtoRGB(charHue(ci, tLen, idx, total), sat, bri), alpha);
                IntFunction<Integer> botFunc = ci -> withAlpha(Color.HSBtoRGB(charHueOffset(ci, tLen, idx, total, 0.06f), sat, bri), alpha);
                CustomFontRenderer.drawGradientString(g, mc.font, fullText, x, y, topFunc, botFunc);
            }
            // Shadow (rendered separately for gradient)
            if (shadow) {
                if (customFont != null) {
                    CustomFontRenderer.drawGradientString(g, customFont, name, x + 1f, y + 1f,
                            ci -> withAlpha(0, 0x44),
                            ci -> withAlpha(0, 0x44));
                    CustomFontRenderer.drawString(g, customFont, tag, x + nameW + 1f, y + 1f,
                            withAlpha(0, 0x44));
                } else {
                    CustomFontRenderer.drawString(g, mc.font, fullText, x + 1f, y + 1f, withAlpha(0, 0x44));
                }
            }
        } else {
            // Non-gradient modes: single resolved color for entire text
            int textColor = resolveColor(colorModeStr, fontColor.getColor(),
                    a, idx, total, sat, bri, alpha);
            if (!m.enabled) {
                textColor = withAlpha(0xFF5B5B, 0xA0);
                tagBaseColor = withAlpha(0xFF5B5B, 0xA0);
            }

            if (shadow) {
                if (customFont != null) {
                    CustomFontRenderer.drawString(g, customFont, fullText, x + 1f, y + 1f, withAlpha(0, 0x44));
                } else {
                    CustomFontRenderer.drawString(g, mc.font, fullText, x + 1f, y + 1f, withAlpha(0, 0x44));
                }
            }

            // Draw name
            if (customFont != null) {
                CustomFontRenderer.drawString(g, customFont, name, x, y, textColor);
                // Draw tag with tag color
                if (!tag.isEmpty()) {
                    CustomFontRenderer.drawString(g, customFont, tag, x + nameW, y, tagBaseColor);
                }
            } else {
                if (!tag.isEmpty()) {
                    // Draw name + tag separately for different colors
                    CustomFontRenderer.drawString(g, mc.font, name, x, y, textColor);
                    CustomFontRenderer.drawString(g, mc.font, tag, x + nameW, y, tagBaseColor);
                } else {
                    CustomFontRenderer.drawString(g, mc.font, fullText, x, y, textColor);
                }
            }
        }
    }
}
