package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import geminiclient.gemini.customRenderer.glsl.modules.JumpCircleRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillAuraIndicatorRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.SkyLanternRenderer;
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
        GlowRenderer.registerPipelines(registry);
        CustomAcrylicRenderer.registerPipelines(registry);
        CustomFontRenderer.registerPipeline(registry);
        JumpCircleRenderer.registerPipeline(registry);
        KillAuraIndicatorRenderer.registerPipeline(registry);
        SkyLanternRenderer.registerPipeline(registry);
    }
}
