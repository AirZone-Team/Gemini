package geminiclient.gemini.modules.impl.visual.osu4k.screen;

import geminiclient.gemini.base.I18n;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState.Judgment;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kRating;
import geminiclient.gemini.modules.impl.visual.osu4k.model.BeatmapData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.Locale;

/**
 * Results screen shown after a 4K run ends: the letter rank (with a bouncy
 * pop-in), the final accuracy and a thin progress bar, the stat tiles, the
 * per-judgment counts, an optional "Full Combo" badge, and Retry / Back.
 *
 * <p>The panel itself fades in like the other OSU4K screens; inside it the
 * elements stagger in top-to-bottom (rank letter first, buttons last) so the
 * reveal reads as a layered transition rather than one flat fade. The game
 * state is taken frozen from the game screen — the run is over, nothing here
 * mutates it. Retry builds a fresh {@link Osu4kGameScreen} from the same map.</p>
 */
public final class Osu4kResultsScreen extends Osu4kScreen {

    private static final int PANEL_W = 560;
    private static final int PANEL_H = 500;
    private static final int BTN_W = 140;
    private static final int BTN_H = 34;
    private static final int BTN_GAP = 16;

    private final BeatmapData map;
    private final Osu4kGameState state;
    private final Osu4kRating.Rank rank;

    private int panelX, panelY;
    private long openAtMs;
    private GlyphFont rankFont;

    public Osu4kResultsScreen(Screen parent, BeatmapData map, Osu4kGameState state) {
        super(parent, "OSU4k Results");
        this.map = map;
        this.state = state;
        this.rank = Osu4kRating.rank(state.score());
    }

    @Override
    protected void init() {
        super.init();
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
        openAtMs = System.currentTimeMillis();
        rankFont = CustomFontRenderer.loadFont(FONT, 64f);
    }

    // ---------------------------------------------------------------------
    // Reveal helpers (staggered entrance)
    // ---------------------------------------------------------------------

    /** Panel entrance progress, 0..1 (drives the fade + rise of the whole panel). */
    private float panelReveal(long now) {
        return easeOutCubic(clamp01((now - openAtMs) / 300f));
    }

    /** Staggered element progress starting {@code delayMs} after open. */
    private float revealAt(long now, long delayMs) {
        return easeOutCubic(clamp01((now - openAtMs - delayMs) / 400f));
    }

    /** Rank-letter pop: overshoots past 1 (bouncy settle) but clamps the alpha. */
    private float rankPop(long now) {
        return easeOutBack(clamp01((now - openAtMs - 80f) / 480f));
    }

    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        fillBackground(gui);
        long now = System.currentTimeMillis();
        float panel = panelReveal(now);
        int py = panelY + Math.round((1f - panel) * 12f);

        drawGlassPanel(gui, panelX, py, PANEL_W, PANEL_H, 14);

        int textColor = scaleAlpha(COLOR_TEXT, panel);
        int dimColor = scaleAlpha(COLOR_TEXT_DIM, panel);
        int accentColor = scaleAlpha(COLOR_ACCENT, panel);

        // Title block.
        drawCentered(gui, titleFont, I18n.tr("Results"), panelX + PANEL_W / 2f, py + 28, accentColor);
        String song = map.artist().isEmpty() ? map.title() : map.artist() + " - " + map.title();
        if (!map.version().isEmpty()) {
            song += "  [" + map.version() + "]";
        }
        drawCentered(gui, smallFont, song, panelX + PANEL_W / 2f, py + 48, dimColor);

        // Rank letter with a bouncy pop + glow.
        int rankColor = rankColor(rank);
        float pop = rankPop(now);
        float letterAlpha = clamp01(pop);
        float glowScale = pop; // overshoots slightly, then settles
        float rankCy = py + 128;
        float glowW = (rankFont == null ? 120f : CustomFontRenderer.stringWidth(rankFont, rank.name())) * glowScale;
        drawAccentGlowAt(gui, panelX + PANEL_W / 2f, rankCy, glowW + 70, 86 * glowScale, rankColor,
                Math.round(letterAlpha * 70));
        drawCentered(gui, rankFont, rank.name(), panelX + PANEL_W / 2f, rankCy + Math.round((1f - letterAlpha) * 12f),
                scaleAlpha(rankColor, letterAlpha));

        // Accuracy + thin progress bar.
        float accReveal = revealAt(now, 240);
        float acc = (float) state.accuracy();
        String accText = String.format(Locale.ROOT, "%.2f%%", acc * 100);
        drawCentered(gui, titleFont, accText, panelX + PANEL_W / 2f, py + 216, scaleAlpha(COLOR_TEXT, accReveal));
        int barX = panelX + 140;
        int barW = PANEL_W - 280;
        int barY = py + 232;
        CustomRoundedRectRenderer.drawRoundedRect(gui, barX, barY, barW, 5, 3,
                scaleAlpha(0x4D40495E, accReveal));
        if (acc > 0) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, barX, barY,
                    Math.max(5, Math.round(barW * acc * accReveal)), 5, 3,
                    scaleAlpha(rankColor, accReveal));
        }

        // Full Combo badge.
        boolean fc = Osu4kRating.fullCombo(state.maxCombo(), state.totalNotes());
        if (fc) {
            float fcReveal = revealAt(now, 320);
            drawFullComboBadge(gui, panelX + PANEL_W / 2f, py + 260, fcReveal);
        }

        // Stat tiles: Score / Max Combo / Song Time.
        int tileY = py + 294;
        int tileH = 56;
        int gap = 10;
        int tileW = (PANEL_W - 48 - gap * 2) / 3;
        drawStatTile(gui, panelX + 24, tileY, tileW, tileH, I18n.tr("Score"),
                String.format(Locale.ROOT, "%,d", state.score()), revealAt(now, 360), mouseX, mouseY);
        drawStatTile(gui, panelX + 24 + tileW + gap, tileY, tileW, tileH, I18n.tr("Max Combo"),
                state.maxCombo() + "x", revealAt(now, 420), mouseX, mouseY);
        drawStatTile(gui, panelX + 24 + (tileW + gap) * 2, tileY, tileW, tileH, I18n.tr("Song Time"),
                fmtTime(map.songLengthMs()), revealAt(now, 480), mouseX, mouseY);

        // Per-judgment counts.
        int jY = py + 374;
        int jColW = PANEL_W / 4;
        Judgment[] order = {Judgment.PERFECT, Judgment.GREAT, Judgment.GOOD, Judgment.MISS};
        for (int i = 0; i < order.length; i++) {
            float r = revealAt(now, 420 + i * 60);
            int color = scaleAlpha(judgmentColor(order[i]), r);
            drawCentered(gui, smallFont, I18n.tr(order[i].label), panelX + jColW * i + jColW / 2f, jY, color);
            drawCentered(gui, titleFont, String.valueOf(state.judgmentCount(order[i])),
                    panelX + jColW * i + jColW / 2f, jY + 22, scaleAlpha(COLOR_TEXT, r));
        }

        // Retry / Back buttons.
        float btnReveal = revealAt(now, 700);
        int totalW = BTN_W * 2 + BTN_GAP;
        int btnX = panelX + (PANEL_W - totalW) / 2;
        int btnY = py + 436;
        boolean retryHover = inRect(mouseX, mouseY, btnX, btnY, BTN_W, BTN_H);
        boolean backHover = inRect(mouseX, mouseY, btnX + BTN_W + BTN_GAP, btnY, BTN_W, BTN_H);
        drawButton(gui, btnX, btnY, BTN_W, BTN_H, I18n.tr("Retry"), retryHover, true);
        drawButton(gui, btnX + BTN_W + BTN_GAP, btnY, BTN_W, BTN_H, I18n.tr("Back"), backHover, true);
    }

    private void drawStatTile(GuiGraphicsExtractor gui, int x, int y, int w, int h, String label,
                              String value, float reveal, int mouseX, int mouseY) {
        boolean hovered = inRect(mouseX, mouseY, x, y, w, h);
        int top = hovered ? 0x3A2C3A54 : 0x2A232D40;
        int bottom = hovered ? 0x28222A3C : 0x201B2230;
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, x, y, w, h, 8,
                scaleAlpha(top, reveal), scaleAlpha(bottom, reveal));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, h, 8,
                scaleAlpha(hovered ? 0x88FFFFFF : GLASS_OUTLINE_SOFT, reveal), 1);
        drawCentered(gui, smallFont, label, x + w / 2f, y + 16, scaleAlpha(COLOR_TEXT_DIM, reveal));
        drawCentered(gui, itemFont, value, x + w / 2f, y + 38, scaleAlpha(COLOR_TEXT, reveal));
    }

    /** Small accent pill with the "Full Combo" label. */
    private void drawFullComboBadge(GuiGraphicsExtractor gui, float cx, float cy, float reveal) {
        String label = I18n.tr("Full Combo");
        float w = textWidth(label, 12.5f) + 36;
        int x = Math.round(cx - w / 2f);
        int y = Math.round(cy - 13f);
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, Math.round(w), 26, 13,
                scaleAlpha(0x2E7EE081, reveal));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, Math.round(w), 26, 13,
                scaleAlpha(COLOR_SUCCESS, reveal), 1);
        drawCentered(gui, smallFont, label, cx, cy, scaleAlpha(COLOR_SUCCESS, reveal));
    }

    private void drawAccentGlowAt(GuiGraphicsExtractor gui, float cx, float cy, float w, float h, int color, int alpha) {
        int argb = (alpha << 24) | (color & 0xFFFFFF);
        CustomRoundedRectRenderer.drawRoundedRect(
                gui, Math.round(cx - w / 2f), Math.round(cy - h / 2f), Math.round(w), Math.round(h), (int) (h / 2f), argb);
    }

    // ---------------------------------------------------------------------
    // Colours
    // ---------------------------------------------------------------------

    /** Rank letter colour: gold for SS/S, green A, blue B, orange C, red D. */
    private static int rankColor(Osu4kRating.Rank rank) {
        return switch (rank) {
            case SSS, SS, S -> 0xFFFFD700;
            case A -> 0xFF7EE081;
            case B -> 0xFF4FC3F7;
            case C -> 0xFFFFD28A;
            case D -> 0xFFFF6E6E;
        };
    }

    /** Judgment text colour, matching the in-game popups. */
    private static int judgmentColor(Judgment j) {
        return switch (j) {
            case PERFECT -> 0xFFFFD700;
            case GREAT -> 0xFF7EE081;
            case GOOD -> 0xFF4FC3F7;
            case MISS -> COLOR_ERROR;
        };
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean idk) {
        if (mouse.button() != 0) {
            return super.mouseClicked(mouse, idk);
        }
        double mx = mouse.x();
        double my = mouse.y();
        int py = panelY + Math.round((1f - panelReveal(System.currentTimeMillis())) * 12f);
        int totalW = BTN_W * 2 + BTN_GAP;
        int btnX = panelX + (PANEL_W - totalW) / 2;
        int btnY = py + 436;
        if (inRect(mx, my, btnX, btnY, BTN_W, BTN_H)) {
            retry();
            return true;
        }
        if (inRect(mx, my, btnX + BTN_W + BTN_GAP, btnY, BTN_W, BTN_H)) {
            this.minecraft.gui.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mouse, idk);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            this.minecraft.gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    /** Replays the same difficulty; the game screen reloads the audio and plays. */
    private void retry() {
        this.minecraft.gui.setScreen(new Osu4kGameScreen(parent, map));
    }

    // ---------------------------------------------------------------------
    // Easing
    // ---------------------------------------------------------------------

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3)
                + c1 * (float) Math.pow(t - 1.0f, 2);
    }

    private static String fmtTime(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
