package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * MD3 control primitives drawn entirely from rounded-rect / fill geometry:
 * switches, sliders, hearts, chevrons, checks, category icons, ripples.
 * No texture assets and no font-glyph dependency.
 */
public final class Md3RenderUtils {

    private Md3RenderUtils() {
    }

    // ── Switch ──────────────────────────────────────────

    // Scaled-down MD3 switch (0.75x of the 52x32 spec) to match GUI text scale
    public static final int SWITCH_W = 40;
    public static final int SWITCH_H = 24;

    /**
     * MD3 switch. {@code progress} is the animated 0..1 on/off value.
     */
    public static void drawSwitch(GuiGraphicsExtractor gui, int x, int y,
                                  float progress, boolean hovered, boolean pressed) {
        float p = Md3Anim.easeInOutCubic(clamp01(progress));

        // Track
        int trackColor = Md3Theme.lerpColor(Md3Theme.SURFACE_CONTAINER_HIGHEST, Md3Theme.PRIMARY, p);
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2, trackColor);
        if (p < 0.98f) {
            int outlineCol = Md3Theme.modulateAlpha(Md3Theme.OUTLINE, 1.0f - p);
            CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2, outlineCol, 1);
        }

        // Thumb: diameter 12 -> 17 (pressed widens), travels left -> right
        float d = 12f + (17f - 12f) * p;
        if (pressed) d += 3f;
        float cx = x + SWITCH_H / 2f + (SWITCH_W - SWITCH_H) * p;
        float cy = y + SWITCH_H / 2f;

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
        int trackH = 4;
        int trackY = y - trackH / 2;
        int thumbD = active ? 22 : 20;
        int thumbX = x + Math.round(w * fraction);

        // Inactive track (with a gap around the thumb, per MD3)
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, trackY, w, trackH, trackH / 2,
                Md3Theme.SURFACE_CONTAINER_HIGHEST);

        // Active track
        int activeW = Math.max(trackH, thumbX - x);
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, trackY, activeW, trackH, trackH / 2,
                Md3Theme.PRIMARY);

        // Stop indicator dot at the max end
        CustomRoundedRectRenderer.drawRoundedRect(gui, x + w - 3, y - 2, 4, 4, 2,
                Md3Theme.OUTLINE);

        // Thumb: primary ring with surface-colored core (M3 style)
        CustomRoundedRectRenderer.drawRoundedRect(gui,
                thumbX - thumbD / 2, y - thumbD / 2, thumbD, thumbD, thumbD / 2, Md3Theme.PRIMARY);
        int core = thumbD - 10;
        CustomRoundedRectRenderer.drawRoundedRect(gui,
                thumbX - core / 2, y - core / 2, core, core, core / 2, Md3Theme.SURFACE);

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

    // 11x9 heart bitmap
    private static final String[] HEART = {
            ".XXX...XXX.",
            "XXXXX.XXXXX",
            "XXXXXXXXXXX",
            "XXXXXXXXXXX",
            ".XXXXXXXXX.",
            "..XXXXXXX..",
            "...XXXXX...",
            "....XXX....",
            ".....X....."
    };

    /**
     * Heart icon. When {@code filled} is false, draws an outline heart by
     * stamping the bitmap in {@code color} then erasing the inner cells with
     * {@code background}.
     */
    public static void drawHeart(GuiGraphicsExtractor gui, int x, int y, int cell,
                                 boolean filled, int color, int background) {
        stampHeart(gui, x, y, cell, color);
        if (!filled) {
            // erase the inner cells -> 1-cell outline
            stampHeart(gui, x, y, cell, background, 1);
        }
    }

    private static void stampHeart(GuiGraphicsExtractor gui, int x, int y, int cell, int color) {
        stampHeart(gui, x, y, cell, color, 0);
    }

    private static void stampHeart(GuiGraphicsExtractor gui, int x, int y, int cell, int color, int inset) {
        for (int row = inset; row < HEART.length - inset; row++) {
            String line = HEART[row];
            for (int col = inset; col < line.length() - inset; col++) {
                if (line.charAt(col) == 'X') {
                    gui.fill(x + col * cell, y + row * cell,
                            x + (col + 1) * cell, y + (row + 1) * cell, color);
                }
            }
        }
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

    /** Chevron pointing down (or up), drawn as a two-armed staircase. */
    public static void drawChevron(GuiGraphicsExtractor gui, int cx, int cy, int size,
                                   boolean up, int color) {
        int half = size / 2;
        for (int i = 0; i < half; i++) {
            int yy = up ? cy + half - i : cy - half + i;
            gui.fill(cx - half + i, yy, cx - half + i + 2, yy + 2, color);
            gui.fill(cx + half - i - 2, yy, cx + half - i, yy + 2, color);
        }
    }

    /** Check mark (menu selected indicator). */
    public static void drawCheck(GuiGraphicsExtractor gui, int x, int y, int size, int color) {
        int u = Math.max(1, size / 6);
        // short arm (down-right)
        for (int i = 0; i < 2; i++) {
            gui.fill(x + i * u, y + 2 * u + i * u, x + (i + 1) * u, y + 2 * u + (i + 1) * u + u, color);
        }
        // long arm (up-right)
        for (int i = 0; i < 4; i++) {
            gui.fill(x + (2 + i) * u, y + (4 - i) * u, x + (3 + i) * u, y + (5 - i) * u, color);
        }
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

    // ── Category icons ──────────────────────────────────

    /** Simple 24px vector icons per category, composed from fills. */
    public static void drawCategoryIcon(GuiGraphicsExtractor gui, ModuleEnum category,
                                        int cx, int cy, int size, int color) {
        int x = cx - size / 2, y = cy - size / 2;
        switch (category) {
            case Combat -> {
                // Sword: diagonal blade + guard
                int n = size - 8;
                for (int i = 0; i < n; i++) {
                    gui.fill(x + 4 + i, y + size - 6 - i, x + 7 + i, y + size - 3 - i, color);
                }
                gui.fill(x + 2, y + size - 8, x + 9, y + size - 5, color);   // guard
                gui.fill(x, y + size - 4, x + 4, y + size, color);           // pommel
            }
            case Movement -> {
                // Double chevron right
                drawChevronRight(gui, x + 3, cy, size - 8, color);
                drawChevronRight(gui, x + size / 2 + 2, cy, size - 8, color);
            }
            case Player -> {
                // Person: head + shoulders
                int head = size / 3;
                CustomRoundedRectRenderer.drawRoundedRect(gui,
                        cx - head / 2, y + 1, head, head, head / 2, color);
                CustomRoundedRectRenderer.drawRoundedRect(gui,
                        cx - size / 2 + 3, y + head + 4, size - 6, size - head - 5,
                        (size - 6) / 2 > 6 ? 6 : (size - 6) / 2, color);
            }
            case Visual -> {
                // Eye: rounded outline + pupil
                CustomRoundedRectRenderer.drawRoundedRect(gui, x + 1, cy - size / 4,
                        size - 2, size / 2, size / 4, color);
                int pupil = size / 4;
                CustomRoundedRectRenderer.drawRoundedRect(gui, cx - pupil / 2, cy - pupil / 2,
                        pupil, pupil, pupil / 2, Md3Theme.SURFACE);
            }
        }
    }

    private static void drawChevronRight(GuiGraphicsExtractor gui, int x, int cy, int size, int color) {
        int half = size / 2;
        for (int i = 0; i < half; i++) {
            gui.fill(x + i, cy - half + i, x + i + 2, cy - half + i + 2, color);
            gui.fill(x + i, cy + half - i - 2, x + i + 2, cy + half - i, color);
        }
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
