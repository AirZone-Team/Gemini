package geminiclient.gemini.customRenderer.glsl;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
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
 * GPU-accelerated font renderer with custom GLSL fragment shader.
 *
 * <p>Glyphs are rasterized on the CPU via AWT and uploaded to a GPU
 * texture atlas. Rendering uses a dedicated {@link RenderPipeline}
 * ({@link #FONT_PIPELINE}) whose fragment shader samples the glyph
 * atlas and applies per-vertex color modulation — enabling gradient,
 * rainbow, and quad-color text effects with minimal CPU overhead.</p>
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

    // SDF generation parameters
    private static final int SDF_PADDING = 16;  // extra pixels around each glyph for distance field

    // ========================
    //  Custom pipeline
    // ========================

    public static final RenderPipeline FONT_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/font"))
            .withVertexShader(ResourceLocationUtils.getIdentifier("core/font"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("core/font"))
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
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
        DynamicTexture texture;
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

    private record AtlasSlot(int px, int py, int pw, int ph) {}

    // ========================
    //  GlyphFont
    // ========================

    public static final class GlyphFont {
        final java.awt.Font awtFont;
        final FontRenderContext frc;
        final FontMetrics metrics;
        public final float ascent, descent, lineHeight;
        final Map<Integer, Glyph> glyphs = new HashMap<>();

        final List<AtlasPage> pages = new ArrayList<>();
        int currentPageIdx = -1;

        int cursorX, cursorY, rowHeight;
        final Map<Integer, AtlasSlot> pendingSlots = new HashMap<>();
        final Map<Integer, byte[]> pendingSdfData = new HashMap<>();
        /** SDF normalization spread derived from font size (pixels). */
        final float sdfSpread;

        private java.awt.Font fallbackFont;

        GlyphFont(java.awt.Font awtFont) {
            this.awtFont = awtFont;

            BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = dummy.createGraphics();
            applyRenderingHints(g2d);
            this.frc = g2d.getFontRenderContext();
            this.metrics = g2d.getFontMetrics(awtFont);
            this.ascent = metrics.getAscent();
            this.descent = metrics.getDescent();
            this.lineHeight = ascent + descent;
            // SDF spread = 1/3 of font size — enough to cover the distance field
            // while keeping the gradient visible in the atlas.
            this.sdfSpread = Math.max(awtFont.getSize() / 6.0f, 8.0f);
            g2d.dispose();
            dummy.flush();

            newCpuPage();
        }

        private void applyRenderingHints(java.awt.Graphics2D g2d) {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }

        private java.awt.Font getFallbackFont() {
            if (fallbackFont == null) {
                fallbackFont = new java.awt.Font(FALLBACK_FONT_NAME,
                        java.awt.Font.PLAIN, awtFont.getSize());
            }
            return fallbackFont;
        }

        private void newCpuPage() {
            cursorX = 0;
            cursorY = 0;
            rowHeight = 0;

            AtlasPage page = new AtlasPage(
                    ResourceLocationUtils.getIdentifier("font_atlas_" + ATLAS_ID.getAndIncrement()));
            page.invW = 1.0f / ATLAS_SIZE;
            page.invH = 1.0f / ATLAS_SIZE;

            NativeImage nativeImage = new NativeImage(ATLAS_SIZE, ATLAS_SIZE, false);
            page.nativeImage = nativeImage;
            page.texture = new DynamicTexture(page.textureId::toDebugFileName, nativeImage);
            mc.getTextureManager().register(page.textureId, page.texture);

            pages.add(page);
            currentPageIdx = pages.size() - 1;
        }

        public Glyph getGlyph(int codePoint) {
            Glyph glyph = glyphs.get(codePoint);
            if (glyph != null) {
                return glyph;
            }
            glyph = rasterize(codePoint);
            return glyph;
        }

        private Glyph rasterize(int codePoint) {
            if (pages.isEmpty()) {
                newCpuPage();
            }

            java.awt.Font fontToUse = awtFont;
            if (!awtFont.canDisplay(codePoint)) {
                fontToUse = getFallbackFont();
            }

            String charStr = new String(Character.toChars(codePoint));
            GlyphVector glyphVector = fontToUse.createGlyphVector(frc, charStr);
            Rectangle2D visualBounds = glyphVector.getVisualBounds();

            float advance = (float) fontToUse.getStringBounds(charStr, frc).getWidth();
            if (advance <= 0) {
                advance = metrics.charWidth(codePoint);
            }

            double visualW = visualBounds.getWidth();
            double visualH = visualBounds.getHeight();
            if (visualW <= 0 || visualH <= 0) {
                return new Glyph(0, 0, 0, 0, advance, false, -1);
            }

            int padding = SDF_PADDING;
            int realWidth = (int) Math.ceil(visualW);
            int realHeight = (int) Math.ceil(visualH);
            int atlasWidth = realWidth + padding * 2;
            int atlasHeight = realHeight + padding * 2;

            if (cursorX + atlasWidth > ATLAS_SIZE) {
                cursorX = 0;
                cursorY += rowHeight;
                rowHeight = 0;
            }

            if (cursorY + atlasHeight > ATLAS_SIZE) {
                flushCurrentPage();
                newCpuPage();
            }

            int cellX = cursorX;
            int cellY = cursorY;

            int drawX = padding - Math.round((float) visualBounds.getX());
            int drawY = padding - Math.round((float) visualBounds.getY());

            // --- SDF generation: render glyph mask → distance transform → write to atlas ---

            // 1. Render glyph to a temporary mask image (cleared to transparent)
            BufferedImage maskImage = new BufferedImage(atlasWidth, atlasHeight,
                    BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D maskG2d = maskImage.createGraphics();
            maskG2d.setComposite(java.awt.AlphaComposite.Clear);
            maskG2d.fillRect(0, 0, atlasWidth, atlasHeight);
            maskG2d.setComposite(java.awt.AlphaComposite.SrcOver);
            applyRenderingHints(maskG2d);
            maskG2d.setFont(fontToUse);
            maskG2d.setColor(Color.WHITE);
            maskG2d.drawString(charStr, drawX, drawY);
            maskG2d.dispose();

            // 2. Extract boolean mask from alpha channel
            boolean[][] mask = new boolean[atlasHeight][atlasWidth];
            for (int y = 0; y < atlasHeight; y++) {
                for (int x = 0; x < atlasWidth; x++) {
                    int alpha = (maskImage.getRGB(x, y) >> 24) & 0xFF;
                    mask[y][x] = alpha > 0;
                }
            }
            maskImage.flush();

            // 3. Generate SDF bytes (spread derived from font size)
            byte[] sdfPixels = generateSDF(mask, atlasWidth, atlasHeight, sdfSpread);

            // Debug: log SDF value range
            if (LOGGER.isDebugEnabled()) {
                int sdfMin = 255, sdfMax = 0;
                for (byte b : sdfPixels) {
                    int v = b & 0xFF;
                    if (v < sdfMin) sdfMin = v;
                    if (v > sdfMax) sdfMax = v;
                }
                LOGGER.debug("[SDF] codepoint={} atlas={}x{} sdfRange=[{}, {}]",
                        codePoint, atlasWidth, atlasHeight, sdfMin, sdfMax);
            }

            // 4. Build the Glyph and register in maps BEFORE flushCurrentPage
            //    (flushCurrentPage needs glyphs.get() to succeed)
            Glyph glyph = new Glyph(atlasWidth, atlasHeight,
                    (float) visualBounds.getX() - padding,
                    (float) visualBounds.getY() - padding,
                    advance, true, -1);
            glyphs.put(codePoint, glyph);
            pendingSdfData.put(codePoint, sdfPixels);
            pendingSlots.put(codePoint, new AtlasSlot(cellX, cellY, atlasWidth, atlasHeight));

            cursorX += atlasWidth;
            if (atlasHeight > rowHeight) {
                rowHeight = atlasHeight;
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
                    LOGGER.warn("[SDF] flushCurrentPage: glyph NULL for codepoint {}, UVs will NOT be set!", codePoint);
                    continue;
                }
                AtlasSlot slot = entry.getValue();

                glyph.u0 = slot.px * invW;
                glyph.v0 = slot.py * invH;
                glyph.u1 = (slot.px + slot.pw) * invW;
                glyph.v1 = (slot.py + slot.ph) * invH;
                glyph.pageIndex = currentPageIdx;

                // Upload SDF bytes to NativeImage.
                // Write the same value to R, G, B so the shader can read it
                // regardless of whether NativeImage uses ARGB or ABGR internally.
                byte[] sdfPixels = pendingSdfData.get(codePoint);
                if (sdfPixels != null) {
                    for (int y = 0; y < slot.ph; y++) {
                        int rowBase = y * slot.pw;
                        int dstY = slot.py + y;
                        for (int x = 0; x < slot.pw; x++) {
                            int v = sdfPixels[rowBase + x] & 0xFF;
                            nativeImage.setPixel(slot.px + x, dstY,
                                    (0xFF << 24) | (v << 16) | (v << 8) | v);
                        }
                    }
                } else {
                    LOGGER.warn("[SDF] flushCurrentPage: sdfPixels NULL for codepoint {}", codePoint);
                }
            }
            pendingSlots.clear();
            pendingSdfData.clear();
            page.texture.upload();
            setLinearFilter(page.texture);

            // Debug: dump atlas to file (uncomment to inspect)
            // dumpAtlasToPng(this, new File("atlas_debug.png"));
        }

        private static void setLinearFilter(DynamicTexture texture) {
            try {
                java.lang.reflect.Field field = texture.getClass().getSuperclass()
                        .getDeclaredField("sampler");
                field.setAccessible(true);
                field.set(texture, com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                        .getRepeat(FilterMode.LINEAR));
            } catch (Exception e) {
                LOGGER.warn("Failed to set LINEAR filter on font atlas texture", e);
            }
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
        String key = path.toString() + "@" + size;
        return FONT_CACHE.computeIfAbsent(key, k -> {
            try (InputStream is = mc.getResourceManager()
                    .getResource(path).orElseThrow().open()) {
                java.awt.Font base = java.awt.Font.createFont(
                        java.awt.Font.TRUETYPE_FONT, is);
                return new GlyphFont(base.deriveFont(size));
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

    public static float stringWidth(GlyphFont font, String text) {
        float width = 0f;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            width += font.getGlyph(codePoint).advanceX;
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
        int charIndex = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            Glyph glyph = font.getGlyph(codePoint);
            if (glyph.hasImage) {
                groups.computeIfAbsent(glyph.pageIndex, k -> new ArrayList<>())
                        .add(new GlyphRun(charIndex, glyph, cursorX));
            }
            cursorX += glyph.advanceX;
            i += Character.charCount(codePoint);
            charIndex++;
        }
        if (groups.isEmpty()) {
            return;
        }

        float baselineY = y + font.ascent;
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
                float x0 = snapToPixel(run.x + glyph.bearingX);
                float y0 = snapToPixel(baselineY + glyph.bearingY);
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
    //  SDF generation (Euclidean Distance Transform)
    // ========================

    /**
     * Dump the SDF bytes to a PNG file for debugging.
     * Shows: black background, gray edges, white glyph interior.
     *
     * @param sdfPixels SDF byte array
     * @param w width
     * @param h height
     * @param file output file path
     */
    public static void dumpSdfToPng(byte[] sdfPixels, int w, int h, File file) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = sdfPixels[y * w + x] & 0xFF;
                img.getRaster().setSample(x, y, 0, v);
            }
        }
        try {
            javax.imageio.ImageIO.write(img, "png", file);
            LOGGER.info("[SDF] Dumped SDF debug image to {}", file.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.warn("[SDF] Failed to dump SDF debug image", e);
        }
        img.flush();
    }

    /**
     * Dump the current atlas NativeImage to a PNG file for debugging.
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
                // For SDF debug: show R channel as grayscale
                img.setRGB(x, y, 0xFF000000 | (r << 16) | (r << 8) | r);
            }
        }
        try {
            javax.imageio.ImageIO.write(img, "png", file);
            LOGGER.info("[SDF] Dumped atlas debug image to {}", file.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.warn("[SDF] Failed to dump atlas debug image", e);
        }
        img.flush();
    }

    /**
     * Generate a signed distance field from a binary glyph mask.
     *
     * <p>Uses the Felzenszwalb &amp; Huttenlocher algorithm for O(n) EDT.
     * Output is a byte array where 128 = edge, 255 = center, 0 = far outside.</p>
     *
     * @param mask  true = inside glyph, false = outside
     * @param w     mask width
     * @param h     mask height
     * @return SDF bytes, one per pixel (row-major)
     */
    private static byte[] generateSDF(boolean[][] mask, int w, int h, float spread) {
        // Use a large but finite value — Float.MAX_VALUE overflows to INF
        // in the EDT parabola formula (data[i] + i*i), breaking the algorithm.
        final float INF = 1e20f;

        float[] inside = new float[w * h];
        float[] outside = new float[w * h];

        // Initialize: inside[i]=0 if mask[i], else INF; outside[i]=INF if mask[i], else 0
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (mask[y][x]) {
                    inside[i] = 0f;
                    outside[i] = INF;
                } else {
                    inside[i] = INF;
                    outside[i] = 0f;
                }
            }
        }

        // Compute EDT for both fields (squared distances)
        edt(inside, w, h);
        edt(outside, w, h);

        // Normalize and encode to bytes: edge=128, center=255, far outside=0
        byte[] sdf = new byte[w * h];
        for (int i = 0; i < sdf.length; i++) {
            // Signed distance: positive inside, negative outside
            float dist = (float) (Math.sqrt(outside[i]) - Math.sqrt(inside[i]));
            float normalized = dist / spread;
            int value = (int) (127.5f + normalized * 127.5f);
            sdf[i] = (byte) Math.max(0, Math.min(255, value));
        }
        return sdf;
    }

    /**
     * 2D Euclidean Distance Transform (separable, Felzenszwalb algorithm).
     * Operates on squared distances in-place.
     */
    private static void edt(float[] data, int w, int h) {
        // Transform rows
        for (int y = 0; y < h; y++) {
            edt1d(data, y * w, w);
        }
        // Transform columns
        float[] column = new float[h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                column[y] = data[y * w + x];
            }
            edt1d(column, 0, h);
            for (int y = 0; y < h; y++) {
                data[y * w + x] = column[y];
            }
        }
    }

    /**
     * 1D squared-distance transform (lower envelope of parabolas).
     *
     * @param data array containing input values (modified in-place)
     * @param offset start index in data
     * @param n number of elements
     */
    private static void edt1d(float[] data, int offset, int n) {
        float[] d = new float[n];
        int[] v = new int[n];
        float[] z = new float[n + 1];

        int k = 0;
        v[0] = 0;
        z[0] = Float.NEGATIVE_INFINITY;
        z[1] = Float.POSITIVE_INFINITY;

        // Build lower envelope
        for (int q = 1; q < n; q++) {
            float s = ((data[offset + q] + q * q) - (data[offset + v[k]] + v[k] * v[k]))
                    / (2f * q - 2f * v[k]);
            while (s <= z[k]) {
                k--;
                s = ((data[offset + q] + q * q) - (data[offset + v[k]] + v[k] * v[k]))
                        / (2f * q - 2f * v[k]);
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Float.POSITIVE_INFINITY;
        }

        // Fill distances from envelope
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) {
                k++;
            }
            float dx = q - v[k];
            d[q] = dx * dx + data[offset + v[k]];
        }

        System.arraycopy(d, 0, data, offset, n);
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
        font.pendingSdfData.clear();
        font.glyphs.clear();
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
