package geminiclient.gemini.customRenderer.glsl;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import geminiclient.gemini.base.BackgroundSelectorScreen;
import geminiclient.gemini.base.I18n;
import geminiclient.gemini.base.MainMenuScreen;
import geminiclient.gemini.base.alt.AltManagerScreen;
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
import java.util.LinkedHashSet;
import java.util.Set;

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
 * <p>What is warmed, and on which thread:</p>
 * <ul>
 *   <li>Render thread, {@code apply}: the SLUG font pipeline and the
 *       glow/drop-shadow pipeline used by the MD3 ClickGui (and the classic
 *       GUI's shadows), the SDF rounded-rect/icon/loader pipelines, and the
 *       custom region-blur pipeline ({@link CustomBlurRenderer}).</li>
 *   <li>Render thread, {@code apply}: the vanilla {@code minecraft:blur} post
 *       chain — the background blur rendered behind the ClickGui (blur stratum
 *       boundary), and the single most expensive lazy compile on first open.</li>
 *   <li>Worker thread, {@code prepare} ({@code gemini:ui_font_warmup}): the five
 *       MiSans {@link Md3Fonts} faces with printable ASCII, the main menu /
 *       alt manager / background picker sets, plus the full glyph set the
 *       client's own UI draws — the I18n Chinese strings plus ASCII and UI
 *       symbols — for the three MD3 body faces. Still blocking for the reload
 *       (the loading screen does not finish until every UI glyph is ready and
 *       the menu never shows the vanilla-font fallback), but never on the
 *       render thread: {@code GeminiLoadingOverlay} has to keep getting frames
 *       to animate its progress.</li>
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
     * Everything it does is device work that has to run on the render thread;
     * the CPU-heavy glyph build lives in {@link #createFontWarmupListener()}
     * instead, off the render thread.
     */
    public static SimplePreparableReloadListener<Object> createReloadListener() {
        return new SimplePreparableReloadListener<>() {
            @Override
            protected Object prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Object preparations, ResourceManager manager, ProfilerFiller profiler) {
                warmupPipelines();
                warmupBlurPostChain();
                CustomFontRenderer.flushPendingGlyphs();
            }
        };
    }

    /**
     * The reload listener that triangulates the client UI's full glyph set
     * (see {@link #warmupGlyphs()}) as its own item in the loading screen's
     * reload pipeline.
     *
     * <p>The work runs in the reload's <em>prepare</em> stage, i.e. on a
     * resource-load worker thread. In {@code apply} it would run on the render
     * thread and, for the thousands of glyphs the CJK UI set needs, starve
     * {@code GeminiLoadingOverlay} of frames — the loading screen sat on one
     * still image for the whole warmup. Publishing finished geometry
     * ({@code flushPendingGlyphs}) stays on the render thread.</p>
     */
    public static SimplePreparableReloadListener<Object> createFontWarmupListener() {
        return new SimplePreparableReloadListener<>() {
            @Override
            protected Object prepare(ResourceManager manager, ProfilerFiller profiler) {
                warmupGlyphs();
                return null;
            }

            @Override
            protected void apply(Object preparations, ResourceManager manager, ProfilerFiller profiler) {
                try {
                    CustomFontRenderer.flushPendingGlyphs();
                } catch (Throwable t) {
                    LOGGER.warn("[UiWarmup] Client glyph warmup failed; glyphs will tessellate lazily", t);
                }
            }
        };
    }

    /**
     * Compile every custom pipeline the client UI binds. Render-thread only
     * (device calls); safe to call repeatedly, caches make repeats cheap.
     */
    public static void warmupPipelines() {
        long start = System.nanoTime();
        // ── Custom render pipelines (SLUG font + glow shadows + SDF rounded UI) ──
        precompile("font", CustomFontRenderer.FONT_PIPELINE);
        precompile("glow_rect", GlowRenderer.GLOW_PIPELINE);
        precompile("sdf_rounded_rect", SdfUIRenderer.SDF_RECT_PIPELINE);
        precompile("sdf_rounded_shadow", SdfUIRenderer.SDF_SHADOW_PIPELINE);
        precompile("sdf_wavy_ring", SdfUIRenderer.SDF_WAVY_RING_PIPELINE);
        precompile("sdf_md3_icon", SdfUIRenderer.SDF_ICON_PIPELINE);
        precompile("sdf_loader_star", SdfUIRenderer.SDF_STAR_PIPELINE);
        precompile("sdf_loader_ring", SdfUIRenderer.SDF_LOADER_RING_PIPELINE);
        precompile("sdf_triangle", SdfUIRenderer.SDF_TRIANGLE_PIPELINE);

        // ── Custom region blur pipeline ──
        try {
            CustomBlurRenderer.precompile();
        } catch (Throwable t) {
            LOGGER.warn("[UiWarmup] Region-blur pipeline warmup failed; will compile lazily", t);
        }

        LOGGER.info("[UiWarmup] Pipeline warmup finished in {} ms",
                (System.nanoTime() - start) / 1_000_000L);
    }

    /**
     * Vanilla {@code minecraft:blur} post chain — the background blur behind the
     * ClickGui (blur stratum boundary), and the single most expensive lazy
     * compile on first open. Render-thread device work.
     */
    public static void warmupBlurPostChain() {
        try {
            Minecraft.getInstance().getShaderManager()
                    .getPostChain(BLUR_POST_CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
        } catch (Throwable t) {
            LOGGER.warn("[UiWarmup] Blur post-chain warmup failed; will compile lazily", t);
        }
    }

    /**
     * Load the font faces and triangulate every glyph the client's own UI draws
     * (see {@link #warmupClientGlyphs()}). CPU-only: SLUG glyphs are analytic
     * geometry, so nothing here touches GL and the whole pass can run on a
     * reload worker thread. Repeat calls are cheap — faces and finished glyphs
     * are cached.
     */
    public static void warmupGlyphs() {
        long start = System.nanoTime();
        try {
            Md3Fonts.warmup();
            MainMenuScreen.warmup();
            AltManagerScreen.warmup();
            BackgroundSelectorScreen.warmup();
        } catch (Throwable t) {
            LOGGER.warn("[UiWarmup] Font warmup failed; glyphs will tessellate lazily", t);
        }
        try {
            warmupClientGlyphs();
        } catch (Throwable t) {
            LOGGER.warn("[UiWarmup] Client glyph warmup failed; glyphs will tessellate lazily", t);
        }
        LOGGER.info("[UiWarmup] Font/glyph warmup finished in {} ms",
                (System.nanoTime() - start) / 1_000_000L);
    }

    /** UI symbols the client draws besides printable ASCII. */
    private static final String UI_SYMBOL_GLYPHS = "·—↑↓×▶←→";

    /**
     * Triangulates the full glyph set the client's own UI draws — the I18n
     * Chinese strings, printable ASCII and {@link #UI_SYMBOL_GLYPHS} — for
     * the three MD3 faces that render it. Called from the reload prepare
     * stage, so it blocks a worker thread, never the render thread. In
     * English mode the CJK set is empty and ASCII is already cached by
     * {@link Md3Fonts#warmup()}, so this is nearly free there.
     */
    private static void warmupClientGlyphs() {
        Set<Integer> codePoints = new LinkedHashSet<>(I18n.cjkCodepoints());
        for (int cp = 0x20; cp <= 0x7E; cp++) {
            codePoints.add(cp);
        }
        for (int i = 0; i < UI_SYMBOL_GLYPHS.length(); i++) {
            codePoints.add((int) UI_SYMBOL_GLYPHS.charAt(i));
        }
        for (CustomFontRenderer.GlyphFont face : new CustomFontRenderer.GlyphFont[] {
                Md3Fonts.body(), Md3Fonts.label(), Md3Fonts.title()}) {
            if (face == null || face.isDisposed()) {
                continue;
            }
            for (int cp : codePoints) {
                face.getGlyphBlocking(cp);
            }
        }
    }

    private static void precompile(String name, RenderPipeline pipeline) {
        try {
            if (RenderSystem.getCompiledPipelineNullable(pipeline) == null) {
                LOGGER.warn("[UiWarmup] Pipeline {} did not compile; will retry lazily", name);
            }
        } catch (Throwable t) {
            LOGGER.warn("[UiWarmup] Pipeline {} warmup failed; will compile lazily", name, t);
        }
    }
}
