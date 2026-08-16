package geminiclient.gemini.modules.impl.visual.osu4k.screen;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomBlurRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import geminiclient.gemini.modules.impl.visual.osu4k.Osu4k;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Shared scaffolding for the OSU4K screens: fonts, palette and small layout
 * helpers. Follows the pattern of {@code MainMenuScreen} / {@code AltManagerScreen}.
 *
 * <p>The visuals use the same glass-panel language as the ClickGUI / main menu:
 * blurred, softly-shadowed rounded panels with a vertical gradient fill and a
 * thin translucent outline, on a dark scene with a faint accent glow.</p>
 */
public abstract class Osu4kScreen extends Screen {

    protected static final Identifier FONT = Identifier.fromNamespaceAndPath("gemini", "font/misans-bold.ttf");

    // Scene + panel palette (slightly lighter than pure black for depth).
    protected static final int COLOR_BG = 0xFF0A0C11;
    protected static final int GLASS_TOP = 0x33202636;
    protected static final int GLASS_BOTTOM = 0x1E141B24;
    protected static final int GLASS_OUTLINE = 0x5AFFFFFF;
    protected static final int GLASS_OUTLINE_SOFT = 0x33FFFFFF;
    protected static final int SHADOW = 0x3C000000;

    // Text + accent palette.
    protected static final int COLOR_TEXT = 0xFFEFF3FA;
    protected static final int COLOR_TEXT_DIM = 0xFF9AA3B4;
    protected static final int COLOR_ACCENT = 0xFF4FC3F7;
    protected static final int COLOR_ACCENT_SOFT = 0x334FC3F7;
    protected static final int COLOR_SUCCESS = 0xFF7EE081;
    protected static final int COLOR_WARN = 0xFFFFD28A;
    protected static final int COLOR_ERROR = 0xFFFF6E6E;

    // Header/footer strip colors (translucent so the blurred backdrop shows).
    protected static final int STRIP = 0x9910141D;
    protected static final int STRIP_EDGE = 0x33FFFFFF;

    protected final Screen parent;
    protected GlyphFont titleFont;
    protected GlyphFont itemFont;
    protected GlyphFont smallFont;
    /** Large display font (countdown / rank), loaded lazily; may be null. */
    protected GlyphFont bigFont;

    protected Osu4kScreen(Screen parent, String title) {
        super(Component.literal(title));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        loadFonts();
    }

    protected void loadFonts() {
        try {
            titleFont = CustomFontRenderer.loadFont(FONT, 24f);
            itemFont = CustomFontRenderer.loadFont(FONT, 15f);
            smallFont = CustomFontRenderer.loadFont(FONT, 12.5f);
            bigFont = CustomFontRenderer.loadFont(FONT, 60f);
        } catch (Exception e) {
            titleFont = null;
            itemFont = null;
            smallFont = null;
        }
    }

    // ---------------------------------------------------------------------
    // Background / panels
    // ---------------------------------------------------------------------

    /**
     * Paints the scene backdrop. Instead of an opaque fill this lays a
     * translucent dark vignette over whatever Minecraft has already drawn and
     * blurred for us ({@code Screen.extractBackground} blurs the panorama / menu
     * background or the paused world), giving a frosted-glass scene instead of
     * a flat colour.
     */
    protected void fillBackground(GuiGraphicsExtractor gui) {
        // Deep navy vignette, slightly darker at the edges.
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, 0, 0, this.width, this.height, 0, 0xA0141826, 0xC00A0C12);
    }

    /**
     * Full glass panel: shadow -> blur -> gradient fill -> outline.
     * Mirrors the AltManager/ClickGUI panel recipe.
     */
    protected void drawGlassPanel(GuiGraphicsExtractor gui, int x, int y, int w, int h, int r) {
        SdfUIRenderer.drawShadow(gui, x, y, w, h, r, 0, 5, 16, SHADOW);
        CustomBlurRenderer.render(x, y, w, h, r, 0x220A0E14, 6f);
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, x, y, w, h, r, GLASS_TOP, GLASS_BOTTOM);
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, h, r, GLASS_OUTLINE, 1);
    }

    /** Subtle accent glow strip used under titles and active elements. */
    protected void drawAccentGlow(GuiGraphicsExtractor gui, float cx, float y, float halfW, float height) {
        int a = 0x28;
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui,
                Math.round(cx - halfW), Math.round(y), Math.round(halfW * 2), Math.round(height),
                2, (a << 24) | (COLOR_ACCENT & 0xFFFFFF), 0x00000000);
    }

    // ---------------------------------------------------------------------
    // Text helpers
    // ---------------------------------------------------------------------

    protected float textWidth(String text, float fallbackSize) {
        GlyphFont f = itemFont;
        if (f == null) {
            return text.length() * fallbackSize * 0.6f;
        }
        return CustomFontRenderer.stringWidth(f, text);
    }

    protected void drawCentered(GuiGraphicsExtractor gui, GlyphFont font, String text,
                                float centerX, float centerY, int color) {
        if (font == null) {
            return;
        }
        float w = CustomFontRenderer.stringWidth(font, text);
        float h = fontHeight(font);
        CustomFontRenderer.drawString(gui, font, text, centerX - w / 2f, centerY - h / 2f, color);
    }

    protected void drawStringCenteredY(GuiGraphicsExtractor gui, GlyphFont font, String text,
                                       float x, float centerY, int color) {
        if (font == null) {
            return;
        }
        CustomFontRenderer.drawString(gui, font, text, x, centerY - fontHeight(font) / 2f, color);
    }

    protected float fontHeight(GlyphFont font) {
        return font == null ? 14f : font.lineHeight;
    }

    // ---------------------------------------------------------------------
    // Buttons
    // ---------------------------------------------------------------------

    protected static boolean inRect(double x, double y, double rx, double ry, double rw, double rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }

    protected void drawButton(GuiGraphicsExtractor gui, int x, int y, int w, int h, String label,
                              boolean hovered, boolean enabled) {
        int top = hovered ? 0x3A2A3A52 : 0x33232B3A;
        int bottom = hovered ? 0x261D2431 : 0x1F1B2230;
        int outline = hovered ? 0x99FFFFFF : GLASS_OUTLINE_SOFT;
        int textColor = enabled ? COLOR_TEXT : COLOR_TEXT_DIM;
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, x, y, w, h, 7, top, bottom);
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, h, 7, outline, 1);
        drawCentered(gui, itemFont, label, x + w / 2f, y + h / 2f, textColor);
    }

    /** Compact icon-style button (smaller corner radius, tighter text). */
    protected void drawIconButton(GuiGraphicsExtractor gui, int x, int y, int w, int h, String label, boolean hovered) {
        int fill = hovered ? 0x44313C55 : 0x2E253047;
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, w, h, 6, fill);
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, h, 6, GLASS_OUTLINE_SOFT, 1);
        drawCentered(gui, smallFont, label, x + w / 2f, y + h / 2f, COLOR_TEXT);
    }

    // ---------------------------------------------------------------------
    // Colour helpers
    // ---------------------------------------------------------------------

    /** Multiplies a colour's alpha by {@code scale} (0..1). */
    protected static int scaleAlpha(int color, float scale) {
        int a = (color >>> 24) & 0xFF;
        a = Math.max(0, Math.min(255, Math.round(a * scale)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** Blends {@code foreground} over {@code background} by an integer alpha (0..255). */
    protected static int blend(int foreground, int background, int fgAlpha) {
        int a = fgAlpha & 0xFF;
        int inv = 255 - a;
        int r = ((foreground >>> 16 & 0xFF) * a + (background >>> 16 & 0xFF) * inv) / 255;
        int g = ((foreground >>> 8 & 0xFF) * a + (background >>> 8 & 0xFF) * inv) / 255;
        int b = ((foreground & 0xFF) * a + (background & 0xFF) * inv) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
