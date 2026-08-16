package geminiclient.gemini.modules.impl.visual.osu4k;

import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState.HitEntry;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState.Judgment;
import geminiclient.gemini.modules.impl.visual.osu4k.game.Osu4kGameState.Preset;
import geminiclient.gemini.modules.impl.visual.osu4k.model.BeatmapData;
import geminiclient.gemini.modules.impl.visual.osu4k.model.OsuParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Judging, scoring and combo rules of the 4K game state. */
class Osu4kGameStateTest {

    /** Simple 4K map: taps at 1000..4000 and one hold at 5000-5500. */
    private static final String MAP = """
            osu file format v14
            [General]
            AudioFilename: audio.mp3
            Mode: 3
            [Metadata]
            Title: T
            Version: Test
            [Mania]
            Keys: 4
            [HitObjects]
            64,192,1000,1,0,0:0:0:0:
            192,192,2000,1,0,0:0:0:0:
            320,192,3000,1,0,0:0:0:0:
            448,192,4000,1,0,0:0:0:0:
            64,192,5000,128,0,5500:0:0:0:0:
            """;

    private static Osu4kGameState state() {
        BeatmapData map = OsuParser.parse(MAP, "t.osu");
        return new Osu4kGameState(map, 0);
    }

    /** Chord: one note in every lane at the same instant (2000ms). */
    private static final String CHORD_MAP = """
            osu file format v14
            [General]
            AudioFilename: audio.mp3
            Mode: 3
            [Metadata]
            Title: T
            Version: Test
            [Mania]
            Keys: 4
            [HitObjects]
            64,192,2000,1,0,0:0:0:0:
            192,192,2000,1,0,0:0:0:0:
            320,192,2000,1,0,0:0:0:0:
            448,192,2000,1,0,0:0:0:0:
            """;

    /** Map with a single hold at 5000-5500, so judgedCount stays 1 in hold tests. */
    private static final String HOLD_ONLY_MAP = """
            osu file format v14
            [General]
            AudioFilename: audio.mp3
            Mode: 3
            [Metadata]
            Title: T
            Version: Test
            [Mania]
            Keys: 4
            [HitObjects]
            64,192,5000,128,0,5500:0:0:0:0:
            """;

    private static Osu4kGameState holdState() {
        return new Osu4kGameState(OsuParser.parse(HOLD_ONLY_MAP, "h.osu"), 0);
    }

    @Test
    void earlyPressOutsideWindowIsIgnored() {
        Osu4kGameState g = state();
        assertNull(g.press(0, 800)); // 200ms early > 164.5ms miss window
        assertEquals(0, g.score());
        assertEquals(0, g.judgedCount());
        // note is still pending and can be hit later
        assertEquals(Judgment.PERFECT, g.press(0, 1010));
    }

    @Test
    void judgementWindows() {
        Osu4kGameState g = state();
        assertEquals(Judgment.PERFECT, g.press(0, 1016));   // ±16.5
        assertEquals(Judgment.GREAT, g.press(1, 2040));     // ±40.5
        assertEquals(Judgment.GOOD, g.press(2, 3127));      // ±127.5
        assertEquals(Judgment.MISS, g.press(3, 4200));      // 200ms late
    }

    @Test
    void missWindowBoundary() {
        Osu4kGameState g = state();
        // 200ms early: outside the ±196.5ms miss window, the press is ignored.
        assertNull(g.press(0, 800));
        assertEquals(0, g.judgedCount());
        // 190ms early: inside the miss window, judged as a miss.
        assertEquals(Judgment.MISS, g.press(0, 810));
        assertEquals(1, g.judgedCount());
        assertEquals(0, g.combo());
    }

    @Test
    void strictestPresetRestoresOriginalWindows() {
        BeatmapData map = OsuParser.parse(MAP, "t.osu");
        Osu4kGameState g = new Osu4kGameState(map, 0, Preset.VERY_STRICT);
        assertEquals(Judgment.PERFECT, g.press(0, 1010));   // ±13.5
        assertEquals(Judgment.GREAT, g.press(1, 2040));     // ±60.5
        assertEquals(Judgment.GOOD, g.press(2, 3100));      // ±121.5
        assertEquals(Judgment.MISS, g.press(3, 4200));      // 200ms late
        // ±158.5 is the outer miss window: a 200ms early press is ignored.
        assertNull(g.press(0, 800));
    }

    @Test
    void lenientPresetWidensWindows() {
        BeatmapData map = OsuParser.parse(MAP, "t.osu");
        Osu4kGameState g = new Osu4kGameState(map, 0, Preset.VERY_LENIENT);
        // +20ms is only GREAT on the default preset but PERFECT on Very Lenient (±25).
        assertEquals(Judgment.PERFECT, g.press(0, 1020));
        // +140ms is outside the GOOD window on Very Strict (±121.5) but still
        // GOOD on Very Lenient (±200).
        assertEquals(Judgment.GOOD, g.press(1, 2140));
    }

    @Test
    void simultaneousChordJudgesEveryColumnIndependently() {
        // All four lanes hit at the same playhead time: each press must judge
        // its own column's note — no press may be swallowed by another lane.
        Osu4kGameState g = new Osu4kGameState(OsuParser.parse(CHORD_MAP, "chord.osu"), 0);
        assertEquals(Judgment.PERFECT, g.press(0, 2000));
        assertEquals(Judgment.PERFECT, g.press(1, 2000));
        assertEquals(Judgment.PERFECT, g.press(2, 2000));
        assertEquals(Judgment.PERFECT, g.press(3, 2000));
        assertEquals(4, g.judgedCount());
        assertEquals(4, g.combo());
        // The 4-note chord map: each note is worth 1,000,000 / 4 = 250,000.
        assertEquals(4 * 250_000, g.score());
    }

    @Test
    void unmissedNotesAutoMissWhenPlayheadPasses() {
        Osu4kGameState g = state();
        g.update(1500); // passes note at 1000 (already pending, auto-miss)
        assertEquals(1, g.judgedCount());
        assertEquals(0, g.combo());
        assertEquals(0, g.score());
    }

    // ---------------------------------------------------------------------
    // Scoring: combo-weighted notes, capped at 1,000,000
    // ---------------------------------------------------------------------

    /**
     * Expected score after {@code perfects} consecutive perfect hits on a
     * {@code notes}-note map, from the combo-weighted formula:
     * {@code Σ (2·combo + notes − 1) · 300 · MAX_SCORE / (600·notes·(notes−1))}.
     */
    private static int expectedPerfectScore(int perfects, int notes) {
        long numerator = 0;
        for (int combo = 0; combo < perfects; combo++) {
            numerator += (2L * combo + notes - 1) * 300L;
        }
        return (int) (numerator * Osu4kGameState.MAX_SCORE / (600L * notes * (notes - 1)));
    }

    @Test
    void perfectPressScoresNoteWeightByCombo() {
        Osu4kGameState g = state();
        // 5-note map, first note at combo 0: weight 2·0 + 4 = 4 of 300·4.
        assertEquals(Judgment.PERFECT, g.press(0, 1000));
        assertEquals(expectedPerfectScore(1, 5), g.score());
        assertEquals(1, g.combo());
        assertEquals(1, g.maxCombo());
    }

    @Test
    void higherComboAwardsMorePointsPerNote() {
        Osu4kGameState g = state();
        g.press(0, 1000);   // perfect at combo 0 -> 100,000
        assertEquals(expectedPerfectScore(1, 5), g.score());
        g.press(1, 2000);   // perfect at combo 1 -> +150,000 (250,000 total)
        assertEquals(expectedPerfectScore(2, 5), g.score());
        assertEquals(expectedPerfectScore(2, 5) - expectedPerfectScore(1, 5), 150_000);
    }

    @Test
    void missResetsCombo() {
        Osu4kGameState g = state();
        g.press(0, 1000);   // combo 1
        g.press(1, 2000);   // combo 2
        g.press(2, 4200);   // miss -> combo 0
        assertEquals(0, g.combo());
        assertEquals(2, g.maxCombo());
        assertEquals(expectedPerfectScore(2, 5), g.score());
    }

    @Test
    void comboBreakNextNoteScoresBelowBase() {
        Osu4kGameState g = state();
        g.press(0, 1000);   // perfect, combo 1 -> 100,000
        g.press(1, 2000);   // perfect, combo 2 -> 250,000
        g.press(2, 4200);   // miss -> combo 0
        int before = g.score();
        g.press(3, 4000);   // perfect right after the break
        // The 5-note map averages 200,000 per note; the note after a break
        // awards only 100,000 (combo 0, the lowest weight).
        int delta = g.score() - before;
        assertEquals(100_000, delta);
        assertTrue(delta < 200_000);
    }

    @Test
    void scoreScalesWithJudgementWeight() {
        // Same combo trajectory as the perfect run, with worse judgements:
        // the total tracks the 300/200/100 weights of each hit.
        Osu4kGameState g = state();
        g.press(0, 1010);   // perfect at combo 0
        g.press(1, 2040);   // great  at combo 1 (200/300 weight)
        g.press(2, 3127);   // good   at combo 2 (100/300 weight)
        g.press(3, 4200);   // miss   -> 0 points, combo 0
        long expected = ((4L * 300 + 6L * 200 + 8L * 100) * Osu4kGameState.MAX_SCORE)
                / (600L * 5 * 4);
        assertEquals(expected, g.score());
    }

    @Test
    void scoreNeverExceedsMaxInMixedRun() {
        // 3 perfects + 1 miss cannot exceed the cap: the note weight a
        // flawless run would have spent is simply not awarded.
        Osu4kGameState g = state();
        g.press(0, 1000);
        g.press(1, 2000);
        g.press(2, 3000);
        g.press(3, 4200);   // 200ms late -> miss
        assertTrue(g.score() <= Osu4kGameState.MAX_SCORE);
        assertEquals(450_000, g.score()); // (4+6+8)·300·1e6/12000
    }

    @Test
    void scoreReachesMaxOnFlawlessRun() {
        // 4-note chord map: 1,000,000 / 4 = 250,000 per note, no remainder.
        Osu4kGameState g = new Osu4kGameState(OsuParser.parse(CHORD_MAP, "chord.osu"), 0);
        g.press(0, 2000);
        g.press(1, 2000);
        g.press(2, 2000);
        g.press(3, 2000);
        assertEquals(Osu4kGameState.MAX_SCORE, g.score());
    }

    @Test
    void scoreReachesMaxOnFlawlessRunWithNonDivisibleNoteCount() {
        // 3 notes: the combo weights (2, 4, 6) make the flawless sum hit the
        // 1,000,000 cap exactly regardless of the note count.
        String map = """
                osu file format v14
                [General]
                AudioFilename: audio.mp3
                Mode: 3
                [Metadata]
                Title: T
                Version: T
                [Mania]
                Keys: 4
                [HitObjects]
                64,192,1000,1,0,0:0:0:0:
                192,192,2000,1,0,0:0:0:0:
                320,192,3000,1,0,0:0:0:0:
                """;
        Osu4kGameState g = new Osu4kGameState(OsuParser.parse(map, "3n.osu"), 0);
        g.press(0, 1000);
        g.press(1, 2000);
        g.press(2, 3000);
        assertEquals(Osu4kGameState.MAX_SCORE, g.score());
    }

    @Test
    void hitEntryRecordsAwardedPoints() {
        Osu4kGameState g = state();
        g.press(0, 1000);   // perfect -> 100,000 points
        HitEntry e = g.debugLog().get(0);
        assertEquals(Judgment.PERFECT, e.judgment());
        assertEquals(100_000, e.points());
        g.press(1, 2000);
        assertEquals(150_000, g.debugLog().get(1).points());
    }

    @Test
    void holdHeadScoresAndTailReleaseKeepsCombo() {
        Osu4kGameState g = holdState();
        g.update(4990);
        assertEquals(Judgment.PERFECT, g.press(0, 5000));
        assertNotNull(g.activeHold(0));
        // Single-note map: the note is worth the full 1,000,000.
        assertEquals(1_000_000, g.score());
        g.update(5450);
        assertNull(g.release(0, 5450)); // in window (tail 5500)
        assertEquals(1, g.combo());
        assertEquals(1, g.judgedCount());
    }

    @Test
    void earlyHoldReleaseBreaksCombo() {
        Osu4kGameState g = holdState();
        g.update(4990);
        g.press(0, 5000);
        g.release(0, 5100); // 400ms before the 5500 tail
        assertEquals(0, g.combo());
        assertEquals(1, g.judgedCount());
    }

    @Test
    void holdAutoCompletesAfterTail() {
        Osu4kGameState g = holdState();
        g.update(4990);
        g.press(0, 5000);
        g.update(5700); // tail + 164.5ms window elapsed
        assertNull(g.activeHold(0));
        assertEquals(1, g.judgedCount());
        assertEquals(1, g.combo());
    }

    @Test
    void resetRestoresCleanSlate() {
        Osu4kGameState g = state();
        g.press(0, 1000);
        g.press(1, 2000);
        assertEquals(2, g.judgedCount());
        g.reset();
        assertEquals(0, g.score());
        assertEquals(0, g.combo());
        assertEquals(0, g.judgedCount());
        assertEquals(0, g.maxCombo());
        assertEquals(Judgment.PERFECT, g.press(0, 1000));
        assertEquals(100_000, g.score());
        assertEquals(1, g.combo());
    }

    @Test
    void offsetShiftsJudgementTime() {
        BeatmapData map = OsuParser.parse(MAP, "t.osu");
        Osu4kGameState g = new Osu4kGameState(map, 30); // +30ms
        // note at 1000 must be pressed at 1030 for a perfect.
        assertEquals(Judgment.PERFECT, g.press(0, 1030));
    }

    @Test
    void finishedDetectsEndOfMap() {
        Osu4kGameState g = state();
        assertFalse(g.isFinished(4000));
        g.update(6000);
        assertTrue(g.isFinished(6000));
    }

    @Test
    void debugLogRecordsHits() {
        Osu4kGameState g = state();
        g.press(0, 1000);
        var log = g.debugLog();
        assertEquals(1, log.size());
        assertEquals(1000, log.get(0).noteTimeMs());
        assertEquals(0, log.get(0).diffMs());
        assertEquals(Judgment.PERFECT, log.get(0).judgment());
    }

    // ---------------------------------------------------------------------
    // Judgment counts + live accuracy
    // ---------------------------------------------------------------------

    @Test
    void judgmentCountsAccumulateAcrossAllPaths() {
        Osu4kGameState g = state();
        assertEquals(0, g.judgmentCount(Judgment.PERFECT));
        g.press(0, 1000);       // perfect tap
        assertEquals(1, g.judgmentCount(Judgment.PERFECT));
        g.press(1, 2040);       // great tap
        assertEquals(1, g.judgmentCount(Judgment.GREAT));
        g.update(6000);         // auto-misses the 3 remaining notes (hold + 2 taps)
        assertEquals(1, g.judgmentCount(Judgment.PERFECT));
        assertEquals(1, g.judgmentCount(Judgment.GREAT));
        assertEquals(3, g.judgmentCount(Judgment.MISS));
        assertEquals(5, g.judgedCount());
    }

    @Test
    void holdEarlyReleaseCountsAsMiss() {
        Osu4kGameState g = holdState();
        g.update(4990);
        g.press(0, 5000);       // perfect hold head
        g.release(0, 5100);     // 400ms before the 5500 tail -> miss
        assertEquals(1, g.judgmentCount(Judgment.PERFECT));
        assertEquals(1, g.judgmentCount(Judgment.MISS));
    }

    @Test
    void judgmentCountsResetWithState() {
        Osu4kGameState g = state();
        g.press(0, 1000);
        assertEquals(1, g.judgmentCount(Judgment.PERFECT));
        g.reset();
        assertEquals(0, g.judgmentCount(Judgment.PERFECT));
        assertEquals(0, g.judgmentCount(Judgment.MISS));
    }

    @Test
    void accuracyIsOneBeforeAnyJudgment() {
        Osu4kGameState g = state();
        assertEquals(1.0, g.accuracy(), 1e-9);
    }

    @Test
    void accuracyPerfectRunIsOne() {
        Osu4kGameState g = state();
        g.press(0, 1000);
        g.press(1, 2000);
        g.press(2, 3000);
        g.press(3, 4000);
        g.press(0, 5000);       // hold head
        g.release(0, 5500);     // in-window tail release
        assertEquals(5, g.judgedCount());
        assertEquals(1.0, g.accuracy(), 1e-9);
    }

    @Test
    void accuracyUsesJudgedNotesAsDenominator() {
        Osu4kGameState g = state();
        // 2 perfect + 1 great = (300*2 + 200*1) / (300*3) = 8/9.
        g.press(0, 1000);
        g.press(1, 2000);
        g.press(2, 3025);       // +25ms on the 3000 note: inside the great window
        assertEquals(8.0 / 9.0, g.accuracy(), 1e-9);
        // A miss adds to the denominator but nothing to the numerator.
        g.update(6000);         // auto-misses the remaining 2 notes
        assertEquals((600.0 + 200.0) / (300.0 * 5.0), g.accuracy(), 1e-9);
    }

    @Test
    void accuracyResetsWithState() {
        Osu4kGameState g = state();
        g.press(0, 1000);
        assertEquals(1.0, g.accuracy(), 1e-9);
        g.reset();
        assertEquals(1.0, g.accuracy(), 1e-9);
    }
}
