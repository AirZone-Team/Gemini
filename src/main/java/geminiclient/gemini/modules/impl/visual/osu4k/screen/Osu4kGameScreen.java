package geminiclient.gemini.modules.impl.visual.osu4k.screen;

import geminiclient.gemini.base.I18n;
import geminiclient.gemini.modules.impl.visual.osu4k.Osu4k;
import geminiclient.gemini.modules.impl.visual.osu4k.audio.FfmpegAudioPlayer;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState.HitEntry;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState.Judgment;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState.ObjectStatus;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kKeyConfig;
import geminiclient.gemini.modules.impl.visual.osu4k.game.SmoothedPlayhead;
import geminiclient.gemini.modules.impl.visual.osu4k.model.BeatmapData;
import geminiclient.gemini.modules.impl.visual.osu4k.model.HitObject;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.Osu4kNoteRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The 4K playfield screen.
 *
 * <p>Layout (top to bottom): a 44 px header (song title only), the score/combo
 * band drawn at the bottom-most layer beneath the playfield, the four-lane
 * playfield with the judgement line at 82% height, and a 64 px footer with the
 * draggable progress bar and a single auto-width button row (transport plus
 * difficulty / keys / pause / exit).</p>
 *
 * <p>Timing is driven entirely by the audio clock ({@link FfmpegAudioPlayer#positionMsFresh()}),
 * so notes stay locked to the music regardless of frame rate. The music is
 * delayed by 3 s: the game clock starts at −3 s and advances by wall time, so
 * the first notes are already falling when the playfield opens and reach the
 * judgement line exactly as the music starts on the beat. Note rendering
 * extrapolates that clock by wall time per frame ({@link SmoothedPlayhead}) so
 * notes glide smoothly between the clock's batch updates; judging uses the
 * wall-time-extrapolated audio clock ({@link FfmpegAudioPlayer#positionMsFresh()}),
 * so a keypress is judged against the audible position at the instant it lands,
 * not the frozen value between the decode thread's batch updates. The game
 * state is advanced every rendered frame in {@link #extractRenderState} and
 * input is applied in {@link #keyPressed} / {@link #keyReleased}.</p>
 */
public final class Osu4kGameScreen extends Osu4kScreen {

    private static final int HEADER_H = 44;
    /** Score/combo band directly below the header (bottom-most draw layer). */
    private static final int SCORE_H = 26;
    private static final int FOOTER_H = 64;
    private static final float JUDGE_LINE_FRACTION = 0.87f;

    /** Note visual scale: shrinks note caps / glows so the longer lane stays
     *  readable and fits more notes on screen at once. */
    private static final float NOTE_SCALE = 0.8f;
    /** Rendered note head thickness (px), scaled down from the original 16. */
    private static final int NOTE_CAP_H = Math.round(16 * NOTE_SCALE);

    // Footer button row ids.
    private static final int BTN_SKIP_BACK = 0;
    private static final int BTN_PLAY = 1;
    private static final int BTN_SKIP_FWD = 2;
    private static final int BTN_DIFF = 3;
    private static final int BTN_PAUSE = 4;
    private static final int BTN_KEYS = 5;
    private static final int BTN_EXIT = 6;
    private static final int FOOTER_BTN_H = 26;
    private static final int FOOTER_BTN_GAP = 8;

    private BeatmapData map;
    private final FfmpegAudioPlayer audio = Osu4k.AUDIO;

    private Osu4kGameState state;
    private Path audioFile;

    /**
     * Renders notes off the audio clock, extrapolated per-frame by wall time so
     * falling notes glide smoothly between the audio clock's batch updates.
     * Judging uses the wall-time-extrapolated audio clock
     * ({@link FfmpegAudioPlayer#positionMsFresh()}) so presses are timed against
     * the audible position at the instant they land.
     */
    private final SmoothedPlayhead smoother = new SmoothedPlayhead();

    /** Wall-clock (System.currentTimeMillis) moment the music starts; while
     *  non-zero the 3s lead-in is still running (0 = music running or failed). */
    private long musicStartAtMs;
    /** Wall-clock of the previous frame while the lead-in runs; freezes the
     *  lead-in clock across a pause (0 = no previous lead-in frame yet). */
    private long lastLeadFrameMs;

    // UI state.
    private boolean difficultyDropdownOpen;
    private boolean paused;
    private boolean draggingProgress;
    private long lastFrameMs;
    private int mouseX;
    private int mouseY;

    // End-of-song transition to the results screen: when the audio ends and the
    // last note is judged, the playfield freezes and shows a "Clear!" flash for
    // CLEAR_HOLD_MS, then the results screen opens (guarded by resultsShown).
    private static final long CLEAR_HOLD_MS = 800;
    private long clearedAt;
    private boolean resultsShown;

    /**
     * Music delay in ms: the audio is held silent for this long after the
     * screen opens while the game clock runs from −3 s up to 0, so the first
     * notes fall into view and land on the judgement line just as the music
     * starts. Replaces the old paused countdown.
     */
    private static final long MUSIC_DELAY_MS = 3000;

    // Judgement popup + lane effects.
    private Judgment lastJudgment;
    private long judgmentAtMs;
    private final int[] laneFlashEndMs = new int[Osu4kKeyConfig.COLUMNS];
    private final boolean[] laneDown = new boolean[Osu4kKeyConfig.COLUMNS];

    // Judgement-line hit effects (expanding rings) + hold pulse state.
    private final List<HitEffect> hitEffects = new java.util.ArrayList<>();
    private final boolean[] holdPulseActive = new boolean[Osu4kKeyConfig.COLUMNS];

    // Layout cache.
    private int fieldTop, fieldBottom, fieldLeft, fieldRight, judgeY, laneW;

    /**
     * Expanding ring burst drawn at the judgement line when a note is hit.
     * {@code hold} marks the burst of a hold head / hold completion.
     */
    private record HitEffect(int lane, long spawnMs, int durationMs, int color, boolean hold) {
        float progress(long now) {
            return (now - spawnMs) / (float) durationMs;
        }
        boolean done(long now) {
            return now - spawnMs >= durationMs;
        }
    }

    /** One auto-sized button in the footer row (transport + actions). */
    private record FooterBtn(int id, int x, int y, int w, int h) {}

    public Osu4kGameScreen(Screen parent, BeatmapData map) {
        super(parent, "OSU4k Play");
        this.map = map;
        this.state = new Osu4kGameState(map, 0, Osu4k.judgementPreset());
    }

    @Override
    protected void init() {
        super.init();
        lastFrameMs = System.currentTimeMillis();
        computeLayout();

        // Extract the audio (shared per beatmap set) and hold it silent for the
        // 3s music lead-in: no countdown, but the audio is delayed 3s — the
        // game clock runs from -3s up to 0 so the first notes are already
        // falling when the playfield opens and land as the music starts.
        try {
            if (Osu4k.currentArchive == null) {
                return;
            }
            audioFile = Osu4k.currentArchive.extractAudio(map, cacheRoot());
            audio.unload();
            audio.load(audioFile);
            audio.setVolume(Osu4k.volume());
            state = new Osu4kGameState(map, Osu4k.offset(), Osu4k.judgementPreset());
            paused = false;
            // No lead-in if the audio failed to load: show the error instead.
            musicStartAtMs = audio.isLoaded() ? System.currentTimeMillis() + MUSIC_DELAY_MS : 0;
            smoother.reset(audio.positionMs(), System.nanoTime());
        } catch (Exception e) {
            errorMessage = I18n.trf("Audio load failed: %s", e.getMessage());
        }
    }

    private String errorMessage;

    private void computeLayout() {
        fieldLeft = Math.max(20, this.width / 2 - 240);
        fieldRight = Math.min(this.width - 20, this.width / 2 + 240);
        fieldTop = HEADER_H + SCORE_H + 6;
        fieldBottom = this.height - FOOTER_H - 6;
        judgeY = fieldTop + (int) ((fieldBottom - fieldTop) * JUDGE_LINE_FRACTION);
        laneW = (fieldRight - fieldLeft) / 4;
    }

    private Path cacheRoot() {
        return this.minecraft.gameDirectory.toPath()
                .resolve("gemini").resolve("osu4k").resolve("cache");
    }

    /** Switches difficulty within the same beatmap set. */
    private void reloadMap(BeatmapData newMap) {
        audio.unload();
        try {
            Path newAudio = Osu4k.currentArchive.extractAudio(newMap, cacheRoot());
            audio.load(newAudio);
            this.map = newMap;
            this.state = new Osu4kGameState(newMap, Osu4k.offset(), Osu4k.judgementPreset());
            paused = false;
            // A fresh difficulty starts like a fresh run: 3s music lead-in with
            // the first notes already falling when it opens.
            musicStartAtMs = System.currentTimeMillis() + MUSIC_DELAY_MS;
            smoother.reset(audio.positionMs(), System.nanoTime());
            lastJudgment = null;
            hitEffects.clear();
            java.util.Arrays.fill(holdPulseActive, false);
            audioFile = newAudio;
        } catch (Exception e) {
            errorMessage = I18n.trf("Failed to switch difficulty: %s", e.getMessage());
        }
    }

    /**
     * Seeks the audio and rewinds the game state. Judgement restarts from the
     * new playhead — notes behind it will be re-missed, matching osu!'s seek
     * behaviour (score/combo reset).
     */
    private void seekTo(long targetMs) {
        audio.seekToMs(targetMs);
        smoother.reset(audio.positionMs(), System.nanoTime());
        state.reset();
        lastJudgment = null;
        hitEffects.clear();
        java.util.Arrays.fill(holdPulseActive, false);
    }

    // ---------------------------------------------------------------------
    // Frame update
    // ---------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastFrameMs) / 1000f, 0.1f);
        lastFrameMs = now;

        // While paused the 3s music lead-in clock is frozen: push the music
        // start forward by the paused time, so the lead-in never runs out and
        // the notes don't keep falling while the music is stopped.
        if (musicStartAtMs != 0 && paused && lastLeadFrameMs != 0) {
            musicStartAtMs += now - lastLeadFrameMs;
        }
        lastLeadFrameMs = now;

        // 3s music lead-in: the audio is held silent while the game clock runs
        // from -3s up to 0 by wall time, so the first notes are already falling
        // when the playfield opens; the music starts on the beat at 0 and the
        // first note lands right as it begins. Nothing can be hit or missed
        // during the lead-in (the playhead is still before the first note).
        if (musicStartAtMs != 0 && now >= musicStartAtMs) {
            musicStartAtMs = 0;
            if (!paused) {
                audio.play();
            }
        }
        // Judging playhead: wall time during the 3s lead-in, then the audio
        // clock extrapolated to now (never frozen between the decode thread's
        // batch updates), so presses and auto-misses are timed against the
        // audible position of this instant.
        long playMs = musicStartAtMs != 0 ? now - musicStartAtMs : audio.positionMsFresh();

        // The song ran out while the final notes were still pending (the audio
        // clock's batch updates can skip the last judging frame): judge them at
        // the true end so the run can finish cleanly.
        if (audio.state() == FfmpegAudioPlayer.State.ENDED) {
            state.update(audio.durationMs());
        }

        // Raw-key fallback: recognize lane presses that the event stream
        // dropped or delayed under heavy frames, so simultaneous presses are
        // never lost. laneDown keeps this and keyPressed from double-firing.
        // Skipped during the lead-in so a key held across the music start is
        // picked up cleanly by the poll as the lead-in ends.
        if (musicStartAtMs == 0) {
            pollLaneInput(playMs);
        }

        // Advance the game logic while playback runs. The song plays to its
        // natural end even after every note has been judged.
        if (audio.isPlaying() && !paused) {
            state.update(playMs);
        }

        // The song ended and every note is judged: freeze the playfield, flash
        // "Clear!" for CLEAR_HOLD_MS, then open the results screen. Fires once
        // per run (resultsShown); the parent screen is the difficulty picker,
        // so Retry/Back from the results screen behave naturally. The finished
        // check uses the true audio end (the playhead can lag it by one audio
        // clock batch, and the extra update above judges the final notes).
        if (!resultsShown && !paused && audio.state() == FfmpegAudioPlayer.State.ENDED
                && state.isFinished(audio.durationMs())) {
            clearedAt = now;
            resultsShown = true;
            audio.pause();
        }
        if (resultsShown && clearedAt != 0 && now - clearedAt >= CLEAR_HOLD_MS && this.minecraft.gui.screen() == this) {
            this.minecraft.gui.setScreen(new Osu4kResultsScreen(parent, map, state));
        }

        // The rendered playhead extrapolates the audio clock by wall time so
        // notes glide smoothly between the audio clock's batch updates; judging
        // above always uses the raw audio clock. While paused the playhead is
        // re-anchored every frame so it neither advances nor jumps on resume.
        long scrollMs;
        if (paused || !audio.isPlaying()) {
            smoother.reset(playMs, System.nanoTime());
            scrollMs = playMs;
        } else {
            scrollMs = smoother.update(playMs, System.nanoTime(), audio.durationMs());
        }

        // Hold pulse lifecycle: a hold that was hit and is no longer active
        // (released or its tail passed) fires its completion burst once.
        for (int c = 0; c < Osu4kKeyConfig.COLUMNS; c++) {
            if (holdPulseActive[c] && state.activeHold(c) == null) {
                holdPulseActive[c] = false;
                spawnHitEffect(c, Osu4k.laneColor(c), 380, true);
            }
        }

        // Prune finished hit effects.
        hitEffects.removeIf(e -> e.done(now));

        // Progress-bar dragging via raw mouse state (same trick as BackgroundSelectorScreen).
        if (draggingProgress) {
            long h = this.minecraft.getWindow().handle();
            boolean leftDown = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (!leftDown) {
                draggingProgress = false;
            } else {
                long dur = Math.max(1, audio.durationMs());
                float frac = (this.mouseX - fieldLeft) / (float) (fieldRight - fieldLeft);
                frac = Math.max(0f, Math.min(1f, frac));
                seekTo((long) (frac * dur));
            }
        }

        fillBackground(gui);
        // Score/combo sit at the bottom-most draw layer, directly below the
        // header — everything else (playfield, footer) paints over them.
        drawScoreInfo(gui);
        drawHeader(gui);
        drawPlayfield(gui, scrollMs);
        // Progress bar tracks the real audio position (the 3s music delay makes
        // the game playhead run ahead of it), so it reads 0% at the start and
        // 100% at the song end.
        drawFooter(gui, audio.positionMsFresh());
        drawJudgmentPopup(gui, now);
        drawClearOverlay(gui, now);
        if (errorMessage != null) {
            drawCentered(gui, itemFont, errorMessage, this.width / 2f, this.height / 2f, COLOR_ERROR);
        }
        if (Osu4k.debug()) {
            drawDebugOverlay(gui, playMs);
        }
    }

    // ---------------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------------

    private void drawHeader(GuiGraphicsExtractor gui) {
        CustomRectRenderer.drawRect(gui, 0, 0, this.width, HEADER_H, STRIP);
        CustomRectRenderer.drawRect(gui, 0, HEADER_H - 1, this.width, 1, STRIP_EDGE);

        // Song title only — score/combo live in their own band below, and the
        // action buttons moved to the footer.
        String title = map.title();
        if (!map.artist().isEmpty()) {
            title = map.artist() + " - " + title;
        }
        drawStringCenteredY(gui, titleFont, title, 14, HEADER_H / 2f, COLOR_TEXT);
        float titleW = titleFont == null
                ? textWidth(title, 24f)
                : CustomFontRenderer.stringWidth(titleFont, title);
        drawStringCenteredY(gui, itemFont, "[" + map.version() + "]", 16 + titleW + 4, HEADER_H / 2f, COLOR_ACCENT);
    }

    /**
     * Score, combo and progress counters drawn directly below the header at the
     * bottom-most layer: the playfield and footer are painted on top of it.
     */
    private void drawScoreInfo(GuiGraphicsExtractor gui) {
        String scoreText = String.format(Locale.ROOT, "%,d", state.score());
        drawCentered(gui, titleFont, scoreText, this.width / 2f, HEADER_H + 10, COLOR_TEXT);

        // Combo line, with the live accuracy centered on the right side of the
        // same row: green while ≥95% (S and above), red below 70% (D), neutral
        // otherwise.
        String comboText = state.combo() + "x  /  " + I18n.tr("max") + " " + state.maxCombo()
                + "  /  " + state.judgedCount() + "/" + state.totalNotes();
        drawCentered(gui, smallFont, comboText, this.width / 2f, HEADER_H + 24, COLOR_TEXT_DIM);

        double acc = state.accuracy();
        String accText = String.format(Locale.ROOT, "%.2f%%", acc * 100);
        int accColor = acc >= 0.95 ? COLOR_SUCCESS : acc >= 0.70 ? COLOR_TEXT : COLOR_ERROR;
        drawCentered(gui, smallFont, accText, this.width * 3f / 4f, HEADER_H + 24, accColor);
    }

    // ---------------------------------------------------------------------
    // Playfield
    // ---------------------------------------------------------------------

    private void drawPlayfield(GuiGraphicsExtractor gui, long playMs) {
        // Frosted-glass playfield backdrop: blur the world behind the lanes and
        // tint it, so the notes stay readable over a blurred background.
        geminiclient.gemini.customRenderer.glsl.CustomBlurRenderer.render(
                fieldLeft, fieldTop, fieldRight - fieldLeft, fieldBottom - fieldTop, 0, 8f);

        // Lanes are translucent — the blurred menu/world behind shows through.
        for (int c = 0; c < 4; c++) {
            int lx = fieldLeft + c * laneW;
            CustomRectRenderer.drawRect(gui, lx, fieldTop, laneW, fieldBottom - fieldTop,
                    (c % 2 == 0) ? 0x38121824 : 0x2E10141E);
            if (c > 0) {
                CustomRectRenderer.drawRect(gui, lx, fieldTop, 1, fieldBottom - fieldTop, 0x3DFFFFFF);
            }
        }
        CustomRectRenderer.drawRect(gui, fieldLeft + laneW * 4, fieldTop, 1, fieldBottom - fieldTop, 0x3DFFFFFF);

        // Judgement line: soft glow + a brighter core, matching the accent.
        CustomRoundedRectRenderer.drawRoundedRect(gui, fieldLeft - 4, judgeY - 6, fieldRight - fieldLeft + 8, 12, 6, 0x3D4FC3F7);
        CustomRectRenderer.drawRect(gui, fieldLeft, judgeY - 2, fieldRight - fieldLeft, 4, 0xCCFFFFFF);

        float scroll = Osu4k.scrollSpeed();
        int offset = Osu4k.offset();
        long now = playMs;

        for (int c = 0; c < 4; c++) {
            List<HitObject> col = state.columnObjects(c);
            int lx = fieldLeft + c * laneW;
            int laneColor = Osu4k.laneColor(c);

            for (int i = 0; i < col.size(); i++) {
                HitObject ho = col.get(i);
                ObjectStatus st = state.status(c, i);

                // Taps vanish once judged (they have their own hit/miss effects).
                // A judged hold is still drawn while its tail is above the line
                // (missed / released early), shrinking toward the line.
                if (st == ObjectStatus.JUDGED && !ho.isHold()) {
                    continue;
                }

                float headY = judgeY - (ho.timeMs() + offset - now) * scroll;
                if (headY < fieldTop - 140) {
                    continue;
                }
                // Notes shrink by NOTE_SCALE (centred in the lane) so more of
                // the longer track stays visible at once.
                int laneWp = Math.max(24, Math.round((laneW - 10) * NOTE_SCALE));
                int lxp = lx + (laneW - laneWp) / 2;

                if (ho.isHold()) {
                    float tailY = judgeY - (ho.endTimeMs() + offset - now) * scroll;
                    // A judged hold with its tail already below the line is fully
                    // consumed — nothing left to draw.
                    if (st == ObjectStatus.JUDGED && tailY > judgeY + 2) {
                        continue;
                    }
                    boolean dead = st == ObjectStatus.JUDGED;

                    // Body is clipped between the appear line (field top) and the
                    // disappear line (judgement line): it grows from the top as the
                    // note enters, holds full length on screen, then shortens toward
                    // the line as it is consumed or as a missed hold slides past it.
                    float top = Math.max(fieldTop, tailY);
                    float bot = Math.min(Math.max(headY, tailY), judgeY);
                    if (bot - top >= 2f) {
                        drawNoteGlow(gui, lxp, top, laneWp, bot - top, laneColor, dead ? 0.12f : 0.25f);
                        CustomRoundedRectRenderer.drawRoundedRect(gui, lxp, (int) top, laneWp, (int) (bot - top), 4,
                                dead ? 0x55FFFFFF : 0x99FFFFFF);
                    }
                    int capColor = dead ? (Osu4k.laneColor(c) & 0xFFFFFF) | 0x99000000 : laneColor;
                    // Head cap: while above the line (the leading edge).
                    if (headY <= judgeY) {
                        drawNoteCap(gui, lxp, Math.max(headY, fieldTop), laneWp, capColor);
                    }
                    // Tail cap: slides in over the top edge, then down to the line.
                    if (tailY >= fieldTop - 2 && tailY <= judgeY) {
                        drawNoteCap(gui, lxp, Math.max(tailY, fieldTop), laneWp, capColor);
                    }
                } else {
                    drawNoteGlow(gui, lxp, headY - NOTE_CAP_H / 2f, laneWp, NOTE_CAP_H, laneColor, 0.45f);
                    drawNoteCap(gui, lxp, headY, laneWp, laneColor);
                }
            }
        }

        // Lane press glow.
        long nowMs = System.currentTimeMillis();
        if (Osu4k.laneHighlight()) {
            for (int c = 0; c < 4; c++) {
                if (laneDown[c] || nowMs < laneFlashEndMs[c]) {
                    int alpha = laneDown[c] ? 0x40 : 0x18;
                    int rgb = Osu4k.laneColor(c) & 0xFFFFFF;
                    CustomRectRenderer.drawRect(gui, fieldLeft + c * laneW, fieldTop, laneW, fieldBottom - fieldTop,
                            (alpha << 24) | rgb);
                }
            }
        }

        // Hold pulse: continuous pulsing ring while a hold is being held.
        if (Osu4k.hitEffects()) {
            for (int c = 0; c < 4; c++) {
                if (holdPulseActive[c] && state.activeHold(c) != null) {
                    drawHoldPulse(gui, c, nowMs);
                }
            }

            // Judgement-line hit effects (expanding rings).
            for (HitEffect e : hitEffects) {
                drawHitEffect(gui, e, nowMs);
            }
        }
    }

    /** Continuous pulsing ring on the judgement line while a hold is held. */
    private void drawHoldPulse(GuiGraphicsExtractor gui, int column, long nowMs) {
        float t = ((nowMs % 600) / 600f); // 0..1 pulse cycle
        float scale = 0.75f + 0.25f * (float) Math.sin(t * Math.PI);
        int radius = Math.round(laneW * 0.30f * scale);
        int alpha = (int) (90 + 60 * (float) Math.sin(t * Math.PI));
        int color = Osu4k.laneColor(column) & 0xFFFFFF;
        float cx = fieldLeft + column * laneW + laneW / 2f;
        CustomRoundedRectRenderer.drawRing(gui, cx, judgeY, radius, 3,
                (alpha << 24) | color);
        // Soft glow disc under the pulse.
        int glow = 0x22 << 24;
        CustomRoundedRectRenderer.drawCircle(gui, cx, judgeY, (int) (radius * 2.2f), glow | color);
    }

    /** Expanding + fading ring burst at the judgement line. */
    private void drawHitEffect(GuiGraphicsExtractor gui, HitEffect e, long nowMs) {
        float p = Math.min(1f, e.progress(nowMs));
        float ease = 1f - (1f - p) * (1f - p); // ease-out for the expansion
        int radius = Math.max(6, Math.round(laneW * (e.hold ? 0.62f : 0.48f) * ease));
        int alpha = (int) (220 * (1f - p));
        int color = e.color() & 0xFFFFFF;
        float cx = fieldLeft + e.lane() * laneW + laneW / 2f;
        CustomRoundedRectRenderer.drawRing(gui, cx, judgeY, radius, 4, (alpha << 24) | color);
        if (p < 0.35f) {
            int burst = (int) (alpha * (1f - p / 0.35f));
            CustomRoundedRectRenderer.drawCircle(gui, cx, judgeY, Math.max(4, radius), (burst << 24) | color);
        }
    }

    private void spawnHitEffect(int column, int color, int durationMs, boolean hold) {
        if (column < 0 || column >= Osu4kKeyConfig.COLUMNS || !Osu4k.hitEffects()) {
            return;
        }
        hitEffects.add(new HitEffect(column, System.currentTimeMillis(), durationMs, color, hold));
    }

    private void drawNoteGlow(GuiGraphicsExtractor gui, int x, float y, int w, float h, int laneColor, float alpha) {
        int cy = (int) Math.max(fieldTop, Math.min(fieldBottom, y));
        if (cy > fieldBottom || cy + 8 < fieldTop) {
            return;
        }
        int glowColor = laneColor | 0xFF000000; // use the lane hue, alpha modulated below
        Osu4kNoteRenderer.drawNoteGlow(gui, x, cy, w, (int) Math.max(8, Math.min(h, fieldBottom - fieldTop)),
                Math.max(6, Math.round(10 * NOTE_SCALE)), alpha, glowColor);
    }

    private void drawNoteCap(GuiGraphicsExtractor gui, int x, float y, int w, int color) {
        if (y < fieldTop || y > fieldBottom + 60) {
            return;
        }
        int h = NOTE_CAP_H;
        int r = Math.min(6, h / 2);
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, (int) (y - h / 2f), w, h, r, color);
    }

    // ---------------------------------------------------------------------
    // Footer (progress + transport)
    // ---------------------------------------------------------------------

    private void drawFooter(GuiGraphicsExtractor gui, long playMs) {
        int fy = this.height - FOOTER_H;
        CustomRectRenderer.drawRect(gui, 0, fy, this.width, FOOTER_H, STRIP);
        CustomRectRenderer.drawRect(gui, 0, fy, this.width, 1, STRIP_EDGE);

        int barX = fieldLeft;
        int barY = fy + 14;
        int barW = fieldRight - fieldLeft;
        long dur = Math.max(1, audio.durationMs());
        float frac = (float) playMs / dur;
        frac = Math.max(0f, Math.min(1f, frac));

        CustomRoundedRectRenderer.drawRoundedRect(gui, barX, barY, barW, 6, 3, 0x332A6C8F);
        if (frac > 0) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, barX, barY, Math.max(6, (int) (barW * frac)), 6, 3, COLOR_ACCENT);
        }
        CustomRoundedRectRenderer.drawCircle(gui, barX + barW * frac, barY + 3, 8, 0xFFE6E9F2);

        String cur = fmtTime(playMs);
        String tot = fmtTime(dur);
        drawStringCenteredY(gui, smallFont, cur, barX - textWidth(cur, 12.5f) - 8, barY + 3, COLOR_TEXT_DIM);
        drawStringCenteredY(gui, smallFont, tot, barX + barW + 8, barY + 3, COLOR_TEXT_DIM);

        // Single auto-width button row: transport controls plus the four
        // actions that used to live in the header.
        String[] labels = footerButtonLabels();
        List<FooterBtn> btns = footerButtons();
        for (int i = 0; i < btns.size(); i++) {
            FooterBtn b = btns.get(i);
            boolean hovered = inRect(mouseX, mouseY, b.x(), b.y(), b.w(), b.h());
            drawButton(gui, b.x(), b.y(), b.w(), b.h(), labels[i], hovered, true);
        }

        // Difficulty dropdown opens upward, clear of the footer.
        if (difficultyDropdownOpen) {
            FooterBtn diff = btns.get(BTN_DIFF);
            List<BeatmapData> maps = Osu4k.currentArchive == null ? List.of() : Osu4k.currentArchive.playableMaps();
            int rows = Math.min(maps.size(), 5);
            int ddH = rows * 24 + 4;
            drawDifficultyDropdown(gui, diff.x(), diff.y() - ddH - 2, rows);
        }
    }

    /** Labels of the footer button row, in draw order. */
    private String[] footerButtonLabels() {
        return new String[]{
                "-5s",
                paused ? I18n.tr("Play") : I18n.tr("Pause"),
                "+5s",
                map.version(),
                paused ? I18n.tr("Resume") : I18n.tr("Pause"),
                I18n.tr("Keys"),
                I18n.tr("ExitGame")
        };
    }

    /**
     * Footer buttons laid out in a single centred row, each sized to its text.
     */
    private List<FooterBtn> footerButtons() {
        String[] labels = footerButtonLabels();
        int[] ws = new int[labels.length];
        int total = 0;
        for (int i = 0; i < labels.length; i++) {
            ws[i] = Math.round(textWidth(labels[i], 15f)) + 36;
            total += ws[i];
        }
        total += FOOTER_BTN_GAP * (labels.length - 1);
        int x = (this.width - total) / 2;
        int y = this.height - FOOTER_H + 32;
        List<FooterBtn> out = new java.util.ArrayList<>(labels.length);
        for (int i = 0; i < labels.length; i++) {
            out.add(new FooterBtn(i, x, y, ws[i], FOOTER_BTN_H));
            x += ws[i] + FOOTER_BTN_GAP;
        }
        return out;
    }

    private void drawDifficultyDropdown(GuiGraphicsExtractor gui, int x, int y, int rows) {
        List<BeatmapData> maps = Osu4k.currentArchive == null ? List.of() : Osu4k.currentArchive.playableMaps();
        int w = 180;
        int h = rows * 24;
        CustomRoundedRectRenderer.drawRoundedRect(gui, x, y, w, h + 4, 6, 0xEE151A26);
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, h + 4, 6, GLASS_OUTLINE, 1);
        for (int i = 0; i < rows; i++) {
            BeatmapData m = maps.get(i);
            int rowY = y + 2 + i * 24;
            if (inRect(mouseX, mouseY, x + 2, rowY, w - 4, 22)) {
                CustomRectRenderer.drawRect(gui, x + 2, rowY, w - 4, 22, 0x332A6C8F);
            }
            drawStringCenteredY(gui, itemFont, m.version(), x + 10, rowY + 11, COLOR_TEXT);
            if (m == map) {
                drawCentered(gui, itemFont, "\u2713", x + w - 14, rowY + 11, COLOR_ACCENT);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Debug overlay
    // ---------------------------------------------------------------------

    private void drawDebugOverlay(GuiGraphicsExtractor gui, long playMs) {
        int x = 12;
        int y = fieldTop + 10;
        drawStringCenteredY(gui, itemFont, "time=" + playMs + "ms audio=" + audio.positionMs() + "ms state=" + audio.state(), x, y, COLOR_WARN);
        y += 14;
        drawStringCenteredY(gui, itemFont, "offset=" + Osu4k.offset() + " scroll=" + Osu4k.scrollSpeed(), x, y, COLOR_WARN);
        y += 14;
        // Judgement windows of the current preset, in ms.
        drawStringCenteredY(gui, itemFont, String.format(Locale.ROOT,
                        "windows: P\u00b1%.0f G\u00b1%.0f Go\u00b1%.0f Ms\u00b1%.0f",
                        state.perfectWindowMs(), state.greatWindowMs(), state.goodWindowMs(), state.missWindowMs()),
                x, y, COLOR_WARN);
        y += 14;
        // Recent hits with the points each note awarded.
        List<HitEntry> log = state.debugLog();
        int shown = Math.min(8, log.size());
        for (int i = log.size() - shown; i < log.size(); i++) {
            HitEntry e = log.get(i);
            int color = e.judgment() == Judgment.PERFECT ? COLOR_SUCCESS
                    : e.judgment() == Judgment.MISS ? COLOR_ERROR : COLOR_WARN;
            drawStringCenteredY(gui, itemFont,
                    String.format(Locale.ROOT, "note=%5d diff=%+4d %-7s +%,d",
                            e.noteTimeMs(), e.diffMs(), I18n.tr(e.judgment().label), e.points()),
                    x, y, color);
            y += 13;
        }
    }

    // ---------------------------------------------------------------------
    // Judgement popup
    // ---------------------------------------------------------------------

    private void flashJudgment(Judgment j) {
        if (j == null) {
            return;
        }
        lastJudgment = j;
        judgmentAtMs = System.currentTimeMillis();
    }

    private void drawJudgmentPopup(GuiGraphicsExtractor gui, long now) {
        if (lastJudgment == null) {
            return;
        }
        long elapsed = now - judgmentAtMs;
        if (elapsed > 700) {
            lastJudgment = null;
            return;
        }
        // Pop in over the first 120ms, then fade out; scales from 1.2x -> 1.0x.
        float fade = 1f - elapsed / 700f;
        float pop = 1f + (elapsed < 120 ? (1f - elapsed / 120f) * 0.2f : 0f);
        int color = judgmentColor(lastJudgment);
        int alpha = (int) (fade * 255);
        int argb = (alpha << 24) | (color & 0xFFFFFF);
        float cx = this.width / 2f;
        float cy = judgeY - 80;

        // Soft glow backing the judgement text.
        drawAccentGlowAt(gui, cx, cy, 120 * pop, 34 * pop, color, (int) (fade * 40));
        drawCentered(gui, titleFont, I18n.tr(lastJudgment.label), cx, cy, argb);
    }

    /** Colour used for the judgement popup, hit-effect rings and hold pulses. */
    private static int judgmentColor(Judgment j) {
        return switch (j) {
            case PERFECT -> 0xFFFFD700;
            case GREAT -> 0xFF7EE081;
            case GOOD -> 0xFF4FC3F7;
            case MISS -> COLOR_ERROR;
        };
    }

    private void drawAccentGlowAt(GuiGraphicsExtractor gui, float cx, float cy, float w, float h, int color, int alpha) {
        int argb = (alpha << 24) | (color & 0xFFFFFF);
        geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer.drawRoundedRect(
                gui, Math.round(cx - w / 2f), Math.round(cy - h / 2f), Math.round(w), Math.round(h), (int) (h / 2f), argb);
    }

    /**
     * End-of-song flash: a white vignette fading from full over the frozen
     * playfield while a "Clear!" caption pops in, for the {@link #CLEAR_HOLD_MS}
     * window before the results screen opens. Nothing is drawn outside that
     * window (also after the screen already switched away).
     */
    private void drawClearOverlay(GuiGraphicsExtractor gui, long now) {
        if (clearedAt == 0 || now < clearedAt || now >= clearedAt + CLEAR_HOLD_MS) {
            return;
        }
        long elapsed = now - clearedAt;
        float p = elapsed / (float) CLEAR_HOLD_MS;
        // Flash fades out over the first 70% of the window.
        float flash = 1f - Math.min(1f, p / 0.7f);
        int flashAlpha = Math.round(140 * flash);
        CustomRectRenderer.drawRect(gui, 0, 0, this.width, this.height,
                (flashAlpha << 24) | 0xFFFFFF);

        // Caption: pops in fast (easeOutBack over 180ms), then fades out.
        float cap = (elapsed - 60f) / 180f;
        if (cap > 0f) {
            cap = Math.min(1f, cap);
            float scale = 1f + (1f - easeOutBack(cap)) * 0.35f;
            float fade = Math.min(1f, (1f - p) * 2f);
            int alpha = Math.round(fade * 255);
            float size = 34f * scale;
            float hw = titleFont == null ? 34f * scale * 5f : CustomFontRenderer.stringWidth(titleFont, clearText()) * scale;
            drawAccentGlowAt(gui, this.width / 2f, this.height / 2f, hw + 60, size + 20, 0xFFFFFF, (int) (fade * 90));
            drawCentered(gui, titleFont, clearText(), this.width / 2f, this.height / 2f,
                    (alpha << 24) | 0xFF4FC3F7);
        }
    }

    private static String clearText() {
        return I18n.tr("Clear!");
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    private int laneForColumn(int key) {
        int[] keys = Osu4k.KEYS;
        for (int c = 0; c < 4; c++) {
            if (keys[c] == key) {
                return c;
            }
        }
        return -1;
    }

    /**
     * Applies a lane press: lane flash, judge, and spawn the hit / hold effects.
     * Shared by {@link #keyPressed} and the per-frame raw-key poll.
     */
    private void handleLanePress(int col, long playMs) {
        laneFlashEndMs[col] = (int) (System.currentTimeMillis() + 220);
        Judgment j = state.press(col, playMs);
        flashJudgment(j);
        if (j != null && j != Judgment.MISS) {
            int ringColor = judgmentColor(j);
            if (state.activeHold(col) != null) {
                // Hold head hit: start the pulse, burst fires on release.
                holdPulseActive[col] = true;
                spawnHitEffect(col, ringColor, 320, true);
            } else {
                spawnHitEffect(col, ringColor, 300, false);
            }
        }
    }

    /** Applies a lane release (completes a hold). Shared with the raw-key poll. */
    private void handleLaneRelease(int col, long playMs) {
        flashJudgment(state.release(col, playMs));
    }

    /**
     * Polls the raw keyboard state once per frame. GLFW events are queued
     * through {@code Minecraft.execute}, so two presses landing close together
     * can be delayed or dropped when a frame renders slowly; polling guarantees
     * every currently-held lane key is recognized at the playhead of this
     * frame. {@code laneDown} mirrors the physical state after each frame, so
     * this only fires for presses / releases the event path never saw.
     */    private void pollLaneInput(long playMs) {
        long h = this.minecraft.getWindow().handle();
        for (int c = 0; c < Osu4kKeyConfig.COLUMNS; c++) {
            boolean down = GLFW.glfwGetKey(h, Osu4k.KEYS[c]) == GLFW.GLFW_PRESS;
            if (down && !laneDown[c]) {
                laneDown[c] = true;
                handleLanePress(c, playMs);
            } else if (!down && laneDown[c]) {
                laneDown[c] = false;
                handleLaneRelease(c, playMs);
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (event.isEscape()) {
            closeToParent();
            return true;
        }
        int col = laneForColumn(key);
        if (col >= 0) {
            // During the 3s music lead-in lane presses are ignored; the
            // per-frame poll picks up any key already held when it ends.
            if (musicStartAtMs != 0) {
                return true;
            }
            // Low-latency fast path; the per-frame poll is the fallback and
            // laneDown keeps the two from double-firing.
            if (!laneDown[col]) {
                laneDown[col] = true;
                handleLanePress(col, audio.positionMsFresh());
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE) {
            togglePause();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            seekTo(Math.max(0, audio.positionMs() - 5000));
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            seekTo(audio.positionMs() + 5000);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        int col = laneForColumn(event.key());
        if (col >= 0) {
            // Match the press gate: releases during the lead-in are ignored so
            // laneDown stays consistent with the presses that were allowed.
            if (musicStartAtMs != 0) {
                return true;
            }
            laneDown[col] = false;
            handleLaneRelease(col, audio.positionMsFresh());
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean idk) {
        if (mouse.button() != 0) {
            return super.mouseClicked(mouse, idk);
        }
        double mx = mouse.x();
        double my = mouse.y();

        List<FooterBtn> btns = footerButtons();
        FooterBtn diff = btns.get(BTN_DIFF);

        // Difficulty dropdown (opens upward from the footer button).
        if (inRect(mx, my, diff.x(), diff.y(), diff.w(), diff.h())) {
            difficultyDropdownOpen = !difficultyDropdownOpen;
            return true;
        }
        if (difficultyDropdownOpen) {
            List<BeatmapData> maps = Osu4k.currentArchive == null ? List.of() : Osu4k.currentArchive.playableMaps();
            int rows = Math.min(maps.size(), 5);
            int ddH = rows * 24 + 4;
            int ddY = diff.y() - ddH - 2;
            for (int i = 0; i < rows; i++) {
                if (inRect(mx, my, diff.x(), ddY + 2 + i * 24, 180, 22)) {
                    BeatmapData m = maps.get(i);
                    if (m != map) {
                        reloadMap(m);
                    }
                    difficultyDropdownOpen = false;
                    return true;
                }
            }
            difficultyDropdownOpen = false;
        }

        for (FooterBtn b : btns) {
            if (!inRect(mx, my, b.x(), b.y(), b.w(), b.h())) {
                continue;
            }
            switch (b.id()) {
                case BTN_SKIP_BACK -> seekTo(Math.max(0, audio.positionMs() - 5000));
                case BTN_PLAY, BTN_PAUSE -> togglePause();
                case BTN_SKIP_FWD -> seekTo(audio.positionMs() + 5000);
                case BTN_KEYS -> this.minecraft.gui.setScreen(new Osu4kKeybindScreen(this));
                case BTN_EXIT -> closeToParent();
                case BTN_DIFF -> { /* handled above */ }
                default -> { }
            }
            return true;
        }

        // Progress bar.
        int fy = this.height - FOOTER_H;
        int barY = fy + 14;
        if (inRect(mx, my, fieldLeft, barY - 6, fieldRight - fieldLeft, 22)) {
            long dur = Math.max(1, audio.durationMs());
            float frac = (float) ((mx - fieldLeft) / (double) (fieldRight - fieldLeft));
            seekTo((long) (Math.max(0f, Math.min(1f, frac)) * dur));
            draggingProgress = true;
            return true;
        }
        return super.mouseClicked(mouse, idk);
    }

    private void togglePause() {
        paused = !paused;
        if (paused) {
            audio.pause();
        } else if (musicStartAtMs == 0) {
            // Resume now. If the music lead-in already finished while paused,
            // the music starts here; otherwise the lead-in clock resumes and
            // the music starts when it ends (handled in extractRenderState).
            audio.play();
        }
    }

    private void closeToParent() {
        audio.pause();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static String fmtTime(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** Overshoot-and-settle easing used by the "Clear!" caption pop. */
    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3)
                + c1 * (float) Math.pow(t - 1.0f, 2);
    }
}
