package geminiclient.gemini.base;

import com.mojang.blaze3d.platform.NativeImage;
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

    private final Path videoPath;
    private final AtomicReference<NativeImage> pendingFrame = new AtomicReference<>();
    private final AtomicReference<NativeImage> reusableFrame = new AtomicReference<>();
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

    NativeImage pollFrame() {
        return pendingFrame.getAndSet(null);
    }

    void releaseFrame(NativeImage frame) {
        if (frame == null || frame.isClosed()) {
            return;
        }
        if (!running) {
            frame.close();
            return;
        }

        NativeImage previous = reusableFrame.getAndSet(frame);
        if (previous != null) {
            previous.close();
        }
        // close() can race this hand-off after the first running check.
        if (!running && reusableFrame.compareAndSet(frame, null)) {
            frame.close();
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
            // Java conversion is needed. No output frame-rate cap is configured.
            grabber.setPixelFormat(AV_PIX_FMT_RGBA);
            grabber.start();

            long firstTimestamp = -1L;
            long playbackStartedAt = System.nanoTime();
            Frame frame;
            while (running && (frame = grabber.grabImage()) != null) {
                long timestamp = Math.max(0L, frame.timestamp);
                if (firstTimestamp < 0L) {
                    firstTimestamp = timestamp;
                    playbackStartedAt = System.nanoTime();
                }

                NativeImage image = copyFrame(frame);
                try {
                    // Preserve the source video's timing. If decoding falls behind,
                    // sleepUntil returns immediately instead of imposing an FPS cap.
                    sleepUntil(playbackStartedAt + (timestamp - firstTimestamp) * MICROS_TO_NANOS);
                    if (running) {
                        publishFrame(image);
                        image = null;
                    }
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }
        }
    }

    private void publishFrame(NativeImage frame) {
        NativeImage previous = pendingFrame.getAndSet(frame);
        if (previous != null) {
            releaseFrame(previous);
        }
    }

    private NativeImage copyFrame(Frame frame) {
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

        NativeImage image = acquireFrame(width, height);
        try {
            ByteBuffer source = pixels.duplicate();
            ByteBuffer target = image.getPixelBytes();
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
            image.close();
            throw throwable;
        }
    }

    private NativeImage acquireFrame(int width, int height) {
        NativeImage image = reusableFrame.getAndSet(null);
        if (image != null && (image.getWidth() != width || image.getHeight() != height)) {
            image.close();
            image = null;
        }
        return image != null ? image : new NativeImage(width, height, false);
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
        NativeImage frame = pendingFrame.getAndSet(null);
        if (frame != null) {
            frame.close();
        }
    }

    private void closeReusableFrame() {
        NativeImage frame = reusableFrame.getAndSet(null);
        if (frame != null) {
            frame.close();
        }
    }
}
