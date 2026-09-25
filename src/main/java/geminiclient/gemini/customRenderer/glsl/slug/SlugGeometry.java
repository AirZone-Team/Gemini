package geminiclient.gemini.customRenderer.glsl.slug;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a glyph outline into the triangle soup the Slug pipeline draws.
 *
 * <p>No distance-field texture is produced or sampled: every fact the fragment
 * shader needs travels with the vertex. A vertex carries its outline-local
 * position plus {@code phi}, the signed distance to the outline (zero on the
 * contour, negative outside it) and a {@code kind} flag, so coverage is computed
 * analytically per fragment from {@code phi} and its screen-space derivative.</p>
 *
 * <h3>Why a geometric partition instead of raw offset bands</h3>
 * <p>Textbook SLUG accumulates a winding count in a stencil buffer and turns
 * that into coverage in a final pass. blaze3d exposes no stencil on the GUI
 * framebuffer and {@code GuiRenderer} replays each batch with a single blend
 * state, so signed contributions cannot add up — {@code ONE, ONE_MINUS_SRC_ALPHA}
 * only ever composites disjoint paints. The outline is therefore split into a
 * partition that never needs accumulation:</p>
 * <ul>
 *   <li><b>Solid pass</b> — the outline region eroded by the inner buffer,
 *       triangulated by {@link PolygonTriangulator}, written with {@code kind = 0}
 *       and full alpha.</li>
 *   <li><b>Inner band</b> — the buffer on the fill side out to half the band
 *       width, with {@code phi} running from {@code 0} on the contour up to that
 *       width.</li>
 *   <li><b>Outer band</b> — the same tiling on the outside out to
 *       {@code AA_MARGIN}, {@code phi} running down to {@code −AA_MARGIN}.</li>
 * </ul>
 * <p>Both bands are tiled by one slab per segment plus one radial wedge per
 * vertex. The three sets hand the plane over without fighting: the solid region
 * is what a boolean leaves after removing the inner buffer from the outline, so
 * it cannot overlap either band, and an inner triangle that would spill across a
 * hairline stroke into the counter beyond is split until it stops at the
 * contour.</p>
 * <p>Putting the ramp on both sides of the contour is the point. A one-sided
 * fringe has to reach full alpha <em>at</em> the contour, which thickens every
 * stem by half a device pixel on each side — enough to close a counter at UI
 * sizes — while an exterior-only soft edge leaves the outer half-weighted and
 * the two errors do not cancel.</p>
 *
 * <h3>The interpolated phi really is the distance</h3>
 * <p>Each slab is cut by the perpendiculars at the ends of its own segment, so
 * within a slab the nearest feature of the outline is that segment, and the
 * distance to it is the perpendicular distance to its supporting line — an
 * affine function that linear interpolation reproduces exactly, including across
 * the miter-free joins where two slabs meet on a shared perpendicular. Each
 * wedge spans the directions in which the vertex itself is the nearest feature,
 * where the distance is radial; its arc points stand at {@link #WEDGE_REACH}
 * times the buffer so its chords are tangent to it, and the fan's interpolation
 * overstates the distance by at most that much.</p>
 * <p>Two slabs still overlap where a notch is narrower than twice the band, so
 * each is clipped to its side of the equal-distance bisector. Overlaps with
 * features that are not adjacent — a hairline counter between two stems — cannot
 * be split that way, and there the later triangle wins: the edge reads up to half
 * a device pixel light.</p>
 *
 * <h3>Conventions</h3>
 * <p>All input geometry lives in the units of the supplied {@link Shape};
 * {@code outScale} maps it to the units the caller renders in, and it is applied
 * to distances as well as positions, so {@code AA_MARGIN} and the flatten
 * tolerance are expressed in <em>output</em> units too. Output is y-down, and
 * orientation is normalised so outer contours have negative shoelace area and
 * counters positive — the same convention the previous scanline-based
 * generator verified. Vertices are emitted relative to the top-left of the
 * outline box already grown by the anti-aliasing band, which keeps the whole
 * band inside {@code [0, boxWidth] × [0, boxHeight]}.</p>
 */
public final class SlugGeometry {

    /**
     * Half-width of the outer anti-aliased buffer, in output units. Must stay at
     * or above half a device pixel at the largest scale the text is drawn at;
     * below that the coverage ramp would be clipped by the buffer's edge.
     */
    public static final float AA_MARGIN = 1.0f;

    /**
     * Default curve-flattening precision, in output units: a contour never
     * deviates from its curves by more than this. Unlike a distance field, which
     * is sampled at a raster resolution and then stretched, this error does not
     * grow with zoom. {@link #build} takes it in input units, so callers scale it
     * by their own {@code outScale} reciprocal.
     */
    public static final double FLATTEN_TOLERANCE = 0.05;

    /** Interleaved floats per vertex: x, y, phi, kind. */
    public static final int FLOATS_PER_VERTEX = 4;

    /** Full-alpha fragment: the solid interior. */
    public static final float KIND_SOLID = 0f;

    /** Analytic-coverage fragment: {@code alpha = clamp(0.5 + phi / pxWidth)}. */
    public static final float KIND_BAND = 1f;

    /** Which side of the contour a buffer is built on. */
    private static final int OUTSIDE = 1;
    private static final int INSIDE = -1;

    /** Largest angular step of a convex vertex's radial fan, in radians. */
    private static final double MAX_WEDGE_STEP = Math.PI / 6;

    /**
     * How far outside the buffer a wedge's arc points stand, so that its chords
     * are tangent to the buffer instead of cutting inside it: a chord spanning
     * {@link #MAX_WEDGE_STEP} reaches only {@code cos(15°)} of its radius, and a
     * wedge that undershoots leaves a hairline of the corner unpainted. The
     * {@code phi} of those points is their own true distance, so the fan's
     * interpolation overstates it by at most the same factor — which the chord
     * error already allowed for.
     */
    static final double WEDGE_REACH = 1.0 / Math.cos(MAX_WEDGE_STEP / 2);

    /**
     * Levels of midpoint split an inner-buffer triangle may undergo to stop at
     * the contour. Each level halves the piece, so four take the inner buffer's
     * half unit down to 0.031 — under the {@link #FLATTEN_TOLERANCE} the contour
     * is already held to.
     */
    private static final int MAX_SPILL_SPLITS = 4;

    /** Recursion cap for curve flattening; 2^12 subdivisions per segment. */
    private static final int MAX_FLATTEN_DEPTH = 12;

    /**
     * Hard ceiling on the vertices one glyph may produce. Pathological
     * outlines (decorative or hand-drawn faces) are rejected instead of
     * uploading megabytes of triangles — the caller then falls back to
     * whatever it renders for a glyph with no geometry.
     */
    public static final int MAX_VERTICES_PER_GLYPH = 24_000;

    private SlugGeometry() {
    }

    /** Triangle soup plus the box it fills, in output units. */
    public static final class Result {

        static final Result EMPTY = new Result(new float[0], 0, 0f, 0f, 0f, 0f);

        /** {@code x, y, phi, kind} per vertex, three vertices per triangle. */
        public final float[] vertices;
        public final int vertexCount;
        /**
         * Outline box grown by the band on every side — by a few percent more at
         * a convex corner, where a wedge reaches {@link #WEDGE_REACH} times the
         * band so that its chords are tangent to it.
         */
        public final float boxWidth, boxHeight;
        /**
         * Top-left of that box in the coordinate space the outline was given
         * in, scaled to output units — the offset a pen position has to pick up
         * to place the geometry.
         */
        public final float originX, originY;

        Result(float[] vertices, int vertexCount, float boxWidth, float boxHeight,
               float originX, float originY) {
            this.vertices = vertices;
            this.vertexCount = vertexCount;
            this.boxWidth = boxWidth;
            this.boxHeight = boxHeight;
            this.originX = originX;
            this.originY = originY;
        }

        public boolean isEmpty() {
            return vertexCount == 0;
        }
    }

    /**
     * Build renderable geometry for {@code shape}.
     *
     * @param shape     closed outline, e.g. {@code GlyphVector.getOutline(0, 0)}
     * @param bandWidth half-width of the outer AA buffer in input units; the
     *                  inner buffer and the solid region's erosion take half
     * @param tolerance max curve deviation in input units
     * @param outScale  multiplier from input to output units
     */
    public static Result build(Shape shape, double bandWidth, double tolerance, double outScale) {
        if (shape == null) {
            return Result.EMPTY;
        }
        // A Shape's iterator flattens its own curves at the flatness it is given,
        // and defaults to 1.0 user unit — several times the band on a small glyph.
        List<Poly> rings = decompose(shape.getPathIterator(null, tolerance), tolerance);
        if (rings.isEmpty()) {
            return Result.EMPTY;
        }
        orient(rings);
        // The ramp has to saturate half a device pixel inside the contour, and a
        // device pixel is never worth more than one output unit at the GUI scales
        // this renders at, so half the outer buffer suffices. Wider would start
        // eating hairline stems whole.
        double innerWidth = bandWidth / 2;

        Pool pool = new Pool();
        try {
            emitBands(pool, rings, bandWidth, null, OUTSIDE);
            emitBands(pool, rings, innerWidth, new Fill(rings, innerWidth), INSIDE);
            emitSolid(pool, coreRings(rings, innerWidth, tolerance));
        } catch (TooManyVerticesException rejected) {
            return Result.EMPTY;
        }
        return pool.finish(outScale);
    }

    // ==================================================================
    //  Contour decomposition from AWT Shape
    // ==================================================================

    /** A closed polyline under construction, plus the open pen position. */
    private static final class Poly {
        final List<Double> xs = new ArrayList<>();
        final List<Double> ys = new ArrayList<>();

        int size() {
            return xs.size();
        }

        void add(double x, double y) {
            int n = xs.size();
            if (n > 0 && near(xs.get(n - 1), ys.get(n - 1), x, y)) {
                return; // duplicate control point — a zero-length segment has no normal
            }
            xs.add(x);
            ys.add(y);
        }

        double lastX() {
            return xs.isEmpty() ? 0 : xs.get(xs.size() - 1);
        }

        double lastY() {
            return ys.isEmpty() ? 0 : ys.get(ys.size() - 1);
        }

        void removeLast() {
            int at = xs.size() - 1;
            xs.remove(at);
            ys.remove(at);
        }

        double[] xArray() {
            return toArray(xs);
        }

        double[] yArray() {
            return toArray(ys);
        }

        double area() {
            double sum = 0;
            int n = xs.size();
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                sum += xs.get(i) * ys.get(j) - xs.get(j) * ys.get(i);
            }
            return sum * 0.5;
        }

        private static double[] toArray(List<Double> values) {
            double[] out = new double[values.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = values.get(i);
            }
            return out;
        }
    }

    private static boolean near(double ax, double ay, double bx, double by) {
        return Math.abs(ax - bx) < 1e-9 && Math.abs(ay - by) < 1e-9;
    }

    private static List<Poly> decompose(PathIterator it, double tolerance) {
        List<Poly> rings = new ArrayList<>();
        double[] c = new double[6];
        Poly current = null;
        while (!it.isDone()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO:
                    current = close(rings, current);
                    current = new Poly();
                    current.add(c[0], c[1]);
                    break;
                case PathIterator.SEG_LINETO:
                    if (current != null) {
                        current.add(c[0], c[1]);
                    }
                    break;
                case PathIterator.SEG_QUADTO:
                    if (current != null) {
                        quadTo(current, c[0], c[1], c[2], c[3], tolerance, 0);
                    }
                    break;
                case PathIterator.SEG_CUBICTO:
                    if (current != null) {
                        cubicTo(current, c[0], c[1], c[2], c[3], c[4], c[5], tolerance, 0);
                    }
                    break;
                case PathIterator.SEG_CLOSE:
                    current = close(rings, current);
                    break;
                default:
                    break;
            }
            it.next();
        }
        close(rings, current);
        return rings;
    }

    private static Poly close(List<Poly> rings, Poly current) {
        if (current != null && current.size() >= 3) {
            // Drop a trailing point that landed on the start; the ring is closed
            // implicitly by wrapping, and a duplicated vertex would be degenerate.
            if (near(current.lastX(), current.lastY(), current.xs.get(0), current.ys.get(0))) {
                current.removeLast();
            }
            if (current.size() >= 3) {
                rings.add(current);
            }
        }
        return null;
    }

    private static void quadTo(Poly p, double cx, double cy, double x, double y,
                               double tolerance, int depth) {
        double x0 = p.lastX();
        double y0 = p.lastY();
        if (depth >= MAX_FLATTEN_DEPTH || deviation(x0, y0, cx, cy, x, y) <= 2 * tolerance) {
            // A quadratic's bow is exactly half its control point's offset from
            // the chord, so 2·tolerance on the control point bounds it by tolerance.
            p.add(x, y);
            return;
        }
        double ax = (x0 + cx) * 0.5, ay = (y0 + cy) * 0.5;
        double bx = (cx + x) * 0.5, by = (cy + y) * 0.5;
        double mx = (ax + bx) * 0.5, my = (ay + by) * 0.5;
        quadTo(p, ax, ay, mx, my, tolerance, depth + 1);
        quadTo(p, bx, by, x, y, tolerance, depth + 1);
    }

    private static void cubicTo(Poly p, double c1x, double c1y, double c2x, double c2y,
                                double x, double y, double tolerance, int depth) {
        double x0 = p.lastX();
        double y0 = p.lastY();
        if (depth >= MAX_FLATTEN_DEPTH
                || Math.max(deviation(x0, y0, c1x, c1y, x, y),
                        deviation(x0, y0, c2x, c2y, x, y)) <= 4 * tolerance / 3) {
            // A cubic bows no more than three quarters of its worst control
            // point offset, so 4/3·tolerance bounds it by tolerance.
            p.add(x, y);
            return;
        }
        double ax = (x0 + c1x) * 0.5, ay = (y0 + c1y) * 0.5;
        double bx = (c1x + c2x) * 0.5, by = (c1y + c2y) * 0.5;
        double cx = (c2x + x) * 0.5, cy = (c2y + y) * 0.5;
        double abx = (ax + bx) * 0.5, aby = (ay + by) * 0.5;
        double bcx = (bx + cx) * 0.5, bcy = (by + cy) * 0.5;
        double mx = (abx + bcx) * 0.5, my = (aby + bcy) * 0.5;
        cubicTo(p, ax, ay, abx, aby, mx, my, tolerance, depth + 1);
        cubicTo(p, bcx, bcy, cx, cy, x, y, tolerance, depth + 1);
    }

    /** Perpendicular distance of a control point from the segment's chord. */
    private static double deviation(double x0, double y0, double px, double py,
                                    double x1, double y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-12) {
            return Math.hypot(px - x0, py - y0);
        }
        return Math.abs((px - x0) * dy - (py - y0) * dx) / length;
    }

    // ==================================================================
    //  Orientation (fill = nonzero winding, outers negative in y-down space)
    // ==================================================================

    /**
     * Normalise the ring set to the convention the rest of the pipeline reads:
     * outer contours negative, counters positive.
     *
     * <p>Contour direction is what carries the fill in a TrueType outline. A
     * counter winds against its container, but a same-direction ring that
     * overlaps or touches another one is fill as well — the detached arm of
     * {@code E}, a crossbar half of {@code A}, every ring of {@code K}. Only a
     * global flip can therefore be legitimate. Deciding ring by ring from
     * nesting parity instead reads such a ring as a hole whenever its sample
     * point happens to fall inside another fill ring, and the damage is total
     * at UI point sizes: the mis-oriented ring then fails the inner buffer's
     * fill test, so every triangle covering that stroke is dropped as a spill
     * and the arm simply disappears.</p>
     *
     * <p>The widest ring is always an outer — a counter cannot exceed its
     * container — so its own direction decides which way the set goes.</p>
     */
    private static void orient(List<Poly> rings) {
        double widest = -1;
        double widestArea = 0;
        for (Poly ring : rings) {
            double area = shoelace(ring.xArray(), ring.yArray());
            if (Math.abs(area) > widest) {
                widest = Math.abs(area);
                widestArea = area;
            }
        }
        if (widestArea > 0) {
            for (Poly ring : rings) {
                reverse(ring);
            }
        }
    }

    private static void reverse(Poly ring) {
        int n = ring.size();
        for (int i = 0, j = n - 1; i < j; i++, j--) {
            Double tx = ring.xs.get(i);
            ring.xs.set(i, ring.xs.get(j));
            ring.xs.set(j, tx);
            Double ty = ring.ys.get(i);
            ring.ys.set(i, ring.ys.get(j));
            ring.ys.set(j, ty);
        }
    }

    private static double shoelace(double[] x, double[] y) {
        double sum = 0;
        int n = x.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            sum += x[i] * y[j] - x[j] * y[i];
        }
        return sum * 0.5;
    }

    private static int windingNumber(double[] x, double[] y, double px, double py) {
        int wn = 0;
        int n = x.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double ax = x[i];
            double ay = y[i];
            double cross = (x[j] - ax) * (py - ay) - (y[j] - ay) * (px - ax);
            if (ay <= py) {
                if (y[j] > py && cross > 0) wn++;
            } else {
                if (y[j] <= py && cross < 0) wn--;
            }
        }
        return wn;
    }

    // ==================================================================
    //  Band passes: the two buffers, one exact distance per vertex
    // ==================================================================

    /**
     * Covers every point on one side of the outline that lies within
     * {@code band} of it, once each, with {@code phi} set to the signed distance
     * to the contour — down to {@code -band} for {@link #OUTSIDE}, up to
     * {@code +band} for {@link #INSIDE}.
     *
     * <p>The buffer is decomposed the way a polygon's distance field is. Each
     * segment owns the slab over its own span, cut by the perpendiculars at its
     * ends, so a segment can never reach past its own end into a concave notch;
     * each vertex whose pair of slabs leaves a gap owns the wedge between those
     * perpendiculars, which is exactly the region where the vertex is the
     * nearest feature. Inside a slab the distance is the perpendicular distance
     * to the segment's line, which is affine, so interpolating {@code phi}
     * across the triangle reproduces it without a correction term; inside a
     * wedge the distance is radial, and the fan's chord error is bounded by
     * {@code band·(1/cos(15°) − 1)}.</p>
     *
     * <p>Neighbouring slabs meet on the shared perpendicular and assign the same
     * {@code phi} there, so the strip is gap-free without a miter and without
     * clamping. Two slabs still overlap where the join turns toward the buffer,
     * which is a reflex corner outside and a convex one inside; each is then
     * clipped to its own side of the bisector, the locus where the two distances
     * agree, which splits the overlap exactly rather than letting the later
     * triangle win.</p>
     *
     * <p>The inner buffer is the one that can go wrong. Across a stroke thinner
     * than the buffer a slab reaches clean out of the outline, and there it
     * would darken the counter it spilled into; {@code fill} is what
     * {@link #emitBufferTriangle} splits such triangles against. The outer
     * buffer needs no such test, since nothing on its side is worth less than
     * what it paints.</p>
     */
    private static void emitBands(Pool pool, List<Poly> rings, double band, Fill fill, int side) {
        for (Poly ring : rings) {
            int n = ring.size();
            if (n < 3) {
                continue;
            }
            double[] px = ring.xArray();
            double[] py = ring.yArray();
            double[] tx = new double[n];
            double[] ty = new double[n];
            double[] ex = new double[n];
            double[] ey = new double[n];
            // After orient(), outers wind negative and counters positive, which
            // puts the fill on the (dy, -dx) side of every segment — including a
            // counter's, whose fill lies outside the region it encloses. So the
            // exterior side is always (-dy, dx), whichever ring a segment is in.
            boolean complete = true;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double sx = px[j] - px[i];
                double sy = py[j] - py[i];
                double length = Math.hypot(sx, sy);
                if (length < 1e-9) {
                    complete = false;
                    break;
                }
                tx[i] = sx / length;
                ty[i] = sy / length;
                ex[i] = -ty[i];
                ey[i] = tx[i];
            }
            if (!complete) {
                continue;
            }
            for (int i = 0; i < n; i++) {
                emitSlab(pool, fill, px, py, tx, ty, ex, ey, band, i, n, side);
            }
            for (int i = 0; i < n; i++) {
                emitWedge(pool, fill, px, py, tx, ty, ex, ey, band, i, n, side);
            }
        }
    }

    /** The slab over segment {@code i}'s own span, within {@code band} of it. */
    private static void emitSlab(Pool pool, Fill fill, double[] px, double[] py,
                                 double[] tx, double[] ty, double[] ex, double[] ey,
                                 double band, int i, int n, int side) {
        int j = (i + 1) % n;
        int prev = (i + n - 1) % n;
        double ox = side * ex[i], oy = side * ey[i];
        List<double[]> cell = new ArrayList<>(6);
        cell.add(new double[] {px[i], py[i]});
        cell.add(new double[] {px[j], py[j]});
        cell.add(new double[] {px[j] + band * ox, py[j] + band * oy});
        cell.add(new double[] {px[i] + band * ox, py[i] + band * oy});
        // Where two neighbouring slabs overlap, the equal-distance locus is the
        // bisector of their buffer normals, and this slab keeps its own side.
        if (overlaps(tx[prev], ty[prev], tx[i], ty[i], side)) {
            cell = clip(cell, px[i], py[i], ox - side * ex[prev], oy - side * ey[prev]);
        }
        if (overlaps(tx[i], ty[i], tx[j], ty[j], side)) {
            cell = clip(cell, px[j], py[j], ox - side * ex[j], oy - side * ey[j]);
        }
        for (int k = 1; k + 1 < cell.size(); k++) {
            double[] a = cell.get(0);
            double[] b = cell.get(k);
            double[] c = cell.get(k + 1);
            // phi keeps the exterior normal whatever side this buffer is on: it
            // returns minus the signed distance, so the outer band comes out
            // negative and the inner one positive from the same expression.
            emitBufferTriangle(pool, fill, MAX_SPILL_SPLITS,
                    a[0], a[1], phi(a[0], a[1], px[i], py[i], ex[i], ey[i]),
                    b[0], b[1], phi(b[0], b[1], px[i], py[i], ex[i], ey[i]),
                    c[0], c[1], phi(c[0], c[1], px[i], py[i], ex[i], ey[i]));
        }
    }

    /** Radial fan over the wedge of a vertex, where the vertex is nearest. */
    private static void emitWedge(Pool pool, Fill fill, double[] px, double[] py,
                                  double[] tx, double[] ty, double[] ex, double[] ey,
                                  double band, int i, int n, int side) {
        int prev = (i + n - 1) % n;
        if (overlaps(tx[prev], ty[prev], tx[i], ty[i], side)) {
            return; // the clipped slabs already cover this corner's buffer
        }
        double from = Math.atan2(side * ey[prev], side * ex[prev]);
        double to = Math.atan2(side * ey[i], side * ex[i]);
        double sweep = to - from;
        while (sweep > Math.PI) sweep -= 2 * Math.PI;
        while (sweep <= -Math.PI) sweep += 2 * Math.PI;
        if (sweep * side >= -1e-9) {
            return; // straight, or a turn whose wedge the neighbouring slabs reach
        }
        int steps = (int) Math.ceil(Math.abs(sweep) / MAX_WEDGE_STEP);
        double step = sweep / steps;
        double reach = band * WEDGE_REACH;
        double vx = px[i], vy = py[i];
        for (int k = 0; k < steps; k++) {
            double a0 = from + k * step;
            double a1 = a0 + step;
            double bx = vx + reach * Math.cos(a0), by = vy + reach * Math.sin(a0);
            double cx = vx + reach * Math.cos(a1), cy = vy + reach * Math.sin(a1);
            emitBufferTriangle(pool, fill, MAX_SPILL_SPLITS,
                    vx, vy, 0, bx, by, -side * reach, cx, cy, -side * reach);
        }
    }

    /**
     * Emits one buffer triangle.
     *
     * <p>The outer buffer lies wholly on the outside and goes straight through.
     * The inner one can cross a stem thinner than itself and paint the counter
     * on the far side, so it splits toward the boundary until a piece is wholly
     * inside the fill. Four levels take the inner buffer's half unit down to
     * 0.031, below the {@link #FLATTEN_TOLERANCE} the contour is already held
     * to, so the split is what the pipeline promises rather than what it
     * guesses. A slab's distance is affine, so averaging the parent's {@code phi}
     * across a split stays exact, and a wedge fan's chord error only shrinks
     * with the piece.</p>
     */
    private static void emitBufferTriangle(Pool pool, Fill fill, int depth,
                                           double ax, double ay, double aphi,
                                           double bx, double by, double bphi,
                                           double cx, double cy, double cphi) {
        if (fill == null || fill.covers(ax, ay, bx, by, cx, cy)) {
            pool.triangle(ax, ay, aphi, KIND_BAND, bx, by, bphi, KIND_BAND, cx, cy, cphi, KIND_BAND);
            return;
        }
        if (depth == 0) {
            return;
        }
        double abx = (ax + bx) / 2, aby = (ay + by) / 2, abPhi = (aphi + bphi) / 2;
        double bcx = (bx + cx) / 2, bcy = (by + cy) / 2, bcPhi = (bphi + cphi) / 2;
        double acx = (ax + cx) / 2, acy = (ay + cy) / 2, acPhi = (aphi + cphi) / 2;
        emitBufferTriangle(pool, fill, depth - 1, ax, ay, aphi, abx, aby, abPhi, acx, acy, acPhi);
        emitBufferTriangle(pool, fill, depth - 1, abx, aby, abPhi, bx, by, bphi, bcx, bcy, bcPhi);
        emitBufferTriangle(pool, fill, depth - 1, acx, acy, acPhi, bcx, bcy, bcPhi, cx, cy, cphi);
        emitBufferTriangle(pool, fill, depth - 1, abx, aby, abPhi, bcx, bcy, bcPhi, acx, acy, acPhi);
    }

    /**
     * Whether the slabs meeting at a vertex overlap on {@code side}. A reflex
     * join is exactly the case that overlaps outside the outline, and the turns
     * swap when the buffer moves to the fill side.
     */
    private static boolean overlaps(double ax, double ay, double bx, double by, int side) {
        return side > 0 ? isReflex(ax, ay, bx, by) : !isReflex(ax, ay, bx, by);
    }

    /** Signed turn of two consecutive unit tangents; positive is a concave notch. */
    private static boolean isReflex(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx > 0;
    }

    /** Signed distance to the outline: the perpendicular distance outside it. */
    private static double phi(double x, double y, double vx, double vy, double nx, double ny) {
        return -((x - vx) * nx + (y - vy) * ny);
    }

    /** Sutherland-Hodgman against the line through {@code v} with the given normal. */
    private static List<double[]> clip(List<double[]> polygon,
                                       double vx, double vy, double nx, double ny) {
        List<double[]> out = new ArrayList<>(polygon.size() + 2);
        int m = polygon.size();
        for (int i = 0; i < m; i++) {
            double[] cur = polygon.get(i);
            double[] next = polygon.get((i + 1) % m);
            double cd = (cur[0] - vx) * nx + (cur[1] - vy) * ny;
            double nd = (next[0] - vx) * nx + (next[1] - vy) * ny;
            if (cd <= 0) {
                out.add(cur);
            }
            if ((cd < 0 && nd > 0) || (cd > 0 && nd < 0)) {
                double t = cd / (cd - nd);
                out.add(new double[] {cur[0] + t * (next[0] - cur[0]),
                        cur[1] + t * (next[1] - cur[1])});
            }
        }
        return out;
    }

    // ==================================================================
    //  Solid pass: the outline region the inner buffer leaves over
    // ==================================================================

    /**
     * The part of the outline farther than {@code inner} from the contour.
     *
     * <p>Stroking the contour at twice the buffer width gives exactly the points
     * within that width of it, round joins and all, so subtracting the stroke
     * from the region is the one way to erode a shape whose stems are thinner
     * than the buffer — where an inward offset would fold over itself. AWT already
     * carries a robust polygon boolean for its own clip engine, and what comes
     * back is a polyline region the triangulator takes as-is.</p>
     */
    private static List<Poly> coreRings(List<Poly> rings, double inner, double tolerance) {
        if (inner <= 0) {
            return rings;
        }
        Path2D.Double contour = toPath(rings);
        Shape buffer = new BasicStroke((float) (2 * inner), BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND).createStrokedShape(contour);
        // Flattened first: a polyline-against-polyline boolean at the tolerance
        // the rest of the pipeline already promises, rather than at one Area
        // picks for itself. On the sample face it halved the worst glyph.
        List<Poly> bufferRings = decompose(buffer.getPathIterator(null, tolerance), tolerance);
        Area core = new Area(contour);
        core.subtract(new Area(toPath(bufferRings)));
        List<Poly> eroded = decompose(core.getPathIterator(null, tolerance), tolerance);
        orient(eroded);
        return eroded;
    }

    /** The rings as one nonzero-winding path of straight segments. */
    private static Path2D.Double toPath(List<Poly> rings) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        for (Poly ring : rings) {
            double[] x = ring.xArray();
            double[] y = ring.yArray();
            if (x.length < 3) {
                continue;
            }
            path.moveTo(x[0], y[0]);
            for (int i = 1; i < x.length; i++) {
                path.lineTo(x[i], y[i]);
            }
            path.closePath();
        }
        return path;
    }

    /**
     * What of the outline a buffer triangle may be painted over.
     *
     * <p>Membership is a nonzero-winding count over the rings, exact under the
     * orientation {@link #orient} produces, with the bounding box settling the
     * common case of a point that has clearly left the glyph without a single
     * crossing.</p>
     *
     * <p>A point alone cannot clear a triangle, though: one whose centroid sits a
     * hair inside a thin stroke can still throw its far corner out through the
     * other side. {@link #covers} therefore also asks whether any of the three
     * edges crosses the contour, which for a triangle small enough to be a
     * buffer's cell is the only way it can leave the fill. Those queries are
     * answered from a uniform grid of the outline's own segments, so a triangle
     * is tested against the strokes it is near rather than the whole glyph.</p>
     */
    private static final class Fill {
        private final double[][] xs;
        private final double[][] ys;
        private double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        private double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        /** Outline segments, bucketed by the cells their bounding box touches. */
        private final double cellSize;
        private final int cols, rows;
        private final int[] starts;
        private final int[] bucket;
        private double[] ex1, ey1, ex2, ey2;
        private int edges;

        Fill(List<Poly> rings, double cell) {
            int n = rings.size();
            xs = new double[n][];
            ys = new double[n][];
            for (int i = 0; i < n; i++) {
                xs[i] = rings.get(i).xArray();
                ys[i] = rings.get(i).yArray();
                for (int k = 0; k < xs[i].length; k++) {
                    if (xs[i][k] < minX) minX = xs[i][k];
                    if (xs[i][k] > maxX) maxX = xs[i][k];
                    if (ys[i][k] < minY) minY = ys[i][k];
                    if (ys[i][k] > maxY) maxY = ys[i][k];
                }
            }
            cellSize = Math.max(cell, 1e-3);
            cols = cells(minX, maxX);
            rows = cells(minY, maxY);
            int edgeTotal = 0;
            for (int i = 0; i < n; i++) {
                edgeTotal += xs[i].length;
            }
            ex1 = new double[edgeTotal];
            ey1 = new double[edgeTotal];
            ex2 = new double[edgeTotal];
            ey2 = new double[edgeTotal];
            starts = new int[cols * rows + 1];
            // One cell of slack on every side, so a triangle just outside the
            // outline still finds the segments it is about to cross.
            for (int i = 0; i < n; i++) {
                int len = xs[i].length;
                for (int k = 0; k < len; k++) {
                    int j = (k + 1) % len;
                    int slot = edges++;
                    ex1[slot] = xs[i][k];
                    ey1[slot] = ys[i][k];
                    ex2[slot] = xs[i][j];
                    ey2[slot] = ys[i][j];
                    for (int at = cellXSpanStart(xs[i][k], xs[i][j]);
                         at <= cellXSpanEnd(xs[i][k], xs[i][j]); at++) {
                        for (int r = cellYSpanStart(ys[i][k], ys[i][j]);
                             r <= cellYSpanEnd(ys[i][k], ys[i][j]); r++) {
                            starts[r * cols + at + 1]++;
                        }
                    }
                }
            }
            for (int i = 0; i < starts.length - 1; i++) {
                starts[i + 1] += starts[i];
            }
            bucket = new int[starts[starts.length - 1]];
            int[] cursor = java.util.Arrays.copyOf(starts, starts.length);
            for (int s = 0; s < edges; s++) {
                for (int c = cellXSpanStart(ex1[s], ex2[s]); c <= cellXSpanEnd(ex1[s], ex2[s]); c++) {
                    for (int r = cellYSpanStart(ey1[s], ey2[s]); r <= cellYSpanEnd(ey1[s], ey2[s]); r++) {
                        bucket[cursor[r * cols + c]++] = s;
                    }
                }
            }
        }

        private int cells(double lo, double hi) {
            return Math.max(1, (int) Math.ceil((hi - lo) / cellSize)) + 2;
        }

        private int cellOfX(double x) {
            return clamp((int) Math.floor((x - minX) / cellSize) + 1, cols);
        }

        private int cellOfY(double y) {
            return clamp((int) Math.floor((y - minY) / cellSize) + 1, rows);
        }

        private int cellXSpanStart(double a, double b) {
            return Math.min(cellOfX(a), cellOfX(b));
        }

        private int cellXSpanEnd(double a, double b) {
            return Math.max(cellOfX(a), cellOfX(b));
        }

        private int cellYSpanStart(double a, double b) {
            return Math.min(cellOfY(a), cellOfY(b));
        }

        private int cellYSpanEnd(double a, double b) {
            return Math.max(cellOfY(a), cellOfY(b));
        }

        private static int clamp(int cell, int count) {
            return cell < 0 ? 0 : Math.min(cell, count - 1);
        }

        boolean contains(double x, double y) {
            if (x < minX || x > maxX || y < minY || y > maxY) {
                return false;
            }
            int winding = 0;
            for (int i = 0; i < xs.length; i++) {
                winding += windingNumber(xs[i], ys[i], x, y);
            }
            return winding != 0;
        }

        /** Whether a triangle is wholly inside the outline. */
        boolean covers(double ax, double ay, double bx, double by, double cx, double cy) {
            return contains((ax + bx + cx) / 3, (ay + by + cy) / 3)
                    && !crosses(ax, ay, bx, by)
                    && !crosses(bx, by, cx, cy)
                    && !crosses(cx, cy, ax, ay);
        }

        /** Whether the segment steps over any segment of the contour. */
        private boolean crosses(double ax, double ay, double bx, double by) {
            for (int i = cellXSpanStart(ax, bx); i <= cellXSpanEnd(ax, bx); i++) {
                for (int j = cellYSpanStart(ay, by); j <= cellYSpanEnd(ay, by); j++) {
                    int at = j * cols + i;
                    for (int k = starts[at]; k < starts[at + 1]; k++) {
                        int e = bucket[k];
                        double q0 = side(ex1[e], ey1[e], ex2[e], ey2[e], ax, ay);
                        double q1 = side(ex1[e], ey1[e], ex2[e], ey2[e], bx, by);
                        if (Math.signum(q0) * Math.signum(q1) >= 0) {
                            continue;
                        }
                        double p0 = side(ax, ay, bx, by, ex1[e], ey1[e]);
                        double p1 = side(ax, ay, bx, by, ex2[e], ey2[e]);
                        if (Math.signum(p0) * Math.signum(p1) < 0) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /** Which side of {@code a→b} the point lies on. */
        private static double side(double ax, double ay, double bx, double by,
                                   double px, double py) {
            return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
        }
    }

    private static void emitSolid(Pool pool, List<Poly> rings) {
        List<PolygonTriangulator.Ring> input = new ArrayList<>(rings.size());
        for (Poly ring : rings) {
            double[] x = ring.xArray();
            double[] y = ring.yArray();
            input.add(new PolygonTriangulator.Ring(x, y, shoelace(x, y) > 0));
        }
        PolygonTriangulator.Result triangulated = PolygonTriangulator.triangulate(input);
        int[] triangles = triangulated.triangles;
        for (int t = 0; t + 2 < triangles.length; t += 3) {
            pool.triangle(triangulated.x[triangles[t]], triangulated.y[triangles[t]], 0, KIND_SOLID,
                    triangulated.x[triangles[t + 1]], triangulated.y[triangles[t + 1]], 0, KIND_SOLID,
                    triangulated.x[triangles[t + 2]], triangulated.y[triangles[t + 2]], 0, KIND_SOLID);
        }
    }

    // ==================================================================
    //  Vertex pool
    // ==================================================================

    /** Growable interleaved vertex storage that also tracks the geometry box. */
    private static final class Pool {
        private float[] data = new float[1024];
        private int vertices;
        private double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        private double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        void triangle(double ax, double ay, double aphi, float akind,
                      double bx, double by, double bphi, float bkind,
                      double cx, double cy, double cphi, float ckind) {
            vertex(ax, ay, aphi, akind);
            vertex(bx, by, bphi, bkind);
            vertex(cx, cy, cphi, ckind);
        }

        private void vertex(double x, double y, double phi, float kind) {
            if (vertices == data.length / SlugGeometry.FLOATS_PER_VERTEX) {
                data = java.util.Arrays.copyOf(data, data.length * 2);
            }
            int at = vertices * SlugGeometry.FLOATS_PER_VERTEX;
            data[at] = (float) x;
            data[at + 1] = (float) y;
            data[at + 2] = (float) phi;
            data[at + 3] = kind;
            vertices++;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (vertices > SlugGeometry.MAX_VERTICES_PER_GLYPH) {
                throw new TooManyVerticesException();
            }
        }

        Result finish(double outScale) {
            if (vertices == 0) {
                return Result.EMPTY;
            }
            for (int i = 0; i < vertices; i++) {
                int at = i * FLOATS_PER_VERTEX;
                data[at] = (float) ((data[at] - minX) * outScale);
                data[at + 1] = (float) ((data[at + 1] - minY) * outScale);
                data[at + 2] = (float) (data[at + 2] * outScale);
            }
            return new Result(java.util.Arrays.copyOf(data, vertices * FLOATS_PER_VERTEX),
                    vertices,
                    (float) ((maxX - minX) * outScale),
                    (float) ((maxY - minY) * outScale),
                    (float) (minX * outScale),
                    (float) (minY * outScale));
        }
    }

    /** Raised instead of letting a pathological outline consume unbounded memory. */
    private static final class TooManyVerticesException extends RuntimeException {
        private TooManyVerticesException() {
            super(null, null, false, false);
        }
    }
}
