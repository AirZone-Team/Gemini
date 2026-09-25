package geminiclient.gemini.customRenderer.glsl;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;

import com.mojang.blaze3d.PrimitiveTopology;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import geminiclient.gemini.customRenderer.glsl.slug.SlugGeometry;
import geminiclient.gemini.utils.ResourceLocationUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import static geminiclient.gemini.base.MinecraftInstance.mc;

/**
 * GPU-accelerated vector font renderer (SLUG, atlas-free).
 *
 * <p>Glyph outlines are flattened and triangulated on the CPU by
 * {@link SlugGeometry} into the three triangle sets the SLUG pipeline draws: the
 * outline eroded by the anti-aliasing band, and the band itself on both sides of
 * the contour. No distance field is ever rasterised to a texture — each vertex
 * carries its own signed distance to the contour, so the fragment shader
 * reconstructs coverage analytically from that value and its screen-space
 * derivative (~1 px AA band at any zoom) and modulates it by the per-vertex
 * color, which keeps gradient, rainbow and quad-color text working as they did
 * under MTSDF.</p>
 *
 * <p><b>Typography.</b> Outlines are extracted from unhinted vector data at a
 * high raster em (see {@link #TARGET_RASTER_EM}) so stroke contrast and
 * counters stay faithful to the typeface at small point sizes. Advances come
 * from {@code GlyphMetrics} (never from visual bounds), and real GPOS pair
 * kerning is applied between glyphs — extracted lazily through
 * {@link TextLayout} and cached per pair. Measurement
 * ({@link #stringWidth(GlyphFont, String)}) and drawing share the same layout
 * code path, so measured and rendered widths always agree.</p>
 *
 * <p><b>Why analytic coverage rather than textbook SLUG.</b> Valve's SLUG
 * accumulates a winding count in a stencil buffer and converts it to coverage in
 * a second pass. blaze3d exposes no stencil on the GUI framebuffer and
 * {@code GuiRenderer} replays each batch with one blend state, so the coverage
 * contribution of every triangle has to be independent: {@link SlugGeometry}
 * therefore partitions the outline into a solid region and a fringe on either
 * side of the contour that never overlap where they disagree. See that class for
 * the construction.</p>
 *
 * <p><b>What replaced the atlas's metadata texel.</b> Under MTSDF the field
 * range travelled through texel (0,0) because a custom uniform block cannot be
 * populated for GUI elements — {@code GuiRenderer} only ever binds the default
 * Fog and DynamicTransforms UBOs. Here the equivalent per-draw constants ride on
 * vertex attributes, so nothing needs an out-of-band channel.</p>
 */
public class CustomFontRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomFontRenderer.class);

    // ========================
    //  Constants
    // ========================

    private static final int TRIANGLE_VERTS = 3;
    private static final int POS_STRIDE = 2;
    private static final int UV_STRIDE = 2;
    /** Corner colors an emitter writes per glyph: top-left, bottom-left, bottom-right, top-right. */
    private static final int CORNER_COLORS = 4;
    private static final String FALLBACK_FONT_NAME = "SansSerif";

    /**
     * Codepoints at or above this (CJK radicals and blocks, fullwidth forms,
     * Hangul, ...) carry no meaningful GPOS pair kerning, so {@link #kern}
     * short-circuits for them instead of building a {@link TextLayout} per
     * pair. Latin, punctuation and general punctuation below the threshold
     * keep full kerning.
     */
    private static final int KERN_MAX_CODEPOINT = 0x2E80;

    /**
     * Outline sampling resolution, in pixels per em: the geometry is divided
     * back out by it, so it controls only how much floating-point headroom
     * curve flattening and metric extraction get.
     * Clamped to [{@link #MIN_RASTER_SCALE}, {@link #MAX_RASTER_SCALE}] so large
     * fonts do not generate coordinate data by the megabyte.
     */
    private static final float TARGET_RASTER_EM = 96f;
    private static final float MIN_RASTER_SCALE = 4f;
    private static final float MAX_RASTER_SCALE = 12f;

    static float rasterScaleFor(float logicalSize) {
        float scale = TARGET_RASTER_EM / logicalSize;
        return Math.max(MIN_RASTER_SCALE, Math.min(MAX_RASTER_SCALE, scale));
    }

    // ========================
    //  Custom pipeline
    // ========================

    /**
     * Draws glyph geometry as triangles straight from the vertex buffer: no
     * texture is sampled, so no sampler bind group is declared and the state
     * submits {@link TextureSetup#noTexture()}. Culling stays off — the
     * triangulation does not control winding.
     */
    public static final RenderPipeline FONT_PIPELINE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/font"))
            .withVertexShader(ResourceLocationUtils.getIdentifier("core/font"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("core/font"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(FONT_PIPELINE);
    }

    // ========================
    //  Glyph
    // ========================

    public static final class Glyph {
        /**
         * Vertex box — the ink box grown outward by the anti-aliasing band — in
         * logical (screen) units.
         * {@code bearingX/bearingY} are its top-left relative to the pen and the
         * baseline, so they place the geometry without another lookup. Both read
         * zero while {@code hasGeometry} is false, since only the flattened
         * outline knows where the ink starts and stops.
         */
        public final float width, height;
        public final float bearingX, bearingY;
        public final float advanceX;
        /**
         * Interleaved {@code x, y, phi, kind} per vertex, three vertices per
         * triangle, relative to the box top-left; {@code null} precisely when
         * {@code hasGeometry} is false — a glyph whose tessellation is still
         * queued has no geometry yet, and one with no ink never gets any.
         */
        final @Nullable float[] vertices;
        final boolean hasGeometry;
        private final int triangleCount;

        /** Metrics-only: the advance is layout-visible, the rest is not. */
        Glyph(float advanceX) {
            this(0f, 0f, 0f, 0f, advanceX, null);
        }

        private Glyph(float width, float height, float bearingX, float bearingY,
                      float advanceX, @Nullable float[] vertices) {
            this.width = width;
            this.height = height;
            this.bearingX = bearingX;
            this.bearingY = bearingY;
            this.advanceX = advanceX;
            this.vertices = vertices;
            this.hasGeometry = vertices != null;
            this.triangleCount = hasGeometry
                    ? vertices.length / SlugGeometry.FLOATS_PER_VERTEX / TRIANGLE_VERTS
                    : 0;
        }

        /**
         * Copy carrying {@code geometry} and the box it describes. Glyphs are
         * immutable because the box is only well-defined once the outline has
         * been flattened, so {@link #applyGeometry} replaces the cached instance
         * rather than patching this one's metrics behind anyone's back.
         */
        Glyph withGeometry(SlugGeometry.Result geometry) {
            return new Glyph(geometry.boxWidth, geometry.boxHeight,
                    geometry.originX, geometry.originY, advanceX, geometry.vertices);
        }

        int triangleCount() {
            return triangleCount;
        }
    }

    // ========================
    //  GlyphFont
    // ========================

    public static final class GlyphFont {
        final java.awt.Font awtFont;
        /** Same face at {@link #rasterScale}x size — used for outline tessellation. */
        final java.awt.Font rasterFont;
        /**
         * {@link #rasterFont} with GPOS kerning enabled for layout. Used only
         * to measure pair kerns via {@link TextLayout} — AWT applies OpenType
         * kerning to laid-out text only when {@link TextAttribute#KERNING} is
         * requested; plain {@code createGlyphVector} applies none.
         */
        final java.awt.Font kernFont;
        /** Rasterisation scale chosen by {@link CustomFontRenderer#rasterScaleFor}. */
        final float rasterScale;
        final FontRenderContext frc;
        final FontMetrics metrics;
        public final float ascent, descent, lineHeight;
        final Map<Integer, Glyph> glyphs = new HashMap<>();

        /** Lazily extracted pair kerns, in logical (screen) units. Key: prev << 32 | cur. */
        private final Map<Long, Float> kernCache = new HashMap<>();

        /**
         * Set by {@link CustomFontRenderer#dispose}; background tessellation
         * jobs check it before doing work and before their results are applied,
         * so a disposed font never writes geometry back into dropped glyphs.
         */
        volatile boolean disposed;

        public boolean isDisposed() {
            return disposed;
        }

        private java.awt.Font fallbackFont;

        GlyphFont(java.awt.Font awtFont) {
            this.awtFont = awtFont;
            this.rasterScale = rasterScaleFor(awtFont.getSize2D());
            this.rasterFont = awtFont.deriveFont(awtFont.getSize2D() * rasterScale);
            this.kernFont = rasterFont.deriveFont(
                    Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON));

            BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = dummy.createGraphics();
            applyRenderingHints(g2d);
            this.frc = g2d.getFontRenderContext();
            this.metrics = g2d.getFontMetrics(awtFont);
            this.ascent = metrics.getAscent();
            this.descent = metrics.getDescent();
            this.lineHeight = ascent + descent;
            g2d.dispose();
            dummy.flush();
        }

        private void applyRenderingHints(java.awt.Graphics2D g2d) {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // Fractional metrics: subpixel advance/kern precision at the
            // raster size (integer rounding would cost up to 0.5 raster px ≈
            // 0.005 em of spacing error per glyph).
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }

        private java.awt.Font getFallbackFont() {
            if (fallbackFont == null) {
                fallbackFont = new java.awt.Font(FALLBACK_FONT_NAME,
                        java.awt.Font.PLAIN, (int) rasterFont.getSize2D());
            }
            return fallbackFont;
        }

        /**
         * Render-thread lookup used by drawing and measuring. Returns the
         * glyph for {@code codePoint}, creating its metrics on first use and
         * queueing tessellation on the background thread
         * ({@link FontGlyphExecutor}) — the calling frame never waits for
         * geometry. Until it has been applied the glyph reports
         * {@code hasGeometry == false} and the draw path renders it through
         * the vanilla fallback.
         */
        Glyph getOrCreateGlyph(int codePoint) {
            Glyph glyph = glyphs.get(codePoint);
            if (glyph != null) {
                return glyph;
            }
            return createGlyphMetrics(codePoint, false);
        }

        /**
         * Blocking variant for the loading-screen warmup paths: tessellates
         * inline on the calling (render) thread. Only intended where a stall
         * is expected and acceptable (resource-reload apply phase).
         */
        public Glyph getGlyphBlocking(int codePoint) {
            Glyph glyph = glyphs.get(codePoint);
            if (glyph != null) {
                return glyph;
            }
            return createGlyphMetrics(codePoint, true);
        }

        /**
         * Font-path prefix of the old synchronous {@code rasterize}: extracts
         * the typographic advance and either flattens plus triangulates the
         * outline inline (warmup) or hands it to the background executor. The
         * only shared state is the glyph cache itself, and the placeholder it
         * installs carries the advance, so {@link #stringWidth} can lay out a
         * string whose glyphs are still in flight.
         */
        private Glyph createGlyphMetrics(int codePoint, boolean generateSynchronously) {
            java.awt.Font fontToUse = rasterFont;
            if (!rasterFont.canDisplay(codePoint)) {
                fontToUse = getFallbackFont();
            }

            String charStr = new String(Character.toChars(codePoint));
            GlyphVector glyphVector = fontToUse.createGlyphVector(frc, charStr);

            // Typographic advance in logical (screen) units — from glyph
            // metrics (exact with fractional metrics on), never from visual
            // bounds. The raster font is rasterScale times larger.
            float advance = 0f;
            for (int i = 0; i < glyphVector.getNumGlyphs(); i++) {
                advance += glyphVector.getGlyphMetrics(i).getAdvanceX();
            }
            advance /= rasterScale;
            if (advance <= 0) {
                advance = metrics.charWidth(codePoint);
            }

            Rectangle2D visualBounds = glyphVector.getVisualBounds();
            if (visualBounds.getWidth() <= 0 || visualBounds.getHeight() <= 0) {
                // 缺失字形（如 MiSans 没有的符号/emoji）：没有轮廓可三角化。
                // advance 改用 vanilla 字体宽度，使 stringWidth 的测量与
                // drawGrouped 中 vanilla 兜底绘制的推进保持一致。结果缓存，
                // 避免每帧为同一个缺失字符重复走 AWT 测量。
                float fallbackAdvance = mc.font != null ? mc.font.width(charStr) : advance;
                Glyph fallback = new Glyph(fallbackAdvance);
                glyphs.put(codePoint, fallback);
                return fallback;
            }

            // Origin-relative outline in raster units. SlugGeometry grows the
            // ink box by the AA band and reports where the result sits relative
            // to that origin, so no placement offset is computed here.
            Shape outline = glyphVector.getOutline(0, 0);
            Glyph placeholder = new Glyph(advance);
            glyphs.put(codePoint, placeholder);

            if (generateSynchronously) {
                applyGeometry(codePoint, buildGeometry(outline));
                return glyphs.get(codePoint);
            }
            FontGlyphExecutor.submit(
                    new FontGlyphExecutor.PendingGlyph(this, codePoint, outline));
            return placeholder;
        }

        /** Tessellate {@code outline} from this font's raster units into logical ones. */
        SlugGeometry.Result buildGeometry(Shape outline) {
            // bandWidth and tolerance are in input (raster) units and outScale
            // divides them back out, so both are stated here as the logical
            // constant scaled up by the raster factor.
            return SlugGeometry.build(outline,
                    SlugGeometry.AA_MARGIN * rasterScale,
                    SlugGeometry.FLATTEN_TOLERANCE * rasterScale,
                    1.0f / rasterScale);
        }

        /**
         * Render thread only: publishes a finished tessellation by caching the
         * geometry-carrying glyph. Invoked from {@link FontGlyphExecutor#drain}
         * for background results and inline by the blocking warmup path.
         */
        void applyGeometry(int codePoint, SlugGeometry.Result geometry) {
            if (disposed || geometry == null || geometry.isEmpty()) {
                return;
            }
            Glyph glyph = glyphs.get(codePoint);
            if (glyph == null || glyph.hasGeometry) {
                return;
            }
            glyphs.put(codePoint, glyph.withGeometry(geometry));
        }

        /**
         * Pair kerning adjustment in logical (screen) units, added to the pen
         * before drawing {@code cur}. Extracted once per pair through a
         * two-codepoint {@link TextLayout} on {@link #kernFont} (which applies
         * the font's GPOS pair positioning) and cached. Pairs involving
         * codepoints the primary face cannot display get 0 — mixed-face
         * kerning data is meaningless.
         */
        float kern(int prev, int cur) {
            // GPOS pair kerning only exists for Latin/typographic ranges; CJK
            // blocks, fullwidth forms and Hangul have none, so skip the
            // TextLayout round-trip entirely for those pairs (a 20-character
            // Chinese sentence would otherwise build ~190 layouts once each).
            if (prev < 0 || prev >= KERN_MAX_CODEPOINT || cur >= KERN_MAX_CODEPOINT) {
                return 0f;
            }
            long key = ((long) prev << 32) | (cur & 0xFFFFFFFFL);
            Float cached = kernCache.get(key);
            if (cached != null) {
                return cached;
            }
            float k = computeKern(prev, cur);
            kernCache.put(key, k);
            return k;
        }

        private float computeKern(int prev, int cur) {
            if (!rasterFont.canDisplay(prev) || !rasterFont.canDisplay(cur)) {
                return 0f;
            }
            String pair = new String(new int[] { prev, cur }, 0, 2);
            float kerned = new TextLayout(pair, kernFont, frc).getAdvance() / rasterScale;
            float raw = getOrCreateGlyph(prev).advanceX + getOrCreateGlyph(cur).advanceX;
            float k = kerned - raw;
            // Defensive: a broken layout result must never collapse or
            // explode spacing. Legitimate kerns stay well under 50% of the
            // pair's raw width.
            return Math.abs(k) > 0.5f * raw ? 0f : k;
        }

        void ensureReady() {
            FontGlyphExecutor.drain();
        }
    }

    // ========================
    //  Font cache & loading
    // ========================

    private static final Map<String, GlyphFont> FONT_CACHE = new HashMap<>();

    public static GlyphFont loadFont(Identifier path, float size) {
        return loadFont(path, size, java.awt.Font.PLAIN);
    }

    /**
     * Fallback face used when a bundled TTF cannot be loaded. {@code Font.createFont}
     * spools the TTF into {@code java.io.tmpdir} while parsing, so it fails (and used
     * to crash the render thread) when the temp volume is full; a plain logical font
     * touches no disk, so this always succeeds.
     */
    private static GlyphFont fallbackFont(int awtStyle, float size) {
        java.awt.Font base = new java.awt.Font(java.awt.Font.SANS_SERIF, awtStyle, 1);
        return new GlyphFont(base.deriveFont(size));
    }

    public static GlyphFont loadFont(Identifier path, float size, int awtStyle) {
        String key = path.toString() + "@" + size + "@" + awtStyle;
        return FONT_CACHE.computeIfAbsent(key, k -> {
            try (InputStream is = mc.getResourceManager()
                    .getResource(path).orElseThrow().open()) {
                java.awt.Font base = java.awt.Font.createFont(
                        java.awt.Font.TRUETYPE_FONT, is);
                return new GlyphFont(base.deriveFont(awtStyle, size));
            } catch (Exception e) {
                LOGGER.warn("[Font] Failed to load {}: {} — using system fallback", path, e.toString());
                return fallbackFont(awtStyle, size);
            }
        });
    }

    public static GlyphFont loadFont(InputStream in, float size) {
        try {
            java.awt.Font base = java.awt.Font.createFont(
                    java.awt.Font.TRUETYPE_FONT, in);
            return new GlyphFont(base.deriveFont(size));
        } catch (Exception e) {
            LOGGER.warn("[Font] Failed to load font from stream: {} — using system fallback", e.toString());
            return fallbackFont(java.awt.Font.PLAIN, size);
        }
    }

    public static GlyphFont loadFont(File file, float size) {
        String key = file.getAbsolutePath() + "@" + size;
        return FONT_CACHE.computeIfAbsent(key, k -> {
            try (FileInputStream fis = new FileInputStream(file)) {
                java.awt.Font base = java.awt.Font.createFont(
                        java.awt.Font.TRUETYPE_FONT, fis);
                return new GlyphFont(base.deriveFont(size));
            } catch (Exception e) {
                LOGGER.warn("[Font] Failed to load {}: {} — using system fallback",
                        file.getAbsolutePath(), e.toString());
                return fallbackFont(java.awt.Font.PLAIN, size);
            }
        });
    }

    public static GlyphFont fromAwtFont(java.awt.Font awtFont) {
        return new GlyphFont(awtFont);
    }

    // ========================
    //  Measurement
    // ========================

    /**
     * Width of the string in logical (screen) units: per-glyph typographic
     * advances plus pair kerning — the same layout code path as
     * {@link #drawString(GuiGraphicsExtractor, GlyphFont, String, float, float, IntFunction)},
     * so measured and rendered widths always agree.
     *
     * <p>Measurement resolves glyph metrics only and never waits for a
     * tessellation, so measuring a string full of unseen characters queues the
     * work on the background thread without stalling the render thread.</p>
     */
    public static float stringWidth(GlyphFont font, String text) {
        float width = 0f;
        int prevCp = -1;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            width += font.kern(prevCp, codePoint)
                    + font.getOrCreateGlyph(codePoint).advanceX;
            prevCp = codePoint;
            i += Character.charCount(codePoint);
        }
        return width;
    }

    public static int stringWidth(Font font, String text) {
        return font.width(text);
    }

    /**
     * Vertical center of a string's visible ink when drawn at {@code y}
     * (the line-box top used by {@code drawString}).
     *
     * <p>The geometry box is the ink box grown outward by the uniform AA
     * band, so, as under the atlas, the quad midpoint is the ink midpoint;
     * only miter clamping at a sharp corner can skew it, by well under a
     * logical pixel.
     * Decorations such as indicator bars should center on this, not on
     * {@code lineHeight / 2}: the line box adds ascent headroom above
     * Latin caps and descent below the baseline, and CJK glyphs sit low
     * in that box, so a line-box-centered bar rides visibly high.</p>
     */
    public static float stringInkCenterY(GlyphFont font, String text, float y) {
        if (font == null) {
            return y;
        }
        if (text == null || text.isEmpty()) {
            return y + font.lineHeight / 2f;
        }
        float baseline = Math.round(y + font.ascent) + 0.5f;
        float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Glyph glyph = font.getGlyphBlocking(cp);
            if (glyph.width <= 0f || glyph.height <= 0f) {
                continue; // 缺失字形无 ink（由 vanilla 字体兜底绘制）
            }
            top = Math.min(top, glyph.bearingY);
            bottom = Math.max(bottom, glyph.bearingY + glyph.height);
        }
        if (top > bottom) {
            return y + font.lineHeight / 2f;
        }
        return baseline + (top + bottom) / 2f;
    }

    // ========================
    //  MC Font drawing
    // ========================

    public static void drawString(GuiGraphicsExtractor gui, Font font,
                                  String text, float x, float y,
                                  IntFunction<Integer> colorFunc) {
        float cursorX = x;
        int charIndex = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            int color = colorFunc.apply(charIndex);
            if ((color >>> 24) != 0) {
                gui.text(font, ch, (int) cursorX, (int) y, color, false);
            }
            cursorX += font.width(ch);
            i += Character.charCount(codePoint);
            charIndex++;
        }
    }

    public static void drawString(GuiGraphicsExtractor gui, Font font,
                                  String text, float x, float y, int color) {
        drawString(gui, font, text, x, y, __ -> color);
    }

    public static void drawGradientString(GuiGraphicsExtractor gui, Font font,
                                          String text, float x, float y,
                                          int topColor, int bottomColor) {
        drawGradientString(gui, font, text, x, y, __ -> topColor, __ -> bottomColor);
    }

    public static void drawGradientString(GuiGraphicsExtractor gui, Font font,
                                          String text, float x, float y,
                                          IntFunction<Integer> topFunc,
                                          IntFunction<Integer> botFunc) {
        float cursorX = x;
        float halfLineHeight = font.lineHeight / 2f;
        int charIndex = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            int charWidth = font.width(ch);
            int top = topFunc.apply(charIndex);
            int bot = botFunc.apply(charIndex);

            int xi = (int) cursorX;
            int yi = (int) y;

            if ((top >>> 24) != 0) {
                gui.enableScissor(xi, yi, xi + charWidth, (int) (yi + halfLineHeight));
                gui.text(font, ch, xi, yi, top, false);
                gui.disableScissor();
            }
            if ((bot >>> 24) != 0) {
                gui.enableScissor(xi, (int) (yi + halfLineHeight), xi + charWidth,
                        yi + font.lineHeight);
                gui.text(font, ch, xi, yi, bot, false);
                gui.disableScissor();
            }

            cursorX += charWidth;
            i += Character.charCount(codePoint);
            charIndex++;
        }
    }

    // ========================
    //  GlyphFont drawing (GLSL pipeline)
    // ========================

    public static void drawString(GuiGraphicsExtractor gui, GlyphFont font,
                                  String text, float x, float y, int color) {
        drawString(gui, font, text, x, y, __ -> color);
    }

    public static void drawString(GuiGraphicsExtractor gui, GlyphFont font,
                                  String text, float x, float y,
                                  IntFunction<Integer> colorFunc) {
        if (text.isEmpty()) {
            return;
        }
        prefetch(font, text);
        font.ensureReady();
        drawGrouped(gui, font, text, x, y, uniformEmitter(colorFunc));
    }

    public static void drawGradientString(GuiGraphicsExtractor gui,
                                          GlyphFont font, String text,
                                          float x, float y,
                                          int topColor, int bottomColor) {
        drawGradientString(gui, font, text, x, y, _ -> topColor, _ -> bottomColor);
    }

    public static void drawGradientString(GuiGraphicsExtractor gui,
                                          GlyphFont font, String text,
                                          float x, float y,
                                          IntFunction<Integer> topFunc,
                                          IntFunction<Integer> botFunc) {
        if (text.isEmpty()) {
            return;
        }
        prefetch(font, text);
        font.ensureReady();
        drawGrouped(gui, font, text, x, y, gradientEmitter(topFunc, botFunc));
    }

    public static void drawHorizontalGradientString(GuiGraphicsExtractor gui,
                                                    GlyphFont font,
                                                    String text, float x,
                                                    float y,
                                                    IntFunction<Integer> leftFunc,
                                                    IntFunction<Integer> rightFunc) {
        if (text.isEmpty()) {
            return;
        }
        prefetch(font, text);
        font.ensureReady();
        drawGrouped(gui, font, text, x, y, horizontalGradientEmitter(leftFunc, rightFunc));
    }

    public interface FourColorFunc {
        int topLeft(int charIndex);
        int topRight(int charIndex);
        int bottomLeft(int charIndex);
        int bottomRight(int charIndex);
    }

    public static FourColorFunc uniform(IntFunction<Integer> f) {
        return new FourColorFunc() {
            public int topLeft(int i)     { return f.apply(i); }
            public int topRight(int i)    { return f.apply(i); }
            public int bottomLeft(int i)  { return f.apply(i); }
            public int bottomRight(int i) { return f.apply(i); }
        };
    }

    public static FourColorFunc gradient(IntFunction<Integer> top,
                                         IntFunction<Integer> bot) {
        return new FourColorFunc() {
            public int topLeft(int i)     { return top.apply(i); }
            public int topRight(int i)    { return top.apply(i); }
            public int bottomLeft(int i)  { return bot.apply(i); }
            public int bottomRight(int i) { return bot.apply(i); }
        };
    }

    public static void drawQuadGradientString(GuiGraphicsExtractor gui,
                                              GlyphFont font, String text,
                                              float x, float y,
                                              FourColorFunc f) {
        if (text.isEmpty()) {
            return;
        }
        prefetch(font, text);
        font.ensureReady();
        drawGrouped(gui, font, text, x, y, quadEmitter(f));
    }

    public static void drawRainbowString(GuiGraphicsExtractor gui,
                                         GlyphFont font, String text,
                                         float x, float y, long speedMs,
                                         float sat, float bri, int alpha) {
        int length = Math.max(1, (int) text.codePoints().count());
        drawString(gui, font, text, x, y, i -> {
            float hue = ((System.currentTimeMillis() % speedMs)
                    / (float) speedMs + (float) i / length) % 1.0f;
            int rgb = Color.HSBtoRGB(hue, sat, bri);
            return (alpha << 24) | (rgb & 0xFFFFFF);
        });
    }

    public static void drawRainbowGradientString(GuiGraphicsExtractor gui,
                                                 GlyphFont font, String text,
                                                 float x, float y,
                                                 long speedMs, float sat,
                                                 float bri, int alpha) {
        int length = Math.max(1, (int) text.codePoints().count());
        drawGradientString(gui, font, text, x, y,
                i -> {
                    float hue = ((System.currentTimeMillis() % speedMs)
                            / (float) speedMs + (float) i / length) % 1.0f;
                    int rgb = Color.HSBtoRGB(hue, sat, bri);
                    return (alpha << 24) | (rgb & 0xFFFFFF);
                },
                i -> {
                    float hue = ((System.currentTimeMillis() % speedMs)
                            / (float) speedMs + (float) i / length
                            + 0.08f) % 1.0f;
                    int rgb = Color.HSBtoRGB(hue, sat, bri);
                    return (alpha << 24) | (rgb & 0xFFFFFF);
                });
    }

    // ========================
    //  Color emitters
    // ========================

    @FunctionalInterface
    private interface ColorEmitter {
        /**
         * Fill {@code dst} from {@code dstOffset} with the glyph box's corner
         * colors as packed ARGB, ordered top-left, bottom-left, bottom-right,
         * top-right — the order {@link #vertexColor} blends across.
         */
        void emit(int charIndex, Glyph glyph, int[] dst, int dstOffset);
    }

    private static final int CORNER_TL = 0, CORNER_BL = 1, CORNER_BR = 2, CORNER_TR = 3;

    private static ColorEmitter uniformEmitter(IntFunction<Integer> colorFunc) {
        return (charIndex, glyph, dst, offset) -> {
            int color = colorFunc.apply(charIndex);
            dst[offset] = color;
            dst[offset + 1] = color;
            dst[offset + 2] = color;
            dst[offset + 3] = color;
        };
    }

    private static ColorEmitter gradientEmitter(IntFunction<Integer> topFunc,
                                                IntFunction<Integer> botFunc) {
        return (charIndex, glyph, dst, offset) -> {
            int top = topFunc.apply(charIndex);
            int bottom = botFunc.apply(charIndex);
            dst[offset + CORNER_TL] = top;
            dst[offset + CORNER_BL] = bottom;
            dst[offset + CORNER_BR] = bottom;
            dst[offset + CORNER_TR] = top;
        };
    }

    private static ColorEmitter horizontalGradientEmitter(
            IntFunction<Integer> leftFunc, IntFunction<Integer> rightFunc) {
        return (charIndex, glyph, dst, offset) -> {
            int left = leftFunc.apply(charIndex);
            int right = rightFunc.apply(charIndex);
            dst[offset + CORNER_TL] = left;
            dst[offset + CORNER_BL] = left;
            dst[offset + CORNER_BR] = right;
            dst[offset + CORNER_TR] = right;
        };
    }

    private static ColorEmitter quadEmitter(FourColorFunc f) {
        return (charIndex, glyph, dst, offset) -> {
            dst[offset + CORNER_TL] = f.topLeft(charIndex);
            dst[offset + CORNER_BL] = f.bottomLeft(charIndex);
            dst[offset + CORNER_BR] = f.bottomRight(charIndex);
            dst[offset + CORNER_TR] = f.topRight(charIndex);
        };
    }

    /**
     * Color of one glyph vertex: the bilinear blend of the emitter's corner
     * colors at normalized box coordinates {@code (u, v)}. A distance field
     * could leave this blend to the rasteriser, because its quad had a vertex
     * exactly at each corner; triangle vertices land wherever the contour says,
     * so the gradient is sampled here instead — one arithmetic blend per vertex,
     * at most a few thousand per string.
     */
    private static int vertexColor(int[] corners, float u, float v) {
        int argb = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            float top = channel(corners[CORNER_TL], shift)
                    + u * (channel(corners[CORNER_TR], shift) - channel(corners[CORNER_TL], shift));
            float bottom = channel(corners[CORNER_BL], shift)
                    + u * (channel(corners[CORNER_BR], shift) - channel(corners[CORNER_BL], shift));
            argb |= Math.round(top + v * (bottom - top)) << shift;
        }
        return argb;
    }

    private static float channel(int argb, int shift) {
        return (argb >>> shift) & 0xFF;
    }

    // ========================
    //  Internal rendering
    // ========================

    private static void prefetch(GlyphFont font, String text) {
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            // Metrics-only: unseen glyphs get a background tessellation queued,
            // so this frame renders them through the vanilla fallback instead
            // of stalling the render thread on the triangulation.
            font.getOrCreateGlyph(codePoint);
            i += Character.charCount(codePoint);
        }
    }

    private record GlyphRun(int charIndex, int codePoint, Glyph glyph, float x) {}

    /**
     * Lay {@code text} out on the pen and submit it: every glyph with geometry
     * becomes one contiguous triangle list in a single render state (no page to
     * break the batch over), and the rest fall back to the vanilla font.
     */
    private static void drawGrouped(GuiGraphicsExtractor gui, GlyphFont font,
                                    String text, float x, float y,
                                    ColorEmitter emitter) {
        List<GlyphRun> runs = new ArrayList<>();
        List<GlyphRun> fallbackRuns = new ArrayList<>();
        float cursorX = x;
        int prevCp = -1;
        int charIndex = 0;
        int vertexCount = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            Glyph glyph = font.getOrCreateGlyph(codePoint);
            cursorX += font.kern(prevCp, codePoint);
            if (glyph.hasGeometry) {
                runs.add(new GlyphRun(charIndex, codePoint, glyph, cursorX));
                vertexCount += glyph.triangleCount() * TRIANGLE_VERTS;
            } else {
                // 字形缺失（如 MiSans 未收录的符号/emoji）或尚未完成三角化：
                // 改由 vanilla 字体逐个绘制兜底，避免出现空白；光标按
                // glyph.advanceX 推进，与 stringWidth 的测量保持一致。
                fallbackRuns.add(new GlyphRun(charIndex, codePoint, glyph, cursorX));
            }
            cursorX += glyph.advanceX;
            prevCp = codePoint;
            i += Character.charCount(codePoint);
            charIndex++;
        }
        if (runs.isEmpty() && fallbackRuns.isEmpty()) {
            return;
        }

        float baselineY = y + font.ascent;
        // 基线统一对齐到像素，确保所有字形在同一条线上
        float snappedBaselineY = snapToPixel(baselineY);

        // 兜底字形：vanilla 字体文本顶部约在基线之上 7px（lineHeight 9、
        // ascent 7），按该值对齐到同一像素基线。
        if (!fallbackRuns.isEmpty() && mc.font != null) {
            float vanillaTop = snappedBaselineY - 7f;
            int[] tmpColor = new int[CORNER_COLORS];
            for (GlyphRun run : fallbackRuns) {
                emitter.emit(run.charIndex, run.glyph, tmpColor, 0);
                String ch = new String(Character.toChars(run.codePoint));
                gui.text(mc.font, ch, Math.round(run.x), Math.round(vanillaTop),
                        tmpColor[CORNER_TL], false);
            }
        }

        if (runs.isEmpty()) {
            return;
        }

        float[] positions = new float[vertexCount * POS_STRIDE];
        float[] uvs       = new float[vertexCount * UV_STRIDE];
        int[]   colors    = new int[vertexCount];

        int vi = 0;
        int[] corners = new int[CORNER_COLORS];
        for (GlyphRun run : runs) {
            Glyph glyph = run.glyph;
            // Box top-left in screen space; the geometry underneath is stated
            // relative to it and already spans the AA band.
            float x0 = run.x + glyph.bearingX;
            float y0 = snappedBaselineY + glyph.bearingY;
            emitter.emit(run.charIndex, glyph, corners, 0);
            float invW = 1f / glyph.width;
            float invH = 1f / glyph.height;

            float[] geometry = glyph.vertices;
            for (int f = 0; f < geometry.length; f += SlugGeometry.FLOATS_PER_VERTEX, vi++) {
                float lx = geometry[f];
                float ly = geometry[f + 1];
                int p = vi * POS_STRIDE;
                positions[p] = x0 + lx;
                positions[p + 1] = y0 + ly;
                int t = vi * UV_STRIDE;
                uvs[t] = geometry[f + 2];      // signed distance to the contour
                uvs[t + 1] = geometry[f + 3];  // solid / fringe selector
                colors[vi] = vertexColor(corners, lx * invW, ly * invH);
            }
        }

        gui.submitGuiElementRenderState(new FontRenderState(new Matrix3x2f(gui.pose()),
                positions, uvs, colors, vertexCount, gui.peekScissorStack()));
    }

    private static float snapToPixel(float v) {
        return Math.round(v) + 0.5f;
    }

    // ========================
    //  Font render state (GLSL pipeline)
    // ========================

    private static final class FontRenderState implements GuiElementRenderState {
        private static final TextureSetup NO_TEXTURE = TextureSetup.noTexture();

        private final Matrix3x2f pose;
        private final float[] positions;
        private final float[] uvs;
        private final int[] colors;
        private final int vertexCount;
        @Nullable private final ScreenRectangle scissor;
        @Nullable private final ScreenRectangle bounds;

        FontRenderState(Matrix3x2f pose, float[] positions, float[] uvs,
                        int[] colors, int vertexCount,
                        @Nullable ScreenRectangle scissor) {
            this.pose = pose;
            this.positions = positions;
            this.uvs = uvs;
            this.colors = colors;
            this.vertexCount = vertexCount;
            this.scissor = scissor;
            this.bounds = computeBounds(scissor);
        }

        private ScreenRectangle computeBounds(@Nullable ScreenRectangle scissor) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            int coordinates = vertexCount * POS_STRIDE;
            for (int i = 0; i < coordinates; i += 2) {
                float px = positions[i];
                float py = positions[i + 1];
                if (px < minX) minX = px;
                if (py < minY) minY = py;
                if (px > maxX) maxX = px;
                if (py > maxY) maxY = py;
            }
            int ix = (int) Math.floor(minX);
            int iy = (int) Math.floor(minY);
            int iw = (int) Math.ceil(maxX) - ix;
            int ih = (int) Math.ceil(maxY) - iy;
            ScreenRectangle bounds = new ScreenRectangle(
                    ix, iy, Math.max(1, iw), Math.max(1, ih));
            return scissor != null ? scissor.intersection(bounds) : bounds;
        }

        @Override
        public void buildVertices(@NonNull VertexConsumer vc) {
            for (int v = 0, pi = 0, ui = 0; v < vertexCount; v++, pi += 2, ui += 2) {
                vc.addVertexWith2DPose(pose, positions[pi], positions[pi + 1])
                        .setUv(uvs[ui], uvs[ui + 1])
                        .setColor(colors[v]);
            }
        }

        @Override
        public @NonNull RenderPipeline pipeline() {
            return FONT_PIPELINE;
        }

        @Override
        public @NonNull TextureSetup textureSetup() {
            return NO_TEXTURE;
        }

        @Override
        @Nullable
        public ScreenRectangle scissorArea() {
            return scissor;
        }

        @Override
        @Nullable
        public ScreenRectangle bounds() {
            return bounds;
        }
    }

    // ========================
    //  Cleanup
    // ========================

    public static void dispose(GlyphFont font) {
        // Queued jobs and in-flight results are dropped by the disposed check,
        // so the flag has to go up before the caches do. Nothing else is
        // released: glyph geometry is plain heap data with no GPU counterpart.
        font.disposed = true;
        font.glyphs.clear();
        font.kernCache.clear();
    }

    /**
     * Publish tessellations the background thread has finished. Called once per
     * frame by the render loop and before every warmup pass, so a glyph never
     * stays invisible longer than the frame that requested it.
     */
    public static void flushPendingGlyphs() {
        FontGlyphExecutor.drain();
    }

    public static void disposeAll() {
        FONT_CACHE.values().forEach(CustomFontRenderer::dispose);
        FONT_CACHE.clear();
    }
}
