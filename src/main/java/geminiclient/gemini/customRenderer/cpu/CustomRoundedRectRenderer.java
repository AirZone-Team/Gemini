package geminiclient.gemini.customRenderer.cpu;

import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Rounded rectangle renderer.
 *
 * <p>All rounded geometry is delegated to {@link SdfUIRenderer}, which draws
 * each shape as a single shader quad evaluated against an exact signed
 * distance field with {@code fwidth}-based anti-aliasing. This replaces the
 * former CPU triangle-fan tessellation (hard polygon edges, ~9 render states
 * per rect) and gives every caller — ClickGui Classic / Material3, sliders,
 * switches, cards, notifications — smooth edges for free.</p>
 *
 * <p>Axis-aligned (radius 0) rects still go through the plain GUI pipeline
 * since right angles need no anti-aliasing.</p>
 */
public class CustomRoundedRectRenderer {

    private static int clampRadius(int w, int h, int radius) {
        int maxR = Math.min(w, h) / 2;
        return Math.min(Math.max(0, radius), maxR);
    }

    // ========================
    //  Filled rounded rect
    // ========================

    public static void drawRoundedRect(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height,
            int radius, int color
    ) {
        drawRoundedRect4C(gui, x, y, width, height, radius, color, color, color, color);
    }

    public static void drawRoundedRect4C(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height,
            int radius,
            int tlColor, int trColor, int blColor, int brColor
    ) {
        if (width <= 0 || height <= 0) return;
        int r = clampRadius(width, height, radius);

        if (r == 0) {
            CustomRectRenderer.drawRect4C(gui, x, y, width, height, tlColor, trColor, blColor, brColor);
            return;
        }

        SdfUIRenderer.drawRect(gui, x, y, width, height, r, tlColor, trColor, blColor, brColor);
    }

    public static void drawRoundedRectVertGrad(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height,
            int radius, int topColor, int bottomColor
    ) {
        drawRoundedRect4C(gui, x, y, width, height, radius, topColor, topColor, bottomColor, bottomColor);
    }

    public static void drawRoundedRectHorizGrad(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height,
            int radius, int leftColor, int rightColor
    ) {
        drawRoundedRect4C(gui, x, y, width, height, radius, leftColor, rightColor, leftColor, rightColor);
    }

    // ========================
    //  Circles / rings
    // ========================

    /**
     * Filled circle of diameter {@code diameter} centred on (cx, cy).
     * Float-precise centre; diameter is integer (SDF vertex format is SHORT2).
     */
    public static void drawCircle(
            GuiGraphicsExtractor gui,
            float cx, float cy, int diameter, int color
    ) {
        SdfUIRenderer.drawCircle(gui, cx, cy, diameter, color);
    }

    /**
     * Circular ring band centred on {@code radius} px around (cx, cy),
     * {@code thickness} px wide, both edges anti-aliased.
     */
    public static void drawRing(
            GuiGraphicsExtractor gui,
            float cx, float cy, int radius, int thickness, int color
    ) {
        SdfUIRenderer.drawRing(gui, cx, cy, radius, thickness, color);
    }

    /**
     * Material 3 Expressive 圆形波浪进度带（仅进度带；平坦轨道用
     * {@link #drawRing} 绘制）。弧自 12 点方向起顺时针扫描至
     * {@code progress}·360°，两端圆角帽；波浪振幅 / 频率 / 相位语义见
     * {@link SdfUIRenderer#drawWavyRing}。
     *
     * @param phase01 波浪相位 0..1（映射 0..2π），由调用方按时间推进
     */
    public static void drawWavyRing(
            GuiGraphicsExtractor gui,
            float cx, float cy, int midRadius, int thickness,
            float progress, float phase01,
            int topColor, int bottomColor
    ) {
        SdfUIRenderer.drawWavyRing(gui, cx, cy, midRadius, thickness,
                progress, phase01, topColor, bottomColor);
    }

    // ========================
    //  Outlined rounded rect
    // ========================

    public static void drawRoundedOutline(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height,
            int radius, int color, int thickness
    ) {
        if (width <= 0 || height <= 0 || thickness <= 0) return;
        int r = clampRadius(width, height, radius);

        // [Fix] 防止描边过粗导致重叠区变暗（Alpha Overlap Bug）
        int t = Math.min(thickness, Math.max(1, r));

        if (r == 0) {
            // Right angles need no AA — plain rects keep the GUI pipeline.
            int x1 = x + width, y1 = y + height;
            CustomRectRenderer.drawRect(gui, x, y, width, t, color);
            CustomRectRenderer.drawRect(gui, x, y1 - t, width, t, color);
            CustomRectRenderer.drawRect(gui, x, y + t, t, height - t * 2, color);
            CustomRectRenderer.drawRect(gui, x1 - t, y + t, t, height - t * 2, color);
            return;
        }

        SdfUIRenderer.drawOutline(gui, x, y, width, height, r, color, t);
    }

    // ========================
    //  Filled + bordered rounded rect
    // ========================

    public static void drawRoundedBorderedRect(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height,
            int radius, int fillColor, int borderColor, int thickness
    ) {
        drawRoundedBorderedRect4C(gui, x, y, width, height, radius, fillColor, borderColor, borderColor, borderColor, borderColor, thickness);
    }

    public static void drawRoundedBorderedRect4C(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height,
            int radius,
            int fillColor,
            int topBorderColor, int bottomBorderColor, int leftBorderColor, int rightBorderColor,
            int thickness
    ) {
        if (width <= 0 || height <= 0) return;
        int r = clampRadius(width, height, radius);

        // 限定厚度不超过半径，避免半透明边缘重叠导致色块加深
        int t = Math.min(thickness, Math.min(width / 2, height / 2));
        t = Math.min(t, Math.max(1, r));

        int fillR = Math.max(0, r - t);
        if ((fillColor >>> 24) != 0) {
            drawRoundedRect(gui, x + t, y + t, width - t * 2, height - t * 2, fillR, fillColor);
        }

        if (t <= 0) return;

        if (r <= 0) {
            CustomRectRenderer.drawBorderedRect(gui, x, y, width, height, topBorderColor, bottomBorderColor, leftBorderColor, rightBorderColor, t);
            return;
        }

        // The SDF border ring is single-colour. All real callers use a
        // uniform border colour; the (currently unused) per-side colour
        // variant falls back to the top colour for the whole ring — drawing
        // straight edge strips under the ring would double-blend the
        // semi-transparent overlap.
        SdfUIRenderer.drawOutline(gui, x, y, width, height, r, topBorderColor, t);
    }
}
