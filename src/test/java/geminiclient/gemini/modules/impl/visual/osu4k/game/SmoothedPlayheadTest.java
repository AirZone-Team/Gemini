package geminiclient.gemini.modules.impl.visual.osu4k.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wall-clock interpolation of the audio clock for smooth note rendering. */
class SmoothedPlayheadTest {

    private static final long NS_PER_MS = 1_000_000L;
    /** One rendered frame at 60 fps. */
    private static final long FRAME_NS = 16 * NS_PER_MS;

    /**
     * The audio clock only advances in batches (every sample write, roughly
     * every 20 ms), so between two reads it must be held at a constant while
     * the smoothed playhead keeps gliding forward with wall time.
     */
    @Test
    void glidesWhileAudioClockIsFrozen() {
        SmoothedPlayhead s = new SmoothedPlayhead();
        s.reset(5000, 0);

        long out1 = s.update(5000, FRAME_NS, 20000);
        long out2 = s.update(5000, 2 * FRAME_NS, 20000);
        long out3 = s.update(5000, 3 * FRAME_NS, 20000);

        assertEquals(5016, out1);
        assertEquals(5032, out2);
        assertEquals(5048, out3);
        assertTrue(out1 < out2 && out2 < out3, "playhead must advance between frames");
    }

    /**
     * When the audio clock finally advances it re-anchors the playhead. The
     * extrapolation is roughly equal to the real elapsed time, so the anchor
     * must never push the playhead backwards.
     */
    @Test
    void reAnchorsWhenAudioClockAdvances() {
        SmoothedPlayhead s = new SmoothedPlayhead();
        s.reset(5000, 0);
        s.update(5000, 3 * FRAME_NS, 20000); // glides to ~5048

        long out = s.update(5060, 3 * FRAME_NS + FRAME_NS / 2, 20000);

        assertEquals(5060, out);
        assertTrue(out >= 5048, "re-anchoring must not jump backwards");

        // And keeps gliding from the new anchor: the anchor call above ran at
        // 56 ms, this frame at 80 ms, so 24 ms elapse before the audio moves.
        assertEquals(5084, s.update(5060, 5 * FRAME_NS, 20000));
    }

    /** Seeking moves the audio clock backwards; the playhead must follow. */
    @Test
    void followsSeekBackwards() {
        SmoothedPlayhead s = new SmoothedPlayhead();
        s.reset(9000, 0);

        assertEquals(2000, s.update(2000, FRAME_NS, 20000));
        assertEquals(2000, s.positionMs());
    }

    /** The playhead is clamped to the song duration. */
    @Test
    void clampsToDuration() {
        SmoothedPlayhead s = new SmoothedPlayhead();
        s.reset(0, 0);

        assertEquals(10000, s.update(0, 20000 * NS_PER_MS, 10000));
        // Wall time went backwards here (never happens with nanoTime in one
        // frame); a negative elapsed simply does not extrapolate, so the
        // playhead holds its clamped position rather than drifting.
        assertEquals(10000, s.update(0, 0, 10000));
    }

    /** While paused the screen re-anchors every frame, so the playhead stalls. */
    @Test
    void stallsWhilePaused() {
        SmoothedPlayhead s = new SmoothedPlayhead();
        s.reset(3000, FRAME_NS);
        assertEquals(3000, s.update(3000, FRAME_NS, 20000));

        // Paused frames: reset to the same audio clock, wall time moves.
        s.reset(3000, 2 * FRAME_NS);
        assertEquals(3000, s.update(3000, 2 * FRAME_NS, 20000));
        s.reset(3000, 3 * FRAME_NS);
        assertEquals(3000, s.update(3000, 3 * FRAME_NS, 20000));

        // Resume: gliding continues from the anchor.
        assertEquals(3016, s.update(3000, 4 * FRAME_NS, 20000));
    }

    /** With no clamping duration the playhead still never goes negative. */
    @Test
    void neverNegativeWithoutDuration() {
        SmoothedPlayhead s = new SmoothedPlayhead();
        s.reset(10, FRAME_NS);
        assertEquals(10, s.update(10, FRAME_NS, 0));
        assertEquals(26, s.update(10, 2 * FRAME_NS, 0));
    }
}
