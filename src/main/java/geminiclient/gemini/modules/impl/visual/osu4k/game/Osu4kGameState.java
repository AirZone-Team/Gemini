package geminiclient.gemini.modules.impl.visual.osu4k.game;

import geminiclient.gemini.modules.impl.visual.osu4k.model.BeatmapData;
import geminiclient.gemini.modules.impl.visual.osu4k.model.HitObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure logic of the mania gameplay: timing, judging, scoring, combo.
 *
 * <p>Kept free of Minecraft and audio dependencies so the judging rules can be
 * unit-tested. All mutations happen through {@link #press}, {@link #release}
 * and {@link #update}, which are called from the render thread only.</p>
 *
 * <p>Judgement windows are chosen from five presets, from lenient to strict.
 * Each preset defines Perfect / Great / Good / Miss limits; a press beyond
 * the Miss limit is ignored and an unhit note beyond it auto-misses.
 * Combo grows when an object is finally settled and resets on a miss.</p>
 *
 * <p>The lane count (1K-10K) comes from the map being played; every
 * per-column structure follows it.</p>
 *
 * <p>Scoring follows an osu!mania-inspired cap: a flawless run is exactly
 * {@value #MAX_SCORE} points and the running score never exceeds it. For a
 * multi-object map, the combo factor grows from 0.5 to 1.5 across a flawless
 * run; the 300 / 200 / 100 / 0 judgement weights scale each award. A single
 * object uses a neutral factor of 1.0, so its judgement quality still matters.</p>
 */
public final class Osu4kGameState {

    /**
     * Selectable judgement-window presets (Perfect / Great / Good / Miss, in
     * ms). {@link #MODERATE} is the default; {@link #VERY_STRICT} matches the
     * original osu!-style 16 / 32 / 64 windows.
     */
    public enum Preset {
        VERY_LENIENT(50.0, 100.0, 200.0, 205.0),
        LENIENT(22.5, 90, 180.0, 210.0),
        MODERATE(20.5, 86.5, 168.5, 196.5),
        STRICT(19.5, 75.5, 135.0, 168.5),
        VERY_STRICT(13.5, 60.5, 121.5, 158.5);

        public final double perfectMs;
        public final double greatMs;
        public final double goodMs;
        /** Outer hit-window limit: presses beyond it are ignored, unhit notes
         *  beyond it auto-miss. */
        public final double missMs;

        Preset(double perfectMs, double greatMs, double goodMs, double missMs) {
            this.perfectMs = perfectMs;
            this.greatMs = greatMs;
            this.goodMs = goodMs;
            this.missMs = missMs;
        }
    }

    /** Score of a flawless run, matching the osu!mania 1,000,000 cap. */
    public static final int MAX_SCORE = 1_000_000;

    /** Judgement weights relative to {@link #SCORE_PERFECT}: the share of a
     *  note's base value that each judgement awards. */
    public static final int SCORE_PERFECT = 300;
    public static final int SCORE_GREAT = 200;
    public static final int SCORE_GOOD = 100;

    /** Note status as seen by the renderer. */
    public enum ObjectStatus { PENDING, ACTIVE, JUDGED }

    public enum Judgment {
        PERFECT(SCORE_PERFECT, "Perfect"),
        GREAT(SCORE_GREAT, "Great"),
        GOOD(SCORE_GOOD, "Good"),
        MISS(0, "Miss");

        /** Weight relative to {@link Osu4kGameState#SCORE_PERFECT}: PERFECT
         *  earns a note's full base, GREAT two thirds, GOOD one third, MISS
         *  nothing. Also used by the accuracy formula. */
        public final int score;
        public final String label;
        Judgment(int score, String label) {
            this.score = score;
            this.label = label;
        }
    }

    /** One judged hit, kept for the debug overlay (bounded ring buffer).
     *  {@code points} is the score the note awarded (0 for misses). */
    public record HitEntry(int noteTimeMs, int diffMs, Judgment judgment, int points) {}

    private final int columnCount;
    private static final int DEBUG_LOG_SIZE = 128;

    /** Per-column object lists, each sorted ascending by time. */
    private final List<HitObject>[] columns;
    private final ObjectStatus[][] status;
    /**
     * Per column, the index of the oldest object not yet judged. The cursor only
     * advances past {@link ObjectStatus#JUDGED} notes; an ACTIVE hold blocks it
     * until the hold is resolved.
     */
    private final int[] cursor;

    private final int offsetMs;

    /** Judgement-window limits for the selected {@link Preset}. */
    private final double perfectMs;
    private final double greatMs;
    private final double goodMs;
    private final double missMs;

    private HitObject[] activeHold;
    /** Head judgement is kept pending until the hold tail is resolved. */
    private Judgment[] activeHoldJudgment;
    private int[] activeHoldDiff;

    /**
     * Fixed denominator of the score fraction. For maps with two or more
     * objects it is {@code 600 · totalNotes · (totalNotes − 1)}; a full-combo
     * perfect run reaches the denominator exactly. A one-object map uses 300 so
     * its single object still has a meaningful Perfect / Great / Good split.
     */
    private final long scoreDenominator;

    // Score state.
    /** Cumulative exact numerator of the score fraction; grows on each hit and
     *  resets with {@link #reset}. */
    private long scoreNumerator;
    private int score;
    private int combo;
    private int maxCombo;
    private int totalNotes;
    private int judgedCount;

    /** Judgments by {@link Judgment}, mirroring the debug log; used for the
     *  accuracy formula and the results screen. */
    private final int[] judgmentCounts = new int[Judgment.values().length];

    private final HitEntry[] debugLog = new HitEntry[DEBUG_LOG_SIZE];
    private int debugLogPos;

    /** Default constructor: uses the {@link Preset#MODERATE} windows. */
    public Osu4kGameState(BeatmapData map, int offsetMs) {
        this(map, offsetMs, Preset.MODERATE);
    }

    @SuppressWarnings("unchecked")
    public Osu4kGameState(BeatmapData map, int offsetMs, Preset preset) {
        this.offsetMs = offsetMs;
        this.perfectMs = preset.perfectMs;
        this.greatMs = preset.greatMs;
        this.goodMs = preset.goodMs;
        this.missMs = preset.missMs;
        this.totalNotes = map.hitObjects().size();
        // Lane count comes from the map (1K-10K); clamp defensively so a
        // hand-built BeatmapData can never break the per-column arrays.
        this.columnCount = Math.max(BeatmapData.MIN_KEY_COUNT,
                Math.min(BeatmapData.MAX_KEY_COUNT, map.keyCount()));
        // 600 · n · (n − 1) makes the flawless numerator sum to the denominator
        // exactly. A one-object map uses a single 300-point base unit.
        this.scoreDenominator = totalNotes <= 1
                ? 300L
                : 600L * totalNotes * (totalNotes - 1L);

        columns = new List[columnCount];
        status = new ObjectStatus[columnCount][];
        cursor = new int[columnCount];
        activeHold = new HitObject[columnCount];
        activeHoldJudgment = new Judgment[columnCount];
        activeHoldDiff = new int[columnCount];

        for (int c = 0; c < columnCount; c++) {
            List<HitObject> col = new ArrayList<>();
            for (HitObject ho : map.hitObjects()) {
                if (ho.column() == c) {
                    col.add(ho);
                }
            }
            col.sort((a, b) -> Integer.compare(a.timeMs(), b.timeMs()));
            columns[c] = List.copyOf(col);
            status[c] = new ObjectStatus[col.size()];
            Arrays.fill(status[c], ObjectStatus.PENDING);
        }
    }

    // ---------------------------------------------------------------------
    // Public accessors (read-only, render thread)
    // ---------------------------------------------------------------------

    public int offsetMs() { return offsetMs; }
    public int score() { return score; }
    public int combo() { return combo; }
    public int maxCombo() { return maxCombo; }
    public int totalNotes() { return totalNotes; }
    public int judgedCount() { return judgedCount; }
    public int columns() { return columnCount; }

    /** How many notes have been judged with the given judgment so far. */
    public int judgmentCount(Judgment j) {
        return judgmentCounts[j.ordinal()];
    }

    /** Judgement windows of the selected preset, in ms (debug overlay). */
    public double perfectWindowMs() { return perfectMs; }
    public double greatWindowMs() { return greatMs; }
    public double goodWindowMs() { return goodMs; }
    public double missWindowMs() { return missMs; }

    /**
     * Live accuracy, following the classic osu! formula:
     * {@code (300*(n_max+n_300) + 200*n_200 + 100*n_100 + 50*n_50)
     *       / (300*(n_max+n_300+n_200+n_100+n_50+n_miss))}.
     *
     * <p>In the 4K player n_300 and n_50 are always zero (the five judgments
     * map to max/300, great/200, good/100, miss/0); the denominator is the
     * number of judged notes, so the value moves in real time while playing.
     * Returns 1.0 before anything has been judged.</p>
     */
    public double accuracy() {
        double nMax = judgmentCount(Judgment.PERFECT);
        double n300 = 0; // no 300-only tier in the 4K judgments
        double n200 = judgmentCount(Judgment.GREAT);
        double n100 = judgmentCount(Judgment.GOOD);
        double n50 = 0;  // no 50 tier in the 4K judgments
        double nMiss = judgmentCount(Judgment.MISS);
        double numerator = 300 * (nMax + n300) + 200 * n200 + 100 * n100 + 50 * n50;
        double denominator = 300 * (nMax + n300 + n200 + n100 + n50 + nMiss);
        return denominator == 0 ? 1.0 : numerator / denominator;
    }

    public List<HitObject> columnObjects(int column) {
        return columns[column];
    }

    public ObjectStatus status(int column, int index) {
        return status[column][index];
    }

    /** Active hold in the column, or null. Used by the renderer. */
    public HitObject activeHold(int column) {
        return activeHold[column];
    }

    public List<HitEntry> debugLog() {
        List<HitEntry> out = new ArrayList<>(DEBUG_LOG_SIZE);
        for (int i = 0; i < DEBUG_LOG_SIZE; i++) {
            HitEntry e = debugLog[(debugLogPos + i) % DEBUG_LOG_SIZE];
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    public boolean isFinished(long playMs) {
        if (judgedCount >= totalNotes && allHoldsResolved()) {
            return true;
        }
        for (int c = 0; c < columnCount; c++) {
            if (!columns[c].isEmpty()) {
                HitObject last = columns[c].get(columns[c].size() - 1);
                if (playMs <= last.endTimeMs() + offsetMs + missMs) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    /**
     * A key went down in {@code column} at playhead time {@code playMs}.
     *
     * @return the head judgment (a hold is not scored until its tail settles),
     *         or {@code null} when the press was ignored (too early, or the lane
     *         is already held)
     */
    public Judgment press(int column, long playMs) {
        if (column < 0 || column >= columnCount) {
            return null;
        }
        if (activeHold[column] != null) {
            // Already holding this lane; ignore the extra press.
            return null;
        }
        HitObject obj = nextPending(column);
        if (obj == null) {
            return null;
        }
        int noteTime = obj.timeMs() + offsetMs;
        int diff = (int) (playMs - noteTime);
        if (diff < -missMs) {
            return null; // way too early — keep the note pending
        }

        Judgment j = judge(diff);

        if (j == Judgment.MISS) {
            autoMiss(column, obj, diff);
            return j;
        }

        if (obj.isHold()) {
            int index = indexOf(column, obj);
            status[column][index] = ObjectStatus.ACTIVE;
            activeHold[column] = obj;
            activeHoldJudgment[column] = j;
            activeHoldDiff[column] = diff;
            return j;
        }

        settleHit(column, obj, j, diff);
        return j;
    }

    /**
     * A key went up in {@code column}; completes an active hold note.
     *
     * @return the final judgment (including the head timing and tail completion),
     *         or null when there was no active hold in the lane
     */
    public Judgment release(int column, long playMs) {
        if (column < 0 || column >= columnCount) {
            return null;
        }
        HitObject hold = activeHold[column];
        if (hold == null) {
            return null;
        }
        long tailTime = hold.endTimeMs() + offsetMs;
        long tailDiff = playMs - tailTime;
        Judgment finalJudgment = activeHoldJudgment[column];
        if (tailDiff < -missMs || tailDiff > missMs) {
            finalJudgment = Judgment.MISS;
        }
        settleHold(column, hold, finalJudgment, activeHoldDiff[column]);
        return finalJudgment;
    }

    /**
     * Advances the playhead; auto-misses notes the player has run past and
     * settles holds whose tail is behind the playhead. A held key that is still
     * down after the tail window is treated as a missed hold. Call every frame.
     */
    public void update(long playMs) {
        for (int c = 0; c < columnCount; c++) {
            List<HitObject> col = columns[c];

            // Skip notes already judged.
            while (cursor[c] < col.size() && status[c][cursor[c]] == ObjectStatus.JUDGED) {
                cursor[c]++;
            }
            if (cursor[c] >= col.size()) {
                continue;
            }

            // Resolve whatever sits at the cursor now.
            HitObject obj = col.get(cursor[c]);
            switch (status[c][cursor[c]]) {
                case ACTIVE -> {
                    if (playMs > obj.endTimeMs() + offsetMs + missMs) {
                        // The key was never released inside the tail window.
                        settleHold(c, obj, Judgment.MISS, activeHoldDiff[c]);
                    }
                }
                case PENDING -> {
                    int noteTime = obj.timeMs() + offsetMs;
                    if (playMs > noteTime + missMs) {
                        autoMiss(c, obj, (int) (playMs - noteTime));
                    }
                }
                default -> { /* handled above */ }
            }
        }
    }

    /**
     * Rewinds everything to a clean slate (used on seek and on difficulty
     * switch). Score, combo and hit history are reset.
     */
    public void reset() {
        scoreNumerator = 0;
        score = 0;
        combo = 0;
        maxCombo = 0;
        judgedCount = 0;
        Arrays.fill(judgmentCounts, 0);
        Arrays.fill(debugLog, null);
        debugLogPos = 0;
        for (int c = 0; c < columnCount; c++) {
            cursor[c] = 0;
            activeHold[c] = null;
            activeHoldJudgment[c] = null;
            activeHoldDiff[c] = 0;
            Arrays.fill(status[c], ObjectStatus.PENDING);
        }
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private void settleHit(int column, HitObject obj, Judgment judgment, int diffMs) {
        int award = applyHit(judgment);
        record(obj.timeMs() + offsetMs, diffMs, judgment, award);
        judgedCount++;
        setJudged(column, obj);
    }

    /** Resolve a held object exactly once when its tail has been dealt with. */
    private void settleHold(int column, HitObject hold, Judgment judgment, int headDiffMs) {
        activeHold[column] = null;
        activeHoldJudgment[column] = null;
        activeHoldDiff[column] = 0;
        setJudged(column, hold);
        if (judgment == Judgment.MISS) {
            applyMiss();
            record(hold.timeMs() + offsetMs, headDiffMs, judgment, 0);
        } else {
            int award = applyHit(judgment);
            record(hold.timeMs() + offsetMs, headDiffMs, judgment, award);
        }
        judgedCount++;
    }

    private boolean allHoldsResolved() {
        for (HitObject hold : activeHold) {
            if (hold != null) {
                return false;
            }
        }
        return true;
    }

    /** Oldest PENDING note in the column, or null if none/busy. */
    private HitObject nextPending(int column) {
        List<HitObject> col = columns[column];
        int i = cursor[column];
        while (i < col.size()) {
            ObjectStatus s = status[column][i];
            if (s == ObjectStatus.PENDING) {
                return col.get(i);
            }
            if (s == ObjectStatus.ACTIVE) {
                return null; // an unresolved hold blocks this lane
            }
            i++;
        }
        return null;
    }

    /** Linear search from the cursor; called only for notes at or near it. */
    private int indexOf(int column, HitObject obj) {
        List<HitObject> col = columns[column];
        for (int i = Math.max(0, cursor[column]); i < col.size(); i++) {
            if (col.get(i) == obj) {
                return i;
            }
        }
        return -1;
    }

    private void setJudged(int column, HitObject obj) {
        int idx = indexOf(column, obj);
        if (idx >= 0) {
            setJudged(column, idx);
        }
    }

    private void setJudged(int column, int index) {
        status[column][index] = ObjectStatus.JUDGED;
    }

    /**
     * Scores a successful object and returns the incremental points awarded.
     * The combo factor runs from 0.5 on the first object to 1.5 on the last
     * object of a flawless multi-object map; the denominator normalizes that
     * exact trajectory to {@link #MAX_SCORE}.
     */
    private int applyHit(Judgment j) {
        int previous = score;
        long comboWeight = totalNotes <= 1
                ? 1L
                : totalNotes - 1L + 2L * combo;
        scoreNumerator += comboWeight * j.score;
        score = (int) Math.min(MAX_SCORE, scoreNumerator * MAX_SCORE / scoreDenominator);
        combo++;
        if (combo > maxCombo) {
            maxCombo = combo;
        }
        return score - previous;
    }

    private void applyMiss() {
        combo = 0;
    }

    /** Marks a note as missed: judged, combo reset, hit logged. */
    private void autoMiss(int column, HitObject obj, int diffMs) {
        setJudged(column, obj);
        judgedCount++;
        applyMiss();
        record(obj.timeMs() + offsetMs, diffMs, Judgment.MISS, 0);
    }

    private void record(int noteTimeMs, int diffMs, Judgment j, int points) {
        debugLog[debugLogPos] = new HitEntry(noteTimeMs, diffMs, j, points);
        debugLogPos = (debugLogPos + 1) % DEBUG_LOG_SIZE;
        judgmentCounts[j.ordinal()]++;
    }

    private Judgment judge(int diffMs) {
        double a = Math.abs(diffMs);
        if (a <= perfectMs) return Judgment.PERFECT;
        if (a <= greatMs) return Judgment.GREAT;
        if (a <= goodMs) return Judgment.GOOD;
        return Judgment.MISS;
    }
}
