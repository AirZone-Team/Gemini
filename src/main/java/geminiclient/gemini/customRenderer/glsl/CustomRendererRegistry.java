package geminiclient.gemini.customRenderer.glsl;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import geminiclient.gemini.customRenderer.glsl.modules.BlackHolePetRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.JumpCircleRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillAuraIndicatorRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillAuraTargetRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectPostProcessor;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.MagicHaloRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.SkyLanternRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.TargetDisplayRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.TargetDisplayRingRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.TrailRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.GhostAfterImageRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.HellHandRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.InstancedParticleRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.MipBloomProcessor;
import geminiclient.gemini.customRenderer.glsl.modules.SweepAttackRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.ThaumaturgyRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.TrajectoriesRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.Osu4kNoteRenderer;
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

    /** 自定义管线是否已可安全提交；初始重载 apply 过一次就单调为真。 */
    private static volatile boolean pipelinesResolvable;

    /**
     * 能否提交 {@code gemini:} 自定义管线。
     *
     * <p>初始重载 apply 之前，设备只用 {@code GameRenderer#preloadUiShader} 装的兜底管线缓存，
     * 而它读的是 {@code Minecraft#vanillaPackResources}（原版包），看不见 {@code assets/gemini/
     * shaders/**}；此时提交自定义管线不会静默不出图，而是编译返回 null 后由
     * {@code RenderSystem#getCompiledPipeline} 抛 {@code IllegalStateException} 崩掉这一帧。
     * 所以判据是「首轮重载已完成」（该标记在 {@code ShaderManager#apply} 之后才置真），
     * 不是「着色器源读得到」——类路径/模组 jar 从第一帧就读得到，等于没有判断。</p>
     */
    public static boolean areShadersReady() {
        if (pipelinesResolvable) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !minecraft.isGameLoadFinished()) {
            return false;
        }
        pipelinesResolvable = true;
        return true;
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
        KillAuraTargetRenderer.registerPipeline(registry);
        KillEffectRenderer.registerPipeline(registry);
        KillEffectPostProcessor.registerPipeline(registry);
        HellHandRenderer.registerPipeline(registry);
        ThaumaturgyRenderer.registerPipeline(registry);
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
        BlackHolePetRenderer.registerPipeline(registry);
        VFXManager.registerPipeline(registry);
        InfiniteGridRenderer.registerPipeline(registry);
        Osu4kNoteRenderer.registerPipeline(registry);
    }
}
