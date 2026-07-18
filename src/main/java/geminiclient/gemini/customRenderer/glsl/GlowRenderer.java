package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GLSL-powered glow / drop-shadow renderer.
 * <p>
 * Uses {@code glow_rect.fsh} — a procedural Gaussian falloff shader that
 * computes the glow intensity from UV distance. No textures required.
 * <p>
 * Source-compatible replacement for the previous Skija-based implementation.
 */
public class GlowRenderer {

    // ========================
    //  GlowSigma enum
    // ========================

    public enum GlowSigma {
        SHARP(0.06f),
        MEDIUM(0.12f),
        SOFT(0.20f),
        BROAD(0.30f);

        public final float value;
        GlowSigma(float value) { this.value = value; }
    }

    // ========================
    //  Configurable parameters
    // ========================

    /** Baked into the shader as GLOW_SIGMA — kept for API compatibility. */
    public static float defaultGlowSigma = GlowSigma.MEDIUM.value;
    public static float defaultShadowSigma = 0.10f;

    // ========================
    //  Pipeline
    // ========================

    /**
     * Procedural rectangular glow pipeline.
     * Fragment shader computes Gaussian falloff purely from UV coordinates.
     */
    public static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/glow_rect"))
            .withVertexShader(getIdentifier("core/glow_rect"))
            .withFragmentShader(getIdentifier("core/glow_rect"))
            .withShaderDefine("GLOW_SIGMA", 0.12F)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    private static final TextureSetup NO_TEXTURE = TextureSetup.noTexture();
    private static final Matrix3x2f IDENTITY = new Matrix3x2f();

    // ========================
    //  Pipeline registration
    // ========================

    public static void registerPipelines(java.util.function.Consumer<RenderPipeline> registry) {
        registry.accept(GLOW_PIPELINE);
    }

    // ========================
    //  Public: glow rect
    // ========================

    public static void drawGlowRect(GuiGraphicsExtractor gui, int x, int y,
                                     int width, int height, int spread, int color) {
        drawGlowQuad(gui, x, y, width, height, 0, 0, spread, color);
    }

    public static void drawGlowRect(GuiGraphicsExtractor gui, int x, int y,
                                     int width, int height, int spread, int color,
                                     RenderPipeline pipeline) {
        drawGlowQuad(gui, x, y, width, height, 0, 0, spread, color);
    }

    public static void drawGlowRect(GuiGraphicsExtractor gui, int x, int y,
                                     int width, int height, int spread, int color,
                                     GlowSigma sigma) {
        drawGlowQuad(gui, x, y, width, height, 0, 0, spread, color);
    }

    // ========================
    //  Public: drop shadow
    // ========================

    public static void drawDropShadow(GuiGraphicsExtractor gui, int x, int y,
                                       int width, int height,
                                       int offsetX, int offsetY,
                                       int spread, int color) {
        drawGlowQuad(gui, x, y, width, height, offsetX, offsetY, spread, color);
    }

    public static void drawDropShadow(GuiGraphicsExtractor gui, int x, int y,
                                       int width, int height,
                                       int offset, int spread, int color) {
        drawGlowQuad(gui, x, y, width, height, offset, offset, spread, color);
    }

    // ========================
    //  Public: rounded glow
    // ========================

    public static void drawGlowRoundedRect(GuiGraphicsExtractor gui,
                                            int x, int y, int width, int height,
                                            int cornerRadius,
                                            int spread, int color) {
        drawGlowQuad(gui, x, y, width, height, 0, 0, spread, color);
    }

    public static void drawGlowRoundedRect(GuiGraphicsExtractor gui,
                                            int x, int y, int width, int height,
                                            int cornerRadius,
                                            int spread, int color,
                                            RenderPipeline pipeline) {
        drawGlowQuad(gui, x, y, width, height, 0, 0, spread, color);
    }

    // ========================
    //  Public: rounded drop shadow
    // ========================

    /**
     * Drop shadow behind a rounded rectangle.
     *
     * <p>Delegated to {@link SdfUIRenderer#drawShadow}: the penumbra is
     * evaluated against an exact rounded-box SDF, so the shadow hugs the
     * corner radius. The legacy implementation ignored {@code cornerRadius}
     * and used a rectangular UV-distance glow, which left dark squared-off
     * corners visible outside rounded cards.</p>
     */
    public static void drawDropShadowRoundedRect(GuiGraphicsExtractor gui,
                                                  int x, int y, int width, int height,
                                                  int cornerRadius,
                                                  int offsetX, int offsetY,
                                                  int spread, int color) {
        SdfUIRenderer.drawShadow(gui, x, y, width, height, cornerRadius,
                offsetX, offsetY, spread, color);
    }

    // ========================
    //  Core rendering
    // ========================

    /**
     * Renders a procedural glow/drop-shadow quad.
     * <p>
     * The quad is expanded by {@code spread} beyond the element bounds,
     * and UVs are mapped so that [0,1] covers the element interior.
     * The fragment shader applies Gaussian falloff for UVs outside [0,1].
     *
     * @param gui    GUI graphics handle
     * @param x, y   element position (before offset)
     * @param w, h   element size
     * @param ox, oy shadow offset from element position
     * @param spread glow spread radius in pixels
     * @param color  ARGB glow tint (alpha controls intensity)
     */
    private static void drawGlowQuad(GuiGraphicsExtractor gui,
                                      int x, int y, int w, int h,
                                      int ox, int oy, int spread, int color) {
        if (w <= 0 || h <= 0 || spread <= 0) return;
        int a = (color >>> 24) & 0xFF;
        if (a == 0) return;

        // Element position after offset
        int ex = x + ox;
        int ey = y + oy;

        // Expanded quad covers element + glow spread
        float qx0 = ex - spread;
        float qy0 = ey - spread;
        float qx1 = ex + w + spread;
        float qy1 = ey + h + spread;

        // UV: [0,1] maps to the element rect.
        // UV < 0 or UV > 1 is the glow spread region.
        float u0 = (qx0 - ex) / (float) w;
        float v0 = (qy0 - ey) / (float) h;
        float u1 = (qx1 - ex) / (float) w;
        float v1 = (qy1 - ey) / (float) h;

        ScreenRectangle scissor = gui.peekScissorStack();
        gui.submitGuiElementRenderState(new GlowQuadState(
                GLOW_PIPELINE, NO_TEXTURE, IDENTITY,
                qx0, qy0, qx1, qy1,
                u0, v0, u1, v1,
                color, scissor));
    }

    // ========================
    //  Render state
    // ========================

    private static final class GlowQuadState implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final Matrix3x2f pose;
        private final float x0, y0, x1, y1;
        private final float u0, v0, u1, v1;
        private final int color;
        @Nullable private final ScreenRectangle scissor;
        @Nullable private final ScreenRectangle bounds;

        GlowQuadState(RenderPipeline pipeline, TextureSetup textureSetup,
                      Matrix3x2f pose,
                      float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1,
                      int color, @Nullable ScreenRectangle scissor) {
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.pose = pose;
            this.x0 = x0; this.y0 = y0;
            this.x1 = x1; this.y1 = y1;
            this.u0 = u0; this.v0 = v0;
            this.u1 = u1; this.v1 = v1;
            this.color = color;
            this.scissor = scissor;

            int ix = (int) Math.floor(x0);
            int iy = (int) Math.floor(y0);
            int iw = (int) Math.ceil(x1) - ix;
            int ih = (int) Math.ceil(y1) - iy;
            ScreenRectangle b = new ScreenRectangle(
                    ix, iy, Math.max(1, iw), Math.max(1, ih));
            this.bounds = scissor != null ? scissor.intersection(b) : b;
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            vc.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
            vc.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
            vc.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
            vc.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
        }

        @Override public @NonNull RenderPipeline pipeline() { return pipeline; }
        @Override public @NonNull TextureSetup textureSetup() { return textureSetup; }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
    }
}
