package geminiclient.gemini.modules.impl.visual.clickgui;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

/**
 * Shared dark "violet" design tokens for the classic ClickGui mode.
 *
 * <p>Every classic-mode surface (category panels, module rows, value
 * components, search bar) draws from these constants so the whole GUI reads
 * as one design language: deep indigo surfaces, white hairline borders and a
 * single violet accent.</p>
 */
public final class ClassicTheme {

    private ClassicTheme() {
    }

    // ── Accent ──────────────────────────────────────────
    public static final int ACCENT          = new Color(139, 92, 246).getRGB();
    public static final int ACCENT_GLOW     = new Color(139, 92, 246, 45).getRGB();
    public static final int ACCENT_TINT     = new Color(139, 92, 246, 14).getRGB();

    // ── Surfaces ────────────────────────────────────────
    public static final int ROW_BG          = new Color(20, 20, 28, 200).getRGB();
    public static final int ROW_BG_HOVER    = new Color(30, 30, 42, 220).getRGB();
    public static final int PANEL_BG        = new Color(13, 14, 20, 200).getRGB();
    public static final int HEADER_BG       = new Color(20, 22, 32, 220).getRGB();
    public static final int HEADER_HOVER    = new Color(28, 30, 42, 230).getRGB();

    // ── Borders ─────────────────────────────────────────
    public static final int BORDER          = new Color(255, 255, 255, 10).getRGB();
    public static final int BORDER_HOVER    = new Color(255, 255, 255, 24).getRGB();

    // ── Text ────────────────────────────────────────────
    public static final int TEXT            = new Color(225, 225, 238).getRGB();
    public static final int TEXT_DIM        = new Color(150, 155, 170).getRGB();

    // ── Controls ────────────────────────────────────────
    public static final int TRACK           = new Color(52, 52, 58).getRGB();
    public static final int HANDLE          = Color.WHITE.getRGB();
    public static final int HANDLE_SHADOW   = new Color(0, 0, 0, 25).getRGB();

    /** Corner radius shared by all small classic controls. */
    public static final int R_CONTROL = 4;

    // ── Shared drawing helpers ──────────────────────────

    /**
     * Standard value-row background: rounded fill that lightens on hover,
     * plus a hairline border that brightens with the same progress.
     *
     * @param hoverT hover spring progress 0..1
     */
    public static void drawRow(GuiGraphicsExtractor gui, int x, int y, int width, int height,
                               float hoverT) {
        int bg = hoverT > 0.01f ? lerpColor(ROW_BG, ROW_BG_HOVER, hoverT) : ROW_BG;
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, width, height, R_CONTROL, bg);

        int border = hoverT > 0.01f ? lerpColor(BORDER, BORDER_HOVER, hoverT) : BORDER;
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, width, height, R_CONTROL, border, 1);
    }

    /**
     * Single-thumb slider: rounded inactive track, accent fill and a round
     * white handle with a soft shadow. The handle grows slightly while
     * hovered or dragged.
     *
     * @param fraction fill fraction 0..1
     * @param active   hovered or dragging (handle emphasis)
     * @return handle center x in pixels
     */
    public static int drawSlider(GuiGraphicsExtractor gui, int trackX, int trackY, int trackWidth,
                                 float fraction, boolean active) {
        fraction = Math.max(0f, Math.min(1f, fraction));
        int trackH = 2;
        int handleD = active ? 8 : 7;
        int handleX = trackX + Math.round(trackWidth * fraction);
        int cy = trackY + trackH / 2;

        // Inactive track + accent fill
        CustomRoundedRectRenderer.drawRoundedRect(gui, trackX, trackY, trackWidth, trackH, 1, TRACK);
        int fillW = handleX - trackX;
        if (fillW > 0) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, trackX, trackY, Math.max(trackH, fillW),
                    trackH, 1, ACCENT);
        }

        // Handle (shadow pass + white core, accent dot while active)
        CustomRoundedRectRenderer.drawRoundedRect(gui, handleX - handleD / 2 + 1, cy - handleD / 2 + 1,
                handleD, handleD, handleD / 2, HANDLE_SHADOW);
        CustomRoundedRectRenderer.drawRoundedRect(gui, handleX - handleD / 2, cy - handleD / 2,
                handleD, handleD, handleD / 2, HANDLE);
        if (active) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, handleX - 2, cy - 2, 4, 4, 2, ACCENT);
        }
        return handleX;
    }

    /**
     * Round range-slider thumb used by dual-thumb controls.
     *
     * @param active this thumb is being dragged (or row hovered)
     */
    public static void drawRangeThumb(GuiGraphicsExtractor gui, int cx, int cy, boolean active) {
        int d = active ? 8 : 7;
        CustomRoundedRectRenderer.drawRoundedRect(gui, cx - d / 2 + 1, cy - d / 2 + 1, d, d, d / 2,
                HANDLE_SHADOW);
        CustomRoundedRectRenderer.drawRoundedRect(gui, cx - d / 2, cy - d / 2, d, d, d / 2, HANDLE);
        if (active) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, cx - 2, cy - 2, 4, 4, 2, ACCENT);
        }
    }

    // ── Color math ──────────────────────────────────────

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

    /** Multiplies the existing alpha channel by factor (0..1). */
    public static int modulateAlpha(int color, float factor) {
        if (factor >= 1.0f) return color;
        int a = (color >> 24) & 0xFF;
        int newA = clamp8((int) (a * factor));
        return (newA << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp8(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
