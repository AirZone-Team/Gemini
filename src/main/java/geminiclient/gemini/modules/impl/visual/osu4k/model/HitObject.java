package geminiclient.gemini.modules.impl.visual.osu4k.model;

/**
 * A single 4K mania hit object.
 *
 * @param timeMs     absolute note time in milliseconds (from the start of the audio)
 * @param column     lane index, 0..3 (0 = leftmost)
 * @param durationMs length of a hold note in ms (0 = regular tap)
 */
public record HitObject(int timeMs, int column, int durationMs) {

    /** True if this object is a hold note (long-note). */
    public boolean isHold() {
        return durationMs > 0;
    }

    /** End time of the object (equal to {@code timeMs} for taps). */
    public int endTimeMs() {
        return timeMs + Math.max(0, durationMs);
    }

    @Override
    public String toString() {
        return "HitObject{" + timeMs + "ms col=" + column
                + (durationMs > 0 ? " hold=" + durationMs + "ms" : "") + '}';
    }
}
