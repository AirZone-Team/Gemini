package geminiclient.gemini.modules.impl.visual.osu4k.screen;

import geminiclient.gemini.base.I18n;
import geminiclient.gemini.modules.impl.visual.Osu4k;
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
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The mania playfield screen (lane count follows the map, 1K-10K).
 *
 * <p>Layout: compact song statistics float at the top, the playfield fills
 * the centre, a vertical progress rail sits on the right, and compact
 * controls are split between the lower left and right edges.</p>
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

    private static final int TOP_INFO_H = 44;
    private static final int CONTROL_H = 28;
    private static final int PROGRESS_W = 18;
    private static final int PROGRESS_GAP = 18;
    private static final int SIDE_MARGIN = 18;
    private static final long NOTE_PREVIEW_MS = 1000L;
    private static final float JUDGE_LINE_FRACTION = 0.95f;

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
    private static final int FOOTER_BTN_H = 20;
    private static final int FOOTER_BTN_GAP = 5;
    private static final int FOOTER_BTN_PAD = 22;
    private static final int FOOTER_BTN_FONT = 12;

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
    private String errorMessage;

    // End-of-song transition to the results screen: when the audio ends and the
    // last note is judged, the playfield freezes and shows a "Clear!" flash for
    // CLEAR_HOLD_MS, then the results screen opens (guarded by resultsShown).
    private static final long CLEAR_HOLD_MS = 800;
    private long clearedAt;
    private boolean resultsShown;
    /**
     * Last line of defence against a spurious audio ENDED: the audio player's
     * own recovery re-seeks and resumes on decode hiccups, but if the playhead
     * still reports the song over with most of it unplayed, this re-seeks to
     * the extrapolated playhead and resumes playback exactly once. If the
     * audio then genuinely ends there, the results screen opens as usual.
     */
    private long resumeAtMs = -1;

    /**
     * Music delay in ms: the audio is held silent for this long after the
     * screen opens while the game clock runs from −3 s up to 0, so the first
     * notes fall into view and land on the judgement line just as the music
     * starts. Replaces the old paused countdown.
     */
    private static final long MUSIC_DELAY_MS = 3000;

    // Judgement popup + lane effects. Arrays are fixed at the maximum lane
    // count so switching difficulty never reallocates them; all loops bound
    // by the current map's column count.
    private Judgment lastJudgment;
    private long judgmentAtMs;
    private final int[] laneFlashEndMs = new int[Osu4kKeyConfig.MAX_KEYS];
    private final boolean[] laneDown = new boolean[Osu4kKeyConfig.MAX_KEYS];

    // Judgement-line hit effects (expanding rings) + hold pulse state.
    private final List<HitEffect> hitEffects = new java.util.ArrayList<>();
    private final boolean[] holdPulseActive = new boolean[Osu4kKeyConfig.MAX_KEYS];

    // Layout cache.
    private int fieldTop, fieldBottom, fieldLeft, fieldRight, judgeY, laneW;
    private int progressX, progressTop, progressBottom;

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
    private record FooterBtn(int id, int x, int y, int w, int h, String label) {}

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
            resumeAtMs = -1;
            // No lead-in if the audio failed to load: show the error instead.
            musicStartAtMs = audio.isLoaded() ? System.currentTimeMillis() + MUSIC_DELAY_MS : 0;
            smoother.reset(audio.positionMs(), System.nanoTime());
        } catch (Exception e) {
            errorMessage = I18n.trf("Audio load failed: %s", e.getMessage());
        }
    }

    private void computeLayout() {
        int progressReserve = PROGRESS_W + PROGRESS_GAP + SIDE_MARGIN;
        fieldLeft = Math.max(SIDE_MARGIN, this.width / 2 - 280);
        fieldRight = Math.min(this.width - progressReserve, this.width / 2 + 280);
        if (fieldRight - fieldLeft < 160) {
            fieldLeft = SIDE_MARGIN;
            fieldRight = Math.max(fieldLeft + 160, this.width - progressReserve);
        }
        fieldTop = TOP_INFO_H;
        fieldBottom = Math.max(fieldTop + 80, this.height - CONTROL_H - 6);
        judgeY = fieldTop + (int) ((fieldBottom - fieldTop) * JUDGE_LINE_FRACTION);
        laneW = Math.max(1, (fieldRight - fieldLeft) / columns());

        progressX = this.width - SIDE_MARGIN - PROGRESS_W;
        progressTop = Math.max(TOP_INFO_H + 8, 42);
        progressBottom = Math.max(progressTop + 80, this.height - CONTROL_H - 6);
    }

    /** Lane count of the current map (1K-10K). */
    private int columns() {
        return map.keyCount();
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
            resumeAtMs = -1;
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
        resumeAtMs = -1;
    }

    /**
     * Re-seeks and resumes once if the audio player reports end-of-stream
     * while most of the song is still unplayed. The decode thread normally
     * recovers from mid-song null frames itself; this catches whatever slips
     * through (or the decoded tail simply underrunning the extrapolated
     * playhead). Only fires when playback was actually running and never when
     * paused or near the true end, so a genuine finish still opens the results
     * screen untouched.
     *
     * @return true when the audio was re-seeked and playhead must be re-read
     */
    private boolean resumeSpuriousEnded(long playMs) {
        if (paused || resumeAtMs != -1 || audio.state() != FfmpegAudioPlayer.State.ENDED) {
            return false;
        }
        long dur = audio.durationMs();
        if (dur <= 0 || playMs >= dur - 500) {
            return false; // genuinely at the end
        }
        resumeAtMs = playMs;
        audio.resumeAt(resumeAtMs);
        return true;
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

        // The audio player recovers from decode hiccups on its own, but if it
        // still reports the song over with most of it unplayed, re-seek to the
        // extrapolated playhead and resume once before giving up (see
        // resumeSpuriousEnded). Runs only when playback was actually going:
        // a pause or a genuine end never re-seeks, so the results screen still
        // opens for a normal finish.
        if (resumeSpuriousEnded(playMs)) {
            playMs = audio.positionMsFresh();
        }

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
        // resumeAtMs == -1 excludes a spurious ENDED that was just recovered:
        // that resume must settle before the results screen may open.
        if (!resultsShown && !paused && resumeAtMs == -1
                && audio.state() == FfmpegAudioPlayer.State.ENDED
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
        for (int c = 0; c < columns(); c++) {
            if (holdPulseActive[c] && state.activeHold(c) == null) {
                holdPulseActive[c] = false;
                spawnHitEffect(c, Osu4k.laneColor(c), 380, true);
            }
        }

        // Prune finished hit effects.
        hitEffects.removeIf(e -> e.done(now));

        // Progress dragging uses the vertical rail: bottom is 0%, top is 100%.
        // The release ends it — see mouseReleased; 26.3 dropped GLFW from the
        // input chain, so there is no button state to poll here.
        if (draggingProgress) {
            seekFromProgressY(this.mouseY);
        }

        fillBackground(gui);
        drawTopInfo(gui);
        drawPlayfield(gui, scrollMs);
        drawCombo(gui);
        drawProgressRail(gui, audio.positionMsFresh());
        drawControlButtons(gui);
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
    // Top information
    // ---------------------------------------------------------------------

    private void drawTopInfo(GuiGraphicsExtractor gui) {
        String title = map.title();
        if (!map.artist().isEmpty()) {
            title = map.artist() + " - " + title;
        }
        String difficulty = "[" + map.version() + "]";
        String titleLine = title + "  " + difficulty;
        drawStringCenteredY(gui, smallFont, titleLine, SIDE_MARGIN, 13, COLOR_TEXT);

        int remaining = Math.max(0, state.totalNotes() - state.judgedCount());
        String stats = I18n.tr("Max Combo") + " " + state.maxCombo()
                + "   /   " + I18n.trf("%d notes", remaining);
        drawStringCenteredY(gui, smallFont, stats, SIDE_MARGIN, 31, COLOR_TEXT_DIM);

        String score = String.format(Locale.ROOT, "%,d", state.score());
        drawStringRightAligned(gui, smallFont, score, this.width - SIDE_MARGIN, 13, COLOR_TEXT);
        double acc = state.accuracy();
        String accText = String.format(Locale.ROOT, "%.2f%%", acc * 100);
        int accColor = acc >= 0.95 ? COLOR_SUCCESS : acc >= 0.70 ? COLOR_TEXT_DIM : COLOR_ERROR;
        drawStringRightAligned(gui, smallFont, accText, this.width - SIDE_MARGIN, 31, accColor);
    }

    private void drawCombo(GuiGraphicsExtractor gui) {
        int alpha = Math.min(130, 38 + state.combo() / 4);
        drawCentered(gui, titleFont, state.combo() + "x", (fieldLeft + fieldRight) / 2f,
                fieldTop + (fieldBottom - fieldTop) * 0.48f, (alpha << 24) | (COLOR_TEXT & 0xFFFFFF));
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
        for (int c = 0; c < columns(); c++) {
            int lx = fieldLeft + c * laneW;
            CustomRectRenderer.drawRect(gui, lx, fieldTop, laneW, fieldBottom - fieldTop,
                    (c % 2 == 0) ? 0x38121824 : 0x2E10141E);
            if (c > 0) {
                CustomRectRenderer.drawRect(gui, lx, fieldTop, 1, fieldBottom - fieldTop, 0x3DFFFFFF);
            }
        }
        CustomRectRenderer.drawRect(gui, fieldLeft + laneW * columns(), fieldTop, 1, fieldBottom - fieldTop, 0x3DFFFFFF);

        // Judgement line: soft glow + a brighter core, matching the accent.
        CustomRoundedRectRenderer.drawRoundedRect(gui, fieldLeft - 4, judgeY - 6, fieldRight - fieldLeft + 8, 12, 6, 0x3D4FC3F7);
        CustomRectRenderer.drawRect(gui, fieldLeft, judgeY - 2, fieldRight - fieldLeft, 4, 0xCCFFFFFF);

        // Keep the configured rhythm while guaranteeing a full second of
        // visible travel from the lane top to the judgement line.
        float configuredScroll = Osu4k.scrollSpeed();
        float maxPreviewScroll = (judgeY - fieldTop) / (float) NOTE_PREVIEW_MS;
        float scroll = Math.min(configuredScroll, Math.max(0.01f, maxPreviewScroll));
        int offset = Osu4k.offset();
        long now = playMs;

        for (int c = 0; c < columns(); c++) {
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
                if (headY < fieldTop - NOTE_CAP_H) {
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
            for (int c = 0; c < columns(); c++) {
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
            for (int c = 0; c < columns(); c++) {
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
        if (column < 0 || column >= columns() || !Osu4k.hitEffects()) {
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
    // Progress rail and controls
    // ---------------------------------------------------------------------

    private void drawProgressRail(GuiGraphicsExtractor gui, long playMs) {
        long dur = Math.max(1, audio.durationMs());
        float frac = Math.max(0f, Math.min(1f, playMs / (float) dur));
        int railH = progressBottom - progressTop;
        int fillH = Math.round(railH * frac);
        int fillY = progressBottom - fillH;

        CustomRoundedRectRenderer.drawRoundedRect(gui, progressX, progressTop, PROGRESS_W, railH, 7, 0x442A6C8F);
        if (fillH > 0) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, progressX, fillY, PROGRESS_W, fillH, 7, COLOR_ACCENT);
        }
        CustomRoundedRectRenderer.drawCircle(gui, progressX + PROGRESS_W / 2f, fillY, 6, 0xFFE6E9F2);

        drawCentered(gui, smallFont, fmtTime(playMs), progressX + PROGRESS_W / 2f, progressBottom + 12, COLOR_TEXT_DIM);
        drawCentered(gui, smallFont, fmtTime(dur), progressX + PROGRESS_W / 2f, progressTop - 10, COLOR_TEXT_DIM);
    }

    private void drawControlButtons(GuiGraphicsExtractor gui) {
        List<FooterBtn> btns = footerButtons();
        for (FooterBtn b : btns) {
            boolean hovered = inRect(mouseX, mouseY, b.x(), b.y(), b.w(), b.h());
            drawCompactButton(gui, b.x(), b.y(), b.w(), b.h(), b.label(), hovered);
        }

        if (difficultyDropdownOpen) {
            FooterBtn diff = btns.get(BTN_DIFF);
            List<BeatmapData> maps = Osu4k.currentArchive == null ? List.of() : Osu4k.currentArchive.playableMaps();
            int rows = Math.min(maps.size(), 5);
            int ddH = rows * 24 + 4;
            drawDifficultyDropdown(gui, difficultyDropdownX(diff), diff.y() - ddH - 2, rows);
        }
    }

    private void drawCompactButton(GuiGraphicsExtractor gui, int x, int y, int w, int h,
                                   String label, boolean hovered) {
        int top = hovered ? 0x4A2A3A52 : 0x38232B3A;
        int bottom = hovered ? 0x30202A38 : 0x241B2230;
        int outline = hovered ? 0x99FFFFFF : GLASS_OUTLINE_SOFT;
        CustomRoundedRectRenderer.drawRoundedRectVertGrad(gui, x, y, w, h, 5, top, bottom);
        CustomRoundedRectRenderer.drawRoundedOutline(gui, x, y, w, h, 5, outline, 1);
        drawCentered(gui, smallFont, label, x + w / 2f, y + h / 2f, COLOR_TEXT);
    }

    /**
     * Builds the footer button row; labels are computed live (they reflect the
     * paused state). Buttons are split to the lower left and right edges
     * instead of stacking at centre.
     */
    private List<FooterBtn> footerButtons() {
        String[] labels = {
                "-5s",
                paused ? I18n.tr("Play") : I18n.tr("Pause"),
                "+5s",
                map.version() + " " + map.keyCount() + "K",
                paused ? I18n.tr("Resume") : I18n.tr("Pause"),
                I18n.tr("Keys"),
                I18n.tr("ExitGame")
        };
        int[] ws = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            ws[i] = Math.max(28, Math.round(textWidth(labels[i], FOOTER_BTN_FONT)) + FOOTER_BTN_PAD);
        }

        int y = this.height - FOOTER_BTN_H - 8;
        List<FooterBtn> out = new java.util.ArrayList<>(labels.length);
        int x = SIDE_MARGIN;
        for (int i = 0; i <= BTN_SKIP_FWD; i++) {
            out.add(new FooterBtn(i, x, y, ws[i], FOOTER_BTN_H, labels[i]));
            x += ws[i] + FOOTER_BTN_GAP;
        }

        int right = this.width - SIDE_MARGIN - PROGRESS_W - PROGRESS_GAP;
        for (int i = BTN_EXIT; i >= BTN_DIFF; i--) {
            right -= ws[i];
            out.add(new FooterBtn(i, right, y, ws[i], FOOTER_BTN_H, labels[i]));
            right -= FOOTER_BTN_GAP;
        }
        out.sort(java.util.Comparator.comparingInt(FooterBtn::id));
        return out;
    }

    private int difficultyDropdownX(FooterBtn diff) {
        int w = 180;
        return Math.max(SIDE_MARGIN, Math.min(diff.x(), this.width - SIDE_MARGIN - PROGRESS_W - PROGRESS_GAP - w));
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
            drawStringCenteredY(gui, itemFont, m.version() + " " + m.keyCount() + "K", x + 10, rowY + 11, COLOR_TEXT);
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
            float hw = titleFont == null ? 34f * scale * 5f : CustomFontRenderer.stringWidth(titleFont, I18n.tr("Clear!")) * scale;
            drawAccentGlowAt(gui, this.width / 2f, this.height / 2f, hw + 60, size + 20, 0xFFFFFF, (int) (fade * 90));
            drawCentered(gui, titleFont, I18n.tr("Clear!"), this.width / 2f, this.height / 2f,
                    (alpha << 24) | 0xFF4FC3F7);
        }
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    private int laneForColumn(int key) {
        int[] keys = Osu4k.keysFor(columns());
        for (int c = 0; c < columns(); c++) {
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
     * Polls the raw keyboard state once per frame. Key events are queued
     * through {@code Minecraft.execute}, so two presses landing close together
     * can be delayed or dropped when a frame renders slowly; polling guarantees
     * every currently-held lane key is recognized at the playhead of this
     * frame. {@code laneDown} mirrors the physical state after each frame, so
     * this only fires for presses / releases the event path never saw.
     */
    private void pollLaneInput(long playMs) {
        int[] keys = Osu4k.keysFor(columns());
        for (int c = 0; c < columns(); c++) {
            boolean down = InputConstants.isKeyDown(keys[c]);
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
    public boolean mouseReleased(@NotNull MouseButtonEvent mouse) {
        if (draggingProgress && mouse.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            draggingProgress = false;
            return true;
        }
        return super.mouseReleased(mouse);
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
        if (key == InputConstants.KEY_SPACE) {
            togglePause();
            return true;
        }
        if (event.isLeft()) {
            seekTo(Math.max(0, audio.positionMs() - 5000));
            return true;
        }
        if (event.isRight()) {
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
        if (mouse.button() != InputConstants.MOUSE_BUTTON_LEFT) {
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
            int ddX = difficultyDropdownX(diff);
            for (int i = 0; i < rows; i++) {
                if (inRect(mx, my, ddX, ddY + 2 + i * 24, 180, 22)) {
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
                case BTN_KEYS -> this.minecraft.gui.setScreen(new Osu4kKeybindScreen(this, columns()));
                case BTN_EXIT -> closeToParent();
                case BTN_DIFF -> { /* handled above */ }
                default -> { }
            }
            return true;
        }

        // Vertical progress rail: bottom is the start of the song.
        if (inRect(mx, my, progressX - 8, progressTop - 8,
                PROGRESS_W + 16, progressBottom - progressTop + 16)) {
            seekFromProgressY(my);
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

    private void seekFromProgressY(double y) {
        long dur = Math.max(1, audio.durationMs());
        float frac = (progressBottom - (float) y) / Math.max(1, progressBottom - progressTop);
        frac = Math.max(0f, Math.min(1f, frac));
        seekTo((long) (frac * dur));
    }
}
