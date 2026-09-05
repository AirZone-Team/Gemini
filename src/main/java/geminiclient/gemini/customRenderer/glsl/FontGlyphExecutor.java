package geminiclient.gemini.customRenderer.glsl;

import geminiclient.gemini.customRenderer.glsl.msdf.MsdfGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Shape;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Off-thread MSDF generation for {@link CustomFontRenderer.GlyphFont}.
 *
 * <p>Generating one glyph's distance field costs on the order of 17 ms of
 * pure CPU work (CJK glyphs are the most expensive), which is far too slow to
 * run on the render thread — a single new sentence would stall a frame by
 * hundreds of milliseconds. The generation itself, however, is plain geometry
 * math over a pre-extracted {@link Shape}: it touches no AWT font state, no
 * GL objects and no atlas bookkeeping, so it moves to a background thread
 * without further synchronization.</p>
 *
 * <p>Flow: the render thread extracts the outline and reserves an atlas cell
 * (all atlas state stays render-thread-confined), hands the shape here, and
 * the worker publishes finished pixel buffers on {@link #COMPLETED}. The
 * render thread picks them up in {@link #drain()} — called every frame via
 * {@code CustomFontRenderer.flushAllPages()} and before every layout pass —
 * where the pixels are written into the atlas and uploaded as a sub-region.
 * Until a glyph's generation lands, {@code Glyph.hasImage} stays false and
 * the draw path renders it through the vanilla fallback.</p>
 */
final class FontGlyphExecutor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FontGlyphExecutor.class);

    private FontGlyphExecutor() {
    }

    record PendingGlyph(CustomFontRenderer.GlyphFont font, int codePoint,
                        Shape outline, int cellWidth, int cellHeight) {
    }

    private record CompletedGlyph(CustomFontRenderer.GlyphFont font,
                                  int codePoint, byte[] msdfPixels) {
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Gemini-FontMSDF");
        thread.setDaemon(true);
        // Below-normal priority: background generation must never contend
        // with the render thread for CPU when frames are tight.
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private static final Queue<CompletedGlyph> COMPLETED = new ConcurrentLinkedQueue<>();

    static void submit(PendingGlyph job) {
        EXECUTOR.execute(() -> {
            // Drop early when the owning font was disposed while queued.
            if (job.font().isDisposed()) {
                return;
            }
            byte[] pixels = MsdfGenerator.generate(
                    job.outline(), job.cellWidth(), job.cellHeight());
            COMPLETED.add(new CompletedGlyph(job.font(), job.codePoint(), pixels));
        });
    }

    /**
     * Render thread only. Applies every finished generation to its atlas page.
     * Cheap when the queue is empty, so callers may invoke it per frame.
     * Individual failures are logged and skipped — a broken task must never
     * break the frame it drains in.
     */
    static void drain() {
        CompletedGlyph done;
        while ((done = COMPLETED.poll()) != null) {
            try {
                done.font().applyRaster(done.codePoint(), done.msdfPixels());
            } catch (Throwable t) {
                LOGGER.warn("[Font] Applying generated glyph failed (cp={})",
                        done.codePoint(), t);
            }
        }
    }
}
