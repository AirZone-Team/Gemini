package geminiclient.gemini.modules.impl.visual.osu4k.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the software gain stage with click-kill ramps. */
class AudioGainTest {

    @Test
    void unityVolumeIsAPassThrough() {
        AudioGain g = new AudioGain();
        g.batchBegin(1f); // first batch: snap to the requested gain
        assertTrue(g.isIdentity());
        assertEquals((short) 32767, g.apply((short) 32767));
        assertEquals((short) -32768, g.apply((short) -32768));
        assertEquals((short) 0, g.apply((short) 0));
    }

    @Test
    void firstBatchSnapsToRequestedVolume() {
        AudioGain g = new AudioGain();
        g.batchBegin(0.5f);
        assertFalse(g.isIdentity());
        assertEquals((short) 10000, g.apply((short) 20000));
        assertEquals((short) -10000, g.apply((short) -20000));
    }

    @Test
    void volumeChangeRampsInsteadOfSnapping() {
        AudioGain g = new AudioGain();
        g.batchBegin(0.8f);
        assertEquals((short) 800, g.apply((short) 1000));

        // Drag the volume down mid-playback: the first sample is still close to
        // the old gain, then it eases to the new one — no hard step.
        g.batchBegin(0.2f);
        short floor = (short) 200;
        short prev = g.apply((short) 1000);
        assertTrue(prev > floor, "ramp must start above the new gain, was " + prev);
        for (int i = 1; i < AudioGain.RAMP_SAMPLES; i++) {
            short cur = g.apply((short) 1000);
            assertTrue(cur <= prev, "ramp must be monotonic descending");
            prev = cur;
        }
        assertEquals(floor, (short) Math.round(1000 * 0.2f));
        assertEquals(floor, g.apply((short) 1000));
    }

    @Test
    void seekResetRampsFromSilence() {
        AudioGain g = new AudioGain();
        g.batchBegin(0.8f);

        g.resetToSilence();
        assertEquals((short) 0, g.apply((short) 32000), "first sample after a seek must be silent");
        short prev = 0;
        for (int i = 1; i < AudioGain.RAMP_SAMPLES; i++) {
            short cur = g.apply((short) 32000);
            assertTrue(cur >= prev, "ramp must rise monotonically");
            prev = cur;
        }
        assertEquals((short) 25600, g.apply((short) 32000), "ramp must settle at 0.8 * input");
    }

    @Test
    void outputNeverExceedsTheS16Range() {
        AudioGain g = new AudioGain();
        g.batchBegin(1f);
        g.resetToSilence();
        for (int i = 0; i < AudioGain.RAMP_SAMPLES + 64; i++) {
            short up = g.apply(Short.MAX_VALUE);
            short down = g.apply(Short.MIN_VALUE);
            assertTrue(up >= Short.MIN_VALUE && up <= Short.MAX_VALUE);
            assertTrue(down >= Short.MIN_VALUE && down <= Short.MAX_VALUE);
        }
        // Volume changes mid-ramp must not clip either.
        g.batchBegin(0.6f);
        for (int i = 0; i < AudioGain.RAMP_SAMPLES + 64; i++) {
            short s = g.apply(Short.MAX_VALUE);
            assertTrue(s >= Short.MIN_VALUE && s <= Short.MAX_VALUE);
        }
    }

    @Test
    void mutedVolumeStaysSilent() {
        AudioGain g = new AudioGain();
        g.batchBegin(0f);
        assertEquals((short) 0, g.apply(Short.MAX_VALUE));
        g.batchBegin(0.8f);
        g.resetToSilence();
        g.batchBegin(0f); // mute during the seek ramp
        for (int i = 0; i < AudioGain.RAMP_SAMPLES + 32; i++) {
            assertEquals((short) 0, g.apply(Short.MAX_VALUE));
        }
    }
}