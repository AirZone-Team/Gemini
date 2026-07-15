package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import geminiclient.gemini.customRenderer.glsl.modules.JumpCircleRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillAuraIndicatorRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectPostProcessor;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.MagicHaloRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.SkyLanternRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.TargetDisplayRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.TrailRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.GhostAfterImageRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.InstancedParticleRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.MipBloomProcessor;
import geminiclient.gemini.customRenderer.glsl.modules.SweepAttackRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.TrajectoriesRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.GlowRenderer;
import geminiclient.gemini.customRenderer.glsl.InfiniteGridRenderer;
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
     * Register all custom render pipelines with the engine.
     */
    public static void registerAll(Consumer<RenderPipeline> registry) {
        CustomFontRenderer.registerPipeline(registry);
        GlowRenderer.registerPipelines(registry);
        CustomAcrylicRenderer.registerPipelines(registry);
        CustomBlurRenderer.registerPipeline(registry);
        JumpCircleRenderer.registerPipeline(registry);
        KillAuraIndicatorRenderer.registerPipeline(registry);
        KillEffectRenderer.registerPipeline(registry);
        KillEffectPostProcessor.registerPipeline(registry);
        MagicHaloRenderer.registerPipeline(registry);
        SkyLanternRenderer.registerPipeline(registry);
        TargetDisplayRenderer.registerPipeline(registry);
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
