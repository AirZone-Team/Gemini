package geminiclient.gemini.modules.impl.visual.osu4k.audio;

/**
 * Software gain stage for {@link FfmpegAudioPlayer}.
 *
 * <p>The player used to route volume through the hardware {@code MASTER_GAIN}
 * control, which is driver-dependent and applies in discrete steps (audible
 * zipper noise when the slider moves, hard pops on some devices). This class
 * scales samples in software instead — deterministic on every device, and the
 * value is clamped to the S16 range so a request can never clip the output
 * into distortion.</p>
 *
 * <p>It also owns the click-kill ramp: after a seek the device buffer was
 * flushed mid-waveform, and the post-seek seam frame can decode into a full
 * scale static burst (MP3 bit reservoir, OGG granule positions). Ramping the
 * gain from silence over {@link #RAMP_SAMPLES} samples right after such a
 * seam makes both inaudible. State is owned by the decode thread only — the
 * render thread just writes {@code volume}.</p>
 */
final class AudioGain {

    /** Ramp length in samples (~11.6 ms at 44.1 kHz, both channels counted). */
    static final int RAMP_SAMPLES = 512;

    /** Gain applied to the last processed sample; {@code < 0} = never applied. */
    private float current = -1f;
    /** Gain the volume slider currently requests; {@code < 0} = not set yet. */
    private float target = -1f;

    private float step;
    private int remaining;

    /**
     * Called once per write batch with the requested gain. Starts a ramp when
     * the request changed; the first batch snaps instead (the song has not
     * reached the audible lead-in yet, so nothing can click).
     */
    void batchBegin(float requested) {
        if (requested == target) {
            return;
        }
        target = requested;
        if (current < 0f) {
            current = requested;
        } else {
            step = (target - current) / RAMP_SAMPLES;
            remaining = RAMP_SAMPLES;
        }
    }

    /**
     * Ducks the output to silence and ramps back up to the current target over
     * {@link #RAMP_SAMPLES} samples. Used right after the device buffer is
     * flushed (seek) so the waveform seam and any post-seek decoder garbage
     * cannot burst into the speakers.
     */
    void resetToSilence() {
        current = 0f;
        remaining = target == 0f ? 0 : RAMP_SAMPLES;
        step = target / RAMP_SAMPLES;
    }

    /** True when the stage is a pure pass-through (unity gain, no ramp). */
    boolean isIdentity() {
        return remaining == 0 && current == 1f && target == 1f;
    }

    /** Applies the gain to one sample, advancing the ramp. Never clips. */
    short apply(short v) {
        float g = current;
        if (remaining > 0) {
            remaining--;
            current += step;
            if (remaining == 0) {
                current = target;
            }
        }
        int s = (int) (v * g);
        if (s > 32767) {
            s = 32767;
        } else if (s < -32768) {
            s = -32768;
        }
        return (short) s;
    }
}