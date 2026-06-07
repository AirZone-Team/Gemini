package geminiclient.gemini.customRenderer.cpu;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Custom rectangle renderer supporting per-corner RGBA colors
 * and per-side colored borders via {@link GuiElementRenderState}.
 *
 * <pre>{@code
 * // 4-corner colored rectangle (smooth interpolation)
 * CustomRectRenderer.drawRect4C(gui, x, y, w, h,
 *     0xFFFF0000, // TL red
 *     0xFF00FF00, // TR green
 *     0xFF0000FF, // BL blue
 *     0xFFFFFF00  // BR yellow
 * );
 *
 * // Vertical gradient
 * CustomRectRenderer.drawRectVertGrad(gui, x, y, w, h, 0xFFFF0000, 0xFF0000FF);
 *
 * // 4-sided borders with different colors
 * CustomRectRenderer.drawBorderedRect(gui, x, y, w, h,
 *     0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, // top, bottom, left, right
 *     2 // thickness
 * );
 * }</pre>
 */
public class CustomRectRenderer {

    // ========================
    //  MultiColorRectState
    // ========================

    /**
     * A {@link GuiElementRenderState} that draws a filled rectangle with
     * 4 independent corner colors. Colors are interpolated across the quad.
     */
    public static final class MultiColorRectState implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final Matrix3x2f pose;
        private final int x0, y0, x1, y1;
        private final int tlColor, trColor, blColor, brColor;
        @Nullable
        private final ScreenRectangle scissor;
        @Nullable
        private final ScreenRectangle bounds;

        public MultiColorRectState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            int x0, int y0, int x1, int y1,
            int tlColor, int trColor, int blColor, int brColor,
            @Nullable ScreenRectangle scissor
        ) {
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.pose = pose;
            if (x0 > x1) { int t = x0; x0 = x1; x1 = t; }
            if (y0 > y1) { int t = y0; y0 = y1; y1 = t; }
            this.x0 = x0; this.y0 = y0;
            this.x1 = x1; this.y1 = y1;
            this.tlColor = tlColor;
            this.trColor = trColor;
            this.blColor = blColor;
            this.brColor = brColor;
            this.scissor = scissor;
            ScreenRectangle b = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
            this.bounds = scissor != null ? scissor.intersection(b) : b;
        }

        @Override
        public void buildVertices(VertexConsumer vc) {
            vc.addVertexWith2DPose(pose, x0, y0).setColor(tlColor);
            vc.addVertexWith2DPose(pose, x0, y1).setColor(blColor);
            vc.addVertexWith2DPose(pose, x1, y1).setColor(brColor);
            vc.addVertexWith2DPose(pose, x1, y0).setColor(trColor);
        }

        @Override public @NonNull RenderPipeline pipeline() { return pipeline; }
        @Override public @NonNull TextureSetup textureSetup() { return textureSetup; }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
    }

    // ========================
    //  Convenience methods
    // ========================

    private static final TextureSetup NO_TEXTURE = TextureSetup.noTexture();
    private static final RenderPipeline GUI_PIPELINE = RenderPipelines.GUI;

    /**
     * Draw a filled rectangle with 4 independent corner colors.
     * Colors are smoothly interpolated across the quad.
     *
     * @param gui     the GuiGraphicsExtractor to submit to
     * @param x       left edge
     * @param y       top edge
     * @param width   rectangle width
     * @param height  rectangle height
     * @param tlColor ARGB color for the top-left corner
     * @param trColor ARGB color for the top-right corner
     * @param blColor ARGB color for the bottom-left corner
     * @param brColor ARGB color for the bottom-right corner
     */
    public static void drawRect4C(
        GuiGraphicsExtractor gui,
        int x, int y, int width, int height,
        int tlColor, int trColor, int blColor, int brColor
    ) {
        drawRect4C(gui, GUI_PIPELINE, x, y, width, height,
            tlColor, trColor, blColor, brColor);
    }

    /**
     * Draw a filled rectangle with 4 independent corner colors using a custom pipeline.
     */
    public static void drawRect4C(
        GuiGraphicsExtractor gui,
        RenderPipeline pipeline,
        int x, int y, int width, int height,
        int tlColor, int trColor, int blColor, int brColor
    ) {
        if (width <= 0 || height <= 0) return;
        gui.submitGuiElementRenderState(
            new MultiColorRectState(
                pipeline, NO_TEXTURE, new Matrix3x2f(gui.pose()),
                x, y, x + width, y + height,
                tlColor, trColor, blColor, brColor,
                gui.peekScissorStack()
            )
        );
    }

    /**
     * Draw a filled rectangle with a vertical gradient (top → bottom).
     */
    public static void drawRectVertGrad(
        GuiGraphicsExtractor gui,
        int x, int y, int width, int height,
        int topColor, int bottomColor
    ) {
        drawRect4C(gui, x, y, width, height,
            topColor, topColor, bottomColor, bottomColor);
    }

    /**
     * Draw a filled rectangle with a horizontal gradient (left → right).
     */
    public static void drawRectHorizGrad(
        GuiGraphicsExtractor gui,
        int x, int y, int width, int height,
        int leftColor, int rightColor
    ) {
        drawRect4C(gui, x, y, width, height,
            leftColor, rightColor, leftColor, rightColor);
    }

    /**
     * Draw a filled rectangle with a uniform color.
     */
    public static void drawRect(
        GuiGraphicsExtractor gui,
        int x, int y, int width, int height, int color
    ) {
        drawRect4C(gui, x, y, width, height, color, color, color, color);
    }

    // ========================
    //  Bordered rectangle
    // ========================

    /**
     * Draw a rectangle outline with 4 independent side colors.
     * Each side (top, bottom, left, right) can have its own ARGB color.
     *
     * @param gui         the GuiGraphicsExtractor
     * @param x           left edge
     * @param y           top edge
     * @param width       outer width
     * @param height      outer height
     * @param topColor    ARGB color for the top border
     * @param bottomColor ARGB color for the bottom border
     * @param leftColor   ARGB color for the left border
     * @param rightColor  ARGB color for the right border
     * @param thickness   border thickness (1–N px)
     */
    public static void drawBorderedRect(
        GuiGraphicsExtractor gui,
        int x, int y, int width, int height,
        int topColor, int bottomColor, int leftColor, int rightColor,
        int thickness
    ) {
        if (width <= 0 || height <= 0 || thickness <= 0) return;

        // Top border
        gui.submitGuiElementRenderState(
            new MultiColorRectState(
                GUI_PIPELINE, NO_TEXTURE, new Matrix3x2f(gui.pose()),
                x, y, x + width, y + thickness,
                leftColor, rightColor, leftColor, rightColor,
                gui.peekScissorStack()
            )
        );

        // Bottom border
        gui.submitGuiElementRenderState(
            new MultiColorRectState(
                GUI_PIPELINE, NO_TEXTURE, new Matrix3x2f(gui.pose()),
                x, y + height - thickness, x + width, y + height,
                leftColor, rightColor, leftColor, rightColor,
                gui.peekScissorStack()
            )
        );

        // Left border (minus top/bottom overlap)
        gui.submitGuiElementRenderState(
            new MultiColorRectState(
                GUI_PIPELINE, NO_TEXTURE, new Matrix3x2f(gui.pose()),
                x, y + thickness, x + thickness, y + height - thickness,
                topColor, topColor, bottomColor, bottomColor,
                gui.peekScissorStack()
            )
        );

        // Right border (minus top/bottom overlap)
        gui.submitGuiElementRenderState(
            new MultiColorRectState(
                GUI_PIPELINE, NO_TEXTURE, new Matrix3x2f(gui.pose()),
                x + width - thickness, y + thickness, x + width, y + height - thickness,
                topColor, topColor, bottomColor, bottomColor,
                gui.peekScissorStack()
            )
        );
    }

    /**
     * Draw a rectangle outline with a uniform color on all 4 sides.
     */
    public static void drawOutlinedRect(
        GuiGraphicsExtractor gui,
        int x, int y, int width, int height,
        int color, int thickness
    ) {
        drawBorderedRect(gui, x, y, width, height,
            color, color, color, color, thickness);
    }

    // ========================
    //  Filled + bordered
    // ========================

    /**
     * Draw a filled rectangle with independently colored borders on each side.
     *
     * @param fillColor   ARGB color for the interior fill
     * @param topColor    ARGB for top border
     * @param bottomColor ARGB for bottom border
     * @param leftColor   ARGB for left border
     * @param rightColor  ARGB for right border
     * @param thickness   border thickness in px
     */
    public static void drawFilledBorderedRect(
        GuiGraphicsExtractor gui,
        int x, int y, int width, int height,
        int fillColor,
        int topColor, int bottomColor, int leftColor, int rightColor,
        int thickness
    ) {
        if (width <= 0 || height <= 0) return;

        if ((fillColor >>> 24) != 0) {
            drawRect(gui, x + thickness, y + thickness,
                width - thickness * 2, height - thickness * 2, fillColor);
        }

        drawBorderedRect(gui, x, y, width, height,
            topColor, bottomColor, leftColor, rightColor, thickness);
    }

    /**
     * Draw a filled rectangle with a uniform border.
     */
    public static void drawFilledBorderedRect(
        GuiGraphicsExtractor gui,
        int x, int y, int width, int height,
        int fillColor, int borderColor, int thickness
    ) {
        drawFilledBorderedRect(gui, x, y, width, height,
            fillColor,
            borderColor, borderColor, borderColor, borderColor,
            thickness);
    }

    // ========================
    //  Split-color rectangle (quarters)
    // ========================

    /**
     * Draw a rectangle split into 4 quadrants, each with an independent color.
     *
     * <pre>
     * +--------+--------+
     * |   TL   |   TR   |
     * +--------+--------+
     * |   BL   |   BR   |
     * +--------+--------+
     * </pre>
     */
    public static void drawSplitRect4(
        GuiGraphicsExtractor gui,
        int x, int y, int width, int height,
        int tlColor, int trColor, int blColor, int brColor
    ) {
        if (width <= 0 || height <= 0) return;

        int hw = width / 2;
        int hh = height / 2;

        drawRect(gui, x, y, hw, hh, tlColor);
        drawRect(gui, x + hw, y, width - hw, hh, trColor);
        drawRect(gui, x, y + hh, hw, height - hh, blColor);
        drawRect(gui, x + hw, y + hh, width - hw, height - hh, brColor);
    }
}
