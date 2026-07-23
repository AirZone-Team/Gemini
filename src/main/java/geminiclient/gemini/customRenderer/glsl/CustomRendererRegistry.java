package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import geminiclient.gemini.customRenderer.glsl.modules.JumpCircleRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillAuraIndicatorRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectPostProcessor;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.MagicHaloRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.SkyLanternRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.TargetDisplayRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.TargetDisplayRingRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.TrailRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.GhostAfterImageRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.InstancedParticleRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.MipBloomProcessor;
import geminiclient.gemini.customRenderer.glsl.modules.SweepAttackRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.TrajectoriesRenderer;
import geminiclient.gemini.modules.impl.visual.effectDisplay.Md3ShadowRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.GlowRenderer;
import geminiclient.gemini.customRenderer.glsl.InfiniteGridRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.function.Consumer;

/**
 * Central registration point for all custom {@link RenderPipeline} instances.
 *
 * <p>Call {@link #registerAll} from a {@link RegisterRenderPipelinesEvent} handler:</p>
 * <pre>{@code
 * @SubscribeEvent
 * public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
 *     CustomRendererRegistry.registerAll(event.register);
 * }
 * }</pre>
 */
public final class CustomRendererRegistry {

    private CustomRendererRegistry() {}

    /**
     * Whether the initial resource reload has published Gemini's generated
     * shader sources. Persisted HUD modules can begin rendering before that
     * reload completes; submitting a custom pipeline in that window permanently
     * caches an invalid pipeline and crashes the first frame.
     */
    public static boolean areShadersReady() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }

        RenderPipeline sentinel = CustomFontRenderer.FONT_PIPELINE;
        return minecraft.getShaderManager().getShader(
                        sentinel.getVertexShader(), ShaderType.VERTEX) != null
                && minecraft.getShaderManager().getShader(
                        sentinel.getFragmentShader(), ShaderType.FRAGMENT) != null;
    }

    /**
     * Register all custom render pipelines with the engine.
     */
    public static void registerAll(Consumer<RenderPipeline> registry) {
        CustomFontRenderer.registerPipeline(registry);
        GlowRenderer.registerPipelines(registry);
        SdfUIRenderer.registerPipelines(registry);
        Md3ShadowRenderer.registerPipeline(registry);
        CustomAcrylicRenderer.registerPipelines(registry);
        CustomBlurRenderer.registerPipeline(registry);
        JumpCircleRenderer.registerPipeline(registry);
        KillAuraIndicatorRenderer.registerPipeline(registry);
        KillEffectRenderer.registerPipeline(registry);
        KillEffectPostProcessor.registerPipeline(registry);
        MagicHaloRenderer.registerPipeline(registry);
        SkyLanternRenderer.registerPipeline(registry);
        TargetDisplayRenderer.registerPipeline(registry);
        TargetDisplayRingRenderer.registerPipeline(registry);
        TrajectoriesRenderer.registerPipeline(registry);
        TrailRenderer.registerPipeline(registry);
        GhostAfterImageRenderer.registerPipeline(registry);
        InstancedParticleRenderer.registerPipeline(registry);
        MipBloomProcessor.registerPipeline(registry);
        MaterialRenderer.registerPipeline(registry);
        SweepAttackRenderer.registerPipeline(registry);
        VFXManager.registerPipeline(registry);
        InfiniteGridRenderer.registerPipeline(registry);
    }
}
