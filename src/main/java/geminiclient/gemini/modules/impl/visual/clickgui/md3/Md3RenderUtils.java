package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.ClickGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * MD3 control primitives. Category and favorite icons are drawn from the
 * clickgui texture set; small control affordances remain SDF/geometry based.
 */
public final class Md3RenderUtils {

    private static final int ICON_TEX_SIZE = 24;
    private static final Identifier FAVORITE = icon("favorite");
    private static final Identifier HEART_PLUS = icon("heart_plus");
    private static final Map<ModuleEnum, Identifier> CATEGORY_ICONS = new EnumMap<>(ModuleEnum.class);

    static {
        CATEGORY_ICONS.put(ModuleEnum.Combat, icon("swords"));
        CATEGORY_ICONS.put(ModuleEnum.Movement, icon("accessible_forward"));
        CATEGORY_ICONS.put(ModuleEnum.Player, icon("person"));
        CATEGORY_ICONS.put(ModuleEnum.Visual, icon("blur_medium"));
    }

    private Md3RenderUtils() {
    }

    // ── Switch ──────────────────────────────────────────

    // Scaled-down MD3 switch (0.75x of the 52x32 spec) to match GUI scale.
    public static int switchWidth() { return ClickGui.md3SwitchWidth(); }

    public static int switchHeight() { return ClickGui.md3SwitchHeight(); }

    /**
     * MD3 switch. {@code progress} is the animated 0..1 on/off value.
     */
    public static void drawSwitch(GuiGraphicsExtractor gui, int x, int y,
                                  float progress, boolean hovered, boolean pressed) {
        int switchW = switchWidth();
        int switchH = switchHeight();
        float p = Md3Anim.easeInOutCubic(clamp01(progress));

        // Track
        int trackColor = Md3Theme.lerpColor(Md3Theme.SURFACE_CONTAINER_HIGHEST, Md3Theme.PRIMARY, p);
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, switchW, switchH, switchH / 2, trackColor);
        if (p < 0.98f) {
            int outlineCol = Md3Theme.modulateAlpha(Md3Theme.OUTLINE, 1.0f - p);
            CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, switchW, switchH, switchH / 2, outlineCol, 1);
        }

        // Handle: 12px off, 18px on. Pressing grows it like the MD3 state.
        float offD = switchH * 0.5f;
        float onD = switchH * 0.75f;
        float d = offD + (onD - offD) * p;
        if (pressed) d += switchH * 0.12f;
        float cx = x + switchH / 2f + (switchW - switchH) * p;
        float cy = y + switchH / 2f;

        // Hover/press state layer halo
        if (hovered || pressed) {
            int haloColor = Md3Theme.withAlpha(
                    p > 0.5f ? Md3Theme.PRIMARY : Md3Theme.ON_SURFACE, pressed ? 0.12f : 0.08f);
            int hd = (int) (d + 10);
            CustomRoundedRectRenderer.drawRoundedRect(gui,
                    (int) cx - hd / 2, (int) cy - hd / 2, hd, hd, hd / 2, haloColor);
        }

        int thumbColor = Md3Theme.lerpColor(Md3Theme.OUTLINE, Md3Theme.ON_PRIMARY, p);
        int di = Math.round(d);
        CustomRoundedRectRenderer.drawRoundedRect(gui,
                (int) cx - di / 2, (int) cy - di / 2, di, di, di / 2, thumbColor);

        // Selected check appears only after there is enough room in the handle.
        if (p > 0.62f) {
            int checkColor = Md3Theme.modulateAlpha(Md3Theme.PRIMARY, (p - 0.62f) / 0.38f);
            int checkSize = Math.max(8, Math.round(switchH * 0.42f));
            drawCheck(gui, Math.round(cx) - checkSize / 2, Math.round(cy) - checkSize / 2, checkSize, checkColor);
        }
    }

    // ── Slider ──────────────────────────────────────────

    /**
     * MD3 slider track + thumb. {@code fraction} is the 0..1 fill position.
     *
     * @return the thumb center x (for value-chip placement)
     */
    public static int drawSlider(GuiGraphicsExtractor gui, int x, int y, int w,
                                 float fraction, boolean active) {
        fraction = clamp01(fraction);
        int trackH = ClickGui.md3SliderTrackHeight();
        int trackY = y - trackH / 2;
        int thumbD = ClickGui.md3SliderHandleHeight(active);
        int thumbX = x + Math.round(w * fraction);

        // Tracks leave a small visual gap around the handle.
        int gap = Math.max(3, thumbD / 3);
        int activeEnd = Math.max(x, thumbX - gap);
        int inactiveStart = Math.min(x + w, thumbX + gap);
        if (activeEnd > x) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, x, trackY, activeEnd - x, trackH,
                    trackH / 2, Md3Theme.PRIMARY);
        }
        if (inactiveStart < x + w) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, inactiveStart, trackY,
                    x + w - inactiveStart, trackH, trackH / 2,
                    Md3Theme.SURFACE_CONTAINER_HIGHEST);
        }

        // Stop indicator dot at the max end
        CustomRoundedRectRenderer.drawRoundedRect(gui, x + w - 3, y - 2, 4, 4, 2,
                Md3Theme.OUTLINE);

        if (active) {
            CustomRoundedRectRenderer.drawRoundedRect(gui,
                    thumbX - (thumbD + 10) / 2, y - (thumbD + 10) / 2,
                    thumbD + 10, thumbD + 10, (thumbD + 10) / 2,
                    Md3Theme.hoverState(Md3Theme.PRIMARY));
        }

        // The current MD3 handle is a narrow vertical pill.
        int handleW = active ? 5 : 4;
        CustomRoundedRectRenderer.drawRoundedRect(gui,
                thumbX - handleW / 2, y - thumbD / 2, handleW, thumbD,
                handleW / 2, Md3Theme.PRIMARY);

        return thumbX;
    }

    /** MD3 slider value chip shown above the thumb while dragging. */
    public static void drawValueChip(GuiGraphicsExtractor gui, int cx, int y, String text) {
        float tw = Md3Fonts.width(Md3Fonts.label(), text);
        float lh = Md3Fonts.label() != null ? Md3Fonts.label().lineHeight : 9f;
        int w = (int) tw + 14;
        int h = (int) (lh + 8);
        Md3Theme.elevation1(gui, cx - w / 2, y, w, h, h / 2);
        CustomRoundedRectRenderer.drawRoundedRect(gui, cx - w / 2, y, w, h, h / 2, Md3Theme.PRIMARY);
        Md3Fonts.drawText(gui, Md3Fonts.label(), text, cx - tw / 2f, y + (h - lh) / 2f,
                Md3Theme.ON_PRIMARY);
    }

    // ── Heart (favorite) ────────────────────────────────

    public static void drawFavoriteIcon(GuiGraphicsExtractor gui, int cx, int cy,
                                        int size, float favoriteProgress,
                                        int idleColor, int selectedColor) {
        float p = Md3Anim.easeInOutCubic(clamp01(favoriteProgress));
        int idleSize = Math.round(size * (1.0f - 0.10f * p));
        int selectedSize = Math.round(size * (0.78f + 0.22f * p));

        drawTextureIcon(gui, FAVORITE, cx, cy, idleSize,
                Md3Theme.modulateAlpha(idleColor, 1.0f - p));
        drawTextureIcon(gui, HEART_PLUS, cx, cy, selectedSize,
                Md3Theme.modulateAlpha(selectedColor, p));
    }

    // ── Search / chevron / check icons ──────────────────

    /** Magnifier: circle outline + stepped 45° handle. */
    public static void drawSearchIcon(GuiGraphicsExtractor gui, int x, int y, int size, int color) {
        int d = size - 4;
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, d, d, d / 2, color, 2);
        // handle: short staircase down-right
        int hx = x + d - 1, hy = y + d - 1;
        gui.fill(hx + 1, hy + 1, hx + 4, hy + 3, color);
        gui.fill(hx + 3, hy + 3, hx + 6, hy + 5, color);
    }

    /** Anti-aliased chevron pointing down or up. */
    public static void drawChevron(GuiGraphicsExtractor gui, int cx, int cy, int size,
                                   boolean up, int color) {
        SdfUIRenderer.drawIcon(gui, cx, cy, Math.max(11, size + 4),
                up ? SdfUIRenderer.ICON_CHEVRON_UP : SdfUIRenderer.ICON_CHEVRON_DOWN,
                color);
    }

    /** Anti-aliased check mark used by menus and selected switches. */
    public static void drawCheck(GuiGraphicsExtractor gui, int x, int y, int size, int color) {
        int iconSize = Math.max(10, size);
        SdfUIRenderer.drawIcon(gui, x + size / 2f, y + size / 2f,
                iconSize, SdfUIRenderer.ICON_CHECK, color);
    }

    /** Hamburger menu icon (three horizontal lines). */
    public static void drawHamburger(GuiGraphicsExtractor gui, int cx, int cy, int size, int color) {
        int w = size;
        int x0 = cx - w / 2;
        int gap = size / 3;
        for (int i = -1; i <= 1; i++) {
            int yy = cy + i * gap - 1;
            CustomRoundedRectRenderer.drawRoundedRect(gui, x0, yy, w, 2, 1, color);
        }
    }

    /** Anti-aliased close icon. */
    public static void drawClose(GuiGraphicsExtractor gui, int cx, int cy, int size, int color) {
        SdfUIRenderer.drawIcon(gui, cx, cy, Math.max(10, size),
                SdfUIRenderer.ICON_CLOSE, color);
    }

    /** Four-point Gemini brand sparkle used by the top app bar. */
    public static void drawSparkle(GuiGraphicsExtractor gui, int cx, int cy, int size, int color) {
        int half = Math.max(2, size / 2);
        int bar = Math.max(2, size / 5);
        CustomRoundedRectRenderer.drawRoundedRect(gui, cx - bar / 2, cy - half,
                bar, half * 2, bar / 2, color);
        CustomRoundedRectRenderer.drawRoundedRect(gui, cx - half, cy - bar / 2,
                half * 2, bar, bar / 2, color);

        int inset = Math.max(1, size / 4);
        int cut = Md3Theme.PRIMARY_CONTAINER;
        for (int i = 0; i < inset; i++) {
            gui.fill(cx - half, cy - half + i, cx - half + inset - i, cy - half + i + 1, cut);
            gui.fill(cx + half - inset + i, cy - half + i, cx + half, cy - half + i + 1, cut);
            gui.fill(cx - half, cy + half - i - 1, cx - half + inset - i, cy + half - i, cut);
            gui.fill(cx + half - inset + i, cy + half - i - 1, cx + half, cy + half - i, cut);
        }
    }

    // ── Category icons ──────────────────────────────────

    public static void drawCategoryIcon(GuiGraphicsExtractor gui, ModuleEnum category,
                                        int cx, int cy, int size, int color) {
        drawTextureIcon(gui, CATEGORY_ICONS.get(category), cx, cy, size, color);
    }

    public static void drawHeartPlusIcon(GuiGraphicsExtractor gui, int cx, int cy, int size, int color) {
        drawTextureIcon(gui, HEART_PLUS, cx, cy, size, color);
    }

    private static Identifier icon(String name) {
        return getIdentifier("icon/clickgui/" + name + ".png");
    }

    private static void drawTextureIcon(GuiGraphicsExtractor gui, Identifier texture,
                                        int cx, int cy, int size, int color) {
        if (texture == null || size <= 0 || ((color >>> 24) & 0xFF) == 0) return;
        var pose = gui.pose();
        pose.pushMatrix();
        pose.translate(cx - size / 2f, cy - size / 2f);
        float scale = size / (float) ICON_TEX_SIZE;
        pose.scale(scale, scale);
        gui.blit(RenderPipelines.GUI_TEXTURED, texture,
                0, 0, 0, 0,
                ICON_TEX_SIZE, ICON_TEX_SIZE,
                ICON_TEX_SIZE, ICON_TEX_SIZE, color);
        pose.popMatrix();
    }

    // ── Ripple ──────────────────────────────────────────

    private static final long RIPPLE_DURATION_MS = 450;

    /** A single ripple instance; create on click, render until finished. */
    public static final class Ripple {
        public final float cx, cy;
        public final long startMs;

        public Ripple(float cx, float cy) {
            this.cx = cx;
            this.cy = cy;
            this.startMs = System.currentTimeMillis();
        }

        public boolean isFinished() {
            return System.currentTimeMillis() - startMs > RIPPLE_DURATION_MS;
        }

        /** Draw inside the caller's scissor region. */
        public void render(GuiGraphicsExtractor gui, float maxRadius, int overlayColor) {
            float p = (System.currentTimeMillis() - startMs) / (float) RIPPLE_DURATION_MS;
            if (p >= 1.0f) return;
            float eased = Md3Anim.easeOutCubic(p);
            float alpha = (1.0f - p) * 0.20f;
            int r = Math.max(1, (int) (maxRadius * eased));
            int col = Md3Theme.withAlpha(overlayColor, alpha);
            CustomRoundedRectRenderer.drawRoundedRect(gui,
                    (int) cx - r, (int) cy - r, r * 2, r * 2, r, col);
            // soft leading edge
            int r2 = Math.max(1, (int) (r * 0.7f));
            int col2 = Md3Theme.withAlpha(overlayColor, alpha * 0.8f);
            CustomRoundedRectRenderer.drawRoundedRect(gui,
                    (int) cx - r2, (int) cy - r2, r2 * 2, r2 * 2, r2, col2);
        }
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
