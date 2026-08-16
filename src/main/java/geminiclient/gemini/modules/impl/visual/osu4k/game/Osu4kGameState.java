package geminiclient.gemini.modules.impl.visual.osu4k.game;

import geminiclient.gemini.modules.impl.visual.osu4k.model.BeatmapData;
import geminiclient.gemini.modules.impl.visual.osu4k.model.HitObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure logic of the 4K mania gameplay: timing, judging, scoring, combo.
 *
 * <p>Kept free of Minecraft and audio dependencies so the judging rules can be
 * unit-tested. All mutations happen through {@link #press}, {@link #release}
 * and {@link #update}, which are called from the render thread only.</p>
 *
 * <p>Judgement windows are chosen from five presets, from lenient to strict.
 * Each preset defines Perfect / Great / Good / Miss limits; a press beyond
 * the Miss limit is ignored and an unhit note beyond it auto-misses.
 * Combo grows on every hit and resets on a miss.</p>
 *
 * <p>Scoring follows the osu!mania cap: a flawless run is exactly
 * {@value #MAX_SCORE} points and the running score never exceeds it. A note's
 * points grow with the combo held before it — right after a combo break a
 * perfect earns half a note's base value, a max-combo note one and a half
 * times the base, and every hit stays strictly above zero. The judgement
 * weight 300 / 200 / 100 / 0 still scales each award, and over a flawless run
 * the combo scaling averages out so the total lands exactly on the cap.</p>
 */
public final class Osu4kGameState {

    /**
     * Selectable judgement-window presets (Perfect / Great / Good / Miss, in
     * ms). {@link #MODERATE} is the default; {@link #VERY_STRICT} matches the
     * original osu!-style 16 / 32 / 64 windows.
     */
    public enum Preset {
        VERY_LENIENT(50.0, 100.0, 200.0, 260.0),
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

    private static final int COLUMNS = 4;
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

    /**
     * Fixed denominator of the score fraction: 600 · totalNotes · (totalNotes − 1).
     * A judged note contributes {@code (2·combo + totalNotes − 1) × weight} to
     * {@link #scoreNumerator}; the live score is
     * {@code floor(MAX_SCORE × numerator / denominator)}. Over a flawless run
     * the numerator reaches exactly the denominator, so the score lands on
     * {@link #MAX_SCORE} and can never overshoot it.
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
        // 600 · n · (n − 1) makes the flawless numerator sum to the denominator
        // exactly (see scoreDenominator); zero for n ≤ 1, which is handled in
        // applyHit without the fraction.
        this.scoreDenominator = 600L * totalNotes * Math.max(0, totalNotes - 1);

        columns = new List[COLUMNS];
        status = new ObjectStatus[COLUMNS][];
        cursor = new int[COLUMNS];
        activeHold = new HitObject[COLUMNS];

        for (int c = 0; c < COLUMNS; c++) {
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
    public int columns() { return COLUMNS; }

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
        if (judgedCount >= totalNotes) {
            return true;
        }
        for (int c = 0; c < COLUMNS; c++) {
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
     * @return the judgment that resulted from this press, or {@code null} when
     *         the press was ignored (too early, or the lane already held)
     */
    public Judgment press(int column, long playMs) {
        if (column < 0 || column >= COLUMNS) {
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
            setJudged(column, obj);
            judgedCount++;
            applyMiss();
            record(obj.timeMs(), diff, j, 0);
            return j;
        }

        int award = applyHit(j);
        record(obj.timeMs(), diff, j, award);
        judgedCount++;
        if (obj.isHold()) {
            status[column][indexOf(column, obj)] = ObjectStatus.ACTIVE;
            activeHold[column] = obj;
        } else {
            setJudged(column, obj);
        }
        return j;
    }

    /**
     * A key went up in {@code column}; completes an active hold note.
     *
     * @return the judgment result (MISS if the hold tail was dropped early), or
     *         null when there was no active hold in the lane
     */
    public Judgment release(int column, long playMs) {
        if (column < 0 || column >= COLUMNS) {
            return null;
        }
        HitObject hold = activeHold[column];
        if (hold == null) {
            return null;
        }
        long tailTime = hold.endTimeMs() + offsetMs;
        if (playMs < tailTime - missMs) {
            // Released too early: the tail is missed and the combo breaks.
            applyMiss();
            record(hold.timeMs(), (int) (playMs - tailTime), Judgment.MISS, 0);
            activeHold[column] = null;
            setJudged(column, hold);
            return Judgment.MISS;
        }
        activeHold[column] = null;
        setJudged(column, hold);
        return null;
    }

    /**
     * Advances the playhead; auto-misses notes the player has run past and
     * completes holds whose tail is behind the playhead. Call every frame.
     */
    public void update(long playMs) {
        for (int c = 0; c < COLUMNS; c++) {
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
                        activeHold[c] = null;
                        setJudged(c, cursor[c]);
                    }
                }
                case PENDING -> {
                    int noteTime = obj.timeMs() + offsetMs;
                    if (playMs > noteTime + missMs) {
                        setJudged(c, cursor[c]);
                        judgedCount++;
                        applyMiss();
                        record(noteTime, (int) (playMs - noteTime), Judgment.MISS, 0);
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
        for (int c = 0; c < COLUMNS; c++) {
            cursor[c] = 0;
            activeHold[c] = null;
            Arrays.fill(status[c], ObjectStatus.PENDING);
        }
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

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
     * Scores a successful hit and returns the points it awarded (always > 0).
     *
     * <p>The award grows with the combo held <i>before</i> this note: the weight
     * for note {@code i} (0-based) is {@code 2·combo + totalNotes − 1}, so a
     * perfect right after a combo break earns half of note 0's weight and a
     * max-combo note one and a half times it. The judgement scales it down to
     * two thirds for GREAT and one third for GOOD. On a flawless run the weights
     * sum to the denominator, so the score lands exactly on the cap. For maps
     * with at most one note every weight-based note scores the full
     * {@link #MAX_SCORE}, keeping the no-overshoot rule.</p>
     */
    private int applyHit(Judgment j) {
        int previous = score;
        int weight = 2 * combo + totalNotes - 1;
        if (scoreDenominator > 0) {
            scoreNumerator += (long) weight * j.score;
            score = (int) Math.min(MAX_SCORE, scoreNumerator * MAX_SCORE / scoreDenominator);
        } else {
            score = MAX_SCORE; // single-note map: the note is worth everything
        }
        combo++;
        if (combo > maxCombo) {
            maxCombo = combo;
        }
        return score - previous;
    }

    private void applyMiss() {
        combo = 0;
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
