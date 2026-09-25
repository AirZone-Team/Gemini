package geminiclient.gemini.customRenderer.glsl.slug;

import java.util.ArrayList;
import java.util.List;

/**
 * Triangulates glyph regions (outers plus their holes) into plain triangles.
 *
 * <p>A TrueType outline encodes the fill with the nonzero winding rule, which
 * ear clipping cannot consume directly. Every hole is therefore spliced into
 * its immediate container through a bridge before ear clipping, turning each
 * component into one simple ring. Bridges are picked as the shortest
 * unobstructed segment from the hole's rightmost vertex to a vertex of the
 * container, which keeps the merged ring simple without the cone-sweep
 * bookkeeping a general-purpose polygoniser needs.</p>
 */
final class PolygonTriangulator {

    /** A closed polyline: vertex {@code i} is followed by {@code (i+1) % n}. */
    static final class Ring {
        final double[] x;
        final double[] y;
        final boolean hole;

        Ring(double[] x, double[] y, boolean hole) {
            this.x = x;
            this.y = y;
            this.hole = hole;
        }

        int size() {
            return x.length;
        }
    }

    /** Merged vertex pool plus triangle indices into it. */
    static final class Result {
        final double[] x;
        final double[] y;
        final int[] triangles;

        Result(double[] x, double[] y, int[] triangles) {
            this.x = x;
            this.y = y;
            this.triangles = triangles;
        }
    }

    private PolygonTriangulator() {
    }

    static Result triangulate(List<Ring> rings) {
        Pool pool = new Pool();
        List<List<Integer>> working = new ArrayList<>(rings.size());
        for (Ring ring : rings) {
            List<Integer> indices = new ArrayList<>(ring.size());
            for (int i = 0; i < ring.size(); i++) {
                indices.add(pool.add(ring.x[i], ring.y[i]));
            }
            working.add(indices);
        }

        // Deepest rings first: an island inside a counter is spliced into that
        // counter before the counter itself is spliced into its outer.
        List<Integer> holes = new ArrayList<>();
        for (int i = 0; i < rings.size(); i++) {
            if (rings.get(i).hole && working.get(i).size() >= 3) {
                holes.add(i);
            }
        }
        holes.sort((a, b) -> Integer.compare(depthOf(rings, working, b),
                depthOf(rings, working, a)));
        boolean[] consumed = new boolean[rings.size()];
        for (int holeIdx : holes) {
            if (consumed[holeIdx] || working.get(holeIdx).size() < 3) {
                continue;
            }
            int container = findContainer(rings, working, consumed, holeIdx);
            if (container < 0) {
                consumed[holeIdx] = true;
                working.get(holeIdx).clear(); // nothing to splice into
                continue;
            }
            Bridge bridge = findBridge(pool, rings, working, consumed, container, holeIdx);
            if (bridge == null) {
                consumed[holeIdx] = true;
                working.get(holeIdx).clear(); // unreachable counter — leave it unfilled
                continue;
            }
            splice(working, container, holeIdx, bridge);
            consumed[holeIdx] = true;
        }

        List<int[]> triangles = new ArrayList<>();
        for (int r = 0; r < working.size(); r++) {
            if (!consumed[r]) {
                earClip(pool, working.get(r), triangles);
            }
        }

        int[] flat = new int[triangles.size() * 3];
        int at = 0;
        for (int[] tri : triangles) {
            flat[at++] = tri[0];
            flat[at++] = tri[1];
            flat[at++] = tri[2];
        }
        return new Result(pool.xArray(), pool.yArray(), flat);
    }

    // ==================================================================
    //  Hole splicing
    // ==================================================================

    /** How many contours enclose this ring — its nesting depth. */
    private static int depthOf(List<Ring> rings, List<List<Integer>> working, int index) {
        Ring self = rings.get(index);
        if (working.get(index).isEmpty() || self.size() == 0) {
            return 0;
        }
        int depth = 0;
        for (int j = 0; j < rings.size(); j++) {
            if (j != index && !working.get(j).isEmpty()
                    && windingNumber(rings.get(j), self.x[0], self.y[0]) != 0) {
                depth++;
            }
        }
        return depth;
    }

    /**
     * Index of the contour that directly encloses {@code hole} — the smallest
     * enclosing one, so a counter is never spliced past an island sitting
     * inside it.
     */
    private static int findContainer(List<Ring> rings, List<List<Integer>> working,
                                     boolean[] consumed, int hole) {
        Ring self = rings.get(hole);
        int best = -1;
        double bestArea = Double.MAX_VALUE;
        for (int j = 0; j < rings.size(); j++) {
            if (j == hole || consumed[j] || working.get(j).size() < 3) {
                continue;
            }
            if (windingNumber(rings.get(j), self.x[0], self.y[0]) == 0) {
                continue;
            }
            double area = Math.abs(shoelaceArea(rings.get(j)));
            if (area < bestArea) {
                bestArea = area;
                best = j;
            }
        }
        return best;
    }

    private record Bridge(int holeAt, int containerAt) {}

    /** Rightmost hole vertex paired with the closest vertex it can see. */
    private static Bridge findBridge(Pool pool, List<Ring> rings, List<List<Integer>> working,
                                     boolean[] consumed, int container, int hole) {
        List<Integer> holeRing = working.get(hole);
        int holeAt = 0;
        for (int i = 1; i < holeRing.size(); i++) {
            if (pool.x[holeRing.get(i)] > pool.x[holeRing.get(holeAt)]) {
                holeAt = i;
            }
        }
        int start = holeRing.get(holeAt);

        List<Integer> target = working.get(container);
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < target.size(); i++) {
            int candidate = target.get(i);
            double distance = distance(pool, start, candidate);
            if (distance >= bestDistance) {
                continue;
            }
            if (canSee(pool, rings, working, consumed, start, candidate)) {
                best = i;
                bestDistance = distance;
            }
        }
        return best < 0 ? null : new Bridge(holeAt, best);
    }

    /**
     * A bridge may only touch other contours at its own endpoints, and its
     * midpoint has to sit inside the fill — otherwise the splice would route
     * the ring through empty space.
     */
    private static boolean canSee(Pool pool, List<Ring> rings, List<List<Integer>> working,
                                  boolean[] consumed, int a, int b) {
        double ax = pool.x[a];
        double ay = pool.y[a];
        double bx = pool.x[b];
        double by = pool.y[b];
        if (ax == bx && ay == by) {
            return false;
        }
        for (int r = 0; r < rings.size(); r++) {
            if (consumed[r]) {
                continue;
            }
            Ring ring = rings.get(r);
            int n = ring.size();
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                if (touches(ring.x[i], ring.y[i], ax, ay, bx, by)
                        || touches(ring.x[j], ring.y[j], ax, ay, bx, by)) {
                    continue;
                }
                if (segmentsIntersect(ax, ay, bx, by,
                        ring.x[i], ring.y[i], ring.x[j], ring.y[j])) {
                    return false;
                }
            }
        }
        return windingSum(rings, (ax + bx) * 0.5, (ay + by) * 0.5) != 0;
    }

    private static boolean touches(double px, double py,
                                   double ax, double ay, double bx, double by) {
        return (px == ax && py == ay) || (px == bx && py == by);
    }

    /**
     * Splice {@code hole} into {@code container}: the bridge is walked twice in
     * opposite directions — {@code container[containerAt] → hole[holeAt]} on the
     * way in and back on the way out — so its winding contribution cancels and
     * the merged ring is simple again.
     */
    private static void splice(List<List<Integer>> working, int container, int hole,
                               Bridge bridge) {
        List<Integer> outer = working.get(container);
        List<Integer> inner = working.get(hole);
        int n = inner.size();
        List<Integer> merged = new ArrayList<>(outer.size() + n + 3);
        for (int i = 0; i <= bridge.containerAt(); i++) {
            merged.add(outer.get(i));
        }
        for (int k = 0; k <= n; k++) {
            merged.add(inner.get((bridge.holeAt() + k) % n));
        }
        for (int i = bridge.containerAt(); i < outer.size(); i++) {
            merged.add(outer.get(i));
        }
        working.set(container, merged);
        inner.clear();
    }

    // ==================================================================
    //  Ear clipping
    // ==================================================================

    private static void earClip(Pool pool, List<Integer> ring, List<int[]> out) {
        List<Integer> rest = new ArrayList<>(ring);
        // The merged ring can come out either way round; ear convexity below
        // is defined against a negative shoelace area, so normalise.
        if (poolArea(pool, rest) > 0) {
            reverse(rest);
        }
        int guard = rest.size() * 4 + 64;
        while (rest.size() > 3 && guard-- > 0) {
            int ear = findEar(pool, rest);
            if (ear < 0) {
                break; // leftover self-intersects — fall back to the fan
            }
            int n = rest.size();
            out.add(new int[] {rest.get((ear + n - 1) % n), rest.get(ear),
                    rest.get((ear + 1) % n)});
            rest.remove(ear);
        }
        if (rest.size() == 3) {
            out.add(new int[] {rest.get(0), rest.get(1), rest.get(2)});
        } else if (rest.size() > 3) {
            int anchor = rest.get(0);
            for (int i = 1; i + 1 < rest.size(); i++) {
                out.add(new int[] {anchor, rest.get(i), rest.get(i + 1)});
            }
        }
    }

    /** The smallest ear tip wins — keeps triangles short and the fill stable. */
    private static int findEar(Pool pool, List<Integer> ring) {
        int n = ring.size();
        int best = -1;
        double bestArea = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int prev = ring.get((i + n - 1) % n);
            int cur = ring.get(i);
            int next = ring.get((i + 1) % n);
            double area = signedArea(pool, prev, cur, next);
            if (area > -1e-9) {
                continue; // reflex or collinear — not an ear tip
            }
            if (-area >= bestArea) {
                continue;
            }
            boolean blocks = false;
            for (int other : ring) {
                if (other == prev || other == cur || other == next) {
                    continue;
                }
                if (pointInTriangle(pool, other, prev, cur, next)) {
                    blocks = true;
                    break;
                }
            }
            if (!blocks) {
                bestArea = -area;
                best = i;
            }
        }
        return best;
    }

    private static boolean pointInTriangle(Pool pool, int p, int a, int b, int c) {
        return signedArea(pool, a, b, p) < -1e-9
                && signedArea(pool, b, c, p) < -1e-9
                && signedArea(pool, c, a, p) < -1e-9;
    }

    private static double signedArea(Pool pool, int a, int b, int c) {
        return ((pool.x[b] - pool.x[a]) * (pool.y[c] - pool.y[a])
                - (pool.y[b] - pool.y[a]) * (pool.x[c] - pool.x[a])) * 0.5;
    }

    private static double poolArea(Pool pool, List<Integer> ring) {
        double sum = 0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            int a = ring.get(i);
            int b = ring.get((i + 1) % n);
            sum += pool.x[a] * pool.y[b] - pool.x[b] * pool.y[a];
        }
        return sum * 0.5;
    }

    private static void reverse(List<Integer> ring) {
        for (int i = 0, j = ring.size() - 1; i < j; i++, j--) {
            Integer a = ring.get(i);
            ring.set(i, ring.get(j));
            ring.set(j, a);
        }
    }

    // ==================================================================
    //  Geometry helpers
    // ==================================================================

    /** Nonzero-rule test against every contour. */
    static int windingSum(List<Ring> rings, double px, double py) {
        int total = 0;
        for (Ring ring : rings) {
            total += windingNumber(ring, px, py);
        }
        return total;
    }

    /** Signed winding number — nonzero means the point is inside this contour. */
    static int windingNumber(Ring ring, double px, double py) {
        int wn = 0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double ax = ring.x[i];
            double ay = ring.y[i];
            double cross = (ring.x[j] - ax) * (py - ay) - (ring.y[j] - ay) * (px - ax);
            if (ay <= py) {
                if (ring.y[j] > py && cross > 0) {
                    wn++;
                }
            } else if (ring.y[j] <= py && cross < 0) {
                wn--;
            }
        }
        return wn;
    }

    static double shoelaceArea(Ring ring) {
        double sum = 0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            sum += ring.x[i] * ring.y[j] - ring.x[j] * ring.y[i];
        }
        return sum * 0.5;
    }

    private static boolean segmentsIntersect(double ax, double ay, double bx, double by,
                                            double cx, double cy, double dx, double dy) {
        double a = cross2(bx - ax, by - ay, cx - ax, cy - ay);
        double b = cross2(bx - ax, by - ay, dx - ax, dy - ay);
        double c = cross2(dx - cx, dy - cy, ax - cx, ay - cy);
        double d = cross2(dx - cx, dy - cy, bx - cx, by - cy);
        if (oppositeSigns(a, b) && oppositeSigns(c, d)) {
            return true;
        }
        return isZero(a) && onSegment(ax, ay, bx, by, cx, cy)
                || isZero(b) && onSegment(ax, ay, bx, by, dx, dy)
                || isZero(c) && onSegment(cx, cy, dx, dy, ax, ay)
                || isZero(d) && onSegment(cx, cy, dx, dy, bx, by);
    }

    private static boolean oppositeSigns(double a, double b) {
        return (a > 0 && b < 0) || (a < 0 && b > 0);
    }

    private static boolean isZero(double v) {
        return Math.abs(v) < 1e-12;
    }

    private static boolean onSegment(double ax, double ay, double bx, double by,
                                     double px, double py) {
        return px >= Math.min(ax, bx) - 1e-9 && px <= Math.max(ax, bx) + 1e-9
                && py >= Math.min(ay, by) - 1e-9 && py <= Math.max(ay, by) + 1e-9;
    }

    private static double cross2(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
    }

    private static double distance(Pool pool, int a, int b) {
        double dx = pool.x[a] - pool.x[b];
        double dy = pool.y[a] - pool.y[b];
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Triangle indices point into this growable vertex pool. */
    private static final class Pool {
        double[] x = new double[256];
        double[] y = new double[256];
        int size;

        int add(double px, double py) {
            if (size == x.length) {
                int grow = size + (size >> 1) + 1;
                x = java.util.Arrays.copyOf(x, grow);
                y = java.util.Arrays.copyOf(y, grow);
            }
            x[size] = px;
            y[size] = py;
            return size++;
        }

        double[] xArray() {
            return java.util.Arrays.copyOf(x, size);
        }

        double[] yArray() {
            return java.util.Arrays.copyOf(y, size);
        }
    }
}
