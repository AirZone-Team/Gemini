package geminiclient.gemini.modules.impl.visual.osu4k.audio;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Streaming audio player for osu! beatmap audio files (mp3/ogg/wav).
 *
 * <p>Decoding is delegated to the FFmpeg natives already shipped with the
 * client (via {@link FFmpegFrameGrabber}) and playback goes through a
 * {@link SourceDataLine} (Java Sound). The grabber is configured to resample
 * everything to interleaved S16 stereo at 44.1 kHz; some codecs still hand
 * back float planes instead of converting them (FLTP decoders, certain states
 * after a seek), so the decode loop normalizes every plane to a
 * {@link ShortBuffer} before it is fed to the line.</p>
 *
 * <p>The playhead is taken from the sound device itself
 * ({@link SourceDataLine#getLongFramePosition()}), which makes it the
 * authoritative audio clock the game loop is synced against. Seeking rebuilds
 * the grabber position, discards about 100 ms of decoded audio (the post-seek
 * decode can burst into full-scale static on lossy codecs if it reaches the
 * line untouched) and re-anchors the frame counter. Volume is applied as a
 * pure software gain with a click-kill ramp, never through the device's
 * {@code MASTER_GAIN} (driver-dependent, zipper noise on changes). All
 * line/grabber mutations happen on the single decode thread; the render
 * thread only reads volatile state and requests actions.</p>
 *
 * <p>A null frame from the grabber is only treated as end-of-stream when the
 * device clock is already at the song length: lossy decoders can return null
 * mid-song (a seek flush, a transient demuxer error) while minutes of audio
 * remain, and ending the run there would auto-miss everything left. Mid-song
 * nulls re-seek to the current position and resume, a bounded number of
 * times, so playback survives the hiccup instead of jumping to the end.</p>
 */
public final class FfmpegAudioPlayer {

    private static final Logger LOGGER = Logger.getLogger(FfmpegAudioPlayer.class.getName());

    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNELS = 2;
    public static final int SAMPLE_WIDTH_BYTES = 2;

    /** Decoded audio dropped after a seek so the lossy-codec seam burst never
     *  reaches the line (MP3 bit reservoir, OGG granule positions). */
    private static final long SEEK_DISCARD_MS = 100;
    /** Upper bound on frames discarded after one seek (100 ms is 4-5 MP3 frames;
     *  the cap guards against a seek landing in a corrupted region). */
    private static final int MAX_DISCARD_FRAMES = 128;
    /** How many times a mid-song null frame is recovered from before the player
     *  gives up and reports end-of-stream. */
    private static final int MAX_NULL_RECOVERIES = 5;
    /** A null frame with at least this much audio still unplayed is a decode
     *  hiccup, not the end of the song. */
    private static final long EOF_GRACE_MS = 250;

    /** Player lifecycle state as seen by the render thread. */
    public enum State { UNLOADED, PLAYING, PAUSED, ENDED, ERROR }

    private final Object lock = new Object();

    private volatile State state = State.UNLOADED;
    private volatile String errorMessage;
    private volatile Path loadedFile;
    private volatile long audioClockMs;
    /**
     * Wall timestamp ({@link System#nanoTime()}) of the last {@link #audioClockMs}
     * update. Written together with the clock (decode thread / lock) so the
     * render thread can extrapolate the clock between batch updates.
     */
    private volatile long audioClockAtNanos;
    /** Requested playback gain (0..1), applied in software by the decode thread. */
    private volatile float volume = 1.0f;
    /**
     * Software gain stage (decode thread only). Replaces the hardware
     * {@code MASTER_GAIN}: deterministic across devices, click-free when the
     * slider moves, and clamped so the output can never clip into distortion.
     */
    private final AudioGain gain = new AudioGain();

    // Read by the render thread (durationMs) and the decode thread;
    // all writes happen under the lock, so volatile gives the required visibility.
    private volatile FFmpegFrameGrabber grabber;
    private volatile SourceDataLine line;
    private volatile long seekBaseMs;
    private volatile long seekBaseFrame;
    private volatile long pendingSeekMs = -1;

    private Thread decodeThread;
    private volatile boolean running;
    /** Mid-song null frames recovered from since the last load (decode thread
     *  only). Once the budget is spent a null is reported as end-of-stream. */
    private int nullRecoveries;
    /** Reusable write buffer for one decode batch (decode thread only); keeps
     *  the per-frame {@code ByteBuffer.allocate} GC churn out of the decode
     *  loop, which shares the CPU with the render thread. */
    private ByteBuffer scratch;

    // ---------------------------------------------------------------------
    // Public API (render thread)
    // ---------------------------------------------------------------------

    public State state() { return state; }
    public String errorMessage() { return errorMessage; }
    public Path loadedFile() { return loadedFile; }
    public boolean isPlaying() { return state == State.PLAYING; }
    public boolean isLoaded() { return state != State.UNLOADED; }

    /** Audio-clock position in ms. Safe to call from the render thread. */
    public long positionMs() {
        return Math.max(0, audioClockMs);
    }

    /**
     * Audio-clock position extrapolated to the current wall time, so callers
     * never see the frozen clock between the decode thread's batch updates.
     *
     * <p>{@link #positionMs()} only advances when the decode thread finishes
     * writing a whole sample batch — tens of ms apart — so judging a keypress
     * against it can be tens of ms late even when the press lands perfectly on
     * the beat. While playing, this adds the wall time elapsed since the last
     * clock update, which tracks the audible position closely (the decode
     * thread re-anchors it every batch, so device/wall-clock drift never
     * accumulates); the result is clamped to the song length. When not playing
     * it falls back to the raw clock, because the playhead must not advance
     * while paused.</p>
     */
    public long positionMsFresh() {
        if (state != State.PLAYING) {
            return Math.max(0, audioClockMs);
        }
        long elapsedMs = (System.nanoTime() - audioClockAtNanos) / 1_000_000L;
        long fresh = audioClockMs + Math.max(0, elapsedMs);
        long dur = durationMs();
        return dur > 0 ? Math.min(fresh, dur) : Math.max(0, fresh);
    }

    /** Total duration in ms, or 0 while unloaded. */
    public long durationMs() {
        FFmpegFrameGrabber g = grabber;
        return g == null ? 0 : g.getLengthInTime() / 1000;
    }

    public void setVolume(float v) {
        this.volume = Math.max(0f, Math.min(1f, v));
    }

    public float volume() {
        return volume;
    }

    /**
     * Loads a new audio file and becomes paused at position 0. Any previously
     * loaded file is unloaded. Call before {@link #play()}.
     */
    public void load(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            enterError("Audio file not found: " + file.getFileName());
            return;
        }
        synchronized (lock) {
            // Fully stop the previous decode thread before opening the new file.
            // This avoids the race where the old thread's blocked write unblocks
            // on line close and poisons the fresh load with an ERROR state.
            stopDecodeThread();
            grabber = openGrabber(file);
            if (grabber == null) {
                running = false;
                return; // enterError already set
            }
            line = openLine();
            if (line == null) {
                running = false;
                shutdownGrabberAndLine(); // release the grabber opened above
                return;
            }
            loadedFile = file;
            seekBaseMs = 0;
            seekBaseFrame = 0;
            pendingSeekMs = -1;
            nullRecoveries = 0;
            audioClockMs = 0;
            audioClockAtNanos = System.nanoTime();
            errorMessage = null;
            state = State.PAUSED;
            // Start the decode thread only now that the grabber/line exist: the
            // thread blocks on the lock until load() returns, so an earlier
            // start would let it observe a null grabber (and, with a previous
            // PLAYING state, exit immediately — leaving a permanently silent
            // player).
            running = true;
            decodeThread = new Thread(this::decodeLoop, "OSU4K-Audio");
            decodeThread.setDaemon(true);
            decodeThread.start();
            lock.notifyAll();
        }
        LOGGER.info("OSU4K audio loaded: " + file.getFileName() + " (" + durationMs() + " ms)");
    }

    /** Starts (or resumes) playback. */
    public void play() {
        synchronized (lock) {
            if (state == State.ERROR || state == State.UNLOADED) {
                return;
            }
            if (state == State.ENDED) {
                seekToMs(0);
                state = State.PAUSED;
            }
            if (state != State.PLAYING) {
                // Re-anchor the wall-clock extrapolation: the anchor was set at
                // the last decode batch (or at load), which can be seconds
                // before this start/resume — extrapolating from it would make
                // positionMsFresh jump forward by the whole paused / 3s-lead-in
                // duration, auto-missing notes that have not appeared yet. The
                // next decode batch re-anchors again, so this stays accurate.
                audioClockAtNanos = System.nanoTime();
                state = State.PLAYING;
            }
            lock.notifyAll();
        }
    }

    public void pause() {
        synchronized (lock) {
            if (state == State.PLAYING) {
                state = State.PAUSED;
            }
        }
    }

    public void togglePlayPause() {
        if (state == State.PLAYING) {
            pause();
        } else {
            play();
        }
    }

    /**
     * Seeks to an absolute position in ms. Works while playing and while paused;
     * the decode thread applies it as soon as it can (a blocking write is
     * unblocked with {@code flush()}). Paused playback stays paused at the new
     * position.
     */
    public void seekToMs(long ms) {
        long clamped = Math.max(0, Math.min(ms, Math.max(0, durationMs())));
        synchronized (lock) {
            if (state == State.UNLOADED) {
                return;
            }
            pendingSeekMs = clamped;
            if (line != null) {
                line.flush();
            }
            lock.notifyAll();
        }
    }

    /**
     * Recovers from a spurious end-of-stream: re-seeks to {@code ms} and
     * resumes playback. Used by the game screen as the last line of defence
     * when {@link #state()} turned {@link State#ENDED} with most of the song
     * still unplayed — the decode thread's own recovery ({@link #recoverFromNull()})
     * normally handles those before they ever surface.
     */
    public void resumeAt(long ms) {
        synchronized (lock) {
            if (state == State.UNLOADED || grabber == null) {
                return;
            }
            pendingSeekMs = Math.max(0, Math.min(ms, Math.max(0, durationMs())));
            if (line != null) {
                line.flush();
            }
            state = State.PLAYING;
            audioClockAtNanos = System.nanoTime();
            lock.notifyAll();
        }
    }

    /** Stops playback and unloads the current file. */
    public void unload() {
        synchronized (lock) {
            stopDecodeThread();
            loadedFile = null;
            audioClockMs = 0;
            audioClockAtNanos = System.nanoTime();
            state = State.UNLOADED;
        }
    }

    // ---------------------------------------------------------------------
    // Decode thread
    // ---------------------------------------------------------------------

    /**
     * Flags the decode thread to stop, closes its grabber/line (which unblocks
     * any pending write), and waits for it to exit. Must be called under
     * {@link #lock}. Sets {@link #running} false first so the thread's teardown
     * exceptions do not surface as ERROR.
     */
    private void stopDecodeThread() {
        running = false;
        lock.notifyAll();
        shutdownGrabberAndLine();
        Thread t = decodeThread;
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        decodeThread = null;
    }

    private void decodeLoop() {
        while (true) {
            synchronized (lock) {
                while (running && (state == State.PAUSED || state == State.ENDED)) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!running || state == State.UNLOADED || state == State.ERROR) {
                    return;
                }
            }

            FFmpegFrameGrabber g = grabber;
            if (g == null) {
                return;
            }
            SourceDataLine dl = line;

            try {
                applyPendingSeek();
                if (state != State.PLAYING) {
                    continue;
                }
                Frame frame = g.grabSamples();
                if (frame == null || frame.samples == null || frame.samples[0] == null) {
                    if (recoverFromNull()) {
                        continue;
                    }
                    synchronized (lock) {
                        state = State.ENDED;
                    }
                    // Keep the clock at its last real position instead of
                    // slamming it to the song length: a spurious EOF must not
                    // make the playhead look like the song already ended.
                    audioClockAtNanos = System.nanoTime();
                    continue;
                }
                ShortBuffer samples = normalizeToShort(frame);
                int frames = samples.remaining() / CHANNELS;
                if (frames <= 0) {
                    continue;
                }
                if (dl != null) {
                    writeToLine(dl, samples);
                    updateAudioClock();
                }
            } catch (FFmpegFrameGrabber.Exception e) {
                if (running) {
                    enterError("Audio decode error: " + e.getMessage());
                }
                return;
            } catch (IOException e) {
                if (running) {
                    enterError("Audio output error: " + e.getMessage());
                }
                return;
            } catch (RuntimeException e) {
                if (!running) {
                    return; // teardown race (line closed mid-write)
                }
                LOGGER.log(Level.WARNING, "OSU4K audio thread exception", e);
                enterError("Audio playback error: " + e.getMessage());
                return;
            }
        }
    }

    /**
     * Decides whether a null frame from the grabber is a genuine end-of-stream
     * or a decode hiccup, and re-seeks for the latter. JavaCV's grabber can
     * return null mid-song on lossy codecs (a seek flush, a transient demuxer
     * or codec error) while minutes of audio remain; treating that as EOF
     * would end the run with every note left un-judged. The device clock is
     * the arbiter: only nulls at the song length are trusted. Recovered nulls
     * re-seek just behind the current position so the seek's own discard does
     * not eat the samples the player is currently hearing, bounded by
     * {@link #MAX_NULL_RECOVERIES} so a genuinely broken file cannot spin.
     */
    private boolean recoverFromNull() {
        long pos;
        synchronized (lock) {
            if (!running || state != State.PLAYING || durationMs() <= 0) {
                return false;
            }
            pos = positionMs();
            if (pos >= durationMs() - EOF_GRACE_MS) {
                return false; // genuinely at the end
            }
            if (nullRecoveries >= MAX_NULL_RECOVERIES) {
                return false;
            }
            nullRecoveries++;
        }
        seekToMs(Math.max(0, pos - EOF_GRACE_MS));
        return true;
    }

    /**
     * Normalizes a decoded frame to interleaved S16, the format the output
     * line expects. The grabber is asked to resample to S16, but some codecs
     * still deliver float planes instead of converting them (FLTP decoders,
     * certain states after a seek), and those planes can be planar — one
     * buffer per channel — rather than interleaved. Both layouts are
     * interleaved here; floats are clamped and scaled the same way FFmpeg's
     * S16 conversion does, so all paths sound identical.
     */
    private ShortBuffer normalizeToShort(Frame frame) throws IOException {
        Buffer[] planes = frame.samples;
        if (planes == null || planes.length == 0) {
            throw new IOException("Audio frame has no sample data");
        }
        if (planes.length == 1) {
            Buffer b = planes[0];
            if (b instanceof ShortBuffer) {
                return (ShortBuffer) b;
            }
            if (b instanceof FloatBuffer) {
                return floatsToShorts((FloatBuffer) b);
            }
            throw new IOException("Unexpected decoded sample type: " + b.getClass().getSimpleName());
        }
        // Planar layout (FLTP etc.): each plane holds one channel's samples for
        // the same set of frames; interleave them into L/R pairs.
        int frameCount = planes[0].remaining();
        ShortBuffer out = ShortBuffer.allocate(frameCount * planes.length);
        for (int f = 0; f < frameCount; f++) {
            for (int ch = 0; ch < planes.length; ch++) {
                out.put(sampleToShort(planes[ch], f));
            }
        }
        out.flip();
        return out;
    }

    private static short sampleToShort(Buffer plane, int index) {
        if (plane instanceof FloatBuffer floats) {
            float v = floats.get(floats.position() + index);
            if (v > 1.0f) {
                v = 1.0f;
            } else if (v < -1.0f) {
                v = -1.0f;
            }
            return (short) (v * 32767.0f);
        }
        if (plane instanceof ShortBuffer shorts) {
            return shorts.get(shorts.position() + index);
        }
        throw new IllegalArgumentException("Unexpected sample plane type: " + plane.getClass().getSimpleName());
    }

    private static ShortBuffer floatsToShorts(FloatBuffer floats) {
        ShortBuffer shorts = ShortBuffer.allocate(floats.remaining());
        while (floats.hasRemaining()) {
            float v = floats.get();
            if (v > 1.0f) {
                v = 1.0f;
            } else if (v < -1.0f) {
                v = -1.0f;
            }
            shorts.put((short) (v * 32767.0f));
        }
        shorts.flip();
        return shorts;
    }

    private void applyPendingSeek() throws FFmpegFrameGrabber.Exception {
        long target = pendingSeekMs;
        if (target < 0) {
            return;
        }
        pendingSeekMs = -1;
        if (grabber != null) {
            long targetUs = target * 1000;
            grabber.setTimestamp(targetUs);
            // JavaCV's seek lands on the frame boundary before the target and
            // caches the straddling frame decoded right after the codec flush.
            // For lossy codecs the post-seek decode can burst into a run of
            // full-scale static (MP3 bit reservoir, OGG granule positions) that
            // lasts several frames — dropping one frame and ramping the gain
            // over 11 ms is not enough. Discard ~SEEK_DISCARD_MS of decoded
            // audio so none of it can reach the line; the gain ramp below masks
            // whatever residual seam the discard leaves. Replay from 0 is
            // exempt: frame 0 decodes cleanly from a fresh decoder, and
            // dropping it would eat the song's first beat.
            if (targetUs > 0) {
                long discardUntilUs = targetUs + SEEK_DISCARD_MS * 1000L;
                int guard = 0;
                while (guard++ < MAX_DISCARD_FRAMES && grabber.getTimestamp() < discardUntilUs) {
                    try {
                        if (grabber.grabSamples() == null) {
                            break; // seeked into the tail; nothing left to drop
                        }
                    } catch (FFmpegFrameGrabber.Exception e) {
                        // A transient decode error while discarding is harmless:
                        // the gain ramp below covers any seam residue.
                        break;
                    }
                }
                // Anchor the clock to the first frame that will actually reach
                // the line — the discarded audio is ~SEEK_DISCARD_MS ahead of
                // the seek target, so counting from the target would make every
                // subsequent position report ~100 ms late.
                long firstUs = grabber.getTimestamp();
                target = Math.min(Math.max(0, firstUs / 1000), Math.max(0, durationMs()));
            }
        }
        if (line != null) {
            line.flush();
            seekBaseFrame = line.getLongFramePosition();
        }
        seekBaseMs = target;
        audioClockMs = target;
        audioClockAtNanos = System.nanoTime();
        // The flush leaves a waveform seam in the device buffer; ramping the
        // gain from silence over a few ms makes the seam (and any residual
        // post-seek garbage the discard missed) inaudible.
        gain.resetToSilence();
    }

    private void writeToLine(SourceDataLine dl, ShortBuffer samples) throws IOException {
        int nFrames = samples.remaining() / CHANNELS;
        int bytes = nFrames * CHANNELS * SAMPLE_WIDTH_BYTES;
        // Reuse the scratch buffer instead of allocating per frame: the decode
        // thread shares the CPU with Minecraft's render thread and a GC pause
        // on every frame-sized allocation is exactly the kind of hiccup that
        // underruns the ~185 ms device buffer into crackle.
        if (scratch == null || scratch.capacity() < bytes) {
            scratch = ByteBuffer.allocate(Math.max(bytes, 8192));
        }
        ByteBuffer bb = scratch;
        bb.clear();
        // Software gain + click-kill ramp (see AudioGain). Unity gain with no
        // active ramp keeps the plain copy path, so the default 100% volume
        // does no per-sample work at all.
        gain.batchBegin(volume);
        if (gain.isIdentity()) {
            for (int i = 0; i < nFrames * CHANNELS; i++) {
                bb.putShort(samples.get());
            }
        } else {
            for (int i = 0; i < nFrames * CHANNELS; i++) {
                bb.putShort(gain.apply(samples.get()));
            }
        }
        byte[] data = bb.array();
        // The scratch buffer is larger than one batch; only the filled bytes
        // up to the position may reach the device.
        int len = bb.position();
        int written = 0;
        while (written < len) {
            int n = dl.write(data, written, len - written);
            if (n <= 0) {
                throw new IOException("Audio device accepted no data");
            }
            written += n;
        }
    }

    private void updateAudioClock() {
        if (line == null) {
            return;
        }
        long framePos = line.getLongFramePosition();
        long playedFrames = Math.max(0, framePos - seekBaseFrame);
        long pos = seekBaseMs + playedFrames * 1000 / SAMPLE_RATE;
        audioClockMs = Math.max(0, Math.min(pos, durationMs()));
        audioClockAtNanos = System.nanoTime();
    }

    // ---------------------------------------------------------------------
    // Setup / teardown (called under lock)
    // ---------------------------------------------------------------------

    private FFmpegFrameGrabber openGrabber(Path file) {
        FFmpegFrameGrabber g = new FFmpegFrameGrabber(file.toFile());
        try {
            g.setSampleFormat(avutil.AV_SAMPLE_FMT_S16);
            g.setSampleRate(SAMPLE_RATE);
            g.setAudioChannels(CHANNELS);
            g.start();
            warmUp(g);
            return g;
        } catch (FFmpegFrameGrabber.Exception e) {
            enterError("Cannot open audio: " + e.getMessage());
            try {
                g.release();
            } catch (FFmpegFrameGrabber.Exception ignored) {
                // best effort
            }
            return null;
        }
    }

    /**
     * Decodes and discards the first frame so the one-time setup — first
     * packet decode, resampler (SWR) initialization — happens at load time
     * instead of when {@link #play()} first fills the device buffer. The 3s
     * lead-in leaves the buffer empty at play(), so a decode stall in that
     * first window underruns into stutter right as the music and the first
     * notes begin; warming up here moves the stall to screen open, where a few
     * ms is invisible. Exactly one frame: the grabber caches the last decoded
     * frame, so the decode thread's first grab at play() re-delivers the
     * song's true first samples instead of starting a later frame.
     */
    private static void warmUp(FFmpegFrameGrabber g) {
        try {
            g.grabSamples();
        } catch (FFmpegFrameGrabber.Exception e) {
            // Warmup only; the decode thread reports real errors at play time.
        }
    }

    private SourceDataLine openLine() {
        AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, true);
        try {
            SourceDataLine dl = AudioSystem.getSourceDataLine(fmt);
            // 64 KiB (~371 ms) buffer instead of a single decode frame: the
            // decode thread shares the CPU with Minecraft's render thread and
            // GC pauses of tens of ms are normal here — a frame-sized buffer
            // underruns into crackle on every hiccup, and the old 32 KiB
            // (~185 ms) left too little slack once the decode thread falls
            // behind, e.g. at song start or during heavy frames. ~371 ms
            // headroom survives those stalls silently. Volume is applied in
            // software, so the device gain is left untouched at its default
            // 0 dB. The trade-off is that a pause keeps up to ~371 ms of
            // buffered audio playing; the position display anchors to the
            // device clock, so it stays correct regardless.
            dl.open(fmt, 65536);
            dl.start();
            return dl;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            enterError("Audio output unavailable: " + e.getMessage());
            return null;
        }
    }

    private void shutdownGrabberAndLine() {
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "OSU4K grabber shutdown", e);
            }
            grabber = null;
        }
        if (line != null) {
            line.flush();
            line.stop();
            line.close();
            line = null;
        }
    }

    private void enterError(String message) {
        LOGGER.warning("OSU4k audio: " + message);
        errorMessage = message;
        state = State.ERROR;
    }
}
