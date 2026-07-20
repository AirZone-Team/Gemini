package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-compiles every GPU resource the ClickGui needs on first open, so the
 * compile/link cost is paid during the loading screen instead of causing a
 * visible hitch when the GUI opens for the first time.
 *
 * <p>Registered as a client resource-reload listener via
 * {@code AddClientReloadListenersEvent}. NeoForge automatically sorts mod
 * listeners after the last vanilla listener, so the {@code apply} stage runs
 * on the render thread <b>after</b> the vanilla {@code ShaderManager} — meaning
 * shader sources and post-chain configs are already loaded and the GL pipeline
 * cache has just been cleared (which is also why this re-warms after F3+T).</p>
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
                warmup();
            }
        };
    }

    /** Run all warmup steps. Safe to call repeatedly (caches make repeats cheap). */
    public static void warmup() {
        long start = System.nanoTime();

        // ── Custom render pipelines (MSDF font + glow shadows + SDF rounded UI) ──
        precompile("font", CustomFontRenderer.FONT_PIPELINE);
        precompile("glow_rect", GlowRenderer.GLOW_PIPELINE);
        precompile("sdf_rounded_rect", SdfUIRenderer.SDF_RECT_PIPELINE);
        precompile("sdf_rounded_shadow", SdfUIRenderer.SDF_SHADOW_PIPELINE);
        precompile("sdf_wavy_ring", SdfUIRenderer.SDF_WAVY_RING_PIPELINE);
        precompile("sdf_md3_icon", SdfUIRenderer.SDF_ICON_PIPELINE);

        // ── Custom region blur pipeline ──
        try {
            CustomBlurRenderer.precompile();
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

    private static void precompile(String name, RenderPipeline pipeline) {
        try {
            CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, null);
            if (!compiled.isValid()) {
                LOGGER.warn("[UiWarmup] Pipeline {} compiled invalid; will retry lazily", name);
            }
        } catch (Throwable t) {
            LOGGER.warn("[UiWarmup] Pipeline {} warmup failed; will compile lazily", name, t);
        }
    }
}
