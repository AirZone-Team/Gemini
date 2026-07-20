package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomBlurRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
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

import java.awt.Color;
import java.io.File;
import java.util.*;

/**
 * Arraylists — modern HUD module list.
 *
 * Two switchable styles ("Style" option):
 *  - "Minimal": one frosted-glass container for the whole list (single tinted
 *    blur pass instead of N per-card blurs), hairline separators, 2px accent
 *    bars anchored to the container edge, name/tag opacity hierarchy
 *  - "Cards": per-module glassmorphism cards
 *    · Smooth 5-layer ambient bloom behind every active card (quadratic falloff)
 *    · Soft multi-layer drop shadow instead of a single hard offset
 *    · True glassmorphism body: vertical gradient fill + top sheen + hairline rim
 *    · Gradient accent indicators (Left/Right/Top/Special) with a soft halo
 *    · Subtle vertical text gradient and a "breathing" accent icon
 *    · Spring-like entry animation (easeOutBack scale + slide-in from list edge)
 *    · Master animation-speed control
 *    · "Random" color mode; TextAlpha is actually applied
 */
public class Arraylists extends Module {

    // ==================== CONFIGURATION VALUES ====================

    // ---- Display ----
    public final BoolValue moduleBackground = new BoolValue("Module BG",    true);
    public final BoolValue compactMode      = new BoolValue("Compact",      false);
    public final BoolValue textShadow       = new BoolValue("Text Shadow",  true);
    public final ListValue sortMode         = new ListValue("Sort",
            "Length", new String[]{"Length", "Alphabetical", "Category"});

    // ---- Style ----
    public final ListValue styleMode        = new ListValue("Style",
            "Minimal", new String[]{"Minimal", "Cards"});
    public final BoolValue showSeparators   = new BoolValue("Separators",   true);
    public final BoolValue showIcons        = new BoolValue("Icons",        false);

    // ---- Color modes ----
    public final ListValue colorMode        = new ListValue("Text-Color",
            "Sync", new String[]{"Custom", "Sync", "Wave", "Gradient", "SkyRainbow", "Slowly", "Static", "Fade", "Random"});
    public final ListValue tagColorMode     = new ListValue("Tag-Color",
            "Custom", new String[]{"Custom", "Sync", "Wave", "Gradient", "SkyRainbow", "Slowly", "Static", "Fade", "Random"});
    public final ListValue rectColorMode    = new ListValue("Rect-Color",
            "Sync", new String[]{"Custom", "Sync", "Wave", "Gradient", "SkyRainbow", "Slowly", "Static", "Fade", "Random"});

    // ---- Static colors ----
    public final ColorValue fontColor       = new ColorValue("Font Color",   0xFFFAFAFA);
    public final ColorValue tagColor        = new ColorValue("Tag Color",    0xFF8A8A8A);
    public final ColorValue rectCustomColor = new ColorValue("Rect Color",   0xFF43E096);
    public final ColorValue backgroundColor = new ColorValue("BG Tint",      0xC0121212);

    // ---- Rainbow control ----
    public final FloatValue rainbowSpeed      = new FloatValue("Speed",       3500f, 500f, 10000f);
    public final FloatValue rainbowSaturation = new FloatValue("Saturation", 0.8f,  0.1f, 1.0f);
    public final FloatValue rainbowBrightness = new FloatValue("Brightness", 1.0f,  0.1f, 1.0f);
    public final FloatRangeValue rainbowHueRange = new FloatRangeValue("Hue Range", 0.0f, 1.0f, 0.0f, 1.0f);

    // ---- Rect indicator style ----
    public final ListValue rectMode          = new ListValue("Rect",
            "Left", new String[]{"None", "Left", "Right", "Outline", "Special", "Top"});

    // ---- Background ----
    public final IntValue backgroundAlpha    = new IntValue("BG-Alpha",   180, 0, 255);
    public final IntValue backgroundExpand   = new IntValue("BG-Expand",  2, 0, 10);

    // ---- Layout ----
    public final ListValue caseMode          = new ListValue("Case",
            "Normal", new String[]{"Upper", "Normal", "Lower"});
    public final FloatValue spaceValue        = new FloatValue("Space",      0f,   0f, 5f);
    public final FloatValue textYOffset       = new FloatValue("TextY",      1f,   0f, 20f);
    public final FloatValue fontAlphaValue    = new FloatValue("TextAlpha",  1.0f, 0.0f, 1.0f);
    public final ListValue ttfFont           = new ListValue("Font",
            "Default", new String[]{"Default"});

    // ---- Effects (new) ----
    public final FloatValue glowIntensity  = new FloatValue("Glow",          1.0f, 0f,   2f);
    public final IntValue   cornerRadius   = new IntValue("Radius",          6, 0, 12);
    public final BoolValue  bgGradient     = new BoolValue("BG Gradient",    true);
    public final BoolValue  textGradient   = new BoolValue("Text Gradient",  true);
    public final BoolValue  iconPulse      = new BoolValue("Icon Pulse",     true);
    public final FloatValue animSpeed      = new FloatValue("Anim Speed",    1.0f, 0.25f, 3f);

    // ---- Misc ----
    public final BoolValue noRenderModules   = new BoolValue("NoRenderModules",  false);

    // ==================== VISUAL THEME CONSTANTS ====================

    private static final int   PADDING_X            = 8;
    private static final int   PADDING_Y            = 6;
    private static final int   LINE_GAP             = 6;
    private static final float BLUR_STRENGTH        = 7f;

    // Compact overrides
    private static final int   C_PADDING_X          = 6;
    private static final int   C_PADDING_Y          = 3;
    private static final int   C_LINE_GAP           = 3;
    private static final float C_BLUR_STRENGTH      = 5f;

    // Glass treatment
    private static final float GLASS_TOP_MIX        = 0.06f;  // white mixed into the top of the card
    private static final float GLASS_BOTTOM_MIX     = 0.12f;  // black mixed into the bottom of the card
    private static final int   GLASS_SHEEN_ALPHA    = 0x14;   // top sheen alpha
    private static final int   GLASS_HAIRLINE_ALPHA = 0x10;   // 1px inner rim alpha

    // Glow / shadow shaping
    private static final int   GLOW_LAYERS          = 5;
    private static final float GLOW_SPREAD          = 6f;     // px of outward bloom
    private static final int   GLOW_BASE_ALPHA      = 0x34;
    private static final int   SHADOW_LAYERS        = 3;
    private static final int   SHADOW_BASE_ALPHA    = 0x12;

    // Text treatment
    private static final float TEXT_TOP_MIX         = 0.18f;  // white mixed into the top of glyphs
    private static final float ICON_PULSE_MIN       = 0.70f;

    // Animation tuning
    private static final float LERP_SPEED           = 0.14f;
    private static final float LERP_SPEED_FAST      = 0.22f;
    private static final float STAGGER_DELAY_MS     = 35f;
    private static final float ENTRY_DURATION_MS    = 350f;
    private static final float FADE_LERP            = 0.12f;
    private static final float SCALE_LERP           = 0.14f;
    private static final float ENTRY_SLIDE_PX       = 26f;

    // Minimalist container (4px grid)
    private static final int   M_PAD_X            = 9;
    private static final int   M_PAD_Y            = 6;
    private static final int   M_RADIUS           = 8;
    private static final float M_BLUR             = 9f;
    private static final int   M_TINT             = 0x3D0A0C10; // frosted dark-glass tint
    private static final int   M_SEP_ALPHA        = 0x0F;       // hairline separators
    private static final int   M_RIM_ALPHA        = 0x14;       // 1px container rim
    private static final float TAG_DIM            = 0.62f;      // tag opacity vs name

    // ==================== COLOR UTILITY ====================

    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0xFFFFFF) | (alpha << 24);
    }

    private static int scaleAlpha(int argb, float mul) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * mul)));
        return (argb & 0xFFFFFF) | (a << 24);
    }

    private static int mixRgb(int rgbA, int rgbB, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((rgbA >> 16) & 0xFF) * (1f - t) + ((rgbB >> 16) & 0xFF) * t);
        int g = (int) (((rgbA >> 8)  & 0xFF) * (1f - t) + ((rgbB >> 8)  & 0xFF) * t);
        int b = (int) (( rgbA        & 0xFF) * (1f - t) + ( rgbB        & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }

    private float mapHue(float hue) {
        float min = rainbowHueRange.getMinValue();
        float max = rainbowHueRange.getMaxValue();
        return min + (hue % 1f) * (max - min);
    }

    private float baseHue(long speedMs, int offset, int divisor) {
        return ((System.currentTimeMillis() % speedMs) / (float) speedMs
                + (float) offset / Math.max(1, divisor)) % 1f;
    }

    private float waveHue(long speedMs, int offset, int divisor) {
        float t = ((System.currentTimeMillis() % speedMs) / (float) speedMs
                + (float) offset / Math.max(1, divisor)) % 1f;
        return (float) (Math.sin(t * Math.PI * 2) * 0.5 + 0.5);
    }

    private float skyRainbowHue(long speedMs, int offset) {
        double v = Math.ceil(System.currentTimeMillis() / (double) speedMs + offset * 109L) / 5.0;
        v %= 360.0;
        double t = v / 360.0;
        return (float) (Math.sin(t * Math.PI * 2) * 0.5 + 0.5);
    }

    private static float slowlyHue(int offset) {
        return (System.nanoTime() / 1.0E9f + offset * -3000000f) / 2f % 1f;
    }

    private static float staticHue(long speedMs, int offset) {
        float t = ((System.currentTimeMillis() % speedMs) / (float) speedMs
                + (float) offset * 0.05f) % 1f;
        return (float) (Math.sin(t * Math.PI * 2) * 0.5 + 0.5);
    }

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

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u  = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Frame-rate independent scaling of a lerp factor by a speed multiplier. */
    private static float lerpScale(float base, float mul) {
        return 1f - (float) Math.pow(1f - base, mul);
    }

    // ==================== PER-MODULE ANIMATION STATE ====================

    private static class ModuleAnimation {
        float alpha       = 0f;
        float scale       = 0.85f;
        float blurAlpha   = 0f;
        float glowAlpha   = 0f;
        float currentY    = 0f;
        float targetY     = 0f;
        float currentX    = 0f;
        float targetX     = 0f;
        long  entryStart  = -1L;
        int   sortIndex   = -1;
        float randomHue   = -1f;
        boolean wasEnabled = false;
    }

    // ==================== INSTANCE STATE ====================

    private final Map<Module, ModuleAnimation> animMap = new LinkedHashMap<>();
    private CustomFontRenderer.GlyphFont customFont;
    private String lastSelectedFont = null;

    // Container-level animation state (Minimal style)
    private float containerW = 0f, containerH = 0f, containerAlpha = 0f;

    // ==================== CONSTRUCTOR ====================

    public Arraylists() {
        super("Arraylists", ModuleEnum.Visual);
        addValue(moduleBackground, compactMode, textShadow,
                sortMode, styleMode, showSeparators, showIcons,
                colorMode, tagColorMode, rectColorMode,
                fontColor, tagColor, rectCustomColor, backgroundColor,
                rainbowSpeed, rainbowSaturation, rainbowBrightness, rainbowHueRange,
                rectMode,
                backgroundAlpha, backgroundExpand, caseMode, spaceValue,
                textYOffset, fontAlphaValue, ttfFont,
                glowIntensity, cornerRadius, bgGradient, textGradient, iconPulse, animSpeed,
                noRenderModules);
    }

    // ==================== EVENT HANDLER ====================

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        initFontIfNeeded();

        if (mc.player == null)
            return;

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

    private boolean isMinimal() {
        return "Minimal".equals(styleMode.get());
    }

    /** Single source of truth for row text — used by sorting, measuring and drawing. */
    private String displayText(Module m) {
        String base = changeCase(m.getName()) + getModuleTag(m);
        return showIcons.enabled ? getModuleIcon(m.getName()) + " " + base : base;
    }

    /** Font-path agnostic single-color text draw. */
    private void drawText(GuiGraphicsExtractor g, String s, float x, float y, int color) {
        if (customFont != null) CustomFontRenderer.drawString(g, customFont, s, x, y, color);
        else                    CustomFontRenderer.drawString(g, mc.font, s, x, y, color);
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
        // Fallback or placeholder for tags until module system provides them fully.
        // Return example: " §7" + module.getSuffix() if available.
        return "";
    }

    private static String getModuleIcon(String name) {
        String n = name.toLowerCase();
        if (n.contains("killaura") || n.contains("aura")) return "⚡";
        if (n.contains("velocity") || n.contains("antiknockback")) return "✦";
        if (n.contains("fly") || n.contains("flight")) return "◆";
        if (n.contains("esp") || n.contains("tracer")) return "◈";
        if (n.contains("speed") || n.contains("bhop")) return "▶";
        if (n.contains("scaffold") || n.contains("tower")) return "▣";
        if (n.contains("reach") || n.contains("click")) return "◎";
        if (n.contains("tp") || n.contains("teleport")) return "↗";
        if (n.contains("steal") || n.contains("chest")) return "♢";
        if (n.contains("antibot") || n.contains("antiv")) return "☖";
        if (n.contains("blink") || n.contains("disabler")) return "⚠";
        if (n.contains("health") || n.contains("heal")) return "❤";
        if (n.contains("timer") || n.contains("tick")) return "⧖";
        if (n.contains("block") || n.contains("safewalk")) return "▧";
        if (n.contains("target") || n.contains("aim")) return "◉";
        if (n.contains("render") || n.contains("visual") || n.contains("arraylist")) return "◇";
        if (n.contains("hud") || n.contains("overlay")) return "▢";
        if (n.contains("world") || n.contains("time")) return "◷";
        if (n.contains("misc") || n.contains("fun")) return "☆";
        return "▪";
    }

    private boolean shouldSkipModule(Module m) {
        return noRenderModules.enabled && m.getModuleEnum() == ModuleEnum.Visual;
    }

    // ==================== LAYOUT & ANIMATION ENGINE ====================

    private List<Module> processAnimationsAndLayout(List<Module> modules) {
        long now = System.currentTimeMillis();
        boolean compact  = compactMode.enabled;
        int paddingY     = isMinimal() ? M_PAD_Y : (compact ? C_PADDING_Y : PADDING_Y);
        int lineGap      = compact ? C_LINE_GAP      : LINE_GAP;
        float extraSpace = spaceValue.getValue();
        int lineHeight   = textLineHeight(lineGap) + (int) extraSpace;
        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(this);

        // Master animation-speed control (frame-rate independent scaling)
        float speedMul  = Math.max(0.05f, animSpeed.getValue());
        float fadeLerp  = lerpScale(FADE_LERP, speedMul);
        float scaleLerp = lerpScale(SCALE_LERP, speedMul);
        float posLerp   = lerpScale(LERP_SPEED, speedMul);
        float fastLerp  = lerpScale(LERP_SPEED_FAST, speedMul);
        float staggerMs = STAGGER_DELAY_MS / speedMul;
        float entryMs   = ENTRY_DURATION_MS / speedMul;

        List<Module> sorted = new ArrayList<>();
        for (Module m : modules) {
            if (shouldSkipModule(m)) continue;
            animMap.computeIfAbsent(m, k -> new ModuleAnimation());
            ModuleAnimation a = animMap.get(m);
            if (m.enabled || a.alpha > 0.01f) {
                sorted.add(m);
            }
        }
        animMap.keySet().retainAll(modules);

        if (sorted.isEmpty()) return sorted;

        // ---- Sorting ----
        switch (sortMode.get()) {
            case "Alphabetical" ->
                    sorted.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
            case "Category" ->
                    sorted.sort(Comparator
                            .comparingInt((Module m) -> m.getModuleEnum().ordinal())
                            .thenComparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
            default ->
                    sorted.sort(Comparator.comparingDouble((Module m) -> -textWidth(displayText(m))));
        }

        // ---- Per-module animation: fade + spring scale + slide ----
        float yCursor = paddingY;
        for (int i = 0; i < sorted.size(); i++) {
            Module m = sorted.get(i);
            ModuleAnimation a = animMap.get(m);
            boolean toggled = a.wasEnabled != m.enabled;

            if (a.sortIndex != i) {
                a.sortIndex = i;
                if (m.enabled && toggled && a.entryStart < 0) {
                    a.entryStart = now + (long) (i * staggerMs);
                    a.currentX = rightAligned ? ENTRY_SLIDE_PX : -ENTRY_SLIDE_PX;
                }
            }

            if (m.enabled && a.randomHue < 0) a.randomHue = (float) Math.random();
            else if (!m.enabled) a.randomHue = -1f;

            float targetAlpha, targetScale, targetBlur, targetGlow;
            if (m.enabled) {
                float entryProgress = 1f;
                if (a.entryStart >= 0) {
                    long elapsed = now - a.entryStart;
                    entryProgress = elapsed <= 0 ? 0f : Math.min(1f, elapsed / entryMs);
                }
                targetAlpha = easeOutExpo(entryProgress);
                targetScale = 0.92f + 0.08f * easeOutBack(entryProgress); // gentle spring pop
                targetBlur  = easeOutCubic(entryProgress);
                targetGlow  = easeOutCubic(entryProgress);
                a.targetX   = 0f;
            } else {
                a.entryStart = -1L;
                targetAlpha = 0f;
                targetScale = 0.92f;
                targetBlur  = 0f;
                targetGlow  = 0f;
                a.targetX   = rightAligned ? 30f : -30f; // slide out to the edge
            }

            a.alpha     += (targetAlpha - a.alpha)     * fadeLerp;
            a.scale     += (targetScale - a.scale)     * scaleLerp;
            a.blurAlpha += (targetBlur  - a.blurAlpha) * fadeLerp;
            a.glowAlpha += (targetGlow  - a.glowAlpha) * fadeLerp;
            a.currentX  += (a.targetX   - a.currentX)  * fastLerp;

            if (Math.abs(targetAlpha - a.alpha) < 0.005f) a.alpha = targetAlpha;
            if (Math.abs(targetScale - a.scale) < 0.002f) a.scale = targetScale;
            if (Math.abs(a.targetX - a.currentX) < 0.5f)  a.currentX = a.targetX;

            a.targetY = yCursor;
            a.currentY += (a.targetY - a.currentY) * posLerp;
            if (Math.abs(a.targetY - a.currentY) < 0.05f) {
                a.currentY = a.targetY;
                a.wasEnabled = m.enabled;
            }

            yCursor += lineHeight;
        }

        return sorted;
    }

    // ==================== COLOR RESOLUTION ====================

    private int resolveColor(String mode, int baseColor, ModuleAnimation anim,
                             int idx, int total, float sat, float bri, int alpha) {
        long speed = (long) rainbowSpeed.getValue();

        int rgb = switch (mode) {
            case "Sync"       -> Color.HSBtoRGB(mapHue(baseHue(speed, 0, 1)), sat, bri);
            case "Wave"       -> Color.HSBtoRGB(mapHue(waveHue(speed, idx, total)), sat, bri);
            case "Gradient"   -> Color.HSBtoRGB(mapHue(baseHue(speed, idx, total)), sat, bri);
            case "SkyRainbow" -> Color.HSBtoRGB(skyRainbowHue(speed, idx), sat, bri);
            case "Slowly"     -> Color.HSBtoRGB(slowlyHue(idx), sat, bri);
            case "Static"     -> Color.HSBtoRGB(staticHue(speed, idx), sat, bri);
            case "Fade"       -> fadeColor(baseColor, idx, total);
            case "Random"     -> {
                float h = (anim != null && anim.randomHue >= 0f)
                        ? anim.randomHue
                        : (idx * 0.6180339887f) % 1f; // golden-ratio fallback spread
                yield Color.HSBtoRGB(mapHue(h), sat, bri);
            }
            default           -> baseColor;
        };
        return withAlpha(rgb, alpha);
    }

    // ==================== RENDER PIPELINE ====================

    private int currentRadius(boolean compact) {
        return Math.max(0, cornerRadius.getValue() - (compact ? 2 : 0));
    }

    private void drawUserInterface(GuiGraphicsExtractor g, List<Module> modules) {
        boolean minimal  = isMinimal();
        boolean compact  = compactMode.enabled;
        int paddingX     = minimal ? M_PAD_X : (compact ? C_PADDING_X : PADDING_X);
        int radius       = currentRadius(compact);
        float blurStr    = compact ? C_BLUR_STRENGTH : BLUR_STRENGTH;
        int lineGap      = compact ? C_LINE_GAP      : LINE_GAP;
        int lineHeight   = textLineHeight(lineGap);
        float extraSpace = spaceValue.getValue();
        int fullLineH    = lineHeight + (int) extraSpace;

        int originX = hudX;
        int originY = hudY;

        int maxItemW = 0;
        for (Module m : modules) {
            int w = (int) textWidth(displayText(m)) + paddingX * 2 + backgroundExpand.getValue();
            if (w > maxItemW) maxItemW = w;
        }
        int totalH = paddingY() * 2 + modules.size() * fullLineH;
        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(this);

        if (minimal) {
            drawMinimalContainer(g, modules, originX, originY, maxItemW, totalH, fullLineH, rightAligned);
        } else {
            for (int i = 0; i < modules.size(); i++) {
                renderModuleCard(g, modules.get(i), originX, originY, maxItemW, i, modules.size(),
                        compact, paddingX, radius, blurStr, fullLineH, rightAligned);
            }
        }

        Gemini.hudDragManager.registerDragRegion(this, originX, originY, maxItemW, totalH);
    }

    @Override
    public void renderEditorPlaceholder(GuiGraphicsExtractor g) {
        boolean compact = compactMode.enabled;
        int paddingX    = compact ? C_PADDING_X     : PADDING_X;
        int lineGap     = compact ? C_LINE_GAP      : LINE_GAP;
        int lineHeight  = textLineHeight(lineGap);
        int radius      = currentRadius(compact);
        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(this);
        List<Module> allMods = Gemini.moduleManager.getModules();

        int count = 0;
        int maxItemW = 0;
        for (Module m : allMods) {
            if (shouldSkipModule(m)) continue;
            count++;
            String fullText = displayText(m);
            int tw = (int) textWidth(fullText) + paddingX * 2 + backgroundExpand.getValue();
            if (tw > maxItemW) maxItemW = tw;
        }

        int totalH = paddingY() * 2 + count * lineHeight;
        int originX = hudX;
        int originY = hudY;

        if (!enabled) {
            int i = 0;
            for (Module m : allMods) {
                if (shouldSkipModule(m)) continue;
                int cardY = originY + paddingY() + i * lineHeight;
                String fullText = displayText(m);
                int itemW = (int) textWidth(fullText) + paddingX * 2 + backgroundExpand.getValue();
                int cardX = rightAligned ? originX + maxItemW - itemW : originX;

                // Faint body so the placeholder reads as a real card
                CustomRoundedRectRenderer.drawRoundedRect(g,
                        cardX, cardY, itemW, lineHeight, radius, 0x1A000000);
                CustomRoundedRectRenderer.drawRoundedOutline(g,
                        cardX, cardY, itemW, lineHeight, radius, 0xAAFFD700, 1);

                float textX = cardX + paddingX;
                float textY = cardY + lineGap / 2f;
                if (customFont != null) {
                    CustomFontRenderer.drawString(g, customFont, fullText, textX, textY, 0x88FFD700);
                } else {
                    CustomFontRenderer.drawString(g, mc.font, fullText, textX, textY, 0x88FFD700);
                }
                i++;
            }
        }
        Gemini.hudDragManager.registerDragRegion(this, originX, originY, maxItemW, totalH);
    }

    private int paddingY() {
        return isMinimal() ? M_PAD_Y : (compactMode.enabled ? C_PADDING_Y : PADDING_Y);
    }

    // ---- Single module card ----
    private void renderModuleCard(GuiGraphicsExtractor g, Module m,
                                  int baseX, int baseY, int maxW, int idx, int total,
                                  boolean compact, int paddingX, int radius,
                                  float blurStr, int fullLineH, boolean rightAligned) {
        ModuleAnimation a = animMap.get(m);
        if (a == null || a.alpha < 0.01f) return;

        int lineHeight = fullLineH;
        int modAlpha   = (int) (a.alpha * 255);

        String icon = showIcons.enabled ? getModuleIcon(m.getName()) : "";
        String name = changeCase(m.getName());
        String tag  = getModuleTag(m);
        String fullText = icon.isEmpty() ? name + tag : icon + " " + name + tag;

        int extraW = backgroundExpand.getValue();
        int itemW = (int) textWidth(fullText) + paddingX * 2 + extraW;

        // Slide-in animation on the X coordinate
        float modX = rightAligned ? (baseX + maxW - itemW) + a.currentX : baseX + a.currentX;
        float modY = baseY + a.currentY;

        // Scale transform: shrink/grow around center
        float centerX = modX + itemW / 2f;
        float centerY = modY + lineHeight / 2f;
        float drawX = centerX - (itemW / 2f) * a.scale;
        float drawY = centerY - (lineHeight / 2f) * a.scale;
        float drawW = itemW * a.scale;
        float drawH = lineHeight * a.scale;
        int scaledRadius = (int) (radius * a.scale);

        float sat = rainbowSaturation.getValue();
        float bri = rainbowBrightness.getValue();
        int accent = resolveColor(rectColorMode.get(), rectCustomColor.getColor(), a, idx, total, sat, bri, modAlpha);

        // ---- 1. Ambient bloom (rendered BEHIND everything) ----
        if (m.enabled && a.glowAlpha > 0.01f && glowIntensity.getValue() > 0.01f) {
            drawAmbientGlow(g, drawX, drawY, drawW, drawH, scaledRadius,
                    accent & 0xFFFFFF, a.glowAlpha * glowIntensity.getValue());
        }

        // ---- 2. Soft drop shadow ----
        drawSoftShadow(g, drawX, drawY, drawW, drawH, scaledRadius, a.alpha);

        // ---- 3. Blur background ----
        if (moduleBackground.enabled && a.blurAlpha > 0.01f) {
            CustomBlurRenderer.render(drawX, drawY, drawW, drawH, scaledRadius, blurStr * a.blurAlpha);
        }

        // ---- 4-6. Glassmorphism body ----
        if (moduleBackground.enabled) {
            int bgColor = backgroundColor.getColor();
            int baseBgAlpha = (bgColor >>> 24) & 0xFF;
            int customBgAlpha = backgroundAlpha.getValue();
            int bodyAlpha = (int) ((customBgAlpha > 0 ? customBgAlpha : baseBgAlpha) * a.alpha);

            // 4. Gradient or flat body fill
            if (bodyAlpha > 2) {
                int bgRgb = bgColor & 0xFFFFFF;
                if (bgGradient.enabled) {
                    int topRgb = mixRgb(bgRgb, 0xFFFFFF, GLASS_TOP_MIX);
                    int botRgb = mixRgb(bgRgb, 0x000000, GLASS_BOTTOM_MIX);
                    CustomRoundedRectRenderer.drawRoundedRectVertGrad(g,
                            (int) drawX, (int) drawY, (int) drawW, (int) drawH, scaledRadius,
                            withAlpha(topRgb, bodyAlpha), withAlpha(botRgb, bodyAlpha));
                } else {
                    CustomRoundedRectRenderer.drawRoundedRect(g,
                            (int) drawX, (int) drawY, (int) drawW, (int) drawH, scaledRadius,
                            withAlpha(bgRgb, bodyAlpha));
                }
            }

            // 5. Top sheen (glass edge light)
            int sheenAlpha = (int) (GLASS_SHEEN_ALPHA * a.alpha);
            if (sheenAlpha > 2) {
                int sheenH = Math.max(1, (int) (drawH * 0.4f));
                CustomRoundedRectRenderer.drawRoundedRectVertGrad(g,
                        (int) drawX, (int) drawY, (int) drawW, sheenH, scaledRadius,
                        withAlpha(0xFFFFFF, sheenAlpha), withAlpha(0xFFFFFF, 0));
            }

            // 6. Hairline inner rim
            int hairAlpha = (int) (GLASS_HAIRLINE_ALPHA * a.alpha);
            if (hairAlpha > 2) {
                CustomRoundedRectRenderer.drawRoundedOutline(g,
                        (int) drawX, (int) drawY, (int) drawW, (int) drawH, scaledRadius,
                        withAlpha(0xFFFFFF, hairAlpha), 1);
            }
        }

        // ---- 7. Accent indicator ----
        if (m.enabled) {
            drawRectIndicator(g, rectMode.get(), accent,
                    drawX, drawY, drawW, drawH, scaledRadius, compact, a.alpha);
        }

        // ---- 8. Text ----
        float textX = drawX + paddingX + (extraW / 2f);
        float textY = drawY + (drawH - lineHeight) / 2f + textYOffset.getValue();
        renderModuleText(g, name, tag, icon, textX, textY, m, a, idx, total, modAlpha, sat, bri);
    }

    // ---- Smooth multi-layer ambient glow (quadratic falloff) ----
    private void drawAmbientGlow(GuiGraphicsExtractor g, float x, float y, float w, float h,
                                 int radius, int rgb, float strength) {
        if (strength <= 0.01f) return;
        for (int i = 1; i <= GLOW_LAYERS; i++) {
            float t = i / (float) GLOW_LAYERS;
            int expand = (int) (t * GLOW_SPREAD);
            float falloff = (1f - t) * (1f - t);
            int alpha = (int) (GLOW_BASE_ALPHA * strength * falloff);
            if (alpha < 3) continue;
            CustomRoundedRectRenderer.drawRoundedRect(g,
                    (int) x - expand, (int) y - expand,
                    (int) w + expand * 2, (int) h + expand * 2,
                    radius + expand, withAlpha(rgb, alpha));
        }
    }

    // ---- Layered soft drop shadow ----
    private void drawSoftShadow(GuiGraphicsExtractor g, float x, float y, float w, float h,
                                int radius, float alphaMul) {
        if (alphaMul <= 0.01f) return;
        for (int i = 1; i <= SHADOW_LAYERS; i++) {
            int alpha = (int) (SHADOW_BASE_ALPHA * alphaMul / i);
            if (alpha < 3) continue;
            int expand = i - 1;
            CustomRoundedRectRenderer.drawRoundedRect(g,
                    (int) x - expand, (int) y + i,
                    (int) w + expand * 2, (int) h + expand,
                    radius + expand, withAlpha(0x000000, alpha));
        }
    }

    // ---- Accent indicator with gradient + halo ----
    private void drawRectIndicator(GuiGraphicsExtractor g, String rMode, int accent,
                                   float x, float y, float w, float h, int radius,
                                   boolean compact, float alphaMul) {
        if ("None".equals(rMode) || alphaMul < 0.1f) return;

        int a   = (accent >>> 24) & 0xFF;
        int rgb = accent & 0xFFFFFF;
        int barT = compact ? 2 : 3;
        int fadeEnd = (int) (a * 0.55f);
        int haloA   = (int) (a * 0.30f);

        switch (rMode) {
            case "Left" -> {
                if (haloA > 2) {
                    CustomRoundedRectRenderer.drawRoundedRect(g,
                            (int) x - 1, (int) y + 1, barT + 2, (int) h - 2, 2,
                            withAlpha(rgb, haloA));
                }
                CustomRoundedRectRenderer.drawRoundedRectVertGrad(g,
                        (int) x, (int) y, barT, (int) h, 1,
                        accent, withAlpha(rgb, fadeEnd));
            }
            case "Right" -> {
                int rx = (int) (x + w - barT);
                if (haloA > 2) {
                    CustomRoundedRectRenderer.drawRoundedRect(g,
                            rx - 1, (int) y + 1, barT + 2, (int) h - 2, 2,
                            withAlpha(rgb, haloA));
                }
                CustomRoundedRectRenderer.drawRoundedRectVertGrad(g,
                        rx, (int) y, barT, (int) h, 1,
                        accent, withAlpha(rgb, fadeEnd));
            }
            case "Top" -> {
                CustomRoundedRectRenderer.drawRoundedRectHorizGrad(g,
                        (int) x, (int) y, (int) w, barT, 1,
                        accent, withAlpha(rgb, (int) (a * 0.45f)));
            }
            case "Outline" -> {
                int outlineHalo = (int) (a * 0.20f);
                if (outlineHalo > 2) {
                    CustomRoundedRectRenderer.drawRoundedOutline(g,
                            (int) x - 1, (int) y - 1, (int) w + 2, (int) h + 2,
                            radius + 1, withAlpha(rgb, outlineHalo), 1);
                }
                CustomRoundedRectRenderer.drawRoundedOutline(g,
                        (int) x, (int) y, (int) w, (int) h, radius,
                        accent, compact ? 1 : 2);
            }
            case "Special" -> {
                // Centered capsule with a soft halo
                int pillH = (int) (h * 0.62f);
                int pillW = compact ? 2 : 3;
                int px = (int) x + 3;
                int py = (int) y + ((int) h - pillH) / 2;
                if (haloA > 2) {
                    CustomRoundedRectRenderer.drawRoundedRect(g,
                            px - 1, py - 2, pillW + 2, pillH + 4, 2,
                            withAlpha(rgb, haloA));
                }
                CustomRoundedRectRenderer.drawRoundedRectVertGrad(g,
                        px, py, pillW, pillH, 1,
                        accent, withAlpha(rgb, fadeEnd));
            }
        }
    }

    // ---- Module icon + name + tag tri-tone rendering ----
    private void renderModuleText(GuiGraphicsExtractor g, String name, String tag,
                                  String icon, float x, float y,
                                  Module m, ModuleAnimation a,
                                  int idx, int total, int alpha, float sat, float bri) {
        boolean shadow = textShadow.enabled && m.enabled;

        // TextAlpha is now honored (previously defined but unused)
        float fontAlpha = fontAlphaValue.getValue();
        int textAlpha = (int) (alpha * fontAlpha);
        if (textAlpha < 3) return;

        // Tri-tone palette: accent icon, font-colored name, dimmed tag
        int nameColor = resolveColor(colorMode.get(), fontColor.getColor(), a, idx, total, sat, bri, textAlpha);
        int iconColor = resolveColor(rectColorMode.get(), rectCustomColor.getColor(), a, idx, total, sat, bri, textAlpha);
        int tagColorR = resolveColor(tagColorMode.get(), tagColor.getColor(), a, idx, total, sat, bri, textAlpha);

        if (!m.enabled) {
            nameColor = withAlpha(0xA0A0A0, textAlpha);
            iconColor = withAlpha(0x909090, textAlpha);
            tagColorR = withAlpha(0x707070, textAlpha);
        } else if (iconPulse.enabled) {
            // Gentle "breathing" on the accent icon
            float pulse = ICON_PULSE_MIN + (1f - ICON_PULSE_MIN)
                    * (float) (Math.sin(System.currentTimeMillis() / 340.0 + idx * 0.9) * 0.5 + 0.5);
            iconColor = scaleAlpha(iconColor, pulse);
        }

        // Subtle vertical gradient: brighter at the cap, true color at the baseline
        boolean gradient = textGradient.enabled;
        int nameTop = gradient
                ? withAlpha(mixRgb(nameColor & 0xFFFFFF, 0xFFFFFF, TEXT_TOP_MIX), (nameColor >>> 24) & 0xFF)
                : nameColor;

        float iconW  = icon.isEmpty() ? 0f : textWidth(icon);
        float spaceW = icon.isEmpty() ? 0f : textWidth(" ");

        // ---- Shadow pass (single combined draw keeps offsets identical) ----
        if (shadow) {
            int shadowCol = withAlpha(0x000000, (int) (0.40f * textAlpha));
            String combined = icon.isEmpty() ? name + tag : icon + " " + name + tag;
            if (customFont != null) {
                CustomFontRenderer.drawString(g, customFont, combined, x + 1f, y + 1f, shadowCol);
            } else {
                CustomFontRenderer.drawString(g, mc.font, combined, x + 1f, y + 1f, shadowCol);
            }
        }

        // ---- Foreground pass ----
        float cx = x;
        if (customFont != null) {
            CustomFontRenderer.drawString(g, customFont, icon, cx, y, iconColor);
            cx += iconW + spaceW;
            if (gradient) CustomFontRenderer.drawGradientString(g, customFont, name, cx, y, nameTop, nameColor);
            else          CustomFontRenderer.drawString(g, customFont, name, cx, y, nameColor);
            cx += textWidth(name);
            if (!tag.isEmpty()) CustomFontRenderer.drawString(g, customFont, tag, cx, y, tagColorR);
        } else {
            CustomFontRenderer.drawString(g, mc.font, icon, cx, y, iconColor);
            cx += iconW + spaceW;
            if (gradient) CustomFontRenderer.drawGradientString(g, mc.font, name, cx, y, nameTop, nameColor);
            else          CustomFontRenderer.drawString(g, mc.font, name, cx, y, nameColor);
            cx += textWidth(name);
            if (!tag.isEmpty()) CustomFontRenderer.drawString(g, mc.font, tag, cx, y, tagColorR);
        }
    }

    // ==================== MINIMAL STYLE ====================

    /**
     * Single frosted-glass container for the whole list.
     * One tinted blur pass + one soft shadow replace the per-card pipeline.
     */
    private void drawMinimalContainer(GuiGraphicsExtractor g, List<Module> modules,
                                      int originX, int originY, int maxItemW, int totalH,
                                      int fullLineH, boolean rightAligned) {
        // Container spring: size + master visibility (frame-rate independent)
        float speedMul = Math.max(0.05f, animSpeed.getValue());
        float sizeLerp = lerpScale(LERP_SPEED, speedMul);
        float fadeLerp = lerpScale(FADE_LERP, speedMul);
        containerW     += (maxItemW - containerW)     * sizeLerp;
        containerH     += (totalH   - containerH)     * sizeLerp;
        containerAlpha += (1f       - containerAlpha) * fadeLerp;
        if (containerAlpha < 0.01f) return;

        float cx = originX, cy = originY, cw = containerW, ch = containerH;
        int radius = (int) Math.min(M_RADIUS, Math.min(cw, ch) / 2f);

        if (cw >= 2f && ch >= 2f) {
            // 1. Soft shadow — one pass for the whole list
            drawSoftShadow(g, cx, cy, cw, ch, radius, containerAlpha);

            if (moduleBackground.enabled) {
                // 2. Frosted glass — ONE tinted blur pass (replaces N per-card blurs)
                CustomBlurRenderer.render(cx, cy, cw, ch, radius,
                        scaleAlpha(M_TINT, containerAlpha), M_BLUR * containerAlpha);

                // 3. Low-alpha vertical glass gradient over the blur
                int bodyA = (int) (backgroundAlpha.getValue() * 0.38f * containerAlpha);
                int bgRgb = backgroundColor.getColor() & 0xFFFFFF;
                if (bodyA > 2) {
                    CustomRoundedRectRenderer.drawRoundedRectVertGrad(g,
                            (int) cx, (int) cy, (int) cw, (int) ch, radius,
                            withAlpha(mixRgb(bgRgb, 0xFFFFFF, 0.05f), bodyA),
                            withAlpha(mixRgb(bgRgb, 0x000000, 0.10f), bodyA));
                }

                // 4. 1px hairline rim
                int rimA = (int) (M_RIM_ALPHA * containerAlpha);
                if (rimA > 2) {
                    CustomRoundedRectRenderer.drawRoundedOutline(g,
                            (int) cx, (int) cy, (int) cw, (int) ch, radius,
                            withAlpha(0xFFFFFF, rimA), 1);
                }
            }
        }

        // 5. Rows + hairline separators (each separator tracks the row above's animated Y)
        float sat = rainbowSaturation.getValue();
        float bri = rainbowBrightness.getValue();
        for (int i = 0; i < modules.size(); i++) {
            renderMinimalRow(g, modules.get(i), cx, cy, cw, i, modules.size(),
                    fullLineH, rightAligned, sat, bri);

            if (showSeparators.enabled && i < modules.size() - 1) {
                ModuleAnimation a = animMap.get(modules.get(i));
                float rowBottom = originY + (a != null ? a.currentY : 0f) + fullLineH;
                int sepA = (int) (M_SEP_ALPHA * containerAlpha * (a != null ? a.alpha : 1f));
                if (sepA > 2) {
                    CustomRoundedRectRenderer.drawRoundedRect(g,
                            (int) (cx + M_PAD_X), (int) rowBottom,
                            (int) (cw - M_PAD_X * 2), 1, 0,
                            withAlpha(0xFFFFFF, sepA));
                }
            }
        }
    }

    /**
     * Minimal row: optional icon → name (primary) → tag (dimmed),
     * plus a 2px accent bar anchored to the container's outer edge.
     */
    private void renderMinimalRow(GuiGraphicsExtractor g, Module m,
                                  float cx, float cy, float cw,
                                  int idx, int total, int fullLineH,
                                  boolean rightAligned, float sat, float bri) {
        ModuleAnimation a = animMap.get(m);
        if (a == null || a.alpha < 0.01f) return;

        String icon = showIcons.enabled ? getModuleIcon(m.getName()) : "";
        String name = changeCase(m.getName());
        String tag  = getModuleTag(m);

        int textA = (int) (a.alpha * fontAlphaValue.getValue() * 255);
        if (textA < 3) return;

        float rowY  = cy + a.currentY;
        float rowCX = cx + a.currentX; // only the text slides; the accent bar stays at the edge

        // ---- Hierarchy: name (primary) / tag (dimmed) / accent (2px bar) ----
        int nameColor = resolveColor(colorMode.get(), fontColor.getColor(), a, idx, total, sat, bri, textA);
        int tagColorR = scaleAlpha(
                resolveColor(tagColorMode.get(), tagColor.getColor(), a, idx, total, sat, bri, textA), TAG_DIM);
        int accent    = resolveColor(rectColorMode.get(), rectCustomColor.getColor(), a, idx, total, sat, bri, textA);
        if (!m.enabled) {
            nameColor = withAlpha(0xA0A0A0, textA);
            tagColorR = withAlpha(0x707070, textA);
            accent    = withAlpha(0x909090, textA);
        }

        // Accent: 2px vertical-gradient bar hugging the container's outer edge
        if (m.enabled && !"None".equals(rectMode.get()) && cw >= 4f) {
            float barH = Math.max(4f, fullLineH * 0.52f);
            float barX = rightAligned ? cx + cw - 3 : cx + 1;
            CustomRoundedRectRenderer.drawRoundedRectVertGrad(g,
                    (int) barX, (int) (rowY + (fullLineH - barH) / 2f), 2, (int) barH, 1,
                    accent, scaleAlpha(accent, 0.55f));
        }

        // Text position: anchored to the text-side edge
        float iconPart = icon.isEmpty() ? 0f : textWidth(icon) + textWidth(" ");
        float fullW    = iconPart + textWidth(name + tag);
        float textX    = rightAligned ? rowCX + cw - M_PAD_X - fullW : rowCX + M_PAD_X;
        float fontH    = customFont != null ? customFont.lineHeight : mc.font.lineHeight;
        float textY    = rowY + (fullLineH - fontH) / 2f + textYOffset.getValue() - 1f;

        // Shadow (MSDF has no native shadow — offset double-draw)
        if (textShadow.enabled && m.enabled) {
            String combined = icon.isEmpty() ? name + tag : icon + " " + name + tag;
            drawText(g, combined, textX + 0.75f, textY + 0.75f,
                    withAlpha(0x000000, (int) (0.35f * textA)));
        }

        // Foreground: icon (optional) → name (gradient-able) → tag (dimmed)
        float x = textX;
        if (!icon.isEmpty()) {
            drawText(g, icon, x, textY, scaleAlpha(accent, 0.9f));
            x += iconPart;
        }
        boolean grad = textGradient.enabled;
        int nameTop = grad
                ? withAlpha(mixRgb(nameColor & 0xFFFFFF, 0xFFFFFF, TEXT_TOP_MIX), (nameColor >>> 24) & 0xFF)
                : nameColor;
        if (customFont != null) {
            if (grad) CustomFontRenderer.drawGradientString(g, customFont, name, x, textY, nameTop, nameColor);
            else      CustomFontRenderer.drawString(g, customFont, name, x, textY, nameColor);
        } else {
            if (grad) CustomFontRenderer.drawGradientString(g, mc.font, name, x, textY, nameTop, nameColor);
            else      CustomFontRenderer.drawString(g, mc.font, name, x, textY, nameColor);
        }
        x += textWidth(name);
        if (!tag.isEmpty()) drawText(g, tag, x + 1f, textY, tagColorR);
    }
}