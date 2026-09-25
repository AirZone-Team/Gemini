package geminiclient.gemini.customRenderer.glsl;

import geminiclient.gemini.customRenderer.glsl.slug.SlugGeometry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Shape;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Off-thread glyph tessellation for {@link CustomFontRenderer.GlyphFont}.
 *
 * <p>Flattening an outline and triangulating it costs well under a millisecond
 * for a Latin glyph but can reach tens for a dense CJK one, and a newly opened
 * screen tessellates dozens of glyphs at once — far too slow to run on the
 * render thread. The work itself is plain geometry math over a
 * {@link Shape} that the render thread has already extracted and will not touch
 * again: it reads no AWT font state and no GL objects, so it moves to a
 * background thread without further synchronization.</p>
 *
 * <p>Flow: the render thread extracts the outline, hands the shape here, and
 * the worker publishes finished geometry on {@link #COMPLETED}. The render
 * thread picks it up in {@link #drain()} — called every frame via
 * {@code CustomFontRenderer.flushPendingGlyphs()} and before every layout
 * pass — where it becomes the cached glyph's vertex data. Until that lands,
 * {@code Glyph.hasGeometry} stays false and the draw path renders the
 * character through the vanilla fallback.</p>
 */
final class FontGlyphExecutor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FontGlyphExecutor.class);

    private FontGlyphExecutor() {
    }

    record PendingGlyph(CustomFontRenderer.GlyphFont font, int codePoint,
                        Shape outline) {
    }

    private record CompletedGlyph(CustomFontRenderer.GlyphFont font,
                                  int codePoint, SlugGeometry.Result geometry) {
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Gemini-FontTessellate");
        thread.setDaemon(true);
        // Below-normal priority: background tessellation must never contend
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
            try {
                COMPLETED.add(new CompletedGlyph(job.font(), job.codePoint(),
                        job.font().buildGeometry(job.outline())));
            } catch (Throwable t) {
                LOGGER.warn("[Font] Tessellating glyph failed (cp={})",
                        job.codePoint(), t);
            }
        });
    }

    /**
     * Render thread only. Publishes every finished tessellation to its glyph.
     * Cheap when the queue is empty, so callers may invoke it per frame.
     * Individual failures are logged and skipped — a broken task must never
     * break the frame it drains in.
     */
    static void drain() {
        CompletedGlyph done;
        while ((done = COMPLETED.poll()) != null) {
            try {
                done.font().applyGeometry(done.codePoint(), done.geometry());
            } catch (Throwable t) {
                LOGGER.warn("[Font] Applying tessellated glyph failed (cp={})",
                        done.codePoint(), t);
            }
        }
    }
}
