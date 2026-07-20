package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.customRenderer.glsl.GlowRenderer;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * Material Design 3 design tokens for the ClickGui.
 *
 * <p>The palette follows the baseline light scheme while the surface ladder,
 * shape scale and state layers mirror the roles used by Material 3. Keeping
 * these roles centralized prevents individual controls from inventing
 * one-off colours and radii.</p>
 */
public final class Md3Theme {

    private Md3Theme() {
    }

    // Material 3 baseline light colour scheme.
    public static final int PRIMARY                  = rgb(0x6750A4);
    public static final int ON_PRIMARY               = rgb(0xFFFFFF);
    public static final int PRIMARY_CONTAINER        = rgb(0xEADDFF);
    public static final int ON_PRIMARY_CONTAINER     = rgb(0x21005D);
    public static final int SECONDARY                = rgb(0x625B71);
    public static final int ON_SECONDARY             = rgb(0xFFFFFF);
    public static final int SECONDARY_CONTAINER      = rgb(0xE8DEF8);
    public static final int ON_SECONDARY_CONTAINER   = rgb(0x1D192B);
    public static final int TERTIARY                 = rgb(0x7D5260);
    public static final int TERTIARY_CONTAINER       = rgb(0xFFD8E4);
    public static final int ON_TERTIARY_CONTAINER    = rgb(0x31111D);
    public static final int ERROR                    = rgb(0xB3261E);
    public static final int ON_ERROR                 = rgb(0xFFFFFF);

    public static final int SURFACE                   = rgb(0xFFFBFE);
    public static final int SURFACE_BRIGHT            = rgb(0xFFFBFE);
    public static final int SURFACE_DIM               = rgb(0xDED8E1);
    public static final int SURFACE_CONTAINER_LOWEST  = rgb(0xFFFFFF);
    public static final int SURFACE_CONTAINER_LOW     = rgb(0xF7F2FA);
    public static final int SURFACE_CONTAINER         = rgb(0xF3EDF7);
    public static final int SURFACE_CONTAINER_HIGH    = rgb(0xECE6F0);
    public static final int SURFACE_CONTAINER_HIGHEST = rgb(0xE6E0E9);

    public static final int ON_SURFACE           = rgb(0x1D1B20);
    public static final int ON_SURFACE_VARIANT   = rgb(0x49454F);
    public static final int OUTLINE              = rgb(0x79747E);
    public static final int OUTLINE_VARIANT      = rgb(0xCAC4D0);

    // Material 3 shape scale.
    public static final int R_EXTRA_SMALL = 4;
    public static final int R_SMALL       = 8;
    public static final int R_MEDIUM      = 12;
    public static final int R_LARGE       = 16;
    public static final int R_EXTRA_LARGE = 28;
    public static final int R_FULL        = 999;

    // Compatibility aliases used by controls.
    public static final int R_CARD    = R_LARGE;
    public static final int R_ROW     = R_LARGE;
    public static final int R_CONTROL = R_SMALL;

    // Material 3 state layers.
    public static int hoverState(int contentColor) {
        return withAlpha(contentColor, 0.08f);
    }

    public static int focusState(int contentColor) {
        return withAlpha(contentColor, 0.10f);
    }

    public static int pressedState(int contentColor) {
        return withAlpha(contentColor, 0.12f);
    }

    public static int disabledContent(int contentColor) {
        return withAlpha(contentColor, 0.38f);
    }

    public static int disabledContainer(int contentColor) {
        return withAlpha(contentColor, 0.12f);
    }

    // Elevation recipes mapped onto the existing SDF shadow renderer.
    private static final int SHADOW_1A = new Color(0x67, 0x50, 0xA4, 13).getRGB();
    private static final int SHADOW_1B = new Color(0, 0, 0, 10).getRGB();
    private static final int SHADOW_2A = new Color(0x67, 0x50, 0xA4, 20).getRGB();
    private static final int SHADOW_2B = new Color(0, 0, 0, 18).getRGB();
    private static final int SHADOW_3A = new Color(0x21, 0x00, 0x5D, 26).getRGB();
    private static final int SHADOW_3B = new Color(0, 0, 0, 24).getRGB();

    /** Level 1: subtle card separation. */
    public static void elevation1(GuiGraphicsExtractor gui, int x, int y, int w, int h, int radius) {
        GlowRenderer.drawDropShadowRoundedRect(gui, x, y, w, h, radius, 0, 1, 5, SHADOW_1A);
        GlowRenderer.drawDropShadowRoundedRect(gui, x, y, w, h, radius, 0, 2, 9, SHADOW_1B);
    }

    /** Level 2: menus and pickers. */
    public static void elevation2(GuiGraphicsExtractor gui, int x, int y, int w, int h, int radius) {
        GlowRenderer.drawDropShadowRoundedRect(gui, x, y, w, h, radius, 0, 2, 8, SHADOW_2A);
        GlowRenderer.drawDropShadowRoundedRect(gui, x, y, w, h, radius, 0, 5, 16, SHADOW_2B);
    }

    /** Level 3: the main floating window. */
    public static void elevation3(GuiGraphicsExtractor gui, int x, int y, int w, int h, int radius) {
        GlowRenderer.drawDropShadowRoundedRect(gui, x, y, w, h, radius, 0, 3, 12, SHADOW_3A);
        GlowRenderer.drawDropShadowRoundedRect(gui, x, y, w, h, radius, 0, 8, 24, SHADOW_3B);
    }

    private static final Map<ModuleEnum, int[]> HERO_GRADIENTS = new EnumMap<>(ModuleEnum.class);
    private static final Map<ModuleEnum, Integer> CATEGORY_ACCENTS = new EnumMap<>(ModuleEnum.class);
    private static final Map<ModuleEnum, String> CATEGORY_DESCRIPTIONS = new EnumMap<>(ModuleEnum.class);

    static {
        HERO_GRADIENTS.put(ModuleEnum.Combat,   new int[]{rgb(0xF1E8FF), rgb(0xE3D3FF)});
        HERO_GRADIENTS.put(ModuleEnum.Movement, new int[]{rgb(0xE5EAFF), rgb(0xD1DAFF)});
        HERO_GRADIENTS.put(ModuleEnum.Player,   new int[]{rgb(0xDDF2FF), rgb(0xC7E7FA)});
        HERO_GRADIENTS.put(ModuleEnum.Visual,   new int[]{rgb(0xFFE8EF), rgb(0xFFD8E4)});

        CATEGORY_ACCENTS.put(ModuleEnum.Combat, rgb(0x6750A4));
        CATEGORY_ACCENTS.put(ModuleEnum.Movement, rgb(0x4056A1));
        CATEGORY_ACCENTS.put(ModuleEnum.Player, rgb(0x00658A));
        CATEGORY_ACCENTS.put(ModuleEnum.Visual, rgb(0x8C435D));

        CATEGORY_DESCRIPTIONS.put(ModuleEnum.Combat,   "Combat assistance and targeting");
        CATEGORY_DESCRIPTIONS.put(ModuleEnum.Movement, "Movement, speed and traversal");
        CATEGORY_DESCRIPTIONS.put(ModuleEnum.Player,   "Player actions and world interaction");
        CATEGORY_DESCRIPTIONS.put(ModuleEnum.Visual,   "Interface, effects and presentation");
    }

    public static int[] heroGradient(ModuleEnum category) {
        return HERO_GRADIENTS.getOrDefault(category,
                new int[]{PRIMARY_CONTAINER, SURFACE_CONTAINER});
    }

    public static int categoryAccent(ModuleEnum category) {
        return CATEGORY_ACCENTS.getOrDefault(category, PRIMARY);
    }

    public static String categoryDescription(ModuleEnum category) {
        return CATEGORY_DESCRIPTIONS.getOrDefault(category, "");
    }

    public static int rgb(int rgb) {
        return 0xFF000000 | rgb;
    }

    /** Returns the colour with its alpha channel replaced by opacity (0..1). */
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
                | clamp8((int) (ba + (bb - ba) * t));
    }

    private static int clamp8(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
