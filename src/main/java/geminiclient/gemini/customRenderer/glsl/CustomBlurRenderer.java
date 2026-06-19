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
 * <p>Copies the main framebuffer to an internal texture, then runs a single-pass
 * radial blur shader over the specified region. The blurred output is masked to a
 * rounded rectangle and optionally tinted.</p>
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

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec3()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static RenderPipeline pipeline;
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
    }

    /**
     * Register the blur pipeline. Call from
     * {@code RegisterRenderPipelinesEvent}.
     */
    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        ensureProgram();
        registry.accept(pipeline);
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

        float s = (float) scale;
        float rTLPx = Math.max(0.0f, rTL * s);
        float rTRPx = Math.max(0.0f, rTR * s);
        float rBRPx = Math.max(0.0f, rBR * s);
        float rBLPx = Math.max(0.0f, rBL * s);

        float quality = Math.max(0.0f, blurStrength);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        // Copy the main framebuffer into the input texture for sampling
        encoder.copyTextureToTexture(
                fb.getColorTexture(),
                input.getColorTexture(),
                0, 0, 0, 0, 0,
                fbWidth, fbHeight
        );

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
            int scissorY = fbHeight - (int) pxY - (int) pxH;

            renderPass.enableScissor(
                    (int) pxX, Math.max(0, scissorY),
                    Math.max(0, (int) pxW),
                    Math.max(0, (int) pxH)
            );
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
