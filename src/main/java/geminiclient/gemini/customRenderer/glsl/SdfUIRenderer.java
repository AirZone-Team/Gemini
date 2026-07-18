package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * SDF-based rounded-rectangle / rounded-shadow renderer with proper
 * anti-aliasing ({@code fwidth}-wide smooth edges).
 *
 * <p>Replaces the CPU triangle-fan tessellation previously used for rounded
 * rects (hard polygon edges) and the legacy UV-rectangle glow for drop
 * shadows (which squared off at rounded corners, producing dark "corner"
 * artifacts behind rounded cards).</p>
 *
 * <p>Per-element parameters are carried in a custom vertex format because
 * the GUI render-state API does not expose per-element uniform buffers:</p>
 * <ul>
 *   <li>{@code Position} — quad corner (expanded past the element to fit the
 *       AA margin / shadow penumbra)</li>
 *   <li>{@code Color} — per-corner ARGB (fill gradients interpolate for free)</li>
 *   <li>{@code UV0} — element-local pixel coordinates</li>
 *   <li>{@code UV1} — (width, height) in pixels</li>
 *   <li>{@code UV2} — (corner radius, aux): aux = 0 fill, &gt;0 border
 *       thickness, or Gaussian sigma for the shadow pipeline</li>
 * </ul>
 */
public final class SdfUIRenderer {

    private SdfUIRenderer() {}

    // ========================
    //  Vertex format / pipelines
    // ========================

    /** Margin (GUI px) the fill/outline quad is expanded by so the AA ramp fits inside the quad. */
    private static final float AA_MARGIN = 1.5f;

    public static final VertexFormat SDF_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .build();

    /** Anti-aliased rounded-rect fill / border pipeline. */
    public static final RenderPipeline SDF_RECT_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/sdf_rounded_rect"))
            .withVertexShader(getIdentifier("core/sdf_rounded"))
            .withFragmentShader(getIdentifier("core/sdf_rounded_rect"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(SDF_FORMAT, VertexFormat.Mode.QUADS)
            .withCull(false)
            .build();

    /** Gaussian penumbra shadow that follows the corner radius. */
    public static final RenderPipeline SDF_SHADOW_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/sdf_rounded_shadow"))
            .withVertexShader(getIdentifier("core/sdf_rounded"))
            .withFragmentShader(getIdentifier("core/sdf_rounded_shadow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(SDF_FORMAT, VertexFormat.Mode.QUADS)
            .withCull(false)
            .build();

    /** Material 3 波浪圆形进度带（仅进度带；平坦轨道用 {@link #drawRing} 绘制）。 */
    public static final RenderPipeline SDF_WAVY_RING_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/sdf_wavy_ring"))
            .withVertexShader(getIdentifier("core/sdf_rounded"))
            .withFragmentShader(getIdentifier("core/sdf_wavy_ring"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(SDF_FORMAT, VertexFormat.Mode.QUADS)
            .withCull(false)
            .build();

    /**
     * 波浪振幅（GUI px）—— 进度带中线沿径向的 excursion。
     * 必须与 {@code core/sdf_wavy_ring.fsh} 的 {@code AMPLITUDE} 常量保持一致：
     * Java 端用它计算 quad 包围盒，着色器端用它偏移中线。
     */
    public static final float WAVE_AMPLITUDE = 1.2f;

    /** 每圈波浪数 —— 必须与 {@code core/sdf_wavy_ring.fsh} 的 {@code WAVES} 一致（整数保证接缝连续）。 */
    public static final int WAVE_COUNT = 10;

    private static final TextureSetup NO_TEXTURE = TextureSetup.noTexture();

    public static void registerPipelines(Consumer<RenderPipeline> registry) {
        registry.accept(SDF_RECT_PIPELINE);
        registry.accept(SDF_SHADOW_PIPELINE);
        registry.accept(SDF_WAVY_RING_PIPELINE);
    }

    // ========================
    //  Public draw API
    // ========================

    /** Filled rounded rect, single colour. */
    public static void drawRect(GuiGraphicsExtractor gui, int x, int y, int w, int h,
                                int r, int color) {
        drawRect(gui, x, y, w, h, r, color, color, color, color);
    }

    /**
     * Filled rounded rect with per-corner colours (bilinear gradient).
     * Corner colour order matches the quad vertices: TL, TR, BL, BR.
     */
    public static void drawRect(GuiGraphicsExtractor gui, int x, int y, int w, int h,
                                int r, int tlColor, int trColor, int blColor, int brColor) {
        if (w <= 0 || h <= 0) return;
        if (((tlColor >>> 24) == 0) && ((trColor >>> 24) == 0)
                && ((blColor >>> 24) == 0) && ((brColor >>> 24) == 0)) return;

        float qx0 = x - AA_MARGIN, qy0 = y - AA_MARGIN;
        float qx1 = x + w + AA_MARGIN, qy1 = y + h + AA_MARGIN;

        gui.submitGuiElementRenderState(new SdfQuadState(
                SDF_RECT_PIPELINE, new Matrix3x2f(gui.pose()),
                qx0, qy0, qx1, qy1,
                -AA_MARGIN, -AA_MARGIN, w + AA_MARGIN, h + AA_MARGIN,
                w, h, r, 0,
                tlColor, trColor, blColor, brColor,
                gui.peekScissorStack()));
    }

    /** Anti-aliased inner border ring (ring occupies dist ∈ [-thickness, 0]). */
    public static void drawOutline(GuiGraphicsExtractor gui, int x, int y, int w, int h,
                                   int r, int color, int thickness) {
        if (w <= 0 || h <= 0 || thickness <= 0) return;
        if ((color >>> 24) == 0) return;

        float qx0 = x - AA_MARGIN, qy0 = y - AA_MARGIN;
        float qx1 = x + w + AA_MARGIN, qy1 = y + h + AA_MARGIN;

        gui.submitGuiElementRenderState(new SdfQuadState(
                SDF_RECT_PIPELINE, new Matrix3x2f(gui.pose()),
                qx0, qy0, qx1, qy1,
                -AA_MARGIN, -AA_MARGIN, w + AA_MARGIN, h + AA_MARGIN,
                w, h, r, thickness,
                color, color, color, color,
                gui.peekScissorStack()));
    }

    /**
     * Filled circle of diameter {@code d} centred on (cx, cy).
     *
     * <p>The centre is float-precise; the diameter stays integer because the
     * SDF vertex format carries element size as SHORT2. The fragment shader
     * clamps the corner radius to half the element size, so any square quad
     * with radius ≥ d/2 rasterises as an exact circle.</p>
     */
    public static void drawCircle(GuiGraphicsExtractor gui, float cx, float cy, int d, int color) {
        if (d <= 0) return;
        if ((color >>> 24) == 0) return;

        float half = d / 2f;
        float qx0 = cx - half - AA_MARGIN, qy0 = cy - half - AA_MARGIN;
        float qx1 = cx + half + AA_MARGIN, qy1 = cy + half + AA_MARGIN;

        gui.submitGuiElementRenderState(new SdfQuadState(
                SDF_RECT_PIPELINE, new Matrix3x2f(gui.pose()),
                qx0, qy0, qx1, qy1,
                -AA_MARGIN, -AA_MARGIN, d + AA_MARGIN, d + AA_MARGIN,
                d, d, (d + 1) / 2, 0,
                color, color, color, color,
                gui.peekScissorStack()));
    }

    /**
     * Circular ring band centred on {@code radius} px around (cx, cy),
     * {@code thickness} px wide with both edges anti-aliased — the circular
     * specialisation of {@link #drawOutline}.
     */
    public static void drawRing(GuiGraphicsExtractor gui, float cx, float cy,
                                int radius, int thickness, int color) {
        if (radius <= 0 || thickness <= 0) return;
        if ((color >>> 24) == 0) return;

        int outer = radius + (thickness + 1) / 2;
        int d = outer * 2;
        float qx0 = cx - outer - AA_MARGIN, qy0 = cy - outer - AA_MARGIN;
        float qx1 = cx + outer + AA_MARGIN, qy1 = cy + outer + AA_MARGIN;

        gui.submitGuiElementRenderState(new SdfQuadState(
                SDF_RECT_PIPELINE, new Matrix3x2f(gui.pose()),
                qx0, qy0, qx1, qy1,
                -AA_MARGIN, -AA_MARGIN, d + AA_MARGIN, d + AA_MARGIN,
                d, d, outer, thickness,
                color, color, color, color,
                gui.peekScissorStack()));
    }

    /**
     * Material 3 Expressive 圆形波浪进度带（仅进度带；平坦轨道用 {@link #drawRing} 绘制）。
     *
     * <p>几何：中线半径 {@code midRadius}、带厚 {@code thickness}（标称内径
     * midRadius−thickness/2 / 外径 midRadius+thickness/2），波浪中线沿径向
     * ±{@link #WAVE_AMPLITUDE} px 余弦调制，每圈 {@link #WAVE_COUNT} 个波。
     * 弧自 12 点方向起顺时针扫描至 {@code progress}·360°，两端为圆角帽。
     * 完整参数语义见 {@code core/sdf_wavy_ring.fsh} 头部注释。</p>
     *
     * <p>颜色为垂直双线性渐变：{@code topColor}（12 点，弧起始端）→
     * {@code bottomColor}（6 点）。进度以弧长表达，渐变仅作装饰。</p>
     *
     * @param progress   0..1；≤0.001 时不绘制（避免在 12 点留下圆点）
     * @param phase01    波浪相位 0..1（映射 0..2π），由调用方按时间推进；
     *                   相位增长 → 波峰顺时针漂移（与填充同向）
     */
    public static void drawWavyRing(GuiGraphicsExtractor gui, float cx, float cy,
                                    int midRadius, int thickness,
                                    float progress, float phase01,
                                    int topColor, int bottomColor) {
        if (midRadius <= 0 || thickness <= 0 || progress <= 0.001f) return;
        if (((topColor >>> 24) == 0) && ((bottomColor >>> 24) == 0)) return;

        float half = midRadius + thickness / 2f + WAVE_AMPLITUDE;
        float side = half * 2;
        float qx0 = cx - half - AA_MARGIN, qy0 = cy - half - AA_MARGIN;
        float qx1 = cx + half + AA_MARGIN, qy1 = cy + half + AA_MARGIN;

        int progressShort = (int) (Math.min(1f, Math.max(0f, progress)) * 10000);
        int phaseShort    = (int) (Math.min(1f, Math.max(0f, phase01)) * 32767);

        gui.submitGuiElementRenderState(new SdfQuadState(
                SDF_WAVY_RING_PIPELINE, new Matrix3x2f(gui.pose()),
                qx0, qy0, qx1, qy1,
                -AA_MARGIN, -AA_MARGIN, side + AA_MARGIN, side + AA_MARGIN,
                midRadius, thickness, progressShort, phaseShort,
                topColor, topColor, bottomColor, bottomColor,
                gui.peekScissorStack()));
    }

    /**
     * Rounded drop shadow with a Gaussian penumbra that hugs the corner radius.
     *
     * @param offsetX, offsetY shadow offset from the element position
     * @param spread           nominal blur extent in pixels (σ ≈ spread / 3)
     * @param color            ARGB shadow tint (alpha = peak opacity)
     */
    public static void drawShadow(GuiGraphicsExtractor gui, int x, int y, int w, int h,
                                  int r, int offsetX, int offsetY, int spread, int color) {
        if (w <= 0 || h <= 0 || spread <= 0) return;
        if ((color >>> 24) == 0) return;

        int sigma = Math.max(1, Math.round(spread / 3.0f));
        int expand = sigma * 3;

        float ex = x + offsetX, ey = y + offsetY;
        float qx0 = ex - expand, qy0 = ey - expand;
        float qx1 = ex + w + expand, qy1 = ey + h + expand;

        gui.submitGuiElementRenderState(new SdfQuadState(
                SDF_SHADOW_PIPELINE, new Matrix3x2f(gui.pose()),
                qx0, qy0, qx1, qy1,
                -expand, -expand, w + expand, h + expand,
                w, h, r, sigma,
                color, color, color, color,
                gui.peekScissorStack()));
    }

    // ========================
    //  Render state
    // ========================

    private static final class SdfQuadState implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final Matrix3x2f pose;
        private final float x0, y0, x1, y1;
        private final float u0, v0, u1, v1;
        private final int w, h, radius, aux;
        private final int tlColor, trColor, blColor, brColor;
        @Nullable private final ScreenRectangle scissor;
        @Nullable private final ScreenRectangle bounds;

        SdfQuadState(RenderPipeline pipeline, Matrix3x2f pose,
                     float x0, float y0, float x1, float y1,
                     float u0, float v0, float u1, float v1,
                     int w, int h, int radius, int aux,
                     int tlColor, int trColor, int blColor, int brColor,
                     @Nullable ScreenRectangle scissor) {
            this.pipeline = pipeline;
            this.pose = pose;
            this.x0 = x0; this.y0 = y0;
            this.x1 = x1; this.y1 = y1;
            this.u0 = u0; this.v0 = v0;
            this.u1 = u1; this.v1 = v1;
            this.w = w; this.h = h;
            this.radius = radius; this.aux = aux;
            this.tlColor = tlColor; this.trColor = trColor;
            this.blColor = blColor; this.brColor = brColor;
            this.scissor = scissor;

            int ix = (int) Math.floor(x0);
            int iy = (int) Math.floor(y0);
            int iw = (int) Math.ceil(x1) - ix;
            int ih = (int) Math.ceil(y1) - iy;
            ScreenRectangle b = new ScreenRectangle(ix, iy, Math.max(1, iw), Math.max(1, ih))
                    .transformMaxBounds(pose);
            this.bounds = scissor != null ? scissor.intersection(b) : b;
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            // Vanilla quad order: TL, BL, BR, TR.
            vc.addVertexWith2DPose(pose, x0, y0).setColor(tlColor).setUv(u0, v0).setUv1(w, h).setUv2(radius, aux);
            vc.addVertexWith2DPose(pose, x0, y1).setColor(blColor).setUv(u0, v1).setUv1(w, h).setUv2(radius, aux);
            vc.addVertexWith2DPose(pose, x1, y1).setColor(brColor).setUv(u1, v1).setUv1(w, h).setUv2(radius, aux);
            vc.addVertexWith2DPose(pose, x1, y0).setColor(trColor).setUv(u1, v0).setUv1(w, h).setUv2(radius, aux);
        }

        @Override public @NonNull RenderPipeline pipeline() { return pipeline; }
        @Override public @NonNull TextureSetup textureSetup() { return NO_TEXTURE; }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
    }
}
