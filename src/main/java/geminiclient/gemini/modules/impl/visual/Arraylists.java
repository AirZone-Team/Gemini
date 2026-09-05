package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.I18n;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.GlowRenderer;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
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
import java.util.*;

/**
 * Arraylists — MD3 HUD module list.
 *
 * Visual language matches the Mellow notification cards
 * ({@code MellowNotificationRenderer}):
 *  - near-white rounded cards ("Cards") or one MD3 surface container ("Minimal")
 *  - GLSL soft drop shadow via the glow_rect shader
 *  - dark on-surface text with an MD3 accent pill indicator
 *  - spring entry animation (easeOutBack scale + slide-in from the list edge)
 *
 * All text renders through the bundled MiSans face shared with the MD3
 * ClickGui ({@link Md3Fonts}) — there is no user-selectable font.
 */
public class Arraylists extends Module {

    // ==================== CONFIGURATION VALUES ====================

    // ---- Display ----
    public final BoolValue moduleBackground = new BoolValue("Module BG",    true);
    public final BoolValue compactMode      = new BoolValue("Compact",      false);
    public final BoolValue textShadow       = new BoolValue("Text Shadow",  false);
    public final ListValue sortMode         = new ListValue("Sort",
            "Length", new String[]{"Length", "Alphabetical", "Category"});

    // ---- Style ----
    public final ListValue styleMode        = new ListValue("Style",
            "Cards", new String[]{"Cards", "Minimal"});
    public final BoolValue showSeparators   = new BoolValue("Separators",   true);
    public final BoolValue showIcons        = new BoolValue("Icons",        false);

    // ---- Color modes ----
    public final ListValue colorMode        = new ListValue("Text-Color",
            "Custom", new String[]{"Custom", "Sync", "Wave", "Gradient", "SkyRainbow", "Slowly", "Static", "Fade", "Random"});
    public final ListValue tagColorMode     = new ListValue("Tag-Color",
            "Custom", new String[]{"Custom", "Sync", "Wave", "Gradient", "SkyRainbow", "Slowly", "Static", "Fade", "Random"});
    public final ListValue rectColorMode    = new ListValue("Rect-Color",
            "Custom", new String[]{"Custom", "Sync", "Wave", "Gradient", "SkyRainbow", "Slowly", "Static", "Fade", "Random"});

    // ---- Static colors (MD3 baseline light / Mellow card palette) ----
    public final ColorValue fontColor       = new ColorValue("Font Color",   0xFF26242E);
    public final ColorValue tagColor        = new ColorValue("Tag Color",    0xFF49454F);
    public final ColorValue rectCustomColor = new ColorValue("Rect Color",   0xFF6750A4);
    public final ColorValue backgroundColor = new ColorValue("BG Tint",      0xFFF5F4F8);

    // ---- Rainbow control ----
    public final FloatValue rainbowSpeed      = new FloatValue("Speed",       3500f, 500f, 10000f);
    public final FloatValue rainbowSaturation = new FloatValue("Saturation", 0.8f,  0.1f, 1.0f);
    public final FloatValue rainbowBrightness = new FloatValue("Brightness", 1.0f,  0.1f, 1.0f);
    public final FloatRangeValue rainbowHueRange = new FloatRangeValue("Hue Range", 0.0f, 1.0f, 0.0f, 1.0f);

    // ---- Rect indicator style ----
    public final ListValue rectMode          = new ListValue("Rect",
            "Left", new String[]{"None", "Left", "Right", "Outline", "Special", "Top"});

    // ---- Background ----
    public final IntValue backgroundAlpha    = new IntValue("BG-Alpha",   255, 0, 255);
    public final IntValue backgroundExpand   = new IntValue("BG-Expand",  2, 0, 10);

    // ---- Layout ----
    public final ListValue caseMode          = new ListValue("Case",
            "Normal", new String[]{"Upper", "Normal", "Lower"});
    public final FloatValue spaceValue        = new FloatValue("Space",      0f,   0f, 5f);
    public final FloatValue textYOffset       = new FloatValue("TextY",      1f,   0f, 20f);
    public final FloatValue fontAlphaValue    = new FloatValue("TextAlpha",  1.0f, 0.0f, 1.0f);

    // ---- Shape & motion ----
    public final IntValue   cornerRadius   = new IntValue("Radius",       8, 0, 12);
    public final FloatValue animSpeed      = new FloatValue("Anim Speed", 1.0f, 0.25f, 3f);

    // ---- Misc ----
    public final BoolValue noRenderModules   = new BoolValue("NoRenderModules",  false);

    // ==================== MD3 VISUAL CONSTANTS ====================

    private static final int ON_SURFACE_RGB        = 0x26242E; // Notification titleColor
    private static final int OUTLINE_VARIANT_RGB   = 0xCAC4D0; // Md3Theme divider

    // GLSL soft drop shadow — same shaping as the Mellow cards.
    private static final int   SHADOW_SPREAD    = 6;
    private static final int   SHADOW_OFFSET_Y  = 2;
    private static final float SHADOW_MAX_ALPHA = 0.16f;

    // Layout
    private static final int   PADDING_X     = 10;
    private static final int   PADDING_Y     = 6;
    private static final int   LINE_GAP      = 6;
    private static final int   ACCENT_BAR_W  = 3;

    // Compact overrides
    private static final int   C_PADDING_X   = 7;
    private static final int   C_PADDING_Y   = 3;
    private static final int   C_LINE_GAP    = 3;

    // Minimal container
    private static final int   M_PAD_X       = 12;
    private static final int   M_PAD_Y       = 6;
    private static final int   M_SEP_ALPHA   = 150;     // OUTLINE_VARIANT divider alpha
    private static final float TAG_DIM       = 0.62f;   // tag opacity vs name

    // Animation tuning
    private static final float LERP_SPEED           = 0.14f;
    private static final float LERP_SPEED_FAST      = 0.22f;
    private static final float STAGGER_DELAY_MS     = 35f;
    private static final float ENTRY_DURATION_MS    = 400f;    // Mellow intro duration
    private static final float FADE_LERP            = 0.12f;
    private static final float SCALE_LERP           = 0.14f;
    private static final float ENTRY_SLIDE_PX       = 26f;

    // ==================== COLOR UTILITY ====================

    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0xFFFFFF) | (alpha << 24);
    }

    private static int scaleAlpha(int argb, float mul) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * mul)));
        return (argb & 0xFFFFFF) | (a << 24);
    }

    /** MD3 disabled content: on-surface color at 38% opacity. */
    private static int md3Disabled(int textAlpha) {
        return withAlpha(ON_SURFACE_RGB, textAlpha * 38 / 100);
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
                textYOffset, fontAlphaValue,
                cornerRadius, animSpeed,
                noRenderModules);
    }

    // ==================== EVENT HANDLER ====================

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null)
            return;

        List<Module> modules = Gemini.moduleManager.getModules();
        if (modules == null || modules.isEmpty()) return;

        List<Module> sorted = processAnimationsAndLayout(modules);
        if (sorted.isEmpty()) return;

        drawUserInterface(event.guiGraphics(), sorted);
    }

    // ==================== FONT ====================

    /**
     * Bundled MiSans face shared with the MD3 ClickGui — all HUD text renders
     * through it. {@code Md3Fonts} falls back to the vanilla font while the
     * face is still loading, so measuring and drawing never diverge.
     */
    private static CustomFontRenderer.GlyphFont hudFont() {
        return Md3Fonts.label();
    }

    private float textWidth(String text) {
        return Md3Fonts.width(hudFont(), text);
    }

    private int textLineHeight(int lineGap) {
        return (int) Md3Fonts.lineHeight(hudFont()) + lineGap;
    }

    private void drawText(GuiGraphicsExtractor g, String s, float x, float y, int color) {
        Md3Fonts.drawText(g, hudFont(), s, x, y, color);
    }

    private boolean isMinimal() {
        return "Minimal".equals(styleMode.get());
    }

    /** Single source of truth for row text — used by sorting, measuring and drawing. */
    private String displayText(Module m) {
        String base = changeCase(I18n.module(m.getName())) + getModuleTag(m);
        return showIcons.enabled ? getModuleIcon(m.getName()) + " " + base : base;
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

            float targetAlpha, targetScale;
            if (m.enabled) {
                float entryProgress = 1f;
                if (a.entryStart >= 0) {
                    long elapsed = now - a.entryStart;
                    entryProgress = elapsed <= 0 ? 0f : Math.min(1f, elapsed / entryMs);
                }
                targetAlpha = easeOutExpo(entryProgress);
                targetScale = 0.92f + 0.08f * easeOutBack(entryProgress); // gentle spring pop
                a.targetX   = 0f;
            } else {
                a.entryStart = -1L;
                targetAlpha = 0f;
                targetScale = 0.92f;
                a.targetX   = rightAligned ? 30f : -30f; // slide out to the edge
            }

            a.alpha    += (targetAlpha - a.alpha) * fadeLerp;
            a.scale    += (targetScale - a.scale) * scaleLerp;
            a.currentX += (a.targetX  - a.currentX) * fastLerp;

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
        int lineGap      = compact ? C_LINE_GAP : LINE_GAP;
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
                        compact, paddingX, fullLineH, rightAligned);
            }
        }

        Gemini.hudDragManager.registerDragRegion(this, originX, originY, maxItemW, totalH);
    }

    @Override
    public void renderEditorOutline(GuiGraphicsExtractor g) {
        boolean minimal = isMinimal();
        boolean compact = compactMode.enabled;
        int paddingX = minimal ? M_PAD_X : (compact ? C_PADDING_X : PADDING_X);
        int lineGap = compact ? C_LINE_GAP : LINE_GAP;
        int fullLineH = textLineHeight(lineGap) + (int) spaceValue.getValue();
        int radius = currentRadius(compact);
        List<Module> allMods = Gemini.moduleManager.getModules();

        int count = 0;
        int maxItemW = 0;
        for (Module m : allMods) {
            if (shouldSkipModule(m)) continue;
            count++;
            int width = (int) textWidth(displayText(m)) + paddingX * 2 + backgroundExpand.getValue();
            maxItemW = Math.max(maxItemW, width);
        }

        if (count == 0) {
            count = 1;
            maxItemW = (int) textWidth(getName()) + paddingX * 2 + backgroundExpand.getValue();
        }

        int totalH = paddingY() * 2 + count * fullLineH;
        int originX = hudX;
        int originY = hudY;
        CustomRoundedRectRenderer.drawRoundedOutline(
                g, originX, originY, maxItemW, totalH, radius, 0xAAFFD700, 2);
        Gemini.hudDragManager.registerDragRegion(this, originX, originY, maxItemW, totalH);
    }

    private int paddingY() {
        return isMinimal() ? M_PAD_Y : (compactMode.enabled ? C_PADDING_Y : PADDING_Y);
    }

    // ---- Single module card (Mellow-style) ----
    private void renderModuleCard(GuiGraphicsExtractor g, Module m,
                                  int baseX, int baseY, int maxW, int idx, int total,
                                  boolean compact, int paddingX,
                                  int fullLineH, boolean rightAligned) {
        ModuleAnimation a = animMap.get(m);
        if (a == null || a.alpha < 0.01f) return;

        int modAlpha = (int) (a.alpha * 255);

        String icon = showIcons.enabled ? getModuleIcon(m.getName()) : "";
        String name = changeCase(I18n.module(m.getName()));
        String tag  = getModuleTag(m);
        String fullText = icon.isEmpty() ? name + tag : icon + " " + name + tag;

        int extraW = backgroundExpand.getValue();
        int itemW = (int) textWidth(fullText) + paddingX * 2 + extraW;

        // Slide-in animation on the X coordinate
        float modX = rightAligned ? (baseX + maxW - itemW) + a.currentX : baseX + a.currentX;
        float modY = baseY + a.currentY;

        // Scale transform: spring pop around the row center
        float centerX = modX + itemW / 2f;
        float centerY = modY + fullLineH / 2f;
        float drawX = centerX - (itemW / 2f) * a.scale;
        float drawY = centerY - (fullLineH / 2f) * a.scale;
        float drawW = itemW * a.scale;
        float drawH = fullLineH * a.scale;
        int rowRadius = (int) Math.min(currentRadius(compact) * a.scale, drawH / 2f);

        // ---- 1. GLSL soft drop shadow (same shaping as the Mellow cards) ----
        int shadowA = (int) (SHADOW_MAX_ALPHA * a.alpha * 255);
        if (moduleBackground.enabled && shadowA > 2) {
            GlowRenderer.drawDropShadowRoundedRect(g, (int) drawX, (int) drawY,
                    (int) drawW, (int) drawH, rowRadius,
                    0, SHADOW_OFFSET_Y, SHADOW_SPREAD, shadowA << 24);
        }

        // ---- 2. Card body ----
        if (moduleBackground.enabled) {
            int bodyAlpha = (int) (backgroundAlpha.getValue() * a.alpha);
            if (bodyAlpha > 2) {
                CustomRoundedRectRenderer.drawRoundedRect(g, (int) drawX, (int) drawY,
                        (int) drawW, (int) drawH, rowRadius,
                        withAlpha(backgroundColor.getColor() & 0xFFFFFF, bodyAlpha));
            }
        }

        // ---- 3. Accent indicator ----
        float sat = rainbowSaturation.getValue();
        float bri = rainbowBrightness.getValue();
        int accent = resolveColor(rectColorMode.get(), rectCustomColor.getColor(), a, idx, total, sat, bri, modAlpha);
        if (m.enabled) {
            drawRectIndicator(g, rectMode.get(), accent,
                    drawX, drawY, drawW, drawH, rowRadius, compact, a.alpha);
        }

        // ---- 4. Text ----
        float textX = drawX + paddingX + (extraW / 2f);
        float textY = drawY + (drawH - fullLineH) / 2f + textYOffset.getValue();
        renderModuleText(g, name, tag, icon, textX, textY, m, a, idx, total, modAlpha, sat, bri);
    }

    // ---- MD3 accent indicator: solid pill bars / hairline outline ----
    private void drawRectIndicator(GuiGraphicsExtractor g, String rMode, int accent,
                                   float x, float y, float w, float h, int radius,
                                   boolean compact, float alphaMul) {
        if ("None".equals(rMode) || alphaMul < 0.1f) return;
        int barW  = compact ? 2 : ACCENT_BAR_W;
        int inset = compact ? 3 : 4;
        int barH  = (int) h - inset * 2;

        switch (rMode) {
            case "Left" -> CustomRoundedRectRenderer.drawRoundedRect(g,
                    (int) x + 1, (int) y + inset, barW, barH, barW / 2, accent);
            case "Right" -> CustomRoundedRectRenderer.drawRoundedRect(g,
                    (int) (x + w - 1 - barW), (int) y + inset, barW, barH, barW / 2, accent);
            case "Top" -> CustomRoundedRectRenderer.drawRoundedRect(g,
                    (int) (x + inset), (int) y + 1, (int) w - inset * 2, barW, barW / 2, accent);
            case "Outline" -> CustomRoundedRectRenderer.drawRoundedOutline(g,
                    (int) x, (int) y, (int) w, (int) h, radius, accent, 1);
            case "Special" -> {
                int pillH = (int) (h * 0.55f);
                CustomRoundedRectRenderer.drawRoundedRect(g,
                        (int) x + 2, (int) (y + (h - pillH) / 2f), barW, pillH, barW / 2, accent);
            }
        }
    }

    // ---- Module icon + name + tag ----
    private void renderModuleText(GuiGraphicsExtractor g, String name, String tag,
                                  String icon, float x, float y,
                                  Module m, ModuleAnimation a,
                                  int idx, int total, int alpha, float sat, float bri) {
        int textAlpha = (int) (alpha * fontAlphaValue.getValue());
        if (textAlpha < 3) return;

        // Hierarchy: accent icon / name (primary) / tag (dimmed)
        int nameColor = resolveColor(colorMode.get(), fontColor.getColor(), a, idx, total, sat, bri, textAlpha);
        int iconColor = resolveColor(rectColorMode.get(), rectCustomColor.getColor(), a, idx, total, sat, bri, textAlpha);
        int tagColorR = scaleAlpha(
                resolveColor(tagColorMode.get(), tagColor.getColor(), a, idx, total, sat, bri, textAlpha), TAG_DIM);

        if (!m.enabled) {
            nameColor = md3Disabled(textAlpha);
            iconColor = md3Disabled(textAlpha);
            tagColorR = md3Disabled(textAlpha);
        }

        // Optional text shadow (readability when Module BG is off)
        if (textShadow.enabled && m.enabled) {
            String combined = icon.isEmpty() ? name + tag : icon + " " + name + tag;
            drawText(g, combined, x + 1f, y + 1f, withAlpha(0x000000, textAlpha * 35 / 100));
        }

        float cx = x;
        if (!icon.isEmpty()) {
            drawText(g, icon, cx, y, iconColor);
            cx += textWidth(icon) + textWidth(" ");
        }
        drawText(g, name, cx, y, nameColor);
        cx += textWidth(name);
        if (!tag.isEmpty()) drawText(g, tag, cx + 1f, y, tagColorR);
    }

    // ==================== MINIMAL STYLE ====================

    /**
     * Single MD3 surface container for the whole list — one soft shadow and
     * one fill replace the per-card pipeline.
     */
    private void drawMinimalContainer(GuiGraphicsExtractor g, List<Module> modules,
                                      int originX, int originY, int maxItemW, int totalH,
                                      int fullLineH, boolean rightAligned) {
        // Container spring: size + master visibility (frame-rate independent)
        float speedMul = Math.max(0.05f, animSpeed.getValue());
        float sizeLerp = lerpScale(LERP_SPEED, speedMul);
        float fadeLerp = lerpScale(FADE_LERP, speedMul);
        containerW     += (maxItemW - containerW) * sizeLerp;
        containerH     += (totalH   - containerH) * sizeLerp;
        containerAlpha += (1f       - containerAlpha) * fadeLerp;
        if (containerAlpha < 0.01f) return;

        float cx = originX, cy = originY, cw = containerW, ch = containerH;
        int radius = (int) Math.min(currentRadius(false), Math.min(cw, ch) / 2f);

        if (moduleBackground.enabled && cw >= 2f && ch >= 2f) {
            // 1. Soft shadow — one GLSL pass for the whole list
            int shadowA = (int) (SHADOW_MAX_ALPHA * containerAlpha * 255);
            if (shadowA > 2) {
                GlowRenderer.drawDropShadowRoundedRect(g, (int) cx, (int) cy,
                        (int) cw, (int) ch, radius,
                        0, SHADOW_OFFSET_Y, SHADOW_SPREAD, shadowA << 24);
            }

            // 2. Surface fill
            int bodyAlpha = (int) (backgroundAlpha.getValue() * containerAlpha);
            if (bodyAlpha > 2) {
                CustomRoundedRectRenderer.drawRoundedRect(g, (int) cx, (int) cy,
                        (int) cw, (int) ch, radius,
                        withAlpha(backgroundColor.getColor() & 0xFFFFFF, bodyAlpha));
            }
        }

        // 3. Rows + hairline dividers (each divider tracks the row above's animated Y)
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
                            withAlpha(OUTLINE_VARIANT_RGB, sepA));
                }
            }
        }
    }

    /**
     * Minimal row: optional icon → name (primary) → tag (dimmed),
     * plus an accent pill anchored to the container's outer edge.
     */
    private void renderMinimalRow(GuiGraphicsExtractor g, Module m,
                                  float cx, float cy, float cw,
                                  int idx, int total, int fullLineH,
                                  boolean rightAligned, float sat, float bri) {
        ModuleAnimation a = animMap.get(m);
        if (a == null || a.alpha < 0.01f) return;

        String icon = showIcons.enabled ? getModuleIcon(m.getName()) : "";
        String name = changeCase(I18n.module(m.getName()));
        String tag  = getModuleTag(m);

        int textA = (int) (a.alpha * fontAlphaValue.getValue() * 255);
        if (textA < 3) return;

        float rowY  = cy + a.currentY;
        float rowCX = cx + a.currentX; // only the text slides; the accent pill stays at the edge

        // ---- Hierarchy: name (primary) / tag (dimmed) / accent pill ----
        int nameColor = resolveColor(colorMode.get(), fontColor.getColor(), a, idx, total, sat, bri, textA);
        int tagColorR = scaleAlpha(
                resolveColor(tagColorMode.get(), tagColor.getColor(), a, idx, total, sat, bri, textA), TAG_DIM);
        int accent    = resolveColor(rectColorMode.get(), rectCustomColor.getColor(), a, idx, total, sat, bri, textA);
        if (!m.enabled) {
            nameColor = md3Disabled(textA);
            tagColorR = md3Disabled(textA);
            accent    = md3Disabled(textA);
        }

        // Accent pill hugging the container's outer edge
        if (m.enabled && !"None".equals(rectMode.get()) && cw >= 6f) {
            float pillH = Math.max(6f, fullLineH * 0.5f);
            float pillX = rightAligned ? cx + cw - 3 : cx + 1;
            CustomRoundedRectRenderer.drawRoundedRect(g,
                    (int) pillX, (int) (rowY + (fullLineH - pillH) / 2f), 2, (int) pillH, 1, accent);
        }

        // Text position: anchored to the text-side edge
        float iconPart = icon.isEmpty() ? 0f : textWidth(icon) + textWidth(" ");
        float fullW    = iconPart + textWidth(name + tag);
        float textX    = rightAligned ? rowCX + cw - M_PAD_X - fullW : rowCX + M_PAD_X;
        float textY    = rowY + (fullLineH - Md3Fonts.lineHeight(hudFont())) / 2f
                + textYOffset.getValue() - 1f;

        if (textShadow.enabled && m.enabled) {
            String combined = icon.isEmpty() ? name + tag : icon + " " + name + tag;
            drawText(g, combined, textX + 0.75f, textY + 0.75f,
                    withAlpha(0x000000, textA * 35 / 100));
        }

        // Foreground: icon (optional) → name (primary) → tag (dimmed)
        float x = textX;
        if (!icon.isEmpty()) {
            drawText(g, icon, x, textY, scaleAlpha(accent, 0.9f));
            x += iconPart;
        }
        drawText(g, name, x, textY, nameColor);
        x += textWidth(name);
        if (!tag.isEmpty()) drawText(g, tag, x + 1f, textY, tagColorR);
    }
}
