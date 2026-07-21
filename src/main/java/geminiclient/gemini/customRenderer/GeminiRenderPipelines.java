package geminiclient.gemini.customRenderer;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Shared pipeline layouts for Gemini's backend-neutral custom rendering.
 *
 * <p>Minecraft 26.2 replaced the individual uniform/sampler declarations on
 * {@link RenderPipeline.Builder} with explicit bind-group layouts. Keeping the
 * layouts here makes every custom pipeline use the same contract on OpenGL and
 * Vulkan.</p>
 */
public final class GeminiRenderPipelines {
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET =
            RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
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
            builder.withSampler(name);
        }
        return builder.build();
    }

    public static BindGroupLayout uniformAndSamplers(String uniform, String... samplers) {
        BindGroupLayout.Builder builder = BindGroupLayout.builder()
                .withUniform(uniform, UniformType.UNIFORM_BUFFER);
        for (String sampler : samplers) {
            builder.withSampler(sampler);
        }
        return builder.build();
    }
}
