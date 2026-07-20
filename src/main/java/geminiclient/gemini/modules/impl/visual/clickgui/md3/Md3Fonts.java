package geminiclient.gemini.modules.impl.visual.clickgui.md3;

import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * Lazy-loaded Google Sans {@link CustomFontRenderer.GlyphFont} holders for the
 * MD3 ClickGui. All MD3 text renders through the MSDF pipeline — never mc.font.
 *
 * <p>Holders are lazy because font loading needs the resource manager,
 * which is unavailable at class-load time.</p>
 */
public final class Md3Fonts {

    private Md3Fonts() {
    }

    private static final Identifier GOOGLE_SANS = getIdentifier("font/googlesans-regular.ttf");

    private static final float SIZE_DISPLAY = 22f;  // hero card headline
    private static final float SIZE_TITLE   = 12f;  // module/title medium
    private static final float SIZE_BODY    = 10f;  // value labels, menu items
    private static final float SIZE_LABEL   = 9f;   // rail labels, supporting text, chips
    private static final float SIZE_SEARCH  = 11f;  // search bar text

    private static volatile CustomFontRenderer.GlyphFont display;
    private static volatile CustomFontRenderer.GlyphFont title;
    private static volatile CustomFontRenderer.GlyphFont body;
    private static volatile CustomFontRenderer.GlyphFont label;
    private static volatile CustomFontRenderer.GlyphFont search;

    public static CustomFontRenderer.@Nullable GlyphFont display() { return get(0); }
    public static CustomFontRenderer.@Nullable GlyphFont title()   { return get(1); }
    public static CustomFontRenderer.@Nullable GlyphFont body()    { return get(2); }
    public static CustomFontRenderer.@Nullable GlyphFont label()   { return get(3); }
    public static CustomFontRenderer.@Nullable GlyphFont search()  { return get(4); }

    private static CustomFontRenderer.@Nullable GlyphFont get(int which) {
        try {
            return switch (which) {
                case 0 -> display != null ? display : (display = CustomFontRenderer.loadFont(
                        GOOGLE_SANS, SIZE_DISPLAY, Font.BOLD));
                case 1 -> title   != null ? title   : (title   = CustomFontRenderer.loadFont(
                        GOOGLE_SANS, SIZE_TITLE, Font.BOLD));
                case 2 -> body    != null ? body    : (body    = CustomFontRenderer.loadFont(GOOGLE_SANS, SIZE_BODY));
                case 3 -> label   != null ? label   : (label   = CustomFontRenderer.loadFont(GOOGLE_SANS, SIZE_LABEL));
                default -> search != null ? search : (search = CustomFontRenderer.loadFont(GOOGLE_SANS, SIZE_SEARCH));
            };
        } catch (Throwable t) {
            return null;
        }
    }

    // ── Startup warmup ──────────────────────────────────

    /**
     * Extra glyphs beyond printable ASCII that the MD3 GUI draws as text
     * (middle dot + em dash in module supporting lines).
     */
    private static final String EXTRA_WARMUP_GLYPHS = "·—";

    /**
     * Eagerly loads all five font faces and rasterises the printable-ASCII
     * glyph set (plus {@link #EXTRA_WARMUP_GLYPHS}) for each, so the first
     * ClickGui open doesn't stall on MSDF generation. Called by
     * {@code UiShaderWarmup} on the render thread during resource reload;
     * callers must follow with {@code CustomFontRenderer.flushAllPages()} to
     * upload the pending atlas pages. Repeat calls are cheap — loaded faces
     * and rasterised glyphs are cached.
     */
    public static void warmup() {
        for (int which = 0; which <= 4; which++) {
            CustomFontRenderer.GlyphFont font = get(which);
            if (font == null) {
                continue;
            }
            for (int cp = 0x20; cp <= 0x7E; cp++) {
                font.getGlyph(cp);
            }
            for (int i = 0; i < EXTRA_WARMUP_GLYPHS.length(); i++) {
                font.getGlyph(EXTRA_WARMUP_GLYPHS.charAt(i));
            }
        }
    }

    // ── Null-safe drawing / measuring helpers ───────────

    /**
     * Draws text with the given GlyphFont, falling back to the vanilla font.
     * Coordinates are snapped to whole pixels: fractional origins blur MSDF
     * glyph edges, and integer origins keep rendering crisp and consistent
     * across HiDPI GUI scales.
     */
    public static void drawText(GuiGraphicsExtractor gui, CustomFontRenderer.@Nullable GlyphFont font,
                                String text, float x, float y, int color) {
        int xi = Math.round(x);
        int yi = Math.round(y);
        if (font != null) {
            CustomFontRenderer.drawString(gui, font, text, xi, yi, color);
        } else {
            gui.text(mc.font, text, xi, yi, color, false);
        }
    }

    public static float width(CustomFontRenderer.@Nullable GlyphFont font, String text) {
        return font != null ? CustomFontRenderer.stringWidth(font, text) : mc.font.width(text);
    }

    /** Line height of the font (vanilla fallback: 9). */
    public static float lineHeight(CustomFontRenderer.@Nullable GlyphFont font) {
        return font != null ? font.lineHeight : 9f;
    }
}
