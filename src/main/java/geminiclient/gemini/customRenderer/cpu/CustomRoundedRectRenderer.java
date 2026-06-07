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
 * Rounded rectangle renderer using per-corner arc decomposition.
 * Each corner is approximated with triangle segments.
 */
public class CustomRoundedRectRenderer {

    private static final int ARC_SEGMENTS = 8;
    private static final TextureSetup NO_TEXTURE = TextureSetup.noTexture();
    private static final RenderPipeline GUI_PIPELINE = RenderPipelines.GUI;

    // ========================
    //  CornerArcState
    // ========================

    private static final class CornerArcState implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final Matrix3x2f pose;
        private final float cx, cy, radius;
        private final float startAngle;
        private final int color;
        private final int segments;
        @Nullable
        private final ScreenRectangle scissor;
        @Nullable
        private final ScreenRectangle bounds;

        CornerArcState(
                RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                float cx, float cy, float radius, float startAngle,
                int color, int segments, @Nullable ScreenRectangle scissor
        ) {
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.pose = pose;
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.startAngle = startAngle;
            this.color = color;
            this.segments = segments;
            this.scissor = scissor;

            // [Fix] 使用 Math.floor 防止负数截断错误
            int ir = (int) Math.ceil(radius);
            int startX = (int) Math.floor(cx) - ir;
            int startY = (int) Math.floor(cy) - ir;
            ScreenRectangle b = new ScreenRectangle(startX, startY, ir * 2, ir * 2).transformMaxBounds(pose);
            this.bounds = scissor != null ? scissor.intersection(b) : b;
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            float halfPi = (float) (Math.PI / 2.0);
            for (int i = 0; i < segments; i++) {
                float a1 = startAngle + (float) i / segments * halfPi;
                float a2 = startAngle + (float) (i + 1) / segments * halfPi;
                float x1 = cx + (float) Math.cos(a1) * radius;
                float y1 = cy + (float) Math.sin(a1) * radius;
                float x2 = cx + (float) Math.cos(a2) * radius;
                float y2 = cy + (float) Math.sin(a2) * radius;

                vc.addVertexWith2DPose(pose, cx, cy).setColor(color);
                vc.addVertexWith2DPose(pose, cx, cy).setColor(color);
                vc.addVertexWith2DPose(pose, x2, y2).setColor(color); // [优化] 调整为闭合连续顺序
                vc.addVertexWith2DPose(pose, x1, y1).setColor(color);
            }
        }

        @Override public @NonNull RenderPipeline pipeline() { return pipeline; }
        @Override public @NonNull TextureSetup textureSetup() { return textureSetup; }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
    }

    // ========================
    //  Internal helpers
    // ========================

    private static int clampRadius(int w, int h, int radius) {
        int maxR = Math.min(w, h) / 2;
        return Math.min(Math.max(0, radius), maxR);
    }

    private static void drawCorner(
            GuiGraphicsExtractor gui,
            float cx, float cy, float radius, float startAngle,
            int color
    ) {
        if (radius <= 0) return;
        gui.submitGuiElementRenderState(
                new CornerArcState(
                        GUI_PIPELINE, NO_TEXTURE, new Matrix3x2f(gui.pose()),
                        cx, cy, radius, startAngle,
                        color, ARC_SEGMENTS,
                        gui.peekScissorStack()
                )
        );
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
        int x1 = x + width, y1 = y + height;

        if (r == 0) {
            CustomRectRenderer.drawRect4C(gui, x, y, width, height, tlColor, trColor, blColor, brColor);
            return;
        }

        CustomRectRenderer.drawRect4C(gui, x + r, y + r, width - r * 2, height - r * 2, tlColor, trColor, blColor, brColor);
        CustomRectRenderer.drawRect4C(gui, x + r, y, width - r * 2, r, tlColor, trColor, tlColor, trColor);
        CustomRectRenderer.drawRect4C(gui, x + r, y1 - r, width - r * 2, r, blColor, brColor, blColor, brColor);
        CustomRectRenderer.drawRect4C(gui, x, y + r, r, height - r * 2, tlColor, tlColor, blColor, blColor);
        CustomRectRenderer.drawRect4C(gui, x1 - r, y + r, r, height - r * 2, trColor, trColor, brColor, brColor);

        drawCorner(gui, x + r, y + r, r, (float) Math.PI, tlColor);
        drawCorner(gui, x1 - r, y + r, r, (float) (Math.PI * 1.5), trColor);
        drawCorner(gui, x1 - r, y1 - r, r, 0f, brColor);
        drawCorner(gui, x + r, y1 - r, r, (float) (Math.PI / 2.0), blColor);
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
        int x1 = x + width, y1 = y + height;

        CustomRectRenderer.drawRect(gui, x + r, y, width - r * 2, t, color);
        CustomRectRenderer.drawRect(gui, x + r, y1 - t, width - r * 2, t, color);
        CustomRectRenderer.drawRect(gui, x, y + r, t, height - r * 2, color);
        CustomRectRenderer.drawRect(gui, x1 - t, y + r, t, height - r * 2, color);

        if (r > 0) {
            // [Fix] 修正了错误的注释逻辑。向内描边：outer=r, inner=r-t
            float innerR = Math.max(0, r - t);

            drawRingCorner(gui, x + r, y + r, (float) r, innerR, (float) Math.PI, color);
            drawRingCorner(gui, x1 - r, y + r, (float) r, innerR, (float) (Math.PI * 1.5), color);
            drawRingCorner(gui, x1 - r, y1 - r, (float) r, innerR, 0f, color);
            drawRingCorner(gui, x + r, y1 - r, (float) r, innerR, (float) (Math.PI / 2.0), color);
        }
    }

    private static void drawRingCorner(
            GuiGraphicsExtractor gui,
            float cx, float cy, float outerR, float innerR, float startAngle, int color
    ) {
        float halfPi = (float) (Math.PI / 2.0);
        ScreenRectangle scissor = gui.peekScissorStack();
        Matrix3x2f pose = new Matrix3x2f(gui.pose());

        for (int i = 0; i < ARC_SEGMENTS; i++) {
            float a1 = startAngle + (float) i / ARC_SEGMENTS * halfPi;
            float a2 = startAngle + (float) (i + 1) / ARC_SEGMENTS * halfPi;

            float ox1 = cx + (float) Math.cos(a1) * outerR;
            float oy1 = cy + (float) Math.sin(a1) * outerR;
            float ox2 = cx + (float) Math.cos(a2) * outerR;
            float oy2 = cy + (float) Math.sin(a2) * outerR;

            if (innerR <= 0) {
                gui.submitGuiElementRenderState(new RingSegmentState(
                        GUI_PIPELINE, NO_TEXTURE, pose,
                        cx, cy, cx, cy, ox1, oy1, ox2, oy2,
                        color, scissor
                ));
            } else {
                float ix1 = cx + (float) Math.cos(a1) * innerR;
                float iy1 = cy + (float) Math.sin(a1) * innerR;
                float ix2 = cx + (float) Math.cos(a2) * innerR;
                float iy2 = cy + (float) Math.sin(a2) * innerR;

                gui.submitGuiElementRenderState(new RingSegmentState(
                        GUI_PIPELINE, NO_TEXTURE, pose,
                        ix1, iy1, ix2, iy2, ox1, oy1, ox2, oy2,
                        color, scissor
                ));
            }
        }
    }

    private static final class RingSegmentState implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final Matrix3x2f pose;
        private final float ix1, iy1, ix2, iy2;
        private final float ox1, oy1, ox2, oy2;
        private final int color;
        @Nullable
        private final ScreenRectangle scissor;
        @Nullable
        private final ScreenRectangle bounds;

        RingSegmentState(
                RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                float ix1, float iy1, float ix2, float iy2,
                float ox1, float oy1, float ox2, float oy2,
                int color, @Nullable ScreenRectangle scissor
        ) {
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.pose = pose;
            this.ix1 = ix1; this.iy1 = iy1;
            this.ix2 = ix2; this.iy2 = iy2;
            this.ox1 = ox1; this.oy1 = oy1;
            this.ox2 = ox2; this.oy2 = oy2;
            this.color = color;
            this.scissor = scissor;

            float minX = Math.min(Math.min(ix1, ix2), Math.min(ox1, ox2));
            float minY = Math.min(Math.min(iy1, iy2), Math.min(oy1, oy2));
            float maxX = Math.max(Math.max(ix1, ix2), Math.max(ox1, ox2));
            float maxY = Math.max(Math.max(iy1, iy2), Math.max(oy1, oy2));

            // [Fix] 使用 Math.floor 修复负坐标下的强转截断导致裁切问题
            int bX = (int) Math.floor(minX);
            int bY = (int) Math.floor(minY);
            int bW = (int) Math.ceil(maxX) - bX;
            int bH = (int) Math.ceil(maxY) - bY;

            ScreenRectangle b = new ScreenRectangle(bX, bY, bW, bH).transformMaxBounds(pose);
            this.bounds = scissor != null ? scissor.intersection(b) : b;
        }

        @Override
        public void buildVertices(VertexConsumer vc) {
            // [CRITICAL FIX] 修正外圈顶点的传入顺序，解决渲染“蝴蝶结”（交叉四边形）的渲染异常
            vc.addVertexWith2DPose(pose, ix1, iy1).setColor(color);
            vc.addVertexWith2DPose(pose, ix2, iy2).setColor(color);
            vc.addVertexWith2DPose(pose, ox2, oy2).setColor(color); // 倒序传入
            vc.addVertexWith2DPose(pose, ox1, oy1).setColor(color); // 倒序传入
        }

        @Override public @NonNull RenderPipeline pipeline() { return pipeline; }
        @Override public @NonNull TextureSetup textureSetup() { return textureSetup; }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
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

        int x1 = x + width, y1 = y + height;

        int fillR = Math.max(0, r - t);
        if ((fillColor >>> 24) != 0) {
            drawRoundedRect(gui, x + t, y + t, width - t * 2, height - t * 2, fillR, fillColor);
        }

        if (t <= 0) return;

        if (r <= 0) {
            CustomRectRenderer.drawBorderedRect(gui, x, y, width, height, topBorderColor, bottomBorderColor, leftBorderColor, rightBorderColor, t);
            return;
        }

        CustomRectRenderer.drawRect(gui, x + r, y, width - r * 2, t, topBorderColor);
        CustomRectRenderer.drawRect(gui, x + r, y1 - t, width - r * 2, t, bottomBorderColor);
        CustomRectRenderer.drawRect(gui, x, y + r, t, height - r * 2, leftBorderColor);
        CustomRectRenderer.drawRect(gui, x1 - t, y + r, t, height - r * 2, rightBorderColor);

        drawRingCorner(gui, x + r, y + r, r, Math.max(0, r - t), (float) Math.PI, topBorderColor);
        drawRingCorner(gui, x1 - r, y + r, r, Math.max(0, r - t), (float) (Math.PI * 1.5), topBorderColor);
        drawRingCorner(gui, x1 - r, y1 - r, r, Math.max(0, r - t), 0f, bottomBorderColor);
        drawRingCorner(gui, x + r, y1 - r, r, Math.max(0, r - t), (float) (Math.PI / 2.0), bottomBorderColor);
    }
}