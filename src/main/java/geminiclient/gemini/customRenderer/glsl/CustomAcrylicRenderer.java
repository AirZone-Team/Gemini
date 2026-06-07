package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Random;

import static geminiclient.gemini.base.MinecraftInstance.mc;

/**
 * Custom renderer for acrylic / frosted glass material effects.
 *
 * <p>Provides Gaussian blur, noise grain generation, and composited
 * frosted-glass overlays compatible with Minecraft's
 * {@link GuiElementRenderState} pipeline.</p>
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 * // Fast frosted glass (tint + noise, no screen capture)
 * CustomAcrylicRenderer.drawFrostedRect(gui, x, y, w, h,
 *     0x801E1E2E,  // dark tint
 *     0.05f);      // subtle noise
 *
 * // Acrylic with real background blur
 * NativeImage bg = CustomAcrylicRenderer.captureBackground(x, y, w, h, 4);
 * CustomAcrylicRenderer.drawAcrylicRect(gui, x, y, w, h,
 *     bg,            // pre-blurred background
 *     0x80202030,    // tint color
 *     0.03f);        // noise opacity
 * }</pre>
 */
public class CustomAcrylicRenderer {

    private static final Random RNG = new Random();
    private static final int NOISE_TEX_SIZE = 128;
    private static final Matrix3x2f IDENTITY = new Matrix3x2f();

    // ========================
    //  GPU blur pipeline
    // ========================

    /** GPU-accelerated single-pass acrylic blur pipeline.
     *  Combines background blur + tint + noise in one shader pass.
     *  Sampler0 = background, Sampler1 = noise.
     *  Tune via shader defines: BLUR_RADIUS (default 8), NOISE_STRENGTH (default 0.05). */
    public static final RenderPipeline BLUR_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier("pipeline/acrylic_blur"))
            .withVertexShader(geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier("core/glow_gui"))
            .withFragmentShader(geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier("core/acrylic_blur"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(
                    com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
            .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
            .build();

    // --------------- cached noise texture ---------------

    /** Lazy-initialized tileable noise texture for acrylic grain. */
    private static DynamicTexture noiseTexture;
    private static TextureSetup noiseTextureSetup;

    /**
     * Returns (generating if needed) the shared noise
     * {@link TextureSetup} used for the acrylic grain overlay.
     */
    public static TextureSetup getNoiseTextureSetup() {
        if (noiseTexture == null) {
            noiseTexture = generateNoiseTexture(NOISE_TEX_SIZE, 0.7f);
            mc.getTextureManager().register(
                    geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier("acrylic_noise"),
                    noiseTexture);
            noiseTextureSetup = TextureSetup.singleTexture(
                    noiseTexture.getTextureView(), noiseTexture.getSampler());
        }
        return noiseTextureSetup;
    }

    /**
     * Release the shared noise texture and GPU resources.
     */
    public static void disposeNoiseTexture() {
        if (noiseTexture != null) {
            mc.getTextureManager().release(
                    geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier("acrylic_noise"));
            noiseTexture.close();
            noiseTexture = null;
            noiseTextureSetup = null;
        }
    }

    // ========================
    //  Gaussian blur
    // ========================

    /**
     * Generate a normalized 1D Gaussian kernel.
     *
     * @param sigma  standard deviation (higher = softer blur)
     * @param radius pixel radius (kernel size = 2*radius + 1)
     * @return normalized kernel weights
     */
    public static float[] gaussianKernel(float sigma, int radius) {
        int size = 2 * radius + 1;
        float[] kernel = new float[size];
        float sum = 0f;
        float denom = 2f * sigma * sigma;
        for (int i = 0; i < size; i++) {
            int x = i - radius;
            float w = (float) Math.exp(-(x * x) / denom);
            kernel[i] = w;
            sum += w;
        }
        float invSum = 1f / sum;
        for (int i = 0; i < size; i++) kernel[i] *= invSum;
        return kernel;
    }

    /**
     * Generate a uniform box-blur kernel (equal weights).
     * Repeated box blurs approximate a Gaussian.
     */
    public static float[] boxKernel(int radius) {
        int size = 2 * radius + 1;
        float[] kernel = new float[size];
        float w = 1f / size;
        for (int i = 0; i < size; i++) kernel[i] = w;
        return kernel;
    }

    /**
     * Apply separable Gaussian blur to a {@link NativeImage}.
     * Two-pass (horizontal + vertical) for O(2n * radius) instead of O(n * radius²).
     *
     * @param source source image (unchanged)
     * @param sigma  blur strength
     * @param radius kernel radius (0 = no blur)
     * @return a new blurred {@link NativeImage}
     */
    public static NativeImage blurImage(NativeImage source, float sigma, int radius) {
        if (radius <= 0 || source == null) return source;
        int w = source.getWidth(), h = source.getHeight();

        // separable passes on int[] for speed
        int[] src = source.getPixels(); // already ARGB

        float[] kernel = gaussianKernel(sigma, radius);

        int[] hPass = blurHorizontal(src, w, h, kernel);
        int[] vPass = blurVertical(hPass, w, h, kernel);

        NativeImage result = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                result.setPixel(x, y, vPass[row + x]);
            }
        }
        return result;
    }

    /**
     * Apply separable box blur (faster Gaussian approximation).
     * Running {@code passes} iterations improves the Gaussian quality.
     */
    public static NativeImage boxBlur(NativeImage source, int radius, int passes) {
        if (radius <= 0 || source == null) return source;
        int w = source.getWidth(), h = source.getHeight();
        int[] src = source.getPixels();
        float[] kernel = boxKernel(radius);

        int[] work = src;
        for (int i = 0; i < passes; i++) {
            int[] hPass = blurHorizontal(work, w, h, kernel);
            work = blurVertical(hPass, w, h, kernel);
        }

        NativeImage result = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                result.setPixel(x, y, work[row + x]);
            }
        }
        return result;
    }

    private static int[] blurHorizontal(int[] src, int w, int h, float[] kernel) {
        int rad = kernel.length / 2;
        int[] dst = new int[w * h];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float ra = 0, ga = 0, ba = 0, aa = 0;
                for (int k = -rad; k <= rad; k++) {
                    int sx = clamp(x + k, 0, w - 1);
                    int c = src[row + sx];
                    float kw = kernel[k + rad];
                    ra += ((c >> 16) & 0xFF) * kw;
                    ga += ((c >> 8) & 0xFF) * kw;
                    ba += (c & 0xFF) * kw;
                    aa += ((c >> 24) & 0xFF) * kw;
                }
                dst[row + x] = ARGB.color(
                        clamp8(Math.round(aa)), clamp8(Math.round(ra)),
                        clamp8(Math.round(ga)), clamp8(Math.round(ba)));
            }
        }
        return dst;
    }

    private static int[] blurVertical(int[] src, int w, int h, float[] kernel) {
        int rad = kernel.length / 2;
        int[] dst = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float ra = 0, ga = 0, ba = 0, aa = 0;
                for (int k = -rad; k <= rad; k++) {
                    int sy = clamp(y + k, 0, h - 1);
                    int c = src[sy * w + x];
                    float kw = kernel[k + rad];
                    ra += ((c >> 16) & 0xFF) * kw;
                    ga += ((c >> 8) & 0xFF) * kw;
                    ba += (c & 0xFF) * kw;
                    aa += ((c >> 24) & 0xFF) * kw;
                }
                dst[y * w + x] = ARGB.color(
                        clamp8(Math.round(aa)), clamp8(Math.round(ra)),
                        clamp8(Math.round(ga)), clamp8(Math.round(ba)));
            }
        }
        return dst;
    }

    // ========================
    //  Noise texture generation
    // ========================

    /**
     * Generate a tileable RGBA noise texture for the acrylic grain effect.
     * Each pixel is a random grey with the given intensity.
     *
     * @param size      texture width and height
     * @param intensity 0–1 grey-scale intensity
     */
    public static DynamicTexture generateNoiseTexture(int size, float intensity) {
        NativeImage img = new NativeImage(size, size, false);
        int ci = Math.round(clamp01(intensity) * 255);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int v = RNG.nextInt(ci + 1);
                img.setPixel(x, y, ARGB.color(255, v, v, v));
            }
        }
        DynamicTexture tex = new DynamicTexture(() -> "acrylic_noise_" + RNG.nextInt(), img);
        // NativeImage transferred ownership — close after DynamicTexture copies it
        img.close();
        return tex;
    }

    // ========================
    //  Screen capture
    // ========================

    /**
     * Capture a region of the main framebuffer into a {@link NativeImage}.
     * Uses {@code glReadPixels} — call sparingly (once per frame at most).
     * Prefer {@link #drawFrostedRect} for most use cases.
     *
     * @param x        left coordinate in GUI / screen space
     * @param y        top coordinate in GUI / screen space
     * @param width    capture width
     * @param height   capture height
     * @param downsample scale factor (1 = full res, 2 = half res, …)
     * @return captured image, or {@code null} on failure
     */
    @Nullable
    public static NativeImage captureBackground(int x, int y, int width, int height, int downsample) {
        if (width <= 0 || height <= 0) return null;
        RenderSystem.assertOnRenderThread();

        int dw = Math.max(1, width / downsample);
        int dh = Math.max(1, height / downsample);

        // Flip Y: OpenGL framebuffer has origin bottom-left,
        // GUI coords have origin top-left.
        int windowH = mc.getWindow().getHeight();
        int glY = windowH - y - height;

        // Read from the default framebuffer (GUI is rendered to framebuffer 0)
        ByteBuffer buffer = ByteBuffer.allocateDirect(dw * dh * 4);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(x, glY, dw, dh, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        // Wrap in NativeImage
        NativeImage img = new NativeImage(dw, dh, false);
        for (int ry = 0; ry < dh; ry++) {
            for (int rx = 0; rx < dw; rx++) {
                int idx = ((dh - 1 - ry) * dw + rx) * 4; // flip Y back
                int r = buffer.get(idx) & 0xFF;
                int g = buffer.get(idx + 1) & 0xFF;
                int b = buffer.get(idx + 2) & 0xFF;
                int a = buffer.get(idx + 3) & 0xFF;
                img.setPixel(rx, ry, ARGB.color(a, r, g, b));
            }
        }
        return img;
    }

    // ========================
    //  Internal render states
    // ========================

    /**
     * Renders a full-area {@link DynamicTexture} as a GUI element.
     */
    private static final class TextureRectState implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final float x0, y0, x1, y1;
        private final int alpha;
        @Nullable
        private final ScreenRectangle scissor;
        @Nullable
        private final ScreenRectangle bounds;

        TextureRectState(
                RenderPipeline pipeline, TextureSetup textureSetup,
                float x0, float y0, float x1, float y1,
                int alpha, @Nullable ScreenRectangle scissor
        ) {
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.x0 = x0; this.y0 = y0;
            this.x1 = x1; this.y1 = y1;
            this.alpha = alpha;
            this.scissor = scissor;

            int ix = (int) Math.floor(x0), iy = (int) Math.floor(y0);
            int iw = (int) Math.ceil(x1) - ix, ih = (int) Math.ceil(y1) - iy;
            ScreenRectangle b = new ScreenRectangle(ix, iy, iw, ih);
            this.bounds = scissor != null ? scissor.intersection(b) : b;
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            float u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f;
            // Tiled UVs for noise
            vc.addVertexWith2DPose(IDENTITY, x0, y0)
                    .setUv(u0, v0).setColor(255, 255, 255, alpha);
            vc.addVertexWith2DPose(IDENTITY, x0, y1)
                    .setUv(u0, v1).setColor(255, 255, 255, alpha);
            vc.addVertexWith2DPose(IDENTITY, x1, y1)
                    .setUv(u1, v1).setColor(255, 255, 255, alpha);
            vc.addVertexWith2DPose(IDENTITY, x1, y0)
                    .setUv(u1, v0).setColor(255, 255, 255, alpha);
        }

        @Override public @NonNull RenderPipeline pipeline() { return pipeline; }
        @Override public @NonNull TextureSetup textureSetup() { return textureSetup; }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
    }

    /**
     * Renders a texture with tiled UV coordinates for noise repetition.
     */
    private static final class TiledNoiseState implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final float x0, y0, x1, y1;
        private final float tileU, tileV;
        private final int alpha;
        @Nullable
        private final ScreenRectangle scissor;
        @Nullable
        private final ScreenRectangle bounds;

        TiledNoiseState(
                RenderPipeline pipeline, TextureSetup textureSetup,
                float x0, float y0, float x1, float y1,
                float tileU, float tileV, int alpha,
                @Nullable ScreenRectangle scissor
        ) {
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.x0 = x0; this.y0 = y0;
            this.x1 = x1; this.y1 = y1;
            this.tileU = tileU; this.tileV = tileV;
            this.alpha = alpha;
            this.scissor = scissor;

            int ix = (int) Math.floor(x0), iy = (int) Math.floor(y0);
            int iw = (int) Math.ceil(x1) - ix, ih = (int) Math.ceil(y1) - iy;
            ScreenRectangle b = new ScreenRectangle(ix, iy, iw, ih);
            this.bounds = scissor != null ? scissor.intersection(b) : b;
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            vc.addVertexWith2DPose(IDENTITY, x0, y0)
                    .setUv(0f, 0f).setColor(255, 255, 255, alpha);
            vc.addVertexWith2DPose(IDENTITY, x0, y1)
                    .setUv(0f, tileV).setColor(255, 255, 255, alpha);
            vc.addVertexWith2DPose(IDENTITY, x1, y1)
                    .setUv(tileU, tileV).setColor(255, 255, 255, alpha);
            vc.addVertexWith2DPose(IDENTITY, x1, y0)
                    .setUv(tileU, 0f).setColor(255, 255, 255, alpha);
        }

        @Override public @NonNull RenderPipeline pipeline() { return pipeline; }
        @Override public @NonNull TextureSetup textureSetup() { return textureSetup; }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
    }

    // ========================
    //  Public: frosted glass  (fast, no capture)
    // ========================

    /**
     * Draw a frosted-glass rectangle using tint + noise layers.
     * This is a fast approximation — no screen capture is performed.
     *
     * @param gui         the GUI graphics extractor
     * @param x, y        top-left position
     * @param width, height rectangle dimensions
     * @param tintColor   ARGB tint color (alpha controls opacity)
     * @param noiseOpacity 0–1 noise grain strength
     */
    public static void drawFrostedRect(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height,
            int tintColor, float noiseOpacity
    ) {
        if (width <= 0 || height <= 0) return;
        ScreenRectangle scissor = gui.peekScissorStack();

        // 1) Tint layer
        int a = (tintColor >>> 24) & 0xFF;
        if (a != 0) {
            CustomRectRenderer.drawRect(gui, x, y, width, height, tintColor);
        }

        // 2) Noise grain layer
        int noiseAlpha = Math.round(clamp01(noiseOpacity) * 255);
        if (noiseAlpha > 0) {
            TextureSetup noise = getNoiseTextureSetup();
            float tx = (float) width / NOISE_TEX_SIZE;
            float ty = (float) height / NOISE_TEX_SIZE;
            gui.submitGuiElementRenderState(new TiledNoiseState(
                    RenderPipelines.GUI_TEXTURED, noise,
                    x, y, x + width, y + height,
                    tx, ty, noiseAlpha, scissor
            ));
        }
    }

    /**
     * Draw a frosted-glass rounded rectangle.
     *
     * @param cornerRadius rounded corner radius in pixels
     * @see #drawFrostedRect(GuiGraphicsExtractor, int, int, int, int, int, float)
     */
    public static void drawFrostedRoundedRect(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height, int cornerRadius,
            int tintColor, float noiseOpacity
    ) {
        if (width <= 0 || height <= 0) return;
        ScreenRectangle scissor = gui.peekScissorStack();
        int r = clampCornerRadius(width, height, cornerRadius);

        // 1) Tint layer — rounded
        if (((tintColor >>> 24) & 0xFF) != 0) {
            CustomRoundedRectRenderer.drawRoundedRect(
                    gui, x, y, width, height, r, tintColor);
        }

        // 2) Noise grain — draw as full rect, will be clipped by scissor
        //    or by the rounded rect bounds (simplified: we use a full rect
        //    here; for pixel-perfect corners, use a custom scissor).
        int noiseAlpha = Math.round(clamp01(noiseOpacity) * 255);
        if (noiseAlpha > 0) {
            TextureSetup noise = getNoiseTextureSetup();
            float tx = (float) width / NOISE_TEX_SIZE;
            float ty = (float) height / NOISE_TEX_SIZE;
            gui.submitGuiElementRenderState(new TiledNoiseState(
                    RenderPipelines.GUI_TEXTURED, noise,
                    x, y, x + width, y + height,
                    tx, ty, noiseAlpha, scissor
            ));
        }
    }

    /**
     * Draw a frosted-glass rectangle with a subtle border highlight
     * (top/left brighter, bottom/right darker) for a 3D panel effect.
     */
    public static void drawFrostedPanel(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height, int cornerRadius,
            int tintColor, float noiseOpacity,
            int borderHighlightColor, int borderShadowColor, int borderThickness
    ) {
        drawFrostedRoundedRect(gui, x, y, width, height, cornerRadius,
                tintColor, noiseOpacity);

        if (borderThickness <= 0) return;

        int t = borderThickness;

        // Top highlight
        CustomRectRenderer.drawRect(gui, x + cornerRadius, y,
                width - cornerRadius * 2, t, borderHighlightColor);
        // Left highlight
        CustomRectRenderer.drawRect(gui, x, y + cornerRadius,
                t, height - cornerRadius * 2, borderHighlightColor);
        // Bottom shadow
        CustomRectRenderer.drawRect(gui, x + cornerRadius, y + height - t,
                width - cornerRadius * 2, t, borderShadowColor);
        // Right shadow
        CustomRectRenderer.drawRect(gui, x + width - t, y + cornerRadius,
                t, height - cornerRadius * 2, borderShadowColor);
    }

    // ========================
    //  Public: acrylic  (with blurred background)
    // ========================

    /**
     * Draw an acrylic rectangle with a pre-blurred background image.
     * Caller is responsible for capturing and blurring the background
     * via {@link #captureBackground} and {@link #blurImage}.
     *
     * @param bgImage  pre-blurred background {@link NativeImage} (ownership
     *                 transfers — this method closes it)
     * @param tintColor ARGB tint overlay
     * @param noiseOpacity 0–1 noise grain
     */
    public static void drawAcrylicRect(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height,
            NativeImage bgImage,
            int tintColor, float noiseOpacity
    ) {
        if (width <= 0 || height <= 0) return;
        ScreenRectangle scissor = gui.peekScissorStack();

        // 1) Blurred background layer
        if (bgImage != null) {
            String bgKey = "acrylic_bg_" + System.identityHashCode(bgImage);
            DynamicTexture bgTex = new DynamicTexture(() -> bgKey, bgImage);
            mc.getTextureManager().register(
                    geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier(bgKey),
                    bgTex);
            TextureSetup bgSetup = TextureSetup.singleTexture(
                    bgTex.getTextureView(), bgTex.getSampler());

            gui.submitGuiElementRenderState(new TextureRectState(
                    RenderPipelines.GUI_TEXTURED, bgSetup,
                    x, y, x + width, y + height,
                    255, scissor
            ));

            bgImage.close();
            // Texture stays registered — consider managing lifetime
            // via a dedicated cache in production code.
        }

        // 2) Tint overlay
        int a = (tintColor >>> 24) & 0xFF;
        if (a != 0) {
            CustomRectRenderer.drawRect(gui, x, y, width, height, tintColor);
        }

        // 3) Noise grain
        int noiseAlpha = Math.round(clamp01(noiseOpacity) * 255);
        if (noiseAlpha > 0) {
            TextureSetup noise = getNoiseTextureSetup();
            float tx = (float) width / NOISE_TEX_SIZE;
            float ty = (float) height / NOISE_TEX_SIZE;
            gui.submitGuiElementRenderState(new TiledNoiseState(
                    RenderPipelines.GUI_TEXTURED, noise,
                    x, y, x + width, y + height,
                    tx, ty, noiseAlpha, scissor
            ));
        }
    }

    /**
     * Draw an acrylic rounded rectangle with a pre-blurred background.
     *
     * @see #drawAcrylicRect(GuiGraphicsExtractor, int, int, int, int, NativeImage, int, float)
     */
    public static void drawAcrylicRoundedRect(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height, int cornerRadius,
            NativeImage bgImage,
            int tintColor, float noiseOpacity
    ) {
        // For rounded corners on the blurred background, we rely on
        // the tint + noise layers being rounded.  The background
        // texture fills the whole rect.
        drawAcrylicRect(gui, x, y, width, height, bgImage, 0, 0);

        int r = clampCornerRadius(width, height, cornerRadius);

        if (((tintColor >>> 24) & 0xFF) != 0) {
            CustomRoundedRectRenderer.drawRoundedRect(
                    gui, x, y, width, height, r, tintColor);
        }

        int noiseAlpha = Math.round(clamp01(noiseOpacity) * 255);
        if (noiseAlpha > 0) {
            ScreenRectangle scissor = gui.peekScissorStack();
            TextureSetup noise = getNoiseTextureSetup();
            float tx = (float) width / NOISE_TEX_SIZE;
            float ty = (float) height / NOISE_TEX_SIZE;
            gui.submitGuiElementRenderState(new TiledNoiseState(
                    RenderPipelines.GUI_TEXTURED, noise,
                    x, y, x + width, y + height,
                    tx, ty, noiseAlpha, scissor
            ));
        }
    }

    // ========================
    //  GPU-accelerated acrylic  (blur on GPU)
    // ========================

    /**
     * Draw an acrylic rectangle with GPU blur, tint, and noise all in one pass.
     * Uses {@code acrylic_blur.fsh} to blur the background texture on-GPU.
     *
     * @param gui          GUI graphics handle
     * @param x, y         top-left position
     * @param width, height rectangle dimensions
     * @param bgTexture    background texture to blur (uploaded from capture)
     * @param bgWidth, bgHeight background texture dimensions
     * @param tintColor    ARGB tint color
     * @param blurRadius   blur radius in pixels (1-32)
     * @param noiseOpacity 0-1 noise grain
     */
    public static void drawAcrylicRectGpu(
            GuiGraphicsExtractor gui,
            int x, int y, int width, int height,
            DynamicTexture bgTexture, int bgWidth, int bgHeight,
            int tintColor, float blurRadius, float noiseOpacity
    ) {
        if (width <= 0 || height <= 0) return;
        ScreenRectangle scissor = gui.peekScissorStack();

        // Combine background + noise into a single TextureSetup
        TextureSetup noiseSetup = getNoiseTextureSetup();
        TextureSetup combinedSetup = TextureSetup.doubleTexture(
                bgTexture.getTextureView(), bgTexture.getSampler(),
                noiseSetup.texure0(), noiseSetup.sampler0());

        float u1 = (float) width / bgWidth;
        float v1 = (float) height / bgHeight;

        gui.submitGuiElementRenderState(new AcrylicBlurState(
                BLUR_PIPELINE, combinedSetup,
                x, y, x + width, y + height,
                0f, 0f, u1, v1,
                tintColor, blurRadius, noiseOpacity, scissor
        ));
    }

    /**
     * Simple GPU blur of a texture (no tint, no noise).
     * Renders the texture through the blur shader with zero tint and noise.
     */
    public static void drawBlurredTexture(
            GuiGraphicsExtractor gui,
            DynamicTexture texture, int texW, int texH,
            int x, int y, int width, int height,
            float blurRadius
    ) {
        drawAcrylicRectGpu(gui, x, y, width, height,
                texture, texW, texH,
                0xFFFFFFFF, blurRadius, 0f);
    }

    // ========================
    //  GPU-acrylic render state
    // ========================

    private static final class AcrylicBlurState implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final float x0, y0, x1, y1;
        private final float u0, v0, u1, v1;
        private final int tintColor;
        private final float blurRadius;
        private final float noiseOpacity;
        @Nullable
        private final ScreenRectangle scissor;
        @Nullable
        private final ScreenRectangle bounds;

        AcrylicBlurState(
                RenderPipeline pipeline,
                TextureSetup textureSetup,
                float x0, float y0, float x1, float y1,
                float u0, float v0, float u1, float v1,
                int tintColor, float blurRadius, float noiseOpacity,
                @Nullable ScreenRectangle scissor
        ) {
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.x0 = x0; this.y0 = y0;
            this.x1 = x1; this.y1 = y1;
            this.u0 = u0; this.v0 = v0;
            this.u1 = u1; this.v1 = v1;
            this.tintColor = tintColor;
            this.blurRadius = blurRadius;
            this.noiseOpacity = noiseOpacity;
            this.scissor = scissor;

            int ix = (int) Math.floor(x0), iy = (int) Math.floor(y0);
            int iw = (int) Math.ceil(x1) - ix, ih = (int) Math.ceil(y1) - iy;
            ScreenRectangle b = new ScreenRectangle(ix, iy, iw, ih);
            this.bounds = scissor != null ? scissor.intersection(b) : b;
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            int a = (tintColor >>> 24) & 0xFF;
            vc.addVertexWith2DPose(IDENTITY, x0, y0)
                    .setUv(u0, v0).setColor(255, 255, 255, a);
            vc.addVertexWith2DPose(IDENTITY, x0, y1)
                    .setUv(u0, v1).setColor(255, 255, 255, a);
            vc.addVertexWith2DPose(IDENTITY, x1, y1)
                    .setUv(u1, v1).setColor(255, 255, 255, a);
            vc.addVertexWith2DPose(IDENTITY, x1, y0)
                    .setUv(u1, v0).setColor(255, 255, 255, a);
        }

        @Override public @NonNull RenderPipeline pipeline() { return pipeline; }
        @Override public @NonNull TextureSetup textureSetup() { return textureSetup; }
        @Override @Nullable public ScreenRectangle scissorArea() { return scissor; }
        @Override @Nullable public ScreenRectangle bounds() { return bounds; }
    }

    // ========================
    //  Pipeline registration
    // ========================

    /**
     * Register the blur pipeline. Call from RegisterRenderPipelinesEvent.
     */
    public static void registerPipelines(java.util.function.Consumer<RenderPipeline> registry) {
        registry.accept(BLUR_PIPELINE);
    }

    // ========================
    //  Helpers
    // ========================

    private static int clampCornerRadius(int w, int h, int radius) {
        int maxR = Math.min(w, h) / 2;
        return Math.min(Math.max(0, radius), maxR);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int clamp8(int v) {
        return clamp(v, 0, 255);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
