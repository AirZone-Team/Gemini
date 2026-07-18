package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.customRenderer.glsl.GlowRenderer;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * Material Design 3 design tokens for the MD3 ClickGui mode.
 * Light lavender baseline scheme + elevation/shadow recipes mapped onto
 * {@link GlowRenderer} drop shadows.
 */
public final class Md3Theme {

    private Md3Theme() {
    }

    // ── MD3 baseline light scheme ───────────────────────
    public static final int PRIMARY                  = rgb(0x6750A4);
    public static final int ON_PRIMARY               = rgb(0xFFFFFF);
    public static final int PRIMARY_CONTAINER        = rgb(0xEADDFF);
    public static final int ON_PRIMARY_CONTAINER     = rgb(0x21005D);
    public static final int SECONDARY_CONTAINER      = rgb(0xE8DEF8);
    public static final int ON_SECONDARY_CONTAINER   = rgb(0x1D192B);

    public static final int SURFACE                  = rgb(0xFFFBFE);
    public static final int SURFACE_CONTAINER_LOW    = rgb(0xF7F2FA);
    public static final int SURFACE_CONTAINER        = rgb(0xF3EDF7);
    public static final int SURFACE_CONTAINER_HIGH   = rgb(0xECE6F0);
    public static final int SURFACE_CONTAINER_HIGHEST= rgb(0xE6E0E9);

    public static final int ON_SURFACE               = rgb(0x1D1B20);
    public static final int ON_SURFACE_VARIANT       = rgb(0x49454F);
    public static final int OUTLINE                  = rgb(0x79747E);
    public static final int OUTLINE_VARIANT          = rgb(0xCAC4D0);

    // ── Corner radii ────────────────────────────────────
    public static final int R_CARD    = 16;
    public static final int R_ROW     = 12;
    public static final int R_CONTROL = 8;

    // ── State layers (MD3 interaction overlays) ─────────
    public static int hoverState(int contentColor)   { return withAlpha(contentColor, 0.08f); }
    public static int pressedState(int contentColor) { return withAlpha(contentColor, 0.12f); }

    // ── Elevation recipes (mapped onto GlowRenderer) ────
    private static final int SHADOW_1A = new Color(0x67, 0x50, 0xA4, 16).getRGB();  // tinted, 6%
    private static final int SHADOW_1B = new Color(0, 0, 0, 12).getRGB();           // contact, 5%
    private static final int SHADOW_2A = new Color(0x67, 0x50, 0xA4, 26).getRGB();  // tinted, 10%
    private static final int SHADOW_2B = new Color(0, 0, 0, 18).getRGB();           // contact, 7%

    /** Level 1: subtle card separation (used sparingly). */
    public static void elevation1(GuiGraphicsExtractor gui, int x, int y, int w, int h, int radius) {
        GlowRenderer.drawDropShadowRoundedRect(gui, x, y, w, h, radius, 0, 1, 5, SHADOW_1A);
        GlowRenderer.drawDropShadowRoundedRect(gui, x, y, w, h, radius, 0, 2, 9, SHADOW_1B);
    }

    /** Level 2: menus, pickers, window shadows. */
    public static void elevation2(GuiGraphicsExtractor gui, int x, int y, int w, int h, int radius) {
        GlowRenderer.drawDropShadowRoundedRect(gui, x, y, w, h, radius, 0, 2, 8, SHADOW_2A);
        GlowRenderer.drawDropShadowRoundedRect(gui, x, y, w, h, radius, 0, 5, 16, SHADOW_2B);
    }

    // ── Per-category hero gradients ─────────────────────
    private static final Map<ModuleEnum, int[]> HERO_GRADIENTS = new EnumMap<>(ModuleEnum.class);
    private static final Map<ModuleEnum, String> CATEGORY_DESCRIPTIONS = new EnumMap<>(ModuleEnum.class);

    static {
        HERO_GRADIENTS.put(ModuleEnum.Combat,   new int[]{rgb(0xEADDFF), rgb(0xD0BCFF)});
        HERO_GRADIENTS.put(ModuleEnum.Movement, new int[]{rgb(0xD8E2FF), rgb(0xB9C3FF)});
        HERO_GRADIENTS.put(ModuleEnum.Player,   new int[]{rgb(0xC4E7FF), rgb(0xA8D4F0)});
        HERO_GRADIENTS.put(ModuleEnum.Visual,   new int[]{rgb(0xFFD8E4), rgb(0xEFB8C8)});

        CATEGORY_DESCRIPTIONS.put(ModuleEnum.Combat,   "Kill others on pvp server");
        CATEGORY_DESCRIPTIONS.put(ModuleEnum.Movement, "Move faster, fly higher");
        CATEGORY_DESCRIPTIONS.put(ModuleEnum.Player,   "Interact with the world around you");
        CATEGORY_DESCRIPTIONS.put(ModuleEnum.Visual,   "Make the game look your way");
    }

    public static int[] heroGradient(ModuleEnum category) {
        return HERO_GRADIENTS.getOrDefault(category,
                new int[]{PRIMARY_CONTAINER, SURFACE_CONTAINER});
    }

    public static String categoryDescription(ModuleEnum category) {
        return CATEGORY_DESCRIPTIONS.getOrDefault(category, "");
    }

    // ── Color utilities ─────────────────────────────────

    public static int rgb(int rgb) {
        return 0xFF000000 | rgb;
    }

    /** Returns the color with its alpha channel replaced by opacity (0..1). */
    public static int withAlpha(int color, float opacity) {
        int a = Math.max(0, Math.min(255, (int) (opacity * 255)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Multiplies the existing alpha channel by factor (0..1). */
    public static int modulateAlpha(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int newA = Math.max(0, Math.min(255, (int) (a * factor)));
        return (newA << 24) | (color & 0x00FFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int aa = (a >> 24) & 0xFF, ra = (a >> 16) & 0xFF, ga = (a >> 8) & 0xFF, ba = a & 0xFF;
        int ab = (b >> 24) & 0xFF, rb = (b >> 16) & 0xFF, gb = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (clamp8((int) (aa + (ab - aa) * t)) << 24)
             | (clamp8((int) (ra + (rb - ra) * t)) << 16)
             | (clamp8((int) (ga + (gb - ga) * t)) << 8)
             |  clamp8((int) (ba + (bb - ba) * t));
    }

    private static int clamp8(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
