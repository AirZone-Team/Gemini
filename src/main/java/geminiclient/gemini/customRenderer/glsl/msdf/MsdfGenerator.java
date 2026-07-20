package geminiclient.gemini.customRenderer.glsl.msdf;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-channel signed distance field (MSDF) generator.
 *
 * <p>A Java port based on the classic msdfgen pipeline (Viktor Chlumský,
 * https://github.com/Chlumsky/msdfgen — v1.12 "legacy" mode plus sign
 * correction):</p>
 *
 * <ol>
 *   <li>Decompose the glyph outline into edge segments
 *       (linear / quadratic / cubic Bézier).</li>
 *   <li>Fix contour orientation (in y-down screen space: outer contours get
 *       negative shoelace area, holes positive).</li>
 *   <li>Color edges with the {@code edgeColoringSimple} heuristic
 *       (angle threshold 3.0 rad, seed 0).</li>
 *   <li>For every atlas pixel and every color channel, find the minimum
 *       signed pseudo-distance over the edges carrying that channel bit.</li>
 *   <li>Resolve the inside/outside sign with the nonzero winding rule while
 *       retaining the independently selected pseudo-distance magnitude of
 *       each edge-color channel.</li>
 *   <li>Legacy clash-detection error correction: equalize texels whose
 *       channel ordering could flip the median under bilinear interpolation.</li>
 *   <li>Track the true signed distance (minimum over <b>all</b> edges,
 *       ignoring color) and store it in the alpha channel — the MTSDF layout
 *       of msdf-atlas-gen. The multi-channel field saturates beyond
 *       RANGE/2 px from the contour; the true SDF keeps a usable gradient
 *       there for soft effects.</li>
 * </ol>
 *
 * <p>The shader reconstructs hard glyph coverage as
 * {@code median(R, G, B)}. Alpha remains available for effects that need a
 * true distance rather than a corner-preserving pseudo-distance.</p>
 *
 * <p>Output encoding per channel: {@code 0.5 + distance / RANGE}, i.e.
 * 128 = exactly on the edge, 255 = RANGE/2 px inside, 0 = RANGE/2 px outside.
 * {@link #RANGE} is the single source of truth for the field range: it is
 * handed to the fragment shader <b>inside the atlas itself</b> (metadata
 * texel (0,0), see {@code CustomFontRenderer}) — no shader constant duplicates
 * it, so the two sides can never drift apart.</p>
 */
public final class MsdfGenerator {

    /**
     * Distance range in atlas pixels. Single source of truth — the fragment
     * shader reads it back from atlas texel (0,0) via {@code texelFetch},
     * encoded as an unsigned 16-bit fixed-point value in the R/G channels.
     */
    public static final double RANGE = 24.0;

    /**
     * Encoding scale of the atlas metadata texel: texel (0,0) stores
     * {@code round(RANGE * METADATA_RANGE_SCALE)} as an unsigned 16-bit
     * fixed-point value split across R (low byte) and G (high byte).
     * 256x gives 1/256 px resolution for ranges up to almost 256 px.
     */
    public static final double METADATA_RANGE_SCALE = 256.0;

    /** Corner angle threshold in radians (msdfgen default). */
    private static final double ANGLE_THRESHOLD = 3.0;
    /** Polyline subdivisions per curve — used only for orientation tests. */
    private static final int FLATTEN_DIVISIONS = 16;
    /** Newton iteration settings for cubic distance (msdfgen defaults). */
    private static final int CUBIC_SEARCH_STARTS = 4;
    private static final int CUBIC_SEARCH_STEPS = 4;

    /** msdfgen SignedDistance default (acts as "infinitely far outside"). */
    private static final double INF_DISTANCE = -1e240;

    // Edge color bit flags
    private static final int RED = 1;
    private static final int GREEN = 2;
    private static final int BLUE = 4;
    private static final int YELLOW = 3;
    private static final int MAGENTA = 5;
    private static final int CYAN = 6;
    private static final int WHITE = 7;

    private MsdfGenerator() {}

    // ==================================================================
    //  Public API
    // ==================================================================

    /**
     * Generate an MTSDF for a glyph outline.
     *
     * @param outline glyph outline already translated into atlas-cell pixel
     *                coordinates (e.g. via {@code GlyphVector.getOutline(x, y)})
     * @param width   cell width in pixels (including padding)
     * @param height  cell height in pixels (including padding)
     * @return interleaved RGBA bytes, 4 per pixel, row-major. R/G/B hold the
     *         multi-channel field (shader: median), A the true SDF.
     */
    public static byte[] generate(Shape outline, int width, int height) {
        List<Contour> contours = decompose(outline);
        orientContours(contours);
        edgeColoringSimple(contours, ANGLE_THRESHOLD, 0L);

        List<Edge> allEdges = new ArrayList<>();
        for (Contour contour : contours) {
            allEdges.addAll(contour.edges);
        }

        // Flattened polylines for the winding-number sign resolution.
        // (Same flattening as used by orientContours.)
        List<List<V>> polys = new ArrayList<>(contours.size());
        for (Contour contour : contours) {
            polys.add(flatten(contour));
        }

        float[] field = new float[width * height * 4];
        double[] param = new double[1];
        for (int y = 0; y < height; y++) {
            // Scanline crossings at this pixel row for winding evaluation.
            double py = y + 0.5;
            float[] crossX = new float[64];
            int[] crossDir = new int[64];
            int crossCount = 0;
            for (List<V> poly : polys) {
                int n = poly.size();
                for (int i = 0; i < n; i++) {
                    V a = poly.get(i);
                    V b = poly.get((i + 1) % n);
                    if ((a.y <= py) != (b.y <= py)) {
                        double t = (py - a.y) / (b.y - a.y);
                        if (crossCount == crossX.length) {
                            crossX = java.util.Arrays.copyOf(crossX, crossX.length * 2);
                            crossDir = java.util.Arrays.copyOf(crossDir, crossDir.length * 2);
                        }
                        crossX[crossCount] = (float) (a.x + t * (b.x - a.x));
                        crossDir[crossCount] = b.y > a.y ? 1 : -1;
                        crossCount++;
                    }
                }
            }
            // Insertion sort by x (crossing counts are small)
            for (int i = 1; i < crossCount; i++) {
                float cx = crossX[i];
                int cd = crossDir[i];
                int j = i - 1;
                while (j >= 0 && crossX[j] > cx) {
                    crossX[j + 1] = crossX[j];
                    crossDir[j + 1] = crossDir[j];
                    j--;
                }
                crossX[j + 1] = cx;
                crossDir[j + 1] = cd;
            }

            int winding = 0;
            int crossIdx = 0;
            for (int x = 0; x < width; x++) {
                double px = x + 0.5;
                while (crossIdx < crossCount && crossX[crossIdx] < px) {
                    winding += crossDir[crossIdx];
                    crossIdx++;
                }
                double sign = winding != 0 ? 1 : -1;

                V p = new V(px, py);

                SignedDistance r = new SignedDistance();
                SignedDistance g = new SignedDistance();
                SignedDistance b = new SignedDistance();
                SignedDistance t = new SignedDistance();
                Edge rEdge = null, gEdge = null, bEdge = null;
                double rParam = 0, gParam = 0, bParam = 0;

                for (Edge edge : allEdges) {
                    // Cheap lower bound via control-polygon AABB: if it already
                    // exceeds every channel's best distance, skip the exact solve.
                    double bound = edge.boundDistance(p);
                    boolean maybeR = (edge.color & RED) != 0 && bound <= Math.abs(r.distance);
                    boolean maybeG = (edge.color & GREEN) != 0 && bound <= Math.abs(g.distance);
                    boolean maybeB = (edge.color & BLUE) != 0 && bound <= Math.abs(b.distance);
                    boolean maybeT = bound <= Math.abs(t.distance);
                    if (!maybeR && !maybeG && !maybeB && !maybeT) {
                        continue;
                    }
                    SignedDistance d = edge.signedDistance(p, param);
                    if (maybeR && d.lessThan(r)) { r = d; rEdge = edge; rParam = param[0]; }
                    if (maybeG && d.lessThan(g)) { g = d; gEdge = edge; gParam = param[0]; }
                    if (maybeB && d.lessThan(b)) { b = d; bEdge = edge; bParam = param[0]; }
                    if (maybeT && d.lessThan(t)) { t = d; }
                }

                // Convert the three colored edge distances to pseudo-distance
                // near segment endpoints so the RGB field stays continuous.
                // Gated to the near field: the conversion extends the edge's
                // perpendicular "shadow" infinitely past its endpoints, which
                // produces faint band artifacts radiating across the cell once
                // the nearest endpoint is farther away than the field can
                // encode anyway (|distance| > RANGE/2 already clamps to 0/1,
                // so skipping the conversion there only removes the bands).
                // Endpoint pseudo-distance is only useful in the immediate
                // corner neighbourhood. Keeping this independent from the
                // wider MTSDF storage range prevents long radial bands from
                // reaching the reconstructed 0.5 contour.
                double nearLimit = Math.min(RANGE * 0.5, 4.0);
                if (rEdge != null && Math.abs(r.distance) <= nearLimit) {
                    rEdge.distanceToPerpendicularDistance(r, p, rParam);
                }
                if (gEdge != null && Math.abs(g.distance) <= nearLimit) {
                    gEdge.distanceToPerpendicularDistance(g, p, gParam);
                }
                if (bEdge != null && Math.abs(b.distance) <= nearLimit) {
                    bEdge.distanceToPerpendicularDistance(b, p, bParam);
                }
                int i = (y * width + x) * 4;

                // The per-edge local sign is unstable around narrow joins in
                // Java2D's y-down outlines. Keep each channel's independently
                // selected pseudo-distance magnitude, and resolve only its
                // inside/outside sign from the exact nonzero fill rule.
                field[i]     = mapDistance(sign * Math.abs(r.distance));
                field[i + 1] = mapDistance(sign * Math.abs(g.distance));
                field[i + 2] = mapDistance(sign * Math.abs(b.distance));

                // MTSDF alpha is the true Euclidean distance to the nearest
                // outline edge. It must not receive pseudo-distance endpoint
                // conversion.
                field[i + 3] = mapDistance(sign * Math.abs(t.distance));
            }
        }

        errorCorrection(field, width, height, 1.001 / RANGE);

        byte[] out = new byte[width * height * 4];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Math.max(0, Math.min(255, (int) Math.round(field[i] * 255.0)));
        }
        return out;
    }

    private static float mapDistance(double distance) {
        return (float) (0.5 + distance / RANGE);
    }

    // ==================================================================
    //  Error correction (msdfgen legacy clash detection)
    //
    //  Bilinear interpolation of the atlas can make the median of the three
    //  interpolated channels dip at corners where adjacent texels disagree
    //  on the channel ordering ("clashes"), producing small notches.
    //  The fix: detect such texel pairs and equalize the offending texel
    //  to its own median, which is always interpolation-safe.
    //
    //  Operates on R/G/B only (STRIDE 4 — the alpha channel holds the true
    //  SDF and must stay exact).
    // ==================================================================

    private static final int STRIDE = 4;

    private static void errorCorrection(float[] sdf, int w, int h, double threshold) {
        List<int[]> clashes = new ArrayList<>();
        // Axial neighbors
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * STRIDE;
                if ((x > 0 && detectClash(sdf, i, sdf, i - STRIDE, threshold))
                        || (x < w - 1 && detectClash(sdf, i, sdf, i + STRIDE, threshold))
                        || (y > 0 && detectClash(sdf, i, sdf, i - w * STRIDE, threshold))
                        || (y < h - 1 && detectClash(sdf, i, sdf, i + w * STRIDE, threshold))) {
                    clashes.add(new int[] { x, y });
                }
            }
        }
        equalize(sdf, w, clashes);
        // Diagonal neighbors
        clashes.clear();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * STRIDE;
                if ((x > 0 && y > 0 && detectClash(sdf, i, sdf, i - w * STRIDE - STRIDE, 2 * threshold))
                        || (x < w - 1 && y > 0 && detectClash(sdf, i, sdf, i - w * STRIDE + STRIDE, 2 * threshold))
                        || (x > 0 && y < h - 1 && detectClash(sdf, i, sdf, i + w * STRIDE - STRIDE, 2 * threshold))
                        || (x < w - 1 && y < h - 1 && detectClash(sdf, i, sdf, i + w * STRIDE + STRIDE, 2 * threshold))) {
                    clashes.add(new int[] { x, y });
                }
            }
        }
        equalize(sdf, w, clashes);
    }

    private static void equalize(float[] sdf, int w, List<int[]> clashes) {
        for (int[] clash : clashes) {
            int i = (clash[1] * w + clash[0]) * STRIDE;
            float med = median(sdf[i], sdf[i + 1], sdf[i + 2]);
            sdf[i] = med;
            sdf[i + 1] = med;
            sdf[i + 2] = med;
        }
    }

    private static float median(float r, float g, float b) {
        return Math.max(Math.min(r, g), Math.min(Math.max(r, g), b));
    }

    /** Port of msdfgen's legacy detectClash. */
    private static boolean detectClash(float[] a, int ai, float[] b, int bi, double threshold) {
        float a0 = a[ai], a1 = a[ai + 1], a2 = a[ai + 2];
        float b0 = b[bi], b1 = b[bi + 1], b2 = b[bi + 2];
        float tmp;
        // Sort channel pairs (a_i, b_i) from biggest to smallest absolute difference
        if (Math.abs(b0 - a0) < Math.abs(b1 - a1)) {
            tmp = a0; a0 = a1; a1 = tmp;
            tmp = b0; b0 = b1; b1 = tmp;
        }
        if (Math.abs(b1 - a1) < Math.abs(b2 - a2)) {
            tmp = a1; a1 = a2; a2 = tmp;
            tmp = b1; b1 = b2; b2 = tmp;
            if (Math.abs(b0 - a0) < Math.abs(b1 - a1)) {
                tmp = a0; a0 = a1; a1 = tmp;
                tmp = b0; b0 = b1; b1 = tmp;
            }
        }
        return (Math.abs(b1 - a1) >= threshold)
                && !(b0 == b1 && b0 == b2) // ignore if other pixel has been equalized
                && Math.abs(a2 - .5f) >= Math.abs(b2 - .5f); // only flag the pixel farther from the edge
    }

    // ==================================================================
    //  Vector2
    // ==================================================================

    private static final class V {
        final double x, y;

        V(double x, double y) { this.x = x; this.y = y; }

        V add(V o) { return new V(x + o.x, y + o.y); }
        V sub(V o) { return new V(x - o.x, y - o.y); }
        V scale(double s) { return new V(x * s, y * s); }
        double dot(V o) { return x * o.x + y * o.y; }
        double cross(V o) { return x * o.y - y * o.x; }
        double length() { return Math.sqrt(x * x + y * y); }
        boolean isZero() { return x == 0 && y == 0; }

        /** msdfgen Vector2::normalize — the zero vector maps to (0, 1). */
        V normalize() {
            double len = length();
            if (len == 0) return new V(0, 1);
            return new V(x / len, y / len);
        }

        /** msdfgen Vector2::getOrthonormal(false) — (y, -x) / len. */
        V orthonormal() {
            double len = length();
            return new V(y / len, -x / len);
        }

        static V mix(V a, V b, double t) {
            return new V((1 - t) * a.x + t * b.x, (1 - t) * a.y + t * b.y);
        }
    }

    private static int nonZeroSign(double n) {
        return 2 * (n > 0 ? 1 : 0) - 1;
    }

    // ==================================================================
    //  SignedDistance
    // ==================================================================

    private static final class SignedDistance {
        double distance;
        double dot;

        SignedDistance() { this.distance = INF_DISTANCE; this.dot = 0; }
        SignedDistance(double distance, double dot) { this.distance = distance; this.dot = dot; }

        /** msdfgen SignedDistance::operator< — smaller |distance| wins; ties go to larger dot. */
        boolean lessThan(SignedDistance o) {
            double a = Math.abs(distance), b = Math.abs(o.distance);
            return a < b || (a == b && dot > o.dot);
        }
    }

    // ==================================================================
    //  Edge segments
    // ==================================================================

    private abstract static class Edge {
        int color;
        protected double minX, minY, maxX, maxY;

        abstract V point(double t);
        abstract V direction(double t);
        /** Control endpoint: index 0 = start, 1 = end. */
        abstract V pointAt(int index);
        abstract SignedDistance signedDistance(V origin, double[] paramOut);
        abstract Edge reversed();
        /** Sub-segment over [t0, t1] via de Casteljau reparameterization. */
        abstract Edge subSegment(double t0, double t1);
        /** Append flattened points t = i/divisions (i = 0 .. divisions-1). */
        abstract void flattenInto(List<V> out, int divisions);
        abstract boolean isDegenerate();

        void computeBounds(V... pts) {
            minX = minY = Double.MAX_VALUE;
            maxX = maxY = -Double.MAX_VALUE;
            for (V p : pts) {
                if (p.x < minX) minX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.x > maxX) maxX = p.x;
                if (p.y > maxY) maxY = p.y;
            }
        }

        /** Lower bound of the true distance from p (via control-polygon AABB). */
        double boundDistance(V p) {
            double dx = Math.max(Math.max(minX - p.x, 0), p.x - maxX);
            double dy = Math.max(Math.max(minY - p.y, 0), p.y - maxY);
            return Math.sqrt(dx * dx + dy * dy);
        }

        /** msdfgen EdgeSegment::distanceToPerpendicularDistance (a.k.a. pseudo-distance). */
        void distanceToPerpendicularDistance(SignedDistance sd, V origin, double param) {
            if (param < 0) {
                V dir = direction(0).normalize();
                V aq = origin.sub(pointAt(0));
                double ts = aq.dot(dir);
                if (ts < 0) {
                    double pd = aq.cross(dir);
                    if (Math.abs(pd) <= Math.abs(sd.distance)) {
                        sd.distance = pd;
                        sd.dot = 0;
                    }
                }
            } else if (param > 1) {
                V dir = direction(1).normalize();
                V bq = origin.sub(pointAt(1));
                double ts = bq.dot(dir);
                if (ts > 0) {
                    double pd = bq.cross(dir);
                    if (Math.abs(pd) <= Math.abs(sd.distance)) {
                        sd.distance = pd;
                        sd.dot = 0;
                    }
                }
            }
        }

        Edge[] splitInThirds() {
            return new Edge[] {
                    subSegment(0, 1.0 / 3),
                    subSegment(1.0 / 3, 2.0 / 3),
                    subSegment(2.0 / 3, 1)
            };
        }
    }

    private static final class LinearEdge extends Edge {
        final V p0, p1;

        LinearEdge(V a, V b) { p0 = a; p1 = b; computeBounds(a, b); }

        @Override V point(double t) { return V.mix(p0, p1, t); }
        @Override V direction(double t) { return p1.sub(p0); }
        @Override V pointAt(int index) { return index == 0 ? p0 : p1; }
        @Override boolean isDegenerate() { return p0.sub(p1).length() < 1e-10; }

        @Override Edge reversed() {
            LinearEdge e = new LinearEdge(p1, p0);
            e.color = color;
            return e;
        }

        @Override Edge subSegment(double t0, double t1) {
            LinearEdge e = new LinearEdge(point(t0), point(t1));
            e.color = color;
            return e;
        }

        @Override void flattenInto(List<V> out, int divisions) { out.add(p0); }

        @Override SignedDistance signedDistance(V origin, double[] paramOut) {
            V aq = origin.sub(p0);
            V ab = p1.sub(p0);
            double param = aq.dot(ab) / ab.dot(ab);
            paramOut[0] = param;
            V eq = (param > 0.5 ? p1 : p0).sub(origin);
            double endpointDistance = eq.length();
            if (param > 0 && param < 1) {
                double orthoDistance = ab.orthonormal().dot(aq);
                if (Math.abs(orthoDistance) < endpointDistance) {
                    return new SignedDistance(orthoDistance, 0);
                }
            }
            return new SignedDistance(
                    nonZeroSign(aq.cross(ab)) * endpointDistance,
                    Math.abs(ab.normalize().dot(eq.normalize())));
        }
    }

    private static final class QuadraticEdge extends Edge {
        final V p0, p1, p2;

        QuadraticEdge(V a, V b, V c) { p0 = a; p1 = b; p2 = c; computeBounds(a, b, c); }

        @Override V point(double t) { return V.mix(V.mix(p0, p1, t), V.mix(p1, p2, t), t); }

        @Override V direction(double t) {
            V tangent = V.mix(p1.sub(p0), p2.sub(p1), t);
            if (tangent.isZero()) return p2.sub(p0);
            return tangent;
        }

        @Override V pointAt(int index) { return index == 0 ? p0 : p2; }

        @Override boolean isDegenerate() {
            return p0.sub(p1).length() < 1e-10 && p1.sub(p2).length() < 1e-10;
        }

        @Override Edge reversed() {
            QuadraticEdge e = new QuadraticEdge(p2, p1, p0);
            e.color = color;
            return e;
        }

        @Override Edge subSegment(double t0, double t1) {
            // q1 = B(t0) + B'(t0) * (t1 - t0) / 2, with B'(t) = 2 * direction(t)
            V q0 = point(t0);
            V q1 = q0.add(direction(t0).scale(t1 - t0));
            V q2 = point(t1);
            QuadraticEdge e = new QuadraticEdge(q0, q1, q2);
            e.color = color;
            return e;
        }

        @Override void flattenInto(List<V> out, int divisions) {
            for (int i = 0; i < divisions; i++) {
                out.add(point((double) i / divisions));
            }
        }

        @Override SignedDistance signedDistance(V origin, double[] paramOut) {
            V qa = p0.sub(origin);
            V ab = p1.sub(p0);
            V br = p2.sub(p1).sub(ab);
            double a = br.dot(br);
            double b = 3 * ab.dot(br);
            double c = 2 * ab.dot(ab) + qa.dot(br);
            double d = qa.dot(ab);
            double[] t = new double[3];
            int solutions = solveCubic(t, a, b, c, d);

            V epDir = direction(0);
            double minDistance = nonZeroSign(epDir.cross(qa)) * qa.length(); // distance from A
            double param = -qa.dot(epDir) / epDir.dot(epDir);
            {
                double distance = p2.sub(origin).length(); // distance from B
                if (distance < Math.abs(minDistance)) {
                    epDir = direction(1);
                    minDistance = nonZeroSign(epDir.cross(p2.sub(origin))) * distance;
                    param = origin.sub(p1).dot(epDir) / epDir.dot(epDir);
                }
            }
            for (int i = 0; i < solutions; i++) {
                if (t[i] > 0 && t[i] < 1) {
                    V qe = qa.add(ab.scale(2 * t[i])).add(br.scale(t[i] * t[i]));
                    double distance = qe.length();
                    if (distance <= Math.abs(minDistance)) {
                        minDistance = nonZeroSign(ab.add(br.scale(t[i])).cross(qe)) * distance;
                        param = t[i];
                    }
                }
            }
            paramOut[0] = param;
            if (param >= 0 && param <= 1) {
                return new SignedDistance(minDistance, 0);
            }
            if (param < 0.5) {
                return new SignedDistance(minDistance,
                        Math.abs(direction(0).normalize().dot(qa.normalize())));
            } else {
                return new SignedDistance(minDistance,
                        Math.abs(direction(1).normalize().dot(p2.sub(origin).normalize())));
            }
        }
    }

    private static final class CubicEdge extends Edge {
        final V p0, p1, p2, p3;

        CubicEdge(V a, V b, V c, V d) { p0 = a; p1 = b; p2 = c; p3 = d; computeBounds(a, b, c, d); }

        @Override V point(double t) {
            V p12 = V.mix(p1, p2, t);
            return V.mix(V.mix(V.mix(p0, p1, t), p12, t),
                    V.mix(p12, V.mix(p2, p3, t), t), t);
        }

        @Override V direction(double t) {
            V tangent = V.mix(V.mix(p1.sub(p0), p2.sub(p1), t),
                    V.mix(p2.sub(p1), p3.sub(p2), t), t);
            if (tangent.isZero()) {
                if (t == 0) return p2.sub(p0);
                if (t == 1) return p3.sub(p1);
            }
            return tangent;
        }

        /** First derivative B'(t) = 3ab + 6t·br + 3t²·as. */
        private V derivative(double t) {
            V ab = p1.sub(p0);
            V br = p2.sub(p1).sub(ab);
            V as = p3.sub(p2).sub(p2.sub(p1)).sub(br);
            return ab.scale(3).add(br.scale(6 * t)).add(as.scale(3 * t * t));
        }

        @Override V pointAt(int index) { return index == 0 ? p0 : p3; }

        @Override boolean isDegenerate() {
            return p0.sub(p1).length() < 1e-10 && p1.sub(p2).length() < 1e-10
                    && p2.sub(p3).length() < 1e-10;
        }

        @Override Edge reversed() {
            CubicEdge e = new CubicEdge(p3, p2, p1, p0);
            e.color = color;
            return e;
        }

        @Override Edge subSegment(double t0, double t1) {
            double dt = (t1 - t0) / 3.0;
            V q0 = point(t0);
            V q1 = q0.add(derivative(t0).scale(dt));
            V q3 = point(t1);
            V q2 = q3.sub(derivative(t1).scale(dt));
            CubicEdge e = new CubicEdge(q0, q1, q2, q3);
            e.color = color;
            return e;
        }

        @Override void flattenInto(List<V> out, int divisions) {
            for (int i = 0; i < divisions; i++) {
                out.add(point((double) i / divisions));
            }
        }

        @Override SignedDistance signedDistance(V origin, double[] paramOut) {
            V qa = p0.sub(origin);
            V ab = p1.sub(p0);
            V br = p2.sub(p1).sub(ab);
            V as = p3.sub(p2).sub(p2.sub(p1)).sub(br);

            V epDir = direction(0);
            double minDistance = nonZeroSign(epDir.cross(qa)) * qa.length(); // distance from A
            double param = -qa.dot(epDir) / epDir.dot(epDir);
            {
                double distance = p3.sub(origin).length(); // distance from B
                if (distance < Math.abs(minDistance)) {
                    epDir = direction(1);
                    minDistance = nonZeroSign(epDir.cross(p3.sub(origin))) * distance;
                    param = epDir.sub(p3.sub(origin)).dot(epDir) / epDir.dot(epDir);
                }
            }
            // Iterative minimum distance search (Newton refinement from several starts)
            for (int i = 0; i <= CUBIC_SEARCH_STARTS; i++) {
                double t = (double) i / CUBIC_SEARCH_STARTS;
                V qe = qa.add(ab.scale(3 * t)).add(br.scale(3 * t * t)).add(as.scale(t * t * t));
                V d1 = ab.scale(3).add(br.scale(6 * t)).add(as.scale(3 * t * t));
                V d2 = br.scale(6).add(as.scale(6 * t));
                double improvedT = t - qe.dot(d1) / (d1.dot(d1) + qe.dot(d2));
                if (improvedT > 0 && improvedT < 1) {
                    int remainingSteps = CUBIC_SEARCH_STEPS;
                    do {
                        t = improvedT;
                        qe = qa.add(ab.scale(3 * t)).add(br.scale(3 * t * t)).add(as.scale(t * t * t));
                        d1 = ab.scale(3).add(br.scale(6 * t)).add(as.scale(3 * t * t));
                        if (--remainingSteps == 0) break;
                        d2 = br.scale(6).add(as.scale(6 * t));
                        improvedT = t - qe.dot(d1) / (d1.dot(d1) + qe.dot(d2));
                    } while (improvedT > 0 && improvedT < 1);
                    double distance = qe.length();
                    if (distance < Math.abs(minDistance)) {
                        minDistance = nonZeroSign(d1.cross(qe)) * distance;
                        param = t;
                    }
                }
            }
            paramOut[0] = param;
            if (param >= 0 && param <= 1) {
                return new SignedDistance(minDistance, 0);
            }
            if (param < 0.5) {
                return new SignedDistance(minDistance,
                        Math.abs(direction(0).normalize().dot(qa.normalize())));
            } else {
                return new SignedDistance(minDistance,
                        Math.abs(direction(1).normalize().dot(p3.sub(origin).normalize())));
            }
        }
    }

    // ==================================================================
    //  Equation solvers (msdfgen equation-solver.cpp)
    // ==================================================================

    /** Solve a·x² + b·x + c = 0; returns the number of roots written to x. */
    private static int solveQuadratic(double[] x, double a, double b, double c) {
        if (a == 0 || Math.abs(b) > 1e12 * Math.abs(a)) {
            if (b == 0) {
                return 0; // c == 0 would mean "infinite solutions" — irrelevant here
            }
            x[0] = -c / b;
            return 1;
        }
        double dscr = b * b - 4 * a * c;
        if (dscr > 0) {
            dscr = Math.sqrt(dscr);
            x[0] = (-b + dscr) / (2 * a);
            x[1] = (-b - dscr) / (2 * a);
            return 2;
        } else if (dscr == 0) {
            x[0] = -b / (2 * a);
            return 1;
        }
        return 0;
    }

    private static int solveCubicNormed(double[] x, double a, double b, double c) {
        double a2 = a * a;
        double q = (a2 - 3 * b) / 9.0;
        double r = (a * (2 * a2 - 9 * b) + 27 * c) / 54.0;
        double r2 = r * r;
        double q3 = q * q * q;
        a *= 1.0 / 3;
        if (r2 < q3) {
            double t = r / Math.sqrt(q3);
            if (t < -1) t = -1;
            if (t > 1) t = 1;
            t = Math.acos(t);
            q = -2 * Math.sqrt(q);
            x[0] = q * Math.cos(t / 3) - a;
            x[1] = q * Math.cos((t + 2 * Math.PI) / 3) - a;
            x[2] = q * Math.cos((t - 2 * Math.PI) / 3) - a;
            return 3;
        } else {
            double u = (r < 0 ? 1 : -1) * Math.pow(Math.abs(r) + Math.sqrt(r2 - q3), 1.0 / 3);
            double v = u == 0 ? 0 : q / u;
            x[0] = (u + v) - a;
            if (u == v || Math.abs(u - v) < 1e-12 * Math.abs(u + v)) {
                x[1] = -0.5 * (u + v) - a;
                return 2;
            }
            return 1;
        }
    }

    /** Solve a·x³ + b·x² + c·x + d = 0; returns the number of roots written to x. */
    private static int solveCubic(double[] x, double a, double b, double c, double d) {
        if (a != 0) {
            double bn = b / a;
            if (Math.abs(bn) < 1e6) {
                return solveCubicNormed(x, bn, c / a, d / a);
            }
        }
        return solveQuadratic(x, b, c, d);
    }

    // ==================================================================
    //  Contour decomposition from AWT Shape
    // ==================================================================

    private static final class Contour {
        final List<Edge> edges = new ArrayList<>();

        Contour reversed() {
            Contour out = new Contour();
            for (int i = edges.size() - 1; i >= 0; i--) {
                out.edges.add(edges.get(i).reversed());
            }
            return out;
        }
    }

    private static List<Contour> decompose(Shape shape) {
        List<Contour> contours = new ArrayList<>();
        PathIterator pi = shape.getPathIterator(null);
        double[] c = new double[6];
        Contour current = null;
        V start = null, cur = null;
        while (!pi.isDone()) {
            int type = pi.currentSegment(c);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    finishContour(contours, current, start, cur);
                    current = new Contour();
                    start = cur = new V(c[0], c[1]);
                    break;
                case PathIterator.SEG_LINETO: {
                    V p = new V(c[0], c[1]);
                    addEdge(current, new LinearEdge(cur, p));
                    cur = p;
                    break;
                }
                case PathIterator.SEG_QUADTO: {
                    V ctrl = new V(c[0], c[1]);
                    V p = new V(c[2], c[3]);
                    addEdge(current, new QuadraticEdge(cur, ctrl, p));
                    cur = p;
                    break;
                }
                case PathIterator.SEG_CUBICTO: {
                    V c1 = new V(c[0], c[1]);
                    V c2 = new V(c[2], c[3]);
                    V p = new V(c[4], c[5]);
                    addEdge(current, new CubicEdge(cur, c1, c2, p));
                    cur = p;
                    break;
                }
                case PathIterator.SEG_CLOSE:
                    finishContour(contours, current, start, cur);
                    current = null;
                    start = null;
                    cur = null;
                    break;
                default:
                    break;
            }
            pi.next();
        }
        finishContour(contours, current, start, cur);
        return contours;
    }

    private static void finishContour(List<Contour> contours, Contour contour, V start, V cur) {
        if (contour == null || contour.edges.isEmpty()) {
            return;
        }
        // Close the loop explicitly — orientation and sign depend on it.
        if (start != null && cur != null && cur.sub(start).length() > 1e-10) {
            addEdge(contour, new LinearEdge(cur, start));
        }
        contours.add(contour);
    }

    private static void addEdge(Contour contour, Edge edge) {
        if (contour == null || edge.isDegenerate()) {
            return;
        }
        contour.edges.add(edge);
    }

    // ==================================================================
    //  Contour orientation (interior = positive distance)
    //
    //  Port-equivalent of msdfgen Shape::orientContours, using flattened
    //  polylines instead of scanline intersections. In y-down screen space
    //  the convention comes out as: outer contours get NEGATIVE shoelace
    //  area, holes POSITIVE (verified against the scanline algorithm).
    // ==================================================================

    private static void orientContours(List<Contour> contours) {
        int n = contours.size();
        List<List<V>> polys = new ArrayList<>(n);
        double[] areas = new double[n];
        for (int i = 0; i < n; i++) {
            List<V> poly = flatten(contours.get(i));
            polys.add(poly);
            areas[i] = shoelaceArea(poly);
        }
        for (int i = 0; i < n; i++) {
            if (Math.abs(areas[i]) < 1e-12) {
                continue; // degenerate contour — leave as-is
            }
            V sample = polys.get(i).get(0);
            int depth = 0;
            for (int j = 0; j < n; j++) {
                if (j != i && windingNumber(sample, polys.get(j)) != 0) {
                    depth++;
                }
            }
            boolean shouldBeNegative = (depth % 2) == 0;
            if ((areas[i] < 0) != shouldBeNegative) {
                contours.set(i, contours.get(i).reversed());
            }
        }
    }

    private static List<V> flatten(Contour contour) {
        List<V> out = new ArrayList<>();
        for (Edge edge : contour.edges) {
            edge.flattenInto(out, FLATTEN_DIVISIONS);
        }
        return out;
    }

    private static double shoelaceArea(List<V> poly) {
        double sum = 0;
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            V a = poly.get(i);
            V b = poly.get((i + 1) % n);
            sum += a.x * b.y - b.x * a.y;
        }
        return 0.5 * sum;
    }

    /** Standard winding-number point-in-polygon test. */
    private static int windingNumber(V p, List<V> poly) {
        int wn = 0;
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            V a = poly.get(i);
            V b = poly.get((i + 1) % n);
            if (a.y <= p.y) {
                if (b.y > p.y && b.sub(a).cross(p.sub(a)) > 0) {
                    wn++;
                }
            } else {
                if (b.y <= p.y && b.sub(a).cross(p.sub(a)) < 0) {
                    wn--;
                }
            }
        }
        return wn;
    }

    // ==================================================================
    //  Edge coloring (msdfgen edgeColoringSimple, verbatim port)
    // ==================================================================

    private static int symmetricalTrichotomy(int position, int n) {
        return (int) (3 + 2.875 * position / (n - 1) - 1.4375 + 0.5) - 3;
    }

    private static boolean isCorner(V aDir, V bDir, double crossThreshold) {
        return aDir.dot(bDir) <= 0 || Math.abs(aDir.cross(bDir)) > crossThreshold;
    }

    private static int seedExtract2(long[] seed) {
        int v = (int) (seed[0] & 1);
        seed[0] >>>= 1;
        return v;
    }

    private static int seedExtract3(long[] seed) {
        int v = (int) (seed[0] % 3);
        seed[0] /= 3;
        return v;
    }

    private static int initColor(long[] seed) {
        return new int[] { CYAN, MAGENTA, YELLOW }[seedExtract3(seed)];
    }

    private static int switchColor(int color, long[] seed) {
        int shifted = color << (1 + seedExtract2(seed));
        return (shifted | (shifted >> 3)) & WHITE;
    }

    private static int switchColorBanned(int color, long[] seed, int banned) {
        int combined = color & banned;
        if (combined == RED || combined == GREEN || combined == BLUE) {
            return combined ^ WHITE;
        }
        return switchColor(color, seed);
    }

    private static void edgeColoringSimple(List<Contour> contours, double angleThreshold, long seedValue) {
        double crossThreshold = Math.sin(angleThreshold);
        long[] seed = { seedValue };
        int color = initColor(seed);
        List<Integer> corners = new ArrayList<>();
        for (Contour contour : contours) {
            List<Edge> edges = contour.edges;
            if (edges.isEmpty()) {
                continue;
            }
            // Identify corners
            corners.clear();
            V prevDirection = edges.get(edges.size() - 1).direction(1);
            for (int i = 0; i < edges.size(); i++) {
                Edge edge = edges.get(i);
                if (isCorner(prevDirection.normalize(), edge.direction(0).normalize(), crossThreshold)) {
                    corners.add(i);
                }
                prevDirection = edge.direction(1);
            }

            int m = edges.size();
            if (corners.isEmpty()) {
                // Smooth contour
                color = switchColor(color, seed);
                for (Edge edge : edges) {
                    edge.color = color;
                }
            } else if (corners.size() == 1) {
                // "Teardrop" case
                int[] colors = new int[3];
                color = switchColor(color, seed);
                colors[0] = color;
                colors[1] = WHITE;
                color = switchColor(color, seed);
                colors[2] = color;
                int corner = corners.get(0);
                if (m >= 3) {
                    for (int i = 0; i < m; i++) {
                        edges.get((corner + i) % m).color = colors[1 + symmetricalTrichotomy(i, m)];
                    }
                } else {
                    // Less than three edge segments for three colors => split edges in thirds
                    Edge[] parts = new Edge[6];
                    Edge[] first = edges.get(0).splitInThirds();
                    parts[3 * corner] = first[0];
                    parts[1 + 3 * corner] = first[1];
                    parts[2 + 3 * corner] = first[2];
                    if (m >= 2) {
                        Edge[] second = edges.get(1).splitInThirds();
                        parts[3 - 3 * corner] = second[0];
                        parts[4 - 3 * corner] = second[1];
                        parts[5 - 3 * corner] = second[2];
                        parts[0].color = parts[1].color = colors[0];
                        parts[2].color = parts[3].color = colors[1];
                        parts[4].color = parts[5].color = colors[2];
                    } else {
                        parts[0].color = colors[0];
                        parts[1].color = colors[1];
                        parts[2].color = colors[2];
                    }
                    edges.clear();
                    for (Edge part : parts) {
                        if (part != null) {
                            edges.add(part);
                        }
                    }
                }
            } else {
                // Multiple corners
                int cornerCount = corners.size();
                int spline = 0;
                int start = corners.get(0);
                color = switchColor(color, seed);
                int initialColor = color;
                for (int i = 0; i < m; i++) {
                    int index = (start + i) % m;
                    if (spline + 1 < cornerCount && corners.get(spline + 1) == index) {
                        spline++;
                        color = switchColorBanned(color, seed, spline == cornerCount - 1 ? initialColor : 0);
                    }
                    edges.get(index).color = color;
                }
            }
        }
    }
}
