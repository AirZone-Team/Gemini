package geminiclient.gemini.customRenderer.glsl;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;

import com.mojang.blaze3d.PrimitiveTopology;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import geminiclient.gemini.customRenderer.glsl.msdf.MsdfGenerator;
import geminiclient.gemini.utils.ResourceLocationUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import static geminiclient.gemini.base.MinecraftInstance.mc;

/**
 * GPU-accelerated vector font renderer (MTSDF).
 *
 * <p>Glyph outlines are converted to multi-channel signed distance fields on
 * the CPU by {@link MsdfGenerator} (a faithful msdfgen port) and uploaded to a
 * GPU texture atlas. The atlas is MTSDF layout: R/G/B hold the per-edge-class
 * distance fields whose median reconstructs the true edge distance (sharp
 * corners at any zoom), and the alpha channel holds the true signed distance
 * which keeps a usable gradient where the median saturates (minification,
 * soft effects). Rendering uses a dedicated {@link RenderPipeline}
 * ({@link #FONT_PIPELINE}) whose fragment shader performs every per-fragment
 * step: atlas sampling, distance reconstruction, derivative-based
 * anti-aliasing (~1 px AA band at any zoom), and per-vertex color modulation —
 * enabling gradient, rainbow, and quad-color text with minimal CPU overhead.</p>
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
 * <p><b>No parameter is hardcoded on both sides of the Java/GLSL boundary.</b>
 * The atlas is self-describing: texel (0,0) of every page stores the field
 * range ({@link MsdfGenerator#RANGE}, the single source of truth) which the
 * fragment shader reads back with {@code texelFetch}; the atlas size comes
 * from {@code textureSize()} and the on-screen scale from {@code fwidth()}.
 * MC's {@code GuiRenderer} only ever binds the default UBOs (Fog,
 * DynamicTransforms), so a custom uniform block cannot be populated for GUI
 * elements — a metadata texel is the robust channel for this value.</p>
 */
public class CustomFontRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomFontRenderer.class);

    // ========================
    //  Constants
    // ========================

    private static final int ATLAS_SIZE = 4096;
    private static final int QUAD_VERTS = 4;
    private static final int POS_STRIDE = 2;
    private static final int UV_STRIDE = 2;
    private static final int COL_STRIDE = 4;
    private static final String FALLBACK_FONT_NAME = "SansSerif";

    // MSDF generation parameters. MsdfGenerator.RANGE is the single source of
    // truth for the field range. The representable extent is RANGE/2 on
    // either side of the contour; two extra texels isolate bilinear samples.
    private static final int SDF_PADDING =
            (int) Math.ceil(MsdfGenerator.RANGE * 0.5) + 2;

    /**
     * Raster resolution targeted when converting outlines to distance fields,
     * in pixels per em. Field quality is governed by the <em>raster
     * resolution of the outline</em>, not the logical font size: below ~60 px
     * em the per-channel distance fields alias against each other (short edge
     * vectors trigger false corner splits in edge coloring), and the
     * reconstructed median wobbles — visible as lumpy contours and uneven
     * stroke weight at 8–12 pt. 96 px em leaves comfortable headroom for the
     * smallest HUD fonts. Each {@link GlyphFont} derives its own raster scale
     * so small fonts are rasterised larger; the fragment shader is
     * scale-agnostic because {@code screenPxRange()} is derivative-based.
     * Clamped to [{@link #MIN_RASTER_SCALE}, {@link #MAX_RASTER_SCALE}] to
     * bound atlas usage and generation cost for large fonts.
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

    public static final RenderPipeline FONT_PIPELINE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/font"))
            .withVertexShader(ResourceLocationUtils.getIdentifier("core/font"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("core/font"))
            .withBindGroupLayout(GeminiRenderPipelines.samplers("Sampler0"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(FONT_PIPELINE);
    }

    // ========================
    //  Glyph
    // ========================

    public static final class Glyph {
        public float u0, v0, u1, v1;
        /** Glyph quad dimensions (includes SDF padding) — used for both screen quad and UV mapping. */
        public final float width, height;
        public final float bearingX, bearingY;
        public final float advanceX;
        boolean hasImage;
        int pageIndex;

        Glyph(float width, float height, float bearingX, float bearingY, float advanceX,
              boolean hasImage, int pageIndex) {
            this.width = width;
            this.height = height;
            this.bearingX = bearingX;
            this.bearingY = bearingY;
            this.advanceX = advanceX;
            this.hasImage = hasImage;
            this.pageIndex = pageIndex;
        }
    }

    // ========================
    //  Atlas
    // ========================

    static final class AtlasPage {
        final Identifier textureId;
        FontAtlasTexture texture;
        NativeImage nativeImage;
        float invW, invH;

        AtlasPage(Identifier textureId) {
            this.textureId = textureId;
        }

        TextureSetup textureSetup() {
            return TextureSetup.singleTexture(
                    texture.getTextureView(), texture.getSampler());
        }
    }

    /**
     * Linear, clamp-to-edge atlas texture.
     *
     * <p>Reflecting {@link DynamicTexture}'s sampler field by name fails in an
     * obfuscated production jar and silently leaves nearest-neighbour
     * filtering enabled. Protected-field access is remapped normally.</p>
     */
    private static final class FontAtlasTexture extends DynamicTexture {
        FontAtlasTexture(Identifier textureId, NativeImage image) {
            super(textureId::toDebugFileName, image);
            this.sampler = RenderSystem.getSamplerCache()
                    .getClampToEdge(FilterMode.LINEAR);
        }
    }

    private record AtlasSlot(int px, int py, int pw, int ph) {}

    // ========================
    //  GlyphFont
    // ========================

    public static final class GlyphFont {
        final java.awt.Font awtFont;
        /** Same face at {@link #rasterScale}x size — used for outline/MSDF generation. */
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

        final List<AtlasPage> pages = new ArrayList<>();
        int currentPageIdx = -1;

        int cursorX, cursorY, rowHeight;
        final Map<Integer, AtlasSlot> pendingSlots = new HashMap<>();
        final Map<Integer, byte[]> pendingMsdfData = new HashMap<>();

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

            newCpuPage();
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

        private void newCpuPage() {
            // Texel (0,0) is reserved for the field-range metadata read by the
            // fragment shader — glyph packing starts one texel in.
            cursorX = 1;
            cursorY = 0;
            rowHeight = 0;

            AtlasPage page = new AtlasPage(
                    ResourceLocationUtils.getIdentifier("font_atlas_" + ATLAS_ID.getAndIncrement()));
            page.invW = 1.0f / ATLAS_SIZE;
            page.invH = 1.0f / ATLAS_SIZE;

            // Zero-filled: every unwritten texel reads as 0 = "maximally
            // outside", consistent with the field's exterior clamp (the
            // metadata texel at (0,0) is written explicitly right after).
            NativeImage nativeImage = new NativeImage(ATLAS_SIZE, ATLAS_SIZE, true);
            writeMetadataTexel(nativeImage);
            page.nativeImage = nativeImage;
            page.texture = new FontAtlasTexture(page.textureId, nativeImage);
            mc.getTextureManager().register(page.textureId, page.texture);

            pages.add(page);
            currentPageIdx = pages.size() - 1;
        }

        /**
         * Writes the self-describing atlas metadata into texel (0,0):
         * {@code round(RANGE * METADATA_RANGE_SCALE)} is stored as unsigned
         * 16-bit fixed point in R (low byte) and G (high byte). font.fsh reads
         * it back with
         * {@code texelFetch(Sampler0, ivec2(0,0), 0)}, so the shader's
         * {@code pxRange} always matches the generator — no mirrored constant.
         * All other unwritten texels stay 0 = "maximally outside", consistent
         * with the field's exterior clamp, so bilinear bleed at cell borders
         * is harmless.
         */
        private static void writeMetadataTexel(NativeImage image) {
            int encoded = (int) Math.round(MsdfGenerator.RANGE * MsdfGenerator.METADATA_RANGE_SCALE);
            int low = encoded & 0xFF;
            int high = (encoded >>> 8) & 0xFF;
            // NativeImage stores ABGR: A=bits31..24, B=23..16, G=15..8, R=7..0
            image.setPixel(0, 0, (0xFF << 24) | (high << 8) | low);
        }

        public Glyph getGlyph(int codePoint) {
            Glyph glyph = glyphs.get(codePoint);
            if (glyph != null) {
                return glyph;
            }
            return rasterize(codePoint);
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
            if (prev < 0) {
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
            float raw = getGlyph(prev).advanceX + getGlyph(cur).advanceX;
            float k = kerned - raw;
            // Defensive: a broken layout result must never collapse or
            // explode spacing. Legitimate kerns stay well under 50% of the
            // pair's raw width.
            return Math.abs(k) > 0.5f * raw ? 0f : k;
        }

        private Glyph rasterize(int codePoint) {
            if (pages.isEmpty()) {
                newCpuPage();
            }

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
            double visualW = visualBounds.getWidth();
            double visualH = visualBounds.getHeight();
            if (visualW <= 0 || visualH <= 0) {
                return new Glyph(0, 0, 0, 0, advance, false, -1);
            }

            int cellWidth = (int) Math.ceil(visualW) + SDF_PADDING * 2;
            int cellHeight = (int) Math.ceil(visualH) + SDF_PADDING * 2;

            if (cursorX + cellWidth > ATLAS_SIZE) {
                cursorX = 0;
                cursorY += rowHeight;
                rowHeight = 0;
            }

            if (cursorY + cellHeight > ATLAS_SIZE) {
                flushCurrentPage();
                newCpuPage();
            }

            int cellX = cursorX;
            int cellY = cursorY;

            // Exact float placement: the ink's top-left lands precisely at
            // (PADDING, PADDING) in the cell, so the field and the screen
            // quad derived from the same visualBounds never drift apart
            // (rounding the draw offset would misplace the outline by up to
            // half a raster pixel relative to the quad).
            float drawX = SDF_PADDING - (float) visualBounds.getX();
            float drawY = SDF_PADDING - (float) visualBounds.getY();

            // --- MTSDF generation: vector outline -> multi-channel + true SDF ---
            Shape outline = glyphVector.getOutline(drawX, drawY);
            // Per channel: 0 = RANGE/2 px outside, 128 = on the edge,
            // 255 = RANGE/2 px inside. The shader reads median(R, G, B),
            // while A retains the true SDF for effects that require it.
            byte[] msdfPixels = MsdfGenerator.generate(outline, cellWidth, cellHeight);

            // Build the Glyph and register in maps BEFORE flushCurrentPage
            // (flushCurrentPage needs glyphs.get() to succeed).
            // Screen-space quad size and bearings are the atlas-cell
            // dimensions divided by the raster scale.
            Glyph glyph = new Glyph(
                    cellWidth / rasterScale,
                    cellHeight / rasterScale,
                    ((float) visualBounds.getX() - SDF_PADDING) / rasterScale,
                    ((float) visualBounds.getY() - SDF_PADDING) / rasterScale,
                    advance, true, -1);
            glyphs.put(codePoint, glyph);
            pendingMsdfData.put(codePoint, msdfPixels);
            pendingSlots.put(codePoint, new AtlasSlot(cellX, cellY, cellWidth, cellHeight));

            cursorX += cellWidth;
            if (cellHeight > rowHeight) {
                rowHeight = cellHeight;
            }

            return glyph;
        }

        private void flushCurrentPage() {
            if (pendingSlots.isEmpty() || currentPageIdx < 0) {
                return;
            }

            AtlasPage page = pages.get(currentPageIdx);
            NativeImage nativeImage = page.nativeImage;
            float invW = page.invW;
            float invH = page.invH;

            for (Map.Entry<Integer, AtlasSlot> entry : pendingSlots.entrySet()) {
                int codePoint = entry.getKey();
                Glyph glyph = glyphs.get(codePoint);
                if (glyph == null) {
                    LOGGER.warn("[MTSDF] flushCurrentPage: glyph NULL for codepoint {}, UVs will NOT be set!", codePoint);
                    continue;
                }
                AtlasSlot slot = entry.getValue();

                glyph.u0 = slot.px * invW;
                glyph.v0 = slot.py * invH;
                glyph.u1 = (slot.px + slot.pw) * invW;
                glyph.v1 = (slot.py + slot.ph) * invH;
                glyph.pageIndex = currentPageIdx;

                // Upload MTSDF bytes to NativeImage (RGBA, 4 bytes per pixel).
                // NativeImage stores ABGR: A=bits31..24, B=23..16, G=15..8, R=7..0
                byte[] msdfPixels = pendingMsdfData.get(codePoint);
                if (msdfPixels != null) {
                    for (int y = 0; y < slot.ph; y++) {
                        int rowBase = y * slot.pw * 4;
                        int dstY = slot.py + y;
                        for (int x = 0; x < slot.pw; x++) {
                            int i = rowBase + x * 4;
                            int r = msdfPixels[i] & 0xFF;
                            int g = msdfPixels[i + 1] & 0xFF;
                            int b = msdfPixels[i + 2] & 0xFF;
                            int a = msdfPixels[i + 3] & 0xFF;
                            nativeImage.setPixel(slot.px + x, dstY,
                                    (a << 24) | (b << 16) | (g << 8) | r);
                        }
                    }
                } else {
                    LOGGER.warn("[MTSDF] flushCurrentPage: msdfPixels NULL for codepoint {}", codePoint);
                }
            }
            pendingSlots.clear();
            pendingMsdfData.clear();
            page.texture.upload();

            // Debug: dump atlas to file (uncomment to inspect)
            // dumpAtlasToPng(this, new File("atlas_debug.png"));
        }

        void ensureReady() {
            if (!pendingSlots.isEmpty()) {
                flushCurrentPage();
            }
        }
    }

    // ========================
    //  Font cache & loading
    // ========================

    private static final Map<String, GlyphFont> FONT_CACHE = new HashMap<>();
    private static final AtomicInteger ATLAS_ID = new AtomicInteger(0);

    public static GlyphFont loadFont(Identifier path, float size) {
        return loadFont(path, size, java.awt.Font.PLAIN);
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
                throw new RuntimeException("Failed to load font: " + path, e);
            }
        });
    }

    public static GlyphFont loadFont(InputStream in, float size) {
        try {
            java.awt.Font base = java.awt.Font.createFont(
                    java.awt.Font.TRUETYPE_FONT, in);
            return new GlyphFont(base.deriveFont(size));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load font from stream", e);
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
                throw new RuntimeException("Failed to load font: " + file.getAbsolutePath(), e);
            }
        });
    }

    public static GlyphFont fromAwtFont(java.awt.Font awtFont) {
        return new GlyphFont(awtFont);
    }

    // ========================
    //  Current TTF font state
    // ========================

    private static volatile GlyphFont currentTtfGlyphFont;
    private static volatile String currentTtfFontName;

    public static GlyphFont getCurrentTtfGlyphFont() {
        return currentTtfGlyphFont;
    }

    public static String getCurrentTtfFontName() {
        return currentTtfFontName;
    }

    public static void setCurrentTtfGlyphFont(GlyphFont font, String name) {
        currentTtfGlyphFont = font;
        currentTtfFontName = name;
    }

    // ========================
    //  Measurement
    // ========================

    /**
     * Width of the string in logical (screen) units: per-glyph typographic
     * advances plus pair kerning — the same layout code path as
     * {@link #drawString(GuiGraphicsExtractor, GlyphFont, String, float, float, IntFunction)},
     * so measured and rendered widths always agree.
     */
    public static float stringWidth(GlyphFont font, String text) {
        float width = 0f;
        int prevCp = -1;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            width += font.kern(prevCp, codePoint)
                    + font.getGlyph(codePoint).advanceX;
            prevCp = codePoint;
            i += Character.charCount(codePoint);
        }
        return width;
    }

    public static int stringWidth(Font font, String text) {
        return font.width(text);
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
        void emit(int charIndex, Glyph glyph, byte[] dst, int dstOffset);
    }

    private static void writeColor(int argb, byte[] dst, int offset) {
        dst[offset]     = (byte) (argb >> 16); // R
        dst[offset + 1] = (byte) (argb >> 8);  // G
        dst[offset + 2] = (byte) argb;          // B
        dst[offset + 3] = (byte) (argb >> 24); // A
    }

    private static ColorEmitter uniformEmitter(IntFunction<Integer> colorFunc) {
        return (charIndex, glyph, dst, offset) -> {
            int color = colorFunc.apply(charIndex);
            byte r = (byte) (color >> 16);
            byte g = (byte) (color >> 8);
            byte b = (byte) color;
            byte a = (byte) (color >> 24);
            for (int v = 0; v < 4; v++, offset += 4) {
                dst[offset]     = r;
                dst[offset + 1] = g;
                dst[offset + 2] = b;
                dst[offset + 3] = a;
            }
        };
    }

    private static ColorEmitter gradientEmitter(IntFunction<Integer> topFunc,
                                                IntFunction<Integer> botFunc) {
        return (charIndex, glyph, dst, offset) -> {
            writeColor(topFunc.apply(charIndex), dst, offset);       // TL
            writeColor(botFunc.apply(charIndex), dst, offset + 4);   // BL
            writeColor(botFunc.apply(charIndex), dst, offset + 8);   // BR
            writeColor(topFunc.apply(charIndex), dst, offset + 12);  // TR
        };
    }

    private static ColorEmitter horizontalGradientEmitter(
            IntFunction<Integer> leftFunc, IntFunction<Integer> rightFunc) {
        return (charIndex, glyph, dst, offset) -> {
            writeColor(leftFunc.apply(charIndex),  dst, offset);       // TL
            writeColor(leftFunc.apply(charIndex),  dst, offset + 4);   // BL
            writeColor(rightFunc.apply(charIndex), dst, offset + 8);   // BR
            writeColor(rightFunc.apply(charIndex), dst, offset + 12);  // TR
        };
    }

    private static ColorEmitter quadEmitter(FourColorFunc f) {
        return (charIndex, glyph, dst, offset) -> {
            writeColor(f.topLeft(charIndex),     dst, offset);       // TL
            writeColor(f.bottomLeft(charIndex),  dst, offset + 4);   // BL
            writeColor(f.bottomRight(charIndex), dst, offset + 8);   // BR
            writeColor(f.topRight(charIndex),    dst, offset + 12);  // TR
        };
    }

    // ========================
    //  Internal rendering
    // ========================

    private static void prefetch(GlyphFont font, String text) {
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            font.getGlyph(codePoint);
            i += Character.charCount(codePoint);
        }
    }

    private record GlyphRun(int charIndex, Glyph glyph, float x) {}

    private static void drawGrouped(GuiGraphicsExtractor gui, GlyphFont font,
                                    String text, float x, float y,
                                    ColorEmitter emitter) {
        Map<Integer, List<GlyphRun>> groups = new LinkedHashMap<>();
        float cursorX = x;
        int prevCp = -1;
        int charIndex = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            Glyph glyph = font.getGlyph(codePoint);
            cursorX += font.kern(prevCp, codePoint);
            if (glyph.hasImage) {
                groups.computeIfAbsent(glyph.pageIndex, k -> new ArrayList<>())
                        .add(new GlyphRun(charIndex, glyph, cursorX));
            }
            cursorX += glyph.advanceX;
            prevCp = codePoint;
            i += Character.charCount(codePoint);
            charIndex++;
        }
        if (groups.isEmpty()) {
            return;
        }

        float baselineY = y + font.ascent;
        // 基线统一对齐到像素，确保所有字形在同一条线上
        float snappedBaselineY = snapToPixel(baselineY);
        Matrix3x2f currentPose = new Matrix3x2f(gui.pose());
        ScreenRectangle currentScissor = gui.peekScissorStack();

        for (Map.Entry<Integer, List<GlyphRun>> entry : groups.entrySet()) {
            List<GlyphRun> runs = entry.getValue();
            AtlasPage page = font.pages.get(entry.getKey());
            int quadCount = runs.size();

            float[] positions = new float[quadCount * QUAD_VERTS * POS_STRIDE];
            float[] uvs       = new float[quadCount * QUAD_VERTS * UV_STRIDE];
            byte[]  colors    = new byte[quadCount * QUAD_VERTS * COL_STRIDE];

            int posIdx = 0, uvIdx = 0, colorIdx = 0;

            for (GlyphRun run : runs) {
                Glyph glyph = run.glyph;
                float x0 = run.x + glyph.bearingX;
                float y0 = snappedBaselineY + glyph.bearingY;
                float x1 = x0 + glyph.width;   // includes SDF padding
                float y1 = y0 + glyph.height;   // includes SDF padding

                emitter.emit(run.charIndex, glyph, colors, colorIdx);

                positions[posIdx] = x0;     positions[posIdx + 1] = y0;
                uvs[uvIdx] = glyph.u0;      uvs[uvIdx + 1] = glyph.v0;
                posIdx += 2; uvIdx += 2; colorIdx += 4;

                positions[posIdx] = x0;     positions[posIdx + 1] = y1;
                uvs[uvIdx] = glyph.u0;      uvs[uvIdx + 1] = glyph.v1;
                posIdx += 2; uvIdx += 2; colorIdx += 4;

                positions[posIdx] = x1;     positions[posIdx + 1] = y1;
                uvs[uvIdx] = glyph.u1;      uvs[uvIdx + 1] = glyph.v1;
                posIdx += 2; uvIdx += 2; colorIdx += 4;

                positions[posIdx] = x1;     positions[posIdx + 1] = y0;
                uvs[uvIdx] = glyph.u1;      uvs[uvIdx + 1] = glyph.v0;
                posIdx += 2; uvIdx += 2; colorIdx += 4;
            }

            gui.submitGuiElementRenderState(
                    new FontRenderState(currentPose, page, positions, uvs,
                            colors, quadCount, currentScissor));
        }
    }

    private static float snapToPixel(float v) {
        return Math.round(v) + 0.5f;
    }

    // ========================
    //  Font render state (GLSL pipeline)
    // ========================

    private static final class FontRenderState implements GuiElementRenderState {
        private final Matrix3x2f pose;
        private final TextureSetup textureSetup;
        private final float[] positions;
        private final float[] uvs;
        private final byte[] colors;
        private final int quadCount;
        @Nullable private final ScreenRectangle scissor;
        @Nullable private final ScreenRectangle bounds;

        FontRenderState(Matrix3x2f pose, AtlasPage page,
                        float[] positions, float[] uvs, byte[] colors,
                        int quadCount, @Nullable ScreenRectangle scissor) {
            this(pose, page.textureSetup(), positions, uvs, colors, quadCount, scissor);
        }

        private FontRenderState(Matrix3x2f pose, TextureSetup textureSetup,
                                float[] positions, float[] uvs, byte[] colors,
                                int quadCount, @Nullable ScreenRectangle scissor) {
            this.pose = pose;
            this.textureSetup = textureSetup;
            this.positions = positions;
            this.uvs = uvs;
            this.colors = colors;
            this.quadCount = quadCount;
            this.scissor = scissor;
            this.bounds = computeBounds(scissor);
        }

        private ScreenRectangle computeBounds(@Nullable ScreenRectangle scissor) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            int vertexCount = quadCount * QUAD_VERTS * POS_STRIDE;
            for (int i = 0; i < vertexCount; i += 2) {
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
            int vi = 0, ui = 0, ci = 0;
            for (int q = 0; q < quadCount; q++) {
                for (int v = 0; v < QUAD_VERTS; v++) {
                    vc.addVertexWith2DPose(pose, positions[vi], positions[vi + 1])
                            .setUv(uvs[ui], uvs[ui + 1])
                            .setColor(colors[ci] & 0xFF, colors[ci + 1] & 0xFF,
                                    colors[ci + 2] & 0xFF, colors[ci + 3] & 0xFF);
                    vi += 2;
                    ui += 2;
                    ci += 4;
                }
            }
        }

        @Override
        public @NonNull RenderPipeline pipeline() {
            return FONT_PIPELINE;
        }

        @Override
        public @NonNull TextureSetup textureSetup() {
            return textureSetup;
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
    //  MSDF debug helpers
    // ========================

    /**
     * Dump interleaved RGB MSDF bytes to a PNG file for debugging.
     * Shows the multi-channel distance field exactly as generated.
     *
     * @param msdfPixels MSDF byte array (RGB, 3 bytes per pixel, row-major)
     * @param w width
     * @param h height
     * @param file output file path
     */
    public static void dumpMsdfToPng(byte[] msdfPixels, int w, int h, File file) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 3;
                int r = msdfPixels[i] & 0xFF;
                int g = msdfPixels[i + 1] & 0xFF;
                int b = msdfPixels[i + 2] & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        try {
            javax.imageio.ImageIO.write(img, "png", file);
            LOGGER.info("[MTSDF] Dumped MSDF debug image to {}", file.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.warn("[MTSDF] Failed to dump MSDF debug image", e);
        }
        img.flush();
    }

    /**
     * Dump the current atlas NativeImage to a PNG file for debugging.
     * The MTSDF atlas is a full-color image (R/G/B distance channels;
     * alpha holds the true SDF, forced opaque in this dump).
     */
    public static void dumpAtlasToPng(GlyphFont font, File file) {
        if (font.pages.isEmpty()) return;
        AtlasPage page = font.pages.get(font.pages.size() - 1);
        NativeImage ni = page.nativeImage;
        if (ni == null) return;
        int size = ATLAS_SIZE;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // NativeImage stores ABGR: A=bits31..24, B=23..16, G=15..8, R=7..0
                int abgr = ni.getPixel(x, y);
                int r = abgr & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        try {
            javax.imageio.ImageIO.write(img, "png", file);
            LOGGER.info("[MTSDF] Dumped atlas debug image to {}", file.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.warn("[MTSDF] Failed to dump atlas debug image", e);
        }
        img.flush();
    }

    // ========================
    //  Cleanup
    // ========================

    public static void dispose(GlyphFont font) {
        for (AtlasPage page : font.pages) {
            if (page.texture != null) {
                mc.getTextureManager().release(page.textureId);
                page.texture.close();
            }
        }
        font.pages.clear();
        font.pendingSlots.clear();
        font.pendingMsdfData.clear();
        font.glyphs.clear();
        font.kernCache.clear();
    }

    public static void flushAllPages() {
        for (GlyphFont font : FONT_CACHE.values()) {
            font.ensureReady();
        }
    }

    public static void disposeAll() {
        FONT_CACHE.values().forEach(CustomFontRenderer::dispose);
        FONT_CACHE.clear();
    }
}
