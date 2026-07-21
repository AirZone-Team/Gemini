package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * Fullscreen GLSL renderer for the modernist infinite perspective grid background.
 *
 * <p>Renders a layered background:</p>
 * <ol>
 *   <li>Pure black base</li>
 *   <li>Perspective infinite grid (lines that recede into the distance)</li>
 *   <li>Subtle fluid noise (breathing feel)</li>
 *   <li>Dynamic aurora (very faint blue accent)</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * InfiniteGridRenderer.render(elapsedTimeInSeconds);
 * }</pre>
 */
public final class InfiniteGridRenderer {

    private static final Identifier GRID_PATH = getIdentifier("core/infinite_grid");

    // Uniform: vec4(screenWidth, screenHeight, time, 0)
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private static RenderPipeline pipeline;
    private static GpuBuffer uniforms;

    private InfiniteGridRenderer() {}

    // ========================
    //  Pipeline setup
    // ========================

    private static void ensureProgram() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(
                    () -> "GeminiGridUniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    UNIFORM_SIZE);
        }
        if (pipeline == null) {
            pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(getIdentifier("pipeline/infinite_grid"))
                    .withVertexShader(GRID_PATH)
                    .withFragmentShader(GRID_PATH)
                    .withBindGroupLayout(GeminiRenderPipelines.uniform("GridUniforms"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .build();
        }
    }

    /**
     * Register the grid pipeline. Call from
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
     * Render the infinite grid background to the current framebuffer.
     *
     * @param time elapsed time in seconds since screen open
     */
    public static void render(float time) {
        ensureProgram();

        RenderTarget fb = mc.gameRenderer.mainRenderTarget();
        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) {
            return;
        }

        int fbWidth = mc.getWindow().getWidth();
        int fbHeight = mc.getWindow().getHeight();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        // Write uniforms
        try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(fbWidth, fbHeight, time, 0f);
        }

        // Render fullscreen triangle
        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Gemini Infinite Grid",
                fb.getColorTextureView(),
                Optional.empty()
        )) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("GridUniforms", uniforms);
            renderPass.draw(3, 1, 0, 0);
        }
    }
}
