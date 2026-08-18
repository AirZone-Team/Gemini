package geminiclient.gemini.modules.impl.visual.osu4k.screen;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomBlurRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import geminiclient.gemini.modules.impl.visual.Osu4k;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState.Judgment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

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
    private static final int GLASS_TOP = 0x33202636;
    private static final int GLASS_BOTTOM = 0x1E141B24;
    protected static final int GLASS_OUTLINE = 0x5AFFFFFF;
    protected static final int GLASS_OUTLINE_SOFT = 0x33FFFFFF;
    private static final int SHADOW = 0x3C000000;

    // Text + accent palette.
    protected static final int COLOR_TEXT = 0xFFEFF3FA;
    protected static final int COLOR_TEXT_DIM = 0xFF9AA3B4;
    protected static final int COLOR_ACCENT = 0xFF4FC3F7;
    protected static final int COLOR_SUCCESS = 0xFF7EE081;
    protected static final int COLOR_WARN = 0xFFFFD28A;
    protected static final int COLOR_ERROR = 0xFFFF6E6E;

    protected final Screen parent;
    protected GlyphFont titleFont;
    protected GlyphFont itemFont;
    protected GlyphFont smallFont;

    private static final long BLUR_FADE_IN_MS = 300L;
    private final long openedAtMs = System.currentTimeMillis();

    protected Osu4kScreen(Screen parent, String title) {
        super(Component.literal(title));
        this.parent = parent;
    }

    /** Fade used by the global blur boundary while the OSU4K screen opens. */
    public float getBlurFade() {
        return Math.min(1.0f, (System.currentTimeMillis() - openedAtMs) / (float) BLUR_FADE_IN_MS);
    }

    /** OSU4K owns the explicit blur boundary in MixinGameRenderer. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractTransparentBackground(graphics);
        this.minecraft.gui.hud.extractDeferredSubtitles();
    }

    @Override
    protected void init() {
        super.init();
        loadFonts();
    }

    /**
     * Closes this screen to {@code next}, disabling the OSU4K module when that
     * leaves the OSU4K screen flow (e.g. Esc on the select screen). Navigation
     * between OSU4K screens keeps the module enabled, so the flow stays usable
     * until the user really exits it.
     */
    protected void closeTo(Screen next) {
        this.minecraft.gui.setScreen(next);
        if (!(next instanceof Osu4kScreen)) {
            Osu4k.requestDisable();
        }
    }

    protected void loadFonts() {
        try {
            titleFont = CustomFontRenderer.loadFont(FONT, 24f);
            itemFont = CustomFontRenderer.loadFont(FONT, 15f);
            smallFont = CustomFontRenderer.loadFont(FONT, 12.5f);
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

    protected void drawStringRightAligned(GuiGraphicsExtractor gui, GlyphFont font, String text,
                                          float rightX, float centerY, int color) {
        if (font == null) {
            return;
        }
        float w = CustomFontRenderer.stringWidth(font, text);
        CustomFontRenderer.drawString(gui, font, text, rightX - w, centerY - fontHeight(font) / 2f, color);
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

    // ---------------------------------------------------------------------
    // Shared helpers (easing, formatting, effects)
    // ---------------------------------------------------------------------

    protected static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    protected static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Overshoot-and-settle easing used by pop-in effects. */
    protected static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3)
                + c1 * (float) Math.pow(t - 1.0f, 2);
    }

    /** Formats ms as m:ss (progress rail, results screen). */
    protected static String fmtTime(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** Colour used for the judgement popup, hit-effect rings and hold pulses. */
    protected static int judgmentColor(Judgment j) {
        return switch (j) {
            case PERFECT -> 0xFFFFD700;
            case GREAT -> 0xFF7EE081;
            case GOOD -> 0xFF4FC3F7;
            case MISS -> COLOR_ERROR;
        };
    }

    /** Soft rounded glow blob centred at {@code (cx, cy)} with the given alpha. */
    protected void drawAccentGlowAt(GuiGraphicsExtractor gui, float cx, float cy, float w, float h, int color, int alpha) {
        int argb = (alpha << 24) | (color & 0xFFFFFF);
        CustomRoundedRectRenderer.drawRoundedRect(
                gui, Math.round(cx - w / 2f), Math.round(cy - h / 2f), Math.round(w), Math.round(h), (int) (h / 2f), argb);
    }
}
