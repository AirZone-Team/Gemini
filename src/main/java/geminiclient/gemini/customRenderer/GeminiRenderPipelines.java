package geminiclient.gemini.customRenderer;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Shared pipeline layouts for Gemini's backend-neutral custom rendering.
 *
 * <p>Minecraft 26.2 replaced the individual uniform/sampler declarations on
 * {@link RenderPipeline.Builder} with explicit bind-group layouts, and 26.3
 * folded samplers into the same {@code withUniform} entry point as
 * {@link UniformType#COMBINED_IMAGE_SAMPLER}. Keeping the layouts here makes
 * every custom pipeline use the same contract on OpenGL and Vulkan.</p>
 */
public final class GeminiRenderPipelines {
    /**
     * 26.3 dropped the combined {@code MATRICES_PROJECTION} layout; the model-view
     * and projection matrices now live in two separate uniform buffers.
     */
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET =
            RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .buildSnippet();

    private GeminiRenderPipelines() {
    }

    public static BindGroupLayout uniform(String name) {
        return uniforms(name);
    }

    public static BindGroupLayout uniforms(String... names) {
        BindGroupLayout.Builder builder = BindGroupLayout.builder();
        for (String name : names) {
            builder.withUniform(name, UniformType.UNIFORM_BUFFER);
        }
        return builder.build();
    }

    public static BindGroupLayout samplers(String... names) {
        BindGroupLayout.Builder builder = BindGroupLayout.builder();
        for (String name : names) {
            builder.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
        }
        return builder.build();
    }

    public static BindGroupLayout uniformAndSamplers(String uniform, String... samplers) {
        BindGroupLayout.Builder builder = BindGroupLayout.builder()
                .withUniform(uniform, UniformType.UNIFORM_BUFFER);
        for (String sampler : samplers) {
            builder.withUniform(sampler, UniformType.COMBINED_IMAGE_SAMPLER);
        }
        return builder.build();
    }
}
