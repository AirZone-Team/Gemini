package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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

    /** 加载画面 SDF 管线是否已由 {@link #prepareLoaderSdfPipelines()} 提前编译成功。 */
    private static volatile boolean loaderSdfReady;

    /**
     * Whether the initial resource reload has published Gemini's generated
     * shader sources. Persisted HUD modules can begin rendering before that
     * reload completes; submitting a custom pipeline in that window throws
     * {@code IllegalStateException: Pipeline is not valid} out of
     * {@code VulkanRenderPass#setPipeline}, and the {@code INVALID} intermediary
     * module cached under that shader key then makes the later
     * {@code ShaderManager#apply} preload fail with "Failed to load required
     * shader programs". Use {@link #prepareLoaderSdfPipelines()} instead of
     * submitting a pipeline that may not have sources yet.
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
     * Compile the loading screen's star and ring SDF pipelines ahead of
     * {@code ShaderManager}, from shader sources read straight off the mod
     * classpath, so the {@code fwidth} anti-aliasing is live during the initial
     * reload instead of only after it finishes. Safe to call every frame: once it
     * succeeds it short-circuits, and {@code ShaderManager#apply} later clears the
     * device caches and recompiles both pipelines from its own source table.
     *
     * <p><b>All</b> sources for <b>both</b> pipelines are resolved before a single
     * compile is attempted. That ordering is load-bearing: a pipeline whose source
     * cannot be found compiles to {@code INVALID}, {@code VulkanRenderPass} then
     * throws on the first frame that draws it, and the {@code INVALID} intermediary
     * module stays cached under {@code (id, type, defines)} until
     * {@code ShaderManager#apply} — which preloads these very pipelines through the
     * same cache and would abort startup. So a frame where anything is missing gives
     * up entirely and retries next frame.</p>
     *
     * @return whether both pipelines are compiled and valid, hence safe to submit
     */
    public static boolean prepareLoaderSdfPipelines() {
        if (loaderSdfReady) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }

        RenderPipeline[] pipelines = {
                SdfUIRenderer.SDF_STAR_PIPELINE, SdfUIRenderer.SDF_LOADER_RING_PIPELINE};
        Map<ShaderKey, String> sources = new HashMap<>();
        for (RenderPipeline pipeline : pipelines) {
            if (!collectSource(sources, minecraft, pipeline.getVertexShader(), ShaderType.VERTEX)
                    || !collectSource(sources, minecraft, pipeline.getFragmentShader(), ShaderType.FRAGMENT)) {
                return false;
            }
        }

        ShaderSource shaderSource = (id, type) -> sources.get(new ShaderKey(id, type));
        try {
            for (RenderPipeline pipeline : pipelines) {
                CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, shaderSource);
                if (compiled == null || !compiled.isValid()) {
                    return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }
        loaderSdfReady = true;
        return true;
    }

    private static boolean collectSource(Map<ShaderKey, String> sources, Minecraft minecraft,
                                         Identifier id, ShaderType type) {
        ShaderKey key = new ShaderKey(id, type);
        if (sources.containsKey(key)) {
            return true;
        }
        String source = readShaderSource(minecraft, id, type);
        if (source == null) {
            return false;
        }
        sources.put(key, source);
        return true;
    }

    /** Mod classpath first (available from the very first frame), resource pack as fallback. */
    private static @Nullable String readShaderSource(Minecraft minecraft, Identifier id, ShaderType type) {
        Identifier file = type.idConverter().idToFile(id);
        String classpathPath = "assets/" + file.getNamespace() + "/" + file.getPath();
        try (InputStream in = CustomRendererRegistry.class.getClassLoader().getResourceAsStream(classpathPath)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
        }

        try (Reader reader = minecraft.getResourceManager().getResourceOrThrow(file).openAsReader()) {
            return IOUtils.toString(reader);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record ShaderKey(Identifier id, ShaderType type) {
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
