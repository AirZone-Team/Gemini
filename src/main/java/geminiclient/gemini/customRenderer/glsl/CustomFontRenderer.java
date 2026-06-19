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

        BufferedImage atlasImage;
        java.awt.Graphics2D atlasG2d;
        int cursorX, cursorY, rowHeight;
        final Map<Integer, AtlasSlot> pendingSlots = new HashMap<>();

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
            g2d.dispose();
            dummy.flush();

            newCpuPage();
        }

        private void applyRenderingHints(java.awt.Graphics2D g2d) {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
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
            atlasImage = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
            atlasG2d = atlasImage.createGraphics();
            applyRenderingHints(atlasG2d);
            atlasG2d.setColor(Color.WHITE);
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
            glyphs.put(codePoint, glyph);
            return glyph;
        }

        private Glyph rasterize(int codePoint) {
            if (atlasImage == null) {
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

            int padding = 1;
            int glyphWidth = (int) Math.ceil(visualW) + padding * 2;
            int glyphHeight = (int) Math.ceil(visualH) + padding * 2;

            if (cursorX + glyphWidth > ATLAS_SIZE) {
                cursorX = 0;
                cursorY += rowHeight;
                rowHeight = 0;
            }

            if (cursorY + glyphHeight > ATLAS_SIZE) {
                flushCurrentPage();
                if (atlasImage != null) {
                    atlasImage.flush();
                    atlasG2d.dispose();
                }
                newCpuPage();
            }

            int cellX = cursorX;
            int cellY = cursorY;

            int drawX = padding - Math.round((float) visualBounds.getX());
            int drawY = padding - Math.round((float) visualBounds.getY());

            atlasG2d.setFont(fontToUse);
            atlasG2d.drawString(charStr, cellX + drawX, cellY + drawY);

            pendingSlots.put(codePoint, new AtlasSlot(cellX, cellY, glyphWidth, glyphHeight));

            cursorX += glyphWidth;
            if (glyphHeight > rowHeight) {
                rowHeight = glyphHeight;
            }

            return new Glyph(glyphWidth, glyphHeight,
                    (float) visualBounds.getX() - padding,
                    (float) visualBounds.getY() - padding,
                    advance, true, -1);
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
                Glyph glyph = glyphs.get(entry.getKey());
                if (glyph == null) {
                    continue;
                }
                AtlasSlot slot = entry.getValue();

                glyph.u0 = slot.px * invW;
                glyph.v0 = slot.py * invH;
                glyph.u1 = (slot.px + slot.pw) * invW;
                glyph.v1 = (slot.py + slot.ph) * invH;
                glyph.pageIndex = currentPageIdx;

                int[] pixels = atlasImage.getRGB(slot.px, slot.py, slot.pw, slot.ph,
                        null, 0, slot.pw);
                for (int y = 0; y < slot.ph; y++) {
                    int rowBase = y * slot.pw;
                    int dstX = slot.px;
                    int dstY = slot.py + y;
                    for (int x = 0; x < slot.pw; x++) {
                        int argb = pixels[rowBase + x];
                        int alpha = (argb >> 24) & 0xFF;
                        int red = (argb >> 16) & 0xFF;
                        int green = (argb >> 8) & 0xFF;
                        int blue = argb & 0xFF;
                        nativeImage.setPixel(dstX + x, dstY,
                                (alpha << 24) | (blue << 16) | (green << 8) | red);
                    }
                }
            }
            pendingSlots.clear();
            page.texture.upload();
            setNearestFilter(page.texture);
        }

        private static void setNearestFilter(DynamicTexture texture) {
            try {
                java.lang.reflect.Field field = texture.getClass().getSuperclass()
                        .getDeclaredField("sampler");
                field.setAccessible(true);
                field.set(texture, com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                        .getRepeat(FilterMode.NEAREST));
            } catch (Exception e) {
                LOGGER.warn("Failed to set NEAREST filter on font atlas texture", e);
            }
        }

        void ensureReady() {
            if (atlasImage != null && !pendingSlots.isEmpty()) {
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
                float x1 = x0 + glyph.width;
                float y1 = y0 + glyph.height;

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
        if (font.atlasImage != null) {
            font.atlasImage.flush();
            font.atlasImage = null;
        }
        if (font.atlasG2d != null) {
            font.atlasG2d.dispose();
            font.atlasG2d = null;
        }
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
