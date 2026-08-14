package geminiclient.gemini.base;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;

final class JavaCvVideoBackground implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(JavaCvVideoBackground.class.getName());
    private static final long MICROS_TO_NANOS = 1_000L;
    // Fallback pacing for videos without usable PTS (frame.timestamp <= 0): a
    // fixed frame interval prevents full-speed busy decoding.
    private static final long DEFAULT_FRAME_INTERVAL_NANOS = 1_000_000_000L / 30;
    private static final long MIN_FRAME_INTERVAL_NANOS = 1_000_000_000L / 125;
    // Back-pressure poll interval while the render side hasn't consumed a frame.
    private static final long PAUSE_POLL_MILLIS = 1L;
    // Pause at end of file so the looped video doesn't restart instantly.
    private static final long LOOP_PAUSE_MILLIS = 150L;

    private final Path videoPath;
    private final AtomicReference<VideoFrame> pendingFrame = new AtomicReference<>();
    private final AtomicReference<VideoFrame> reusableFrame = new AtomicReference<>();
    private volatile boolean running;
    private volatile boolean failed;
    private Thread decoderThread;

    JavaCvVideoBackground(Path videoPath) {
        this.videoPath = videoPath.toAbsolutePath().normalize();
    }

    static boolean isSupportedVideo(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp4") || name.endsWith(".webm");
    }

    boolean isFor(Path path) {
        return path != null && videoPath.equals(path.toAbsolutePath().normalize());
    }

    boolean hasFailed() {
        return failed;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        decoderThread = new Thread(this::decodeLoop, "Gemini-BackgroundVideo");
        decoderThread.setDaemon(true);
        decoderThread.start();
    }

    VideoFrame pollFrame() {
        return pendingFrame.getAndSet(null);
    }

    void releaseFrame(VideoFrame frame) {
        if (frame == null) {
            return;
        }
        if (!running) {
            return;
        }

        reusableFrame.getAndSet(frame);
        // close() can race this hand-off after the first running check. Dropping a
        // direct buffer is safe; it is reclaimed with its frame object.
        if (!running && reusableFrame.compareAndSet(frame, null)) {
            return;
        }
    }

    private void decodeLoop() {
        while (running && !failed) {
            try {
                decodeFileOnce();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable throwable) {
                // An exception while shutting down is not a real failure; close()
                // races the running flag from another thread.
                if (!running) {
                    break;
                }
                failed = true;
                LOGGER.log(Level.WARNING, "Failed to decode background video: " + videoPath, throwable);
            }
        }
        running = false;
        closePendingFrame();
        closeReusableFrame();
    }

    private void decodeFileOnce() throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath.toFile())) {
            grabber.setAudioChannels(0);
            // Let FFmpeg perform the colour conversion in native code. NativeImage and
            // the GPU texture both consume tightly packed RGBA bytes, so no per-pixel
            // Java conversion is needed.
            grabber.setPixelFormat(AV_PIX_FMT_RGBA);
            grabber.start();

            long firstTimestamp = -1L;
            long playbackStartedAt = System.nanoTime();
            // Videos without usable PTS (frame.timestamp <= 0) would make sleepUntil
            // return immediately and busy-loop at full decode speed; fall back to a
            // fixed frame interval in that case.
            boolean usePts = true;
            long frameIntervalNanos = DEFAULT_FRAME_INTERVAL_NANOS;
            long frameIndex = 0L;

            Frame frame;
            while (running) {
                // Back-pressure: never decode more than one frame ahead of the render
                // side. While the menu is closed or the background is disabled nothing
                // consumes frames, so the thread idles here at ~0% CPU instead of
                // decoding at full speed and discarding every frame.
                while (running && pendingFrame.get() != null) {
                    Thread.sleep(PAUSE_POLL_MILLIS);
                }
                if (!running) {
                    break;
                }

                frame = grabber.grabImage();
                if (frame == null) {
                    // End of file: pause briefly so the loop doesn't restart instantly.
                    if (running) {
                        Thread.sleep(LOOP_PAUSE_MILLIS);
                    }
                    break;
                }

                long timestamp = Math.max(0L, frame.timestamp);
                if (firstTimestamp < 0L) {
                    firstTimestamp = timestamp;
                    playbackStartedAt = System.nanoTime();
                    usePts = firstTimestamp > 0L;
                    if (!usePts) {
                        double fps = grabber.getFrameRate();
                        if (fps > 0.0 && !Double.isNaN(fps) && !Double.isInfinite(fps)) {
                            frameIntervalNanos = Math.max(MIN_FRAME_INTERVAL_NANOS,
                                    Math.round(1_000_000_000.0 / fps));
                        }
                    }
                }

                // Preserve the source video's timing. If decoding falls behind,
                // sleepUntil returns immediately instead of imposing an FPS cap.
                sleepUntil(usePts
                        ? playbackStartedAt + (timestamp - firstTimestamp) * MICROS_TO_NANOS
                        : playbackStartedAt + frameIndex * frameIntervalNanos);
                frameIndex++;
                if (!running) {
                    break;
                }

                VideoFrame image = copyFrame(frame);
                boolean published = false;
                try {
                    if (running) {
                        publishFrame(image);
                        published = true;
                    }
                } finally {
                    if (!published) {
                        releaseFrame(image);
                    }
                }
            }
        }
    }

    private void publishFrame(VideoFrame frame) {
        VideoFrame previous = pendingFrame.getAndSet(frame);
        if (previous != null) {
            releaseFrame(previous);
        }
    }

    private VideoFrame copyFrame(Frame frame) {
        if (frame.image == null || frame.image.length == 0 || !(frame.image[0] instanceof ByteBuffer pixels)
                || frame.imageDepth != Frame.DEPTH_UBYTE || frame.imageChannels != 4) {
            throw new IllegalArgumentException("Unsupported decoded video frame format");
        }

        int width = frame.imageWidth;
        int height = frame.imageHeight;
        int stride = frame.imageStride;
        int rowBytes = Math.multiplyExact(width, 4);
        if (width <= 0 || height <= 0 || stride < rowBytes) {
            throw new IllegalArgumentException("Invalid decoded video frame layout");
        }

        VideoFrame image = acquireFrame(width, height);
        try {
            ByteBuffer source = pixels.duplicate();
            ByteBuffer target = image.pixels();
            int sourceBase = source.position();
            long requiredBytes = (long) sourceBase + (long) (height - 1) * stride + rowBytes;
            if (requiredBytes > source.capacity()) {
                throw new IllegalArgumentException("Decoded video frame buffer is too small");
            }

            target.clear();
            if (stride == rowBytes) {
                source.limit(Math.toIntExact(requiredBytes));
                target.put(source);
            } else {
                for (int y = 0; y < height; y++) {
                    int rowStart = Math.addExact(sourceBase, Math.multiplyExact(y, stride));
                    ByteBuffer row = source.duplicate();
                    row.position(rowStart);
                    row.limit(Math.addExact(rowStart, rowBytes));
                    target.position(Math.multiplyExact(y, rowBytes));
                    target.put(row);
                }
            }
            target.rewind();
            return image;
        } catch (Throwable throwable) {
            throw throwable;
        }
    }

    private VideoFrame acquireFrame(int width, int height) {
        VideoFrame frame = reusableFrame.getAndSet(null);
        return frame != null && frame.width() == width && frame.height() == height
                ? frame
                : new VideoFrame(width, height, ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(width, height), 4)));
    }

    private static void sleepUntil(long targetNanos) throws InterruptedException {
        long sleepNanos = targetNanos - System.nanoTime();
        if (sleepNanos > 0L) {
            Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
        }
    }

    @Override
    public void close() {
        running = false;
        if (decoderThread != null) {
            decoderThread.interrupt();
            decoderThread = null;
        }
        closePendingFrame();
        closeReusableFrame();
    }

    private void closePendingFrame() {
        pendingFrame.set(null);
    }

    private void closeReusableFrame() {
        reusableFrame.set(null);
    }

    /** A tightly packed, direct RGBA frame suitable for a GPU upload. */
    record VideoFrame(int width, int height, ByteBuffer pixels) {
    }
}
