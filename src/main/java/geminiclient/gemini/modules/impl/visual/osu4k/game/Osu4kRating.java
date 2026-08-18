package geminiclient.gemini.modules.impl.visual.osu4k.game;

/**
 * Rank (grade) rules for the 4K results screen.
 *
 * <p>The rank is derived from the final score of {@link Osu4kGameState} as a
 * fraction of the {@link Osu4kGameState#MAX_SCORE} cap — mirroring the osu!
 * letter grades — and a "full combo" badge marks a run where the highest combo
 * reached every note in the map. Pure logic so the grading thresholds stay
 * unit-testable.</p>
 */
public final class Osu4kRating {

    public enum Rank { SSS, SS, S, A, B, C, D }

    private Osu4kRating() {
    }

    /**
     * Letter grade for a score (0..{@link Osu4kGameState#MAX_SCORE}). The
     * score is compared as a fraction of the cap: {@code 1.0 -> SSS},
     * {@code 0.85 -> SS}, {@code 0.75 -> S}, {@code 0.65 -> A},
     * {@code 0.50 -> B}, {@code 0.30 -> C}, otherwise {@code D}.
     */
    public static Rank rank(int score) {
        double fraction = (double) score / Osu4kGameState.MAX_SCORE;
        return rankByFraction(fraction);
    }

    /**
     * Letter grade for a score fraction (0..1), used by {@link #rank(int)} and
     * the unit tests. Out-of-range input is clamped.
     */
    public static Rank rankByFraction(double fraction) {
        if (fraction >= 1.0) return Rank.SSS;
        if (fraction >= 0.85) return Rank.SS;
        if (fraction >= 0.75) return Rank.S;
        if (fraction >= 0.65) return Rank.A;
        if (fraction >= 0.50) return Rank.B;
        if (fraction >= 0.30) return Rank.C;
        return Rank.D;
    }

    /**
     * True when the max combo reached every note of the map, i.e. the run never
     * dropped a note (a hold counts as one note). An empty map is never a full
     * combo.
     */
    public static boolean fullCombo(int maxCombo, int totalNotes) {
        return totalNotes > 0 && maxCombo == totalNotes;
    }
}
