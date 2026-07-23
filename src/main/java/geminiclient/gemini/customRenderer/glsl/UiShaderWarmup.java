package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.RenderSystem;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;

/**
 * Pre-compiles every GPU resource the ClickGui needs on first open, so the
 * compile/link cost is paid during the loading screen instead of causing a
 * visible hitch when the GUI opens for the first time.
 *
 * <p>Registered as a client resource-reload listener via
 * {@code AddClientReloadListenersEvent}. Because reload-listener ordering can
 * place this before vanilla's {@code ShaderManager} apply stage, custom shader
 * sources are read directly from the active resource manager.</p>
 *
 * <p>What is warmed:</p>
 * <ul>
 *   <li>The MSDF font pipeline and the glow/drop-shadow pipeline used by the
 *       MD3 ClickGui (and the classic GUI's shadows).</li>
 *   <li>The custom region-blur pipeline ({@link CustomBlurRenderer}).</li>
 *   <li>The vanilla {@code minecraft:blur} post chain — this is the background
 *       blur rendered behind the ClickGui (blur stratum boundary), and is the
 *       single most expensive lazy compile on first open.</li>
 *   <li>The five Google Sans {@link Md3Fonts} faces plus printable-ASCII MSDF
 *       glyph rasterisation for each — the dominant CPU cost of the first
 *       MD3 frame.</li>
 * </ul>
 *
 * <p>Every step is isolated in its own try/catch: a warmup failure must never
 * break the resource reload — the renderers still lazily compile on demand.</p>
 */
public final class UiShaderWarmup {

    private static final Logger LOGGER = LoggerFactory.getLogger(UiShaderWarmup.class);

    /** Vanilla menu-background-blur post chain (GameRenderer.BLUR_POST_CHAIN_ID). */
    private static final Identifier BLUR_POST_CHAIN_ID = Identifier.withDefaultNamespace("blur");

    private UiShaderWarmup() {
    }

    /**
     * The reload listener to register from {@code AddClientReloadListenersEvent}.
     * The prepare stage does nothing; all work happens in the render-thread
     * apply stage.
     */
    public static SimplePreparableReloadListener<Object> createReloadListener() {
        return new SimplePreparableReloadListener<>() {
            @Override
            protected Object prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Object preparations, ResourceManager manager, ProfilerFiller profiler) {
                warmup(manager);
            }
        };
    }

    /** Run all warmup steps. Safe to call repeatedly (caches make repeats cheap). */
    public static void warmup(ResourceManager resourceManager) {
        long start = System.nanoTime();
        ShaderSource shaderSource = (id, type) -> {
            Identifier location = type.idConverter().idToFile(id);
            try (Reader reader = resourceManager.getResourceOrThrow(location).openAsReader()) {
                return IOUtils.toString(reader);
            } catch (IOException exception) {
                LOGGER.error("[UiWarmup] Couldn't load {} shader source {}", type, location, exception);
                return null;
            }
        };

        // ── Custom render pipelines (MSDF font + glow shadows + SDF rounded UI) ──
        precompile("font", CustomFontRenderer.FONT_PIPELINE, shaderSource);
        precompile("glow_rect", GlowRenderer.GLOW_PIPELINE, shaderSource);
        precompile("sdf_rounded_rect", SdfUIRenderer.SDF_RECT_PIPELINE, shaderSource);
        precompile("sdf_rounded_shadow", SdfUIRenderer.SDF_SHADOW_PIPELINE, shaderSource);
        precompile("sdf_wavy_ring", SdfUIRenderer.SDF_WAVY_RING_PIPELINE, shaderSource);
        precompile("sdf_md3_icon", SdfUIRenderer.SDF_ICON_PIPELINE, shaderSource);

        // ── Custom region blur pipeline ──
        try {
            CustomBlurRenderer.precompile(shaderSource);
        } catch (Throwable t) {
            LOGGER.warn("[UiWarmup] Region-blur pipeline warmup failed; will compile lazily", t);
        }

        // ── Vanilla menu blur post chain (ClickGui background blur) ──
        try {
            Minecraft.getInstance().getShaderManager()
                    .getPostChain(BLUR_POST_CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
        } catch (Throwable t) {
            LOGGER.warn("[UiWarmup] Blur post-chain warmup failed; will compile lazily", t);
        }

        // ── MD3 fonts: load faces + rasterise ASCII glyph set ──
        try {
            Md3Fonts.warmup();
            CustomFontRenderer.flushAllPages();
        } catch (Throwable t) {
            LOGGER.warn("[UiWarmup] Font warmup failed; glyphs will rasterise lazily", t);
        }

        LOGGER.info("[UiWarmup] ClickGui shader/font warmup finished in {} ms",
                (System.nanoTime() - start) / 1_000_000L);
    }

    private static void precompile(String name, RenderPipeline pipeline, ShaderSource shaderSource) {
        try {
            CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, shaderSource);
            if (!compiled.isValid()) {
                LOGGER.warn("[UiWarmup] Pipeline {} compiled invalid", name);
            }
        } catch (Throwable t) {
            LOGGER.warn("[UiWarmup] Pipeline {} warmup failed; will compile lazily", name, t);
        }
    }
}
