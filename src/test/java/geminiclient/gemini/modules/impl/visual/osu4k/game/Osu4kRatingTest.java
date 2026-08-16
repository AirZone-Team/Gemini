package geminiclient.gemini.modules.impl.visual.osu4k.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rank thresholds and the full-combo badge rule. */
class Osu4kRatingTest {

    @Test
    void rankPerfectIsSSS() {
        assertEquals(Osu4kRating.Rank.SSS, Osu4kRating.rank(Osu4kGameState.MAX_SCORE));
    }

    @Test
    void rankBoundaries() {
        // Score-based thresholds: fractions of the 1,000,000 cap.
        assertEquals(Osu4kRating.Rank.SSS, Osu4kRating.rankByFraction(1.0));
        assertEquals(Osu4kRating.Rank.SS, Osu4kRating.rankByFraction(0.9999));
        assertEquals(Osu4kRating.Rank.SS, Osu4kRating.rankByFraction(0.85));
        assertEquals(Osu4kRating.Rank.S, Osu4kRating.rankByFraction(0.8499));
        assertEquals(Osu4kRating.Rank.S, Osu4kRating.rankByFraction(0.75));
        assertEquals(Osu4kRating.Rank.A, Osu4kRating.rankByFraction(0.7499));
        assertEquals(Osu4kRating.Rank.A, Osu4kRating.rankByFraction(0.65));
        assertEquals(Osu4kRating.Rank.B, Osu4kRating.rankByFraction(0.6499));
        assertEquals(Osu4kRating.Rank.B, Osu4kRating.rankByFraction(0.50));
        assertEquals(Osu4kRating.Rank.C, Osu4kRating.rankByFraction(0.4999));
        assertEquals(Osu4kRating.Rank.C, Osu4kRating.rankByFraction(0.30));
        assertEquals(Osu4kRating.Rank.D, Osu4kRating.rankByFraction(0.2999));
        assertEquals(Osu4kRating.Rank.D, Osu4kRating.rankByFraction(0.0));
    }

    @Test
    void rankFromScoreMapsToFraction() {
        assertEquals(Osu4kRating.Rank.SSS, Osu4kRating.rank(1_000_000));
        assertEquals(Osu4kRating.Rank.SS, Osu4kRating.rank(950_000));
        assertEquals(Osu4kRating.Rank.SS, Osu4kRating.rank(850_000));
        assertEquals(Osu4kRating.Rank.S, Osu4kRating.rank(800_000));
        assertEquals(Osu4kRating.Rank.A, Osu4kRating.rank(700_000));
        assertEquals(Osu4kRating.Rank.B, Osu4kRating.rank(500_000));
        assertEquals(Osu4kRating.Rank.C, Osu4kRating.rank(300_000));
        assertEquals(Osu4kRating.Rank.D, Osu4kRating.rank(299_999));
        assertEquals(Osu4kRating.Rank.D, Osu4kRating.rank(0));
    }

    @Test
    void rankClampsOutOfRangeInput() {
        assertEquals(Osu4kRating.Rank.SSS, Osu4kRating.rankByFraction(1.5));
        assertEquals(Osu4kRating.Rank.D, Osu4kRating.rankByFraction(-0.5));
    }

    @Test
    void fullComboRequiresEveryNoteHit() {
        assertTrue(Osu4kRating.fullCombo(100, 100));
        assertFalse(Osu4kRating.fullCombo(99, 100));
        assertFalse(Osu4kRating.fullCombo(0, 100));
        assertFalse(Osu4kRating.fullCombo(100, 101));
    }

    @Test
    void emptyMapIsNeverFullCombo() {
        assertFalse(Osu4kRating.fullCombo(0, 0));
    }
}
