package geminiclient.gemini.customRenderer.cpu;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.*;
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

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static geminiclient.gemini.base.MinecraftInstance.mc;

/**
 * Custom text renderer with TTF/OTF font loading, per-character coloring,
 * and per-character-part gradient coloring.
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 * GlyphFont font = CustomFontRenderer.loadFont(
 *     ResourceLocationUtils.getIdentifier("fonts/roboto.ttf"), 24f);
 *
 * // Per-char rainbow
 * CustomFontRenderer.drawString(gui, font, "Hello", x, y,
 *     i -> RainbowUtil.getRainbow(3000, (float)i/5, 1f, 1f, 0xFF));
 *
 * // Per-char vertical gradient
 * CustomFontRenderer.drawGradientString(gui, font, "Hello", x, y,
 *     0xFF4488FF, 0xFFFF4444);
 * }</pre>
 */
public class CustomFontRenderer {

    // ========================
    //  GLYPH
    // ========================

    /**
     * A single rasterized glyph with atlas UV coordinates and layout metrics.
     * Immutable after construction; UVs are set once when the atlas is flushed.
     */
    public static final class Glyph {
        public float u0, v0, u1, v1;
        public final float width, height;
        /** Horizontal offset from pen origin to glyph left edge. */
        public final float bearingX;
        /** Vertical offset from baseline to glyph top edge (positive = above baseline). */
        public final float bearingY;
        /** Horizontal advance to the next glyph origin. */
        public final float advanceX;
        boolean hasImage;
        int pageIndex;

        Glyph(float w, float h, float bx, float by, float ax,
              boolean hasImage, int pageIndex) {
            this.width = w; this.height = h;
            this.bearingX = bx; this.bearingY = by;
            this.advanceX = ax;
            this.hasImage = hasImage;
            this.pageIndex = pageIndex;
        }
    }

    // ========================
    //  ATLAS
    // ========================

    /** One GPU texture page — owns a DynamicTexture registered with TextureManager. */
    static final class AtlasPage {
        final Identifier textureId;
        DynamicTexture texture;
        float invW, invH;

        AtlasPage(Identifier id) { this.textureId = id; }

        TextureSetup textureSetup() {
            return TextureSetup.singleTexture(
                texture.getTextureView(), texture.getSampler());
        }
    }

    /**
     * Records where a glyph sits in the CPU atlas before GPU upload.
     */
        private record AtlasSlot(int px, int py, int pw, int ph) {
    }

    // ========================
    //  GLYPHFONT
    // ========================

    /**
     * A loaded TTF/OTF font backed by one or more GPU texture atlas pages.
     * Glyphs are rasterized lazily into a CPU-side {@link BufferedImage} and
     * flushed to a {@link DynamicTexture} on first draw.
     */
    public static final class GlyphFont {
        private static final int ATLAS_SIZE = 4096;

        final java.awt.Font awtFont;
        final FontRenderContext frc;
        final FontMetrics metrics;
        public final float ascent;
        public final float descent;
        public final float lineHeight;
        final Map<Integer, Glyph> glyphs = new HashMap<>();
        boolean antiAlias = true;

        // GPU pages
        final List<AtlasPage> pages = new ArrayList<>();
        int currentPageIdx = -1;

        // Current CPU atlas — shelf-packing
        BufferedImage atlasImage;
        Graphics2D atlasG2d;
        int cursorX, cursorY, rowHeight;
        final Map<Integer, AtlasSlot> pendingSlots = new HashMap<>();

        GlyphFont(java.awt.Font awtFont) {
            this.awtFont = awtFont;

            // Build FRC with the same hints used for rasterization
            BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = dummy.createGraphics();
            if (antiAlias) {
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            }
            this.frc = g2d.getFontRenderContext();
            this.metrics = g2d.getFontMetrics(awtFont);
            this.ascent = metrics.getAscent();
            this.descent = metrics.getDescent();
            this.lineHeight = metrics.getAscent() + metrics.getDescent();
            g2d.dispose();
            dummy.flush();

            newCpuPage();
        }

        // ---- CPU atlas ----

        private void newCpuPage() {
            this.atlasImage = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE,
                BufferedImage.TYPE_INT_ARGB);
            this.atlasG2d = atlasImage.createGraphics();
            if (antiAlias) {
                atlasG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                atlasG2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            }
            atlasG2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
            atlasG2d.setFont(awtFont);
            atlasG2d.setColor(Color.WHITE);
            this.cursorX = 0;
            this.cursorY = 0;
            this.rowHeight = 0;
        }

        // ---- GPU page ----

        private AtlasPage newGpuPage() {
            AtlasPage page = new AtlasPage(
                ResourceLocationUtils.getIdentifier(
                    "font_atlas_" + ATLAS_ID.getAndIncrement()));
            pages.add(page);
            currentPageIdx = pages.size() - 1;
            return page;
        }

        // ---- Public ----

        public void setAntiAlias(boolean aa) { this.antiAlias = aa; }

        /** Get or rasterize a glyph for the given Unicode code point. */
        public Glyph getGlyph(int codePoint) {
            Glyph g = glyphs.get(codePoint);
            if (g != null) return g;
            g = rasterize(codePoint);
            glyphs.put(codePoint, g);
            return g;
        }

        // ---- Rasterization ----

        private Glyph rasterize(int codePoint) {
            if (atlasImage == null) newCpuPage();

            String str = new String(Character.toChars(codePoint));
            GlyphVector gv = awtFont.createGlyphVector(frc, str);
            Rectangle2D visual = gv.getVisualBounds();

            float advance = (float) awtFont.getStringBounds(str, frc).getWidth();
            if (advance <= 0) advance = metrics.charWidth(codePoint);

            // Whitespace / control characters — no texture, correct advance
            if (visual.getWidth() <= 0 || visual.getHeight() <= 0) {
                return new Glyph(0, 0, 0, 0, advance, false, -1);
            }

            int pad = antiAlias ? 3 : 1;
            int gw = (int) Math.ceil(visual.getWidth()) + pad * 2;
            int gh = (int) Math.ceil(visual.getHeight()) + pad * 2;

            // Shelf-packing: new row if current row full
            if (cursorX + gw > ATLAS_SIZE) {
                cursorX = 0;
                cursorY += rowHeight;
                rowHeight = 0;
            }
            // New page if current page full
            if (cursorY + gh > ATLAS_SIZE) {
                flushCurrentPage();
                newCpuPage();
            }

            int cellX = cursorX;
            int cellY = cursorY;

            // Draw white glyph into atlas (color applied at render time)
            float drawX = pad - (float) visual.getX();
            float drawY = pad - (float) visual.getY();
            atlasG2d.drawString(str, cellX + drawX, cellY + drawY);

            pendingSlots.put(codePoint, new AtlasSlot(cellX, cellY, gw, gh));

            cursorX += gw;
            if (gh > rowHeight) rowHeight = gh;

            // bearingX: offset from pen to visual left edge (minus padding)
            // bearingY: offset from baseline to visual top edge (minus padding)
            return new Glyph(gw, gh,
                (float) visual.getX() - pad,
                (float) visual.getY() - pad,
                advance, true, -1);
        }

        // ---- Flush CPU → GPU ----

        /** Upload the current CPU atlas to a new GPU page and resolve glyph UVs. */
        private void flushCurrentPage() {
            if (atlasImage == null || pendingSlots.isEmpty()) return;

            int w = atlasImage.getWidth();
            int h = atlasImage.getHeight();

            // Copy BufferedImage → NativeImage (setPixel accepts ARGB)
            NativeImage fresh = new NativeImage(w, h, false);
            int[] pixels = atlasImage.getRGB(0, 0, w, h, null, 0, w);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    fresh.setPixel(x, y, pixels[y * w + x]);
                }
            }

            // Create GPU page
            AtlasPage page = newGpuPage();
            page.invW = 1.0f / w;
            page.invH = 1.0f / h;
            page.texture = new DynamicTexture(
                    page.textureId::toDebugFileName, fresh);
            mc.getTextureManager().register(page.textureId, page.texture);

            // Resolve UVs for all pending glyphs
            for (Map.Entry<Integer, AtlasSlot> e : pendingSlots.entrySet()) {
                Glyph g = glyphs.get(e.getKey());
                if (g == null) continue;
                AtlasSlot s = e.getValue();
                g.u0 = s.px * page.invW;
                g.v0 = s.py * page.invH;
                g.u1 = (s.px + s.pw) * page.invW;
                g.v1 = (s.py + s.ph) * page.invH;
                g.pageIndex = currentPageIdx;
            }
            pendingSlots.clear();

            // Dispose CPU atlas
            atlasImage.flush();
            atlasImage = null;
            atlasG2d.dispose();
            atlasG2d = null;
        }

        /** Ensure all pending glyphs are on the GPU. */
        void ensureReady() {
            if (atlasImage != null && !pendingSlots.isEmpty()) {
                flushCurrentPage();
            }
        }
    }

    // ========================
    //  FONT CACHE
    // ========================

    private static final Map<String, GlyphFont> FONT_CACHE = new HashMap<>();
    private static final AtomicInteger ATLAS_ID = new AtomicInteger(0);

    /** Load a TTF/OTF font from a resource {@link Identifier}. */
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

    /** Load a TTF/OTF font from an {@link InputStream}. */
    public static GlyphFont loadFont(InputStream in, float size) {
        try {
            java.awt.Font base = java.awt.Font.createFont(
                java.awt.Font.TRUETYPE_FONT, in);
            return new GlyphFont(base.deriveFont(size));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load font from stream", e);
        }
    }

    /** Wrap a system {@link java.awt.Font} directly. */
    public static GlyphFont fromAwtFont(java.awt.Font awtFont) {
        return new GlyphFont(awtFont);
    }

    // ========================
    //  MEASUREMENT
    // ========================

    /** Total rendered width of {@code text} with this font. */
    public static float stringWidth(GlyphFont font, String text) {
        float w = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            w += font.getGlyph(cp).advanceX;
            i += Character.charCount(cp);
        }
        return w;
    }

    // ========================
    //  MC FONT OVERLOADS
    //  (net.minecraft.client.gui.Font)
    // ========================

    /** Measure text width using Minecraft's built-in {@link Font}. */
    public static int stringWidth(Font font, String text) {
        return font.width(text);
    }

    /**
     * Draw text using Minecraft's built-in {@link Font} with
     * a per-character color function.
     */
    public static void drawString(GuiGraphicsExtractor gui, Font font,
                                   String text, float x, float y,
                                   IntFunction<Integer> colorFunc) {
        float cx = x;
        int ci = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int color = colorFunc.apply(ci);
            if ((color >>> 24) != 0) {
                gui.text(font, ch, (int) cx, (int) y, color, false);
            }
            cx += font.width(ch);
            i += Character.charCount(cp);
            ci++;
        }
    }

    /** Draw text using MC {@link Font} with a uniform color. */
    public static void drawString(GuiGraphicsExtractor gui, Font font,
                                   String text, float x, float y, int color) {
        drawString(gui, font, text, x, y, __ -> color);
    }

    /**
     * Draw text using MC {@link Font} with a per-character vertical gradient.
     * Each character is rendered twice with scissor clipping —
     * top half = {@code topColor}, bottom half = {@code bottomColor}.
     */
    public static void drawGradientString(GuiGraphicsExtractor gui, Font font,
                                           String text, float x, float y,
                                           int topColor, int bottomColor) {
        drawGradientString(gui, font, text, x, y, __ -> topColor, __ -> bottomColor);
    }

    /**
     * Draw text using MC {@link Font} with per-character vertical gradient.
     * {@code topFunc(i)} colors the top half; {@code botFunc(i)} the bottom.
     */
    public static void drawGradientString(GuiGraphicsExtractor gui, Font font,
                                           String text, float x, float y,
                                           IntFunction<Integer> topFunc,
                                           IntFunction<Integer> botFunc) {
        float cx = x;
        float halfLine = font.lineHeight / 2f;
        int ci = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int w = font.width(ch);
            int top = topFunc.apply(ci);
            int bot = botFunc.apply(ci);

            int xi = (int) cx;
            int yi = (int) y;

            if ((top >>> 24) != 0) {
                gui.enableScissor(xi, yi, xi + w, (int) (yi + halfLine));
                gui.text(font, ch, xi, yi, top, false);
                gui.disableScissor();
            }
            if ((bot >>> 24) != 0) {
                gui.enableScissor(xi, (int) (yi + halfLine), xi + w, yi + font.lineHeight);
                gui.text(font, ch, xi, yi, bot, false);
                gui.disableScissor();
            }

            cx += w;
            i += Character.charCount(cp);
            ci++;
        }
    }

    // ========================
    //  PUBLIC DRAWING API  (GlyphFont)
    // ========================

    // --- Uniform color ---

    public static void drawString(GuiGraphicsExtractor gui, GlyphFont font,
                                   String text, float x, float y, int color) {
        drawString(gui, font, text, x, y, __ -> color);
    }

    // --- Per-character color ---

    /**
     * Draw text with a per-character color function.
     * {@code colorFunc(charIndex)} returns an ARGB int.
     */
    public static void drawString(GuiGraphicsExtractor gui, GlyphFont font,
                                   String text, float x, float y,
                                   IntFunction<Integer> colorFunc) {
        if (text.isEmpty()) return;
        prewarm(font, text);
        font.ensureReady();
        drawGrouped(gui, font, text, x, y, (ci, g) -> {
            int c = colorFunc.apply(ci);
            return uniformRGBA(c);
        });
    }

    // --- Uniform vertical gradient ---

    public static void drawGradientString(GuiGraphicsExtractor gui,
                                           GlyphFont font, String text,
                                           float x, float y,
                                           int topColor, int bottomColor) {
        drawGradientString(gui, font, text, x, y,
            __ -> topColor, __ -> bottomColor);
    }

    // --- Per-character vertical gradient ---

    /**
     * Draw text with a per-character vertical gradient.
     * {@code topFunc(i)} colors the top edge; {@code botFunc(i)} the bottom.
     */
    public static void drawGradientString(GuiGraphicsExtractor gui,
                                           GlyphFont font, String text,
                                           float x, float y,
                                           IntFunction<Integer> topFunc,
                                           IntFunction<Integer> botFunc) {
        if (text.isEmpty()) return;
        prewarm(font, text);
        font.ensureReady();
        drawGrouped(gui, font, text, x, y, (ci, g) -> {
            int t = topFunc.apply(ci), b = botFunc.apply(ci);
            return gradientRGBA(t, b);
        });
    }

    // --- Per-character horizontal gradient ---

    public static void drawHorizontalGradientString(GuiGraphicsExtractor gui,
                                                     GlyphFont font,
                                                     String text,
                                                     float x, float y,
                                                     IntFunction<Integer> leftFunc,
                                                     IntFunction<Integer> rightFunc) {
        if (text.isEmpty()) return;
        prewarm(font, text);
        font.ensureReady();
        drawGrouped(gui, font, text, x, y, (ci, g) -> {
            int l = leftFunc.apply(ci), r = rightFunc.apply(ci);
            return horizontalGradientRGBA(l, r);
        });
    }

    // --- Four-corner gradient ---

    /** Per-character four-corner color provider. */
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
        if (text.isEmpty()) return;
        prewarm(font, text);
        font.ensureReady();
        drawGrouped(gui, font, text, x, y, (ci, g) -> {
            int tl = f.topLeft(ci), tr = f.topRight(ci);
            int bl = f.bottomLeft(ci), br = f.bottomRight(ci);
            return quadRGBA(tl, tr, bl, br);
        });
    }

    // --- Rainbow convenience ---

    public static void drawRainbowString(GuiGraphicsExtractor gui,
                                          GlyphFont font, String text,
                                          float x, float y,
                                          long speedMs, float sat, float bri,
                                          int alpha) {
        int len = Math.max(1, (int) text.codePoints().count());
        drawString(gui, font, text, x, y, i -> {
            float hue = ((System.currentTimeMillis() % speedMs)
                / (float) speedMs + (float) i / len) % 1.0f;
            int rgb = java.awt.Color.HSBtoRGB(hue, sat, bri);
            return (alpha << 24) | (rgb & 0xFFFFFF);
        });
    }

    public static void drawRainbowGradientString(GuiGraphicsExtractor gui,
                                                  GlyphFont font, String text,
                                                  float x, float y,
                                                  long speedMs, float sat,
                                                  float bri, int alpha) {
        int len = Math.max(1, (int) text.codePoints().count());
        drawGradientString(gui, font, text, x, y,
            i -> {
                float hue = ((System.currentTimeMillis() % speedMs)
                    / (float) speedMs + (float) i / len) % 1.0f;
                int rgb = java.awt.Color.HSBtoRGB(hue, sat, bri);
                return (alpha << 24) | (rgb & 0xFFFFFF);
            },
            i -> {
                float hue = ((System.currentTimeMillis() % speedMs)
                    / (float) speedMs + (float) i / len + 0.08f) % 1.0f;
                int rgb = java.awt.Color.HSBtoRGB(hue, sat, bri);
                return (alpha << 24) | (rgb & 0xFFFFFF);
            });
    }

    // ========================
    //  INTERNAL RENDERING
    // ========================

    // --- Color packers (4 vertices × 4 channels = 16 ints) ---

    private static int[] uniformRGBA(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF;
        int b = c & 0xFF, a = (c >> 24) & 0xFF;
        return new int[]{r,g,b,a, r,g,b,a, r,g,b,a, r,g,b,a};
    }

    private static int[] gradientRGBA(int top, int bot) {
        return new int[]{
            r8(top), g8(top), b8(top), a8(top),
            r8(bot), g8(bot), b8(bot), a8(bot),
            r8(bot), g8(bot), b8(bot), a8(bot),
            r8(top), g8(top), b8(top), a8(top),
        };
    }

    private static int[] horizontalGradientRGBA(int left, int right) {
        return new int[]{
            r8(left),  g8(left),  b8(left),  a8(left),
            r8(left),  g8(left),  b8(left),  a8(left),
            r8(right), g8(right), b8(right), a8(right),
            r8(right), g8(right), b8(right), a8(right),
        };
    }

    private static int[] quadRGBA(int tl, int tr, int bl, int br) {
        return new int[]{
            r8(tl), g8(tl), b8(tl), a8(tl),
            r8(bl), g8(bl), b8(bl), a8(bl),
            r8(br), g8(br), b8(br), a8(br),
            r8(tr), g8(tr), b8(tr), a8(tr),
        };
    }

    private static int r8(int c) { return (c >> 16) & 0xFF; }
    private static int g8(int c) { return (c >> 8) & 0xFF; }
    private static int b8(int c) { return c & 0xFF; }
    private static int a8(int c) { return (c >> 24) & 0xFF; }

    // --- Pre-warming ---

    /** Ensure all glyphs in the string are rasterized before flushing. */
    private static void prewarm(GlyphFont font, String text) {
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            font.getGlyph(cp);
            i += Character.charCount(cp);
        }
    }

    // --- Grouped rendering ---

    @FunctionalInterface
    private interface ColorEmitter {
        /** Returns 16 ints: [r,g,b,a] × 4 verts (TL, BL, BR, TR). */
        int[] emit(int charIndex, Glyph glyph);
    }

    /**
     * Intermediate run: a glyph at a specific x position.
     */
        private record GlyphRun(int charIndex, Glyph glyph, float x) {
    }

    /**
     * Group glyphs by atlas page, build one {@link GuiElementRenderState}
     * per page, and submit to the GUI render queue.
     */
    private static void drawGrouped(GuiGraphicsExtractor gui,
                                     GlyphFont font, String text,
                                     float x, float y, ColorEmitter emitter) {
        Map<Integer, List<GlyphRun>> groups = new LinkedHashMap<>();
        float cx = x;
        int ci = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            Glyph g = font.getGlyph(cp);
            if (g.hasImage) {
                groups.computeIfAbsent(g.pageIndex,
                    k -> new ArrayList<>()).add(new GlyphRun(ci, g, cx));
            }
            cx += g.advanceX;
            i += Character.charCount(cp);
            ci++;
        }
        if (groups.isEmpty()) return;

        // glyph origin Y = baseline
        float baselineY = y + font.ascent;

        for (Map.Entry<Integer, List<GlyphRun>> entry : groups.entrySet()) {
            List<GlyphRun> runs = entry.getValue();
            AtlasPage page = font.pages.get(entry.getKey());
            int n = runs.size();

            // Pre-compute vertex data: positions, UVs, colors
            float[] pos = new float[n * 8];  // 4 verts × 2 floats
            float[] uv  = new float[n * 8];
            byte[]  col = new byte[n * 16];  // 4 verts × 4 bytes
            int pi = 0, ui = 0, ci2 = 0;

            for (GlyphRun run : runs) {
                Glyph g = run.glyph;
                float x0 = run.x + g.bearingX;
                float y1 = baselineY + g.bearingY;           // top (bearingY is negative above baseline)
                float x1 = x0 + g.width;

                // 【修复】Y轴向下递增，底部Y坐标应该是 顶部Y坐标 + 高度
                float y0 = y1 + g.height;

                int[] rgba = emitter.emit(run.charIndex, g);

                // TL, BL, BR, TR  (clockwise from top-left)
                putVertex(pos, pi, uv, ui, col, ci2, x0, y1, g.u0, g.v0, rgba, 0);
                pi += 2; ui += 2; ci2 += 4;
                putVertex(pos, pi, uv, ui, col, ci2, x0, y0, g.u0, g.v1, rgba, 4);
                pi += 2; ui += 2; ci2 += 4;
                putVertex(pos, pi, uv, ui, col, ci2, x1, y0, g.u1, g.v1, rgba, 8);
                pi += 2; ui += 2; ci2 += 4;
                putVertex(pos, pi, uv, ui, col, ci2, x1, y1, g.u1, g.v0, rgba, 12);
                pi += 2; ui += 2; ci2 += 4;
            }

            gui.submitGuiElementRenderState(
                new GlyphMeshState(page, pos, uv, col, n));
        }
    }

    private static void putVertex(float[] pos, int pi,
                                   float[] uv, int ui,
                                   byte[] col, int ci,
                                   float px, float py, float u, float v,
                                   int[] rgba, int ri) {
        pos[pi]   = px;
        pos[pi+1] = py;
        uv[ui]    = u;
        uv[ui+1]  = v;
        col[ci]   = (byte) rgba[ri];
        col[ci+1] = (byte) rgba[ri+1];
        col[ci+2] = (byte) rgba[ri+2];
        col[ci+3] = (byte) rgba[ri+3];
    }

    // ========================
    //  GUI RENDER STATE
    // ========================

    /**
         * Custom {@link GuiElementRenderState} that renders textured quads
         * with per-vertex RGBA colors. Submitted to the GUI render queue
         * via {@link GuiGraphicsExtractor#submitGuiElementRenderState}.
         */
        private record GlyphMeshState(TextureSetup textureSetup, float[] positions, float[] uvs, byte[] colors,
                                      int quadCount) implements GuiElementRenderState {
            private static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;
            private static final Matrix3x2f IDENTITY = new Matrix3x2f();

            private GlyphMeshState(AtlasPage textureSetup, float[] positions, float[] uvs, byte[] colors,
                                   int quadCount) {
                this(textureSetup.textureSetup(), positions, uvs, colors, quadCount);
            }

            @Override
            public void buildVertices(@NonNull VertexConsumer vc) {
                int vi = 0, ui = 0, ci = 0;
                for (int q = 0; q < quadCount; q++) {
                    for (int v = 0; v < 4; v++) {
                        vc.addVertexWith2DPose(IDENTITY, positions[vi], positions[vi + 1])
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
                return PIPELINE;
            }

        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }

        @Override
        public ScreenRectangle bounds() {
            return null;
        }
        }

    // ========================
    //  CLEANUP
    // ========================

    /** Release GPU resources for this font. */
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

    /** Release all cached fonts. */
    public static void disposeAll() {
        FONT_CACHE.values().forEach(CustomFontRenderer::dispose);
        FONT_CACHE.clear();
    }
}
