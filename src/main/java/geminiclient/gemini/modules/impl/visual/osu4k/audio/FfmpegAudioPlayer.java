package geminiclient.gemini.modules.impl.visual.osu4k.audio;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.nio.ByteBuffer;
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
 * everything to interleaved S16 stereo at 44.1 kHz, so every frame's
 * {@code Frame.samples[0]} is a {@link ShortBuffer} we can feed to the line
 * directly.</p>
 *
 * <p>The playhead is taken from the sound device itself
 * ({@link SourceDataLine#getLongFramePosition()}), which makes it the
 * authoritative audio clock the game loop is synced against. Seeking rebuilds
 * the grabber position and re-anchors the frame counter. All line/grabber
 * mutations happen on the single decode thread; the render thread only reads
 * volatile state and requests actions.</p>
 */
public final class FfmpegAudioPlayer {

    private static final Logger LOGGER = Logger.getLogger(FfmpegAudioPlayer.class.getName());

    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNELS = 2;
    public static final int SAMPLE_WIDTH_BYTES = 2;

    private static final float MAX_GAIN_DB = 6.0f;

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
    private volatile float volume = 1.0f; // 0..1 linear, applied via MASTER_GAIN

    // Read by the render thread (durationMs/applyVolume) and the decode thread;
    // all writes happen under the lock, so volatile gives the required visibility.
    private volatile FFmpegFrameGrabber grabber;
    private volatile SourceDataLine line;
    private volatile long seekBaseMs;
    private volatile long seekBaseFrame;
    private volatile long pendingSeekMs = -1;

    private Thread decodeThread;
    private volatile boolean running;

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
        applyVolume();
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
            running = true;
            decodeThread = new Thread(this::decodeLoop, "OSU4K-Audio");
            decodeThread.setDaemon(true);
            decodeThread.start();
            grabber = openGrabber(file);
            if (grabber == null) {
                return; // enterError already set
            }
            line = openLine();
            if (line == null) {
                return;
            }
            loadedFile = file;
            seekBaseMs = 0;
            seekBaseFrame = 0;
            pendingSeekMs = -1;
            audioClockMs = 0;
            audioClockAtNanos = System.nanoTime();
            errorMessage = null;
            state = State.PAUSED;
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

    /** Stops the decode thread permanently (player teardown). */
    public void close() {
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
                    synchronized (lock) {
                        state = State.ENDED;
                    }
                    audioClockMs = durationMs();
                    audioClockAtNanos = System.nanoTime();
                    continue;
                }
                ShortBuffer samples = (ShortBuffer) frame.samples[0];
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

    private void applyPendingSeek() throws FFmpegFrameGrabber.Exception {
        long target = pendingSeekMs;
        if (target < 0) {
            return;
        }
        pendingSeekMs = -1;
        if (grabber != null) {
            // FFmpeg timestamps are in microseconds.
            grabber.setTimestamp(target * 1000);
        }
        if (line != null) {
            line.flush();
            seekBaseFrame = line.getLongFramePosition();
        }
        seekBaseMs = target;
        audioClockMs = target;
        audioClockAtNanos = System.nanoTime();
    }

    private void writeToLine(SourceDataLine dl, ShortBuffer samples) throws IOException {
        int nFrames = samples.remaining() / CHANNELS;
        int bytes = nFrames * CHANNELS * SAMPLE_WIDTH_BYTES;
        ByteBuffer bb = ByteBuffer.allocate(bytes);
        for (int i = 0; i < nFrames * CHANNELS; i++) {
            bb.putShort(samples.get());
        }
        byte[] data = bb.array();
        int written = 0;
        while (written < data.length) {
            int n = dl.write(data, written, data.length - written);
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

    private SourceDataLine openLine() {
        AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, true);
        try {
            SourceDataLine dl = AudioSystem.getSourceDataLine(fmt);
            dl.open(fmt, 8192);
            dl.start();
            applyVolume(dl);
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

    private void applyVolume() {
        SourceDataLine dl = line;
        if (dl != null) {
            applyVolume(dl);
        }
    }

    private void applyVolume(SourceDataLine dl) {
        try {
            if (dl.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) dl.getControl(FloatControl.Type.MASTER_GAIN);
                float min = gain.getMinimum();
                float max = Math.min(gain.getMaximum(), MAX_GAIN_DB);
                float db = volume <= 0f ? min : (float) (20.0 * Math.log10(volume));
                gain.setValue(Math.max(min, Math.min(max, db)));
            }
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.FINE, "Volume control not available", e);
        }
    }

    private void enterError(String message) {
        LOGGER.warning("OSU4k audio: " + message);
        errorMessage = message;
        state = State.ERROR;
    }
}
