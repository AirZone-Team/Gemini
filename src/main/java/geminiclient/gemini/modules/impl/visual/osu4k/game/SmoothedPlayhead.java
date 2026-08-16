package geminiclient.gemini.modules.impl.visual.osu4k.game;

/**
 * Smooths the audio clock into a per-frame playhead for rendering.
 *
 * <p>The raw audio clock ({@link FfmpegAudioPlayer#positionMs()}) only advances
 * whenever the decode thread finishes writing a whole sample batch to the sound
 * device, so between two updates it stays frozen while wall time keeps moving.
 * At 60 fps that produces the "advance, freeze, jump" stutter on falling notes.
 *
 * <p>This class bridges the gap with wall-clock interpolation: while the audio
 * clock is unchanged we extrapolate it by the wall time elapsed since the last
 * audio update; as soon as the audio clock moves we re-anchor to it (the
 * extrapolation is roughly equal to the real elapsed time, so the anchor is
 * idempotent and never jumps backwards). Judging still uses the raw audio
 * clock — only rendering consumes the smoothed value.</p>
 */
public final class SmoothedPlayhead {

    /** Wall time between two audio-clock reads, in nanoseconds. */
    private static final long NANOS_PER_MS = 1_000_000L;

    /** Last smoothed position in ms. */
    private long playMs;
    /** Audio clock the last {@link #update} saw, in ms. */
    private long lastAudioMs;
    /** Wall timestamp (nanoTime) of the last {@link #update}. */
    private long lastWallNanos;

    /**
     * Re-anchors the playhead to a new audio clock position, e.g. on load,
     * difficulty switch, seek or pause. Extrapolation restarts from here.
     */
    public void reset(long audioMs, long wallNanos) {
        playMs = audioMs;
        lastAudioMs = audioMs;
        lastWallNanos = wallNanos;
    }

    /**
     * Advances the smoothed playhead to the current wall time.
     *
     * <p>If the audio clock has advanced since the last call we re-anchor to it;
     * otherwise we extrapolate it by the wall time elapsed since the last read.
     * The result is clamped to {@code [0, durationMs]} and never moves backwards
     * (except when an audio seek pulls it to an earlier position).</p>
     *
     * @param audioMs    raw audio clock position
     * @param wallNanos  {@link System#nanoTime()} at the start of the frame
     * @param durationMs total song length in ms, {@code <= 0} disables clamping
     * @return smoothed playhead in ms
     */
    public long update(long audioMs, long wallNanos, long durationMs) {
        if (audioMs != lastAudioMs) {
            // Audio clock moved (or was seeked): re-anchor. If it moved forwards
            // the extrapolation was roughly accurate; if a seek moved it
            // backwards we follow, since the audio really is there now.
            playMs = audioMs;
        } else {
            long elapsedMs = (wallNanos - lastWallNanos) / NANOS_PER_MS;
            if (elapsedMs > 0) {
                playMs += elapsedMs;
            }
        }
        lastAudioMs = audioMs;
        lastWallNanos = wallNanos;
        if (durationMs > 0) {
            playMs = Math.max(0, Math.min(playMs, durationMs));
        } else {
            playMs = Math.max(0, playMs);
        }
        return playMs;
    }

    /** Last smoothed position, as returned by the previous {@link #update}. */
    public long positionMs() {
        return playMs;
    }
}
