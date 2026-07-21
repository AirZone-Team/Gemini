package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;
import geminiclient.gemini.customRenderer.GeminiRenderTargets;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GPU-accelerated blur effect with rounded-rectangle masking.
 *
 * <p>Snapshots only the required part of the main framebuffer to an internal
 * texture, then runs a single-pass radial blur shader over the specified region.
 * The blurred output is masked to a rounded rectangle and optionally tinted.</p>
 *
 * <p>All coordinates are in GUI space (same as
 * {@code gui.drawString(...)} etc.).</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Simple blur
 * CustomBlurRenderer.render(x, y, w, h, radius, blurStrength);
 *
 * // Blur with tint color and per-corner radii
 * CustomBlurRenderer.render(x, y, w, h, rTL, rTR, rBR, rBL, tintColor, blurStrength);
 * }</pre>
 */
public final class CustomBlurRenderer {

    private static final Identifier BLUR_PATH = getIdentifier("core/blur");
    private static final Identifier BLUR_COPY_PATH = getIdentifier("core/blur_copy");

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec3()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static RenderPipeline pipeline;
    private static RenderPipeline copyPipeline;
    private static GpuBuffer uniforms;
    private static TextureTarget input;

    private CustomBlurRenderer() {}

    // ========================
    //  Pipeline & buffer setup
    // ========================

    private static void ensureProgram() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(
                    () -> "GeminiBlurUniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    UNIFORMS_SIZE);
        }
        if (pipeline == null) {
            pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(getIdentifier("pipeline/blur"))
                    .withVertexShader(BLUR_PATH)
                    .withFragmentShader(BLUR_PATH)
                    .withBindGroupLayout(GeminiRenderPipelines.uniformAndSamplers("BlurUniforms", "InputSampler"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .build();
        }
        if (copyPipeline == null) {
            copyPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(getIdentifier("pipeline/blur_copy"))
                    .withVertexShader(BLUR_PATH)
                    .withFragmentShader(BLUR_COPY_PATH)
                    .withBindGroupLayout(GeminiRenderPipelines.samplers("InputSampler"))
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withCull(false)
                    .build();
        }
    }

    /**
     * Register the blur pipeline. Call from
     * {@code RegisterRenderPipelinesEvent}.
     */
    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        ensureProgram();
        registry.accept(pipeline);
        registry.accept(copyPipeline);
    }

    /**
     * Compile the blur program ahead of first use. Called by
     * {@link UiShaderWarmup} during the resource-reload stage (render thread,
     * after the vanilla ShaderManager) so the first blur render doesn't pay
     * the compile/link cost. Repeat calls are cheap: the GL pipeline cache
     * deduplicates.
     */
    public static void precompile() {
        ensureProgram();
        RenderSystem.getDevice().precompilePipeline(pipeline, null);
        RenderSystem.getDevice().precompilePipeline(copyPipeline, null);
    }

    // ========================
    //  Main render entry point
    // ========================

    /**
     * Render a blurred region with per-corner radii and optional tint color.
     *
     * @param x, y, width, height  area in GUI coordinates
     * @param rTL, rTR, rBR, rBL   per-corner radii in GUI pixels
     * @param color                ARGB tint color (use {@code 0} for no tint)
     * @param blurStrength         blur radius in pixels (0 = no blur)
     */
    public static void render(
            float x, float y, float width, float height,
            float rTL, float rTR, float rBR, float rBL,
            int color, float blurStrength
    ) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        ensureProgram();

        RenderTarget fb = mc.gameRenderer.mainRenderTarget();
        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) {
            return;
        }

        int targetWidth = fb.width;
        int targetHeight = fb.height;
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        if (targetWidth <= 0 || targetHeight <= 0 || guiWidth <= 0 || guiHeight <= 0) {
            return;
        }

        // The snapshot must match the attachment used by both render passes.
        if (input == null) {
            input = GeminiRenderTargets.colorTarget("Gemini Blur Input", targetWidth, targetHeight, false);
        }
        if (input.width != targetWidth || input.height != targetHeight) {
            input.resize(targetWidth, targetHeight);
        }

        // GUI coordinates use a top-left origin. Render-pass scissors and shader
        // uniforms use attachment pixels, whose dimensions may differ from the window.
        double scaleX = (double) targetWidth / guiWidth;
        double scaleY = (double) targetHeight / guiHeight;
        float pxX = (float) (x * scaleX);
        float pxY = (float) (y * scaleY);
        float pxRight = (float) ((x + width) * scaleX);
        float pxBottom = (float) ((y + height) * scaleY);
        float pxW = pxRight - pxX;
        float pxH = pxBottom - pxY;
        if (!Float.isFinite(pxX) || !Float.isFinite(pxY)
                || !Float.isFinite(pxRight) || !Float.isFinite(pxBottom)
                || pxW <= 0.0f || pxH <= 0.0f) {
            return;
        }

        // Outward rounding covers every touched attachment pixel. Clamp endpoints
        // before deriving sizes because RenderPass rejects empty/out-of-bounds scissors.
        int rawScissorX = floorToInt(pxX);
        int rawScissorY = floorToInt(targetHeight - pxBottom);
        int rawScissorRight = ceilToInt(pxRight);
        int rawScissorTop = ceilToInt(targetHeight - pxY);
        int scissorX = clamp(rawScissorX, 0, targetWidth);
        int scissorY = clamp(rawScissorY, 0, targetHeight);
        int scissorRight = clamp(rawScissorRight, 0, targetWidth);
        int scissorTop = clamp(rawScissorTop, 0, targetHeight);
        int scissorWidth = scissorRight - scissorX;
        int scissorHeight = scissorTop - scissorY;
        if (scissorWidth <= 0 || scissorHeight <= 0) {
            return;
        }

        float scalarScale = (float) Math.min(scaleX, scaleY);
        float maxRadius = Math.min(pxW, pxH) * 0.5f;
        float rTLPx = Math.min(maxRadius, scaledLength(rTL, scalarScale));
        float rTRPx = Math.min(maxRadius, scaledLength(rTR, scalarScale));
        float rBRPx = Math.min(maxRadius, scaledLength(rBR, scalarScale));
        float rBLPx = Math.min(maxRadius, scaledLength(rBL, scalarScale));

        float quality = Math.min(
                Math.max(targetWidth, targetHeight),
                scaledLength(blurStrength, scalarScale));

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        /*
         * Copy only pixels the blur kernel can reach. The old hardware blit
         * copied the entire framebuffer for every small UI card. This
         * scissored nearest-texel pass produces the same snapshot, including a
         * one-texel guard for linear filtering, with region-sized bandwidth.
         */
        int sampleGuard = (int) Math.ceil(quality) + 1;
        int copyX = clamp(scissorX - sampleGuard, 0, targetWidth);
        int copyY = clamp(scissorY - sampleGuard, 0, targetHeight);
        int copyRight = clamp(scissorRight + sampleGuard, 0, targetWidth);
        int copyTop = clamp(scissorTop + sampleGuard, 0, targetHeight);
        int copyWidth = copyRight - copyX;
        int copyHeight = copyTop - copyY;
        if (copyWidth <= 0 || copyHeight <= 0) {
            return;
        }
        try (RenderPass copyPass = encoder.createRenderPass(
                () -> "Gemini Blur Region Copy",
                input.getColorTextureView(),
                Optional.empty()
        )) {
            copyPass.setPipeline(copyPipeline);
            copyPass.enableScissor(copyX, copyY, copyWidth, copyHeight);
            RenderSystem.bindDefaultUniforms(copyPass);
            copyPass.bindTexture(
                    "InputSampler",
                    fb.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            );
            copyPass.draw(3, 1, 0, 0);
        }

        // Write uniforms
        try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
            Std140Builder builder = Std140Builder.intoBuffer(view.data());
            builder.putVec3(targetWidth, targetHeight, quality);
            builder.putVec4(pxW, pxH, pxX, pxY);
            builder.putVec4(
                    ((color >> 16) & 0xFF) / 255.0f,
                    ((color >> 8) & 0xFF) / 255.0f,
                    (color & 0xFF) / 255.0f,
                    ((color >> 24) & 0xFF) / 255.0f
            );
            builder.putVec4(rTLPx, rTRPx, rBRPx, rBLPx);
        }

        // Render the blur
        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Gemini Blur",
                fb.getColorTextureView(),
                Optional.empty()
        )) {
            renderPass.setPipeline(pipeline);
            renderPass.enableScissor(scissorX, scissorY, scissorWidth, scissorHeight);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("BlurUniforms", uniforms);
            renderPass.bindTexture(
                    "InputSampler",
                    input.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            );
            renderPass.draw(3, 1, 0, 0);
        }
    }

    private static float scaledLength(float value, float scale) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            return 0.0f;
        }
        float scaled = value * scale;
        return Float.isFinite(scaled) ? scaled : Float.MAX_VALUE;
    }

    private static int floorToInt(float value) {
        if (value <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.floor(value);
    }

    private static int ceilToInt(float value) {
        if (value <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    // ========================
    //  Convenience overloads
    // ========================

    /**
     * Render a blurred region with uniform corner radius and no tint.
     */
    public static void render(
            float x, float y, float width, float height,
            float radius, float blurStrength
    ) {
        render(x, y, width, height, radius, radius, radius, radius,
                0x00000000, blurStrength);
    }

    /**
     * Render a blurred region with uniform corner radius and tint color.
     */
    public static void render(
            float x, float y, float width, float height,
            float radius, int color, float blurStrength
    ) {
        render(x, y, width, height, radius, radius, radius, radius,
                color, blurStrength);
    }
}
