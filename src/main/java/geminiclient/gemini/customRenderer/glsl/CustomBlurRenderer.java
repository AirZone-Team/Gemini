package geminiclient.gemini.customRenderer.glsl;

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

import java.util.OptionalInt;
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
                    .withUniform("BlurUniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .build();
        }
        if (copyPipeline == null) {
            copyPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(getIdentifier("pipeline/blur_copy"))
                    .withVertexShader(BLUR_PATH)
                    .withFragmentShader(BLUR_COPY_PATH)
                    .withSampler("InputSampler")
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

        RenderTarget fb = mc.getMainRenderTarget();
        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) {
            return;
        }

        int fbWidth = mc.getWindow().getWidth();
        int fbHeight = mc.getWindow().getHeight();

        // Lazily create the input texture (copy of framebuffer)
        if (input == null) {
            input = new TextureTarget("Gemini Blur Input", fbWidth, fbHeight, false);
        }
        if (input.width != fbWidth || input.height != fbHeight) {
            input.resize(fbWidth, fbHeight);
        }

        // Convert GUI coordinates to framebuffer pixels
        double scale = (double) fbWidth / mc.getWindow().getGuiScaledWidth();
        float pxX = (float) (x * scale);
        float pxY = (float) (y * scale);
        float pxW = (float) (width * scale);
        float pxH = (float) (height * scale);

        // Preserve the original integer scissor coverage, then clip it before
        // issuing GPU work. A fully off-screen region now costs nothing.
        int rawScissorX = (int) pxX;
        int rawScissorY = fbHeight - (int) pxY - (int) pxH;
        int rawScissorRight = rawScissorX + Math.max(0, (int) pxW);
        int rawScissorTop = rawScissorY + Math.max(0, (int) pxH);
        int scissorX = Math.max(0, rawScissorX);
        int scissorY = Math.max(0, rawScissorY);
        int scissorRight = Math.min(fbWidth, rawScissorRight);
        int scissorTop = Math.min(fbHeight, rawScissorTop);
        int scissorWidth = scissorRight - scissorX;
        int scissorHeight = scissorTop - scissorY;
        if (scissorWidth <= 0 || scissorHeight <= 0) {
            return;
        }

        float s = (float) scale;
        float rTLPx = Math.max(0.0f, rTL * s);
        float rTRPx = Math.max(0.0f, rTR * s);
        float rBRPx = Math.max(0.0f, rBR * s);
        float rBLPx = Math.max(0.0f, rBL * s);

        float quality = Math.max(0.0f, blurStrength);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        /*
         * Copy only pixels the blur kernel can reach. The old hardware blit
         * copied the entire framebuffer for every small UI card. This
         * scissored nearest-texel pass produces the same snapshot, including a
         * one-texel guard for linear filtering, with region-sized bandwidth.
         */
        int sampleGuard = (int) Math.ceil(quality) + 1;
        int copyX = Math.max(0, scissorX - sampleGuard);
        int copyY = Math.max(0, scissorY - sampleGuard);
        int copyRight = Math.min(fbWidth, scissorRight + sampleGuard);
        int copyTop = Math.min(fbHeight, scissorTop + sampleGuard);
        try (RenderPass copyPass = encoder.createRenderPass(
                () -> "Gemini Blur Region Copy",
                input.getColorTextureView(),
                OptionalInt.empty()
        )) {
            copyPass.setPipeline(copyPipeline);
            copyPass.enableScissor(copyX, copyY, copyRight - copyX, copyTop - copyY);
            RenderSystem.bindDefaultUniforms(copyPass);
            copyPass.bindTexture(
                    "InputSampler",
                    fb.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            );
            copyPass.draw(0, 3);
        }

        // Write uniforms
        try (GpuBuffer.MappedView view = encoder.mapBuffer(uniforms, false, true)) {
            Std140Builder builder = Std140Builder.intoBuffer(view.data());
            builder.putVec3(fbWidth, fbHeight, quality);
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
                OptionalInt.empty()
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
            renderPass.draw(0, 3);
        }
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
