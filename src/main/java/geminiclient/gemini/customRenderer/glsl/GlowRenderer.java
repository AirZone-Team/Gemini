package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

public class GlowRenderer {

    public enum GlowSigma {
        SHARP(0.06f),
        MEDIUM(0.12f),
        SOFT(0.20f),
        BROAD(0.30f);

        public final float value;
        GlowSigma(float value) { this.value = value; }
    }

    public static final RenderPipeline GLOW_RECT = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/glow_rect"))
            .withVertexShader(getIdentifier("core/glow_rect"))
            .withFragmentShader(getIdentifier("core/glow_rect"))
            .withColorTargetState(new ColorTargetState(new BlendFunction(
                    SourceFactor.SRC_ALPHA, DestFactor.ONE,
                    SourceFactor.ONE, DestFactor.ZERO)))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    public static final RenderPipeline GLOW_RECT_ALPHA = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/glow_rect_alpha"))
            .withVertexShader(getIdentifier("core/glow_rect"))
            .withFragmentShader(getIdentifier("core/glow_rect"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    public static final RenderPipeline SHADOW_RECT = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/shadow_rect"))
            .withVertexShader(getIdentifier("core/glow_rect"))
            .withFragmentShader(getIdentifier("core/shadow_rect"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    private static final TextureSetup NO_TEXTURE = TextureSetup.noTexture();
    private static final Matrix3x2f IDENTITY = new Matrix3x2f();

    public static void registerPipelines(Consumer<RenderPipeline> registry) {
        registry.accept(GLOW_RECT);
        registry.accept(GLOW_RECT_ALPHA);
        registry.accept(SHADOW_RECT);
    }

    // ========================
    //  Public: glow rect
    // ========================

    public static void drawGlowRect(GuiGraphicsExtractor gui, int x, int y, int width, int height, int spread, int color) {
        drawGlowRect(gui, x, y, width, height, spread, color, GLOW_RECT);
    }

    public static void drawGlowRect(GuiGraphicsExtractor gui, int x, int y, int width, int height, int spread, int color, RenderPipeline pipeline) {
        if (width <= 0 || height <= 0 || spread <= 0) return;
        int a = (color >>> 24) & 0xFF;
        if (a == 0) return;

        float invW = 1f / width;
        float invH = 1f / height;

        float u0 = -spread * invW;
        float v0 = -spread * invH;
        float u1 = 1f + spread * invW;
        float v1 = 1f + spread * invH;

        int gx = x - spread;
        int gy = y - spread;
        int gw = width + spread * 2;
        int gh = height + spread * 2;

        ScreenRectangle scissor = gui.peekScissorStack();
        Matrix3x2f pose = new Matrix3x2f(gui.pose());

        gui.submitGuiElementRenderState(new GlowRectState(
                pipeline, NO_TEXTURE, pose,
                gx, gy, gx + gw, gy + gh,
                u0, v0, u1, v1,
                color, scissor
        ));
    }

    public static void drawGlowRect(GuiGraphicsExtractor gui, int x, int y, int width, int height, int spread, int color, GlowSigma sigma) {
        drawGlowRect(gui, x, y, width, height, spread, color, GLOW_RECT);
    }

    // ========================
    //  Public: drop shadow
    // ========================

    public static void drawDropShadow(GuiGraphicsExtractor gui, int x, int y, int width, int height, int offsetX, int offsetY, int spread, int color) {
        if (width <= 0 || height <= 0 || spread <= 0) return;
        int a = (color >>> 24) & 0xFF;
        if (a == 0) return;

        float invW = 1f / width;
        float invH = 1f / height;

        float u0 = -(spread + offsetX) * invW;
        float v0 = -(spread + offsetY) * invH;
        float u1 = 1f + (spread - offsetX) * invW;
        float v1 = 1f + (spread - offsetY) * invH;

        int sx = x + offsetX - spread;
        int sy = y + offsetY - spread;
        int sw = width + spread * 2;
        int sh = height + spread * 2;

        ScreenRectangle scissor = gui.peekScissorStack();
        Matrix3x2f pose = new Matrix3x2f(gui.pose());

        gui.submitGuiElementRenderState(new GlowRectState(
                SHADOW_RECT, NO_TEXTURE, pose,
                sx, sy, sx + sw, sy + sh,
                u0, v0, u1, v1,
                color, scissor
        ));
    }

    public static void drawDropShadow(GuiGraphicsExtractor gui, int x, int y, int width, int height, int offset, int spread, int color) {
        drawDropShadow(gui, x, y, width, height, offset, offset, spread, color);
    }

    // ========================
    //  Public: rounded drop shadow (NEW)
    // ========================

    /**
     * Draw a drop shadow behind a rounded rectangle by physically offsetting
     * a rounded shadow render state.
     */
    public static void drawDropShadowRoundedRect(GuiGraphicsExtractor gui,
                                                 int x, int y, int width, int height,
                                                 int cornerRadius, int offsetX, int offsetY,
                                                 int spread, int color) {
        // We reuse the rounded glow logic but shift the coordinates and use the SHADOW_RECT pipeline
        drawGlowRoundedRect(gui, x + offsetX, y + offsetY, width, height, cornerRadius, spread, color, SHADOW_RECT);
    }

    // ========================
    //  Public: rounded glow
    // ========================

    public static void drawGlowRoundedRect(GuiGraphicsExtractor gui,
                                           int x, int y, int width, int height,
                                           int cornerRadius,
                                           int spread, int color,
                                           RenderPipeline pipeline) {
        if (width <= 0 || height <= 0 || spread <= 0) return;
        int a = (color >>> 24) & 0xFF;
        if (a == 0) return;

        int r = Math.min(cornerRadius, Math.min(width, height) / 2);
        float invS = 1f / (r + spread);

        int gx = x - spread;
        int gy = y - spread;
        int gw = width + spread * 2;
        int gh = height + spread * 2;
        int x1 = x + width;
        int y1 = y + height;

        ScreenRectangle scissor = gui.peekScissorStack();
        Matrix3x2f pose = new Matrix3x2f(gui.pose());

        int cornerSize = r + spread;

        drawGlowQuad(gui, pipeline, pose, scissor, color,
                x + r, y - spread, width - r * 2, cornerSize,
                0, -spread * invS, 1, 1, scissor);

        drawGlowQuad(gui, pipeline, pose, scissor, color,
                x + r, y1 - r, width - r * 2, cornerSize,
                0, 0, 1, 1 + spread * invS, scissor);

        drawGlowQuad(gui, pipeline, pose, scissor, color,
                x - spread, y + r, cornerSize, height - r * 2,
                -spread * invS, 0, 1, 1, scissor);

        drawGlowQuad(gui, pipeline, pose, scissor, color,
                x1 - r, y + r, cornerSize, height - r * 2,
                0, 0, 1 + spread * invS, 1, scissor);

        drawGlowQuad(gui, pipeline, pose, scissor, color,
                x - spread, y - spread, cornerSize, cornerSize,
                -spread * invS, -spread * invS, 1, 1, scissor);
        drawGlowQuad(gui, pipeline, pose, scissor, color,
                x1 - r, y - spread, cornerSize, cornerSize,
                0, -spread * invS, 1 + spread * invS, 1, scissor);
        drawGlowQuad(gui, pipeline, pose, scissor, color,
                x1 - r, y1 - r, cornerSize, cornerSize,
                0, 0, 1 + spread * invS, 1 + spread * invS, scissor);
        drawGlowQuad(gui, pipeline, pose, scissor, color,
                x - spread, y1 - r, cornerSize, cornerSize,
                -spread * invS, 0, 1, 1 + spread * invS, scissor);
    }

    public static void drawGlowRoundedRect(GuiGraphicsExtractor gui, int x, int y, int width, int height, int cornerRadius, int spread, int color) {
        drawGlowRoundedRect(gui, x, y, width, height, cornerRadius, spread, color, GLOW_RECT);
    }

    // ========================
    //  Internal helpers
    // ========================

    private static void drawGlowQuad(GuiGraphicsExtractor gui, RenderPipeline pipeline, Matrix3x2f pose,
                                     @Nullable ScreenRectangle scissor, int color,
                                     int qx, int qy, int qw, int qh,
                                     float u0, float v0, float u1, float v1,
                                     @Nullable ScreenRectangle elementScissor) {
        if (qw <= 0 || qh <= 0) return;
        @Nullable ScreenRectangle effectiveScissor = elementScissor != null ? elementScissor : scissor;
        gui.submitGuiElementRenderState(new GlowRectState(
                pipeline, NO_TEXTURE, pose, qx, qy, qx + qw, qy + qh, u0, v0, u1, v1, color, effectiveScissor
        ));
    }

    // ========================
    //  Bloom overlay (texture-based)
    // ========================

    /**
     * Draw a bloom overlay from a pre-processed {@link NativeImage}.
     * The image is uploaded to a temporary {@link DynamicTexture}
     * and rendered as a textured quad.
     *
     * @param bloomImage pre-blurred bright-extraction image (ownership
     *                   transfers — this method closes it)
     */
    public static void drawBloomOverlay(GuiGraphicsExtractor gui,
                                        NativeImage bloomImage,
                                        int x, int y, int width, int height) {
        if (bloomImage == null || width <= 0 || height <= 0) {
            if (bloomImage != null) bloomImage.close();
            return;
        }

        String texKey = "bloom_" + System.identityHashCode(bloomImage);
        DynamicTexture tex = new DynamicTexture(() -> texKey, bloomImage);
        var identifier = getIdentifier(texKey);

        mc.getTextureManager().register(identifier, tex);

        TextureSetup setup = TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler());
        ScreenRectangle scissor = gui.peekScissorStack();
        gui.submitGuiElementRenderState(new BloomTextureState(
                RenderPipelines.GUI_TEXTURED, setup,
                x, y, x + width, y + height, scissor));

        tex.close();
        mc.getTextureManager().release(identifier);
        bloomImage.close();
    }

    private static final class BloomTextureState implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final float x0, y0, x1, y1;
        @Nullable private final ScreenRectangle scissor;
        @Nullable private final ScreenRectangle bounds;

        BloomTextureState(RenderPipeline pipeline, TextureSetup textureSetup,
                         float x0, float y0, float x1, float y1,
                         @Nullable ScreenRectangle scissor) {
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.x0 = x0; this.y0 = y0;
            this.x1 = x1; this.y1 = y1;
            this.scissor = scissor;
            int ix = (int) Math.floor(x0), iy = (int) Math.floor(y0);
            int iw = (int) Math.ceil(x1) - ix, ih = (int) Math.ceil(y1) - iy;
            ScreenRectangle b = new ScreenRectangle(ix, iy, iw, ih);
            this.bounds = scissor != null ? scissor.intersection(b) : b;
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            vc.addVertexWith2DPose(IDENTITY, x0, y0).setUv(0f, 0f).setColor(255, 255, 255, 255);
            vc.addVertexWith2DPose(IDENTITY, x0, y1).setUv(0f, 1f).setColor(255, 255, 255, 255);
            vc.addVertexWith2DPose(IDENTITY, x1, y1).setUv(1f, 1f).setColor(255, 255, 255, 255);
            vc.addVertexWith2DPose(IDENTITY, x1, y0).setUv(1f, 0f).setColor(255, 255, 255, 255);
        }

        @Override public @NonNull RenderPipeline pipeline() { return pipeline; }
        @Override public @NonNull TextureSetup textureSetup() { return textureSetup; }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
    }

    private static final class GlowRectState implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final Matrix3x2f pose;
        private final float x0, y0, x1, y1;
        private final float u0, v0, u1, v1;
        private final int color;
        @Nullable private final ScreenRectangle scissor;
        @Nullable private final ScreenRectangle bounds;

        GlowRectState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                      float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                      int color, @Nullable ScreenRectangle scissor) {
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.pose = pose;
            if (x0 > x1) { float t = x0; x0 = x1; x1 = t; }
            if (y0 > y1) { float t = y0; y0 = y1; y1 = t; }
            this.x0 = x0; this.y0 = y0;
            this.x1 = x1; this.y1 = y1;
            this.u0 = u0; this.v0 = v0;
            this.u1 = u1; this.v1 = v1;
            this.color = color;
            this.scissor = scissor;

            int ix = (int) Math.floor(x0), iy = (int) Math.floor(y0);
            int iw = (int) Math.ceil(x1) - ix, ih = (int) Math.ceil(y1) - iy;
            ScreenRectangle b = new ScreenRectangle(ix, iy, iw, ih).transformMaxBounds(pose);
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