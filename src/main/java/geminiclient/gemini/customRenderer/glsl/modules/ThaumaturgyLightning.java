package geminiclient.gemini.customRenderer.glsl.modules;

/**
 * Lightning geometry for the Thaumaturgy Strike columns.
 *
 * <p>A bolt is a closed-form polyline: given a strike axis, a seed and a
 * flicker step, every vertex falls out of a hash. Nothing is simulated, so the
 * bolts are reproducible and testable without a running game, and the whole
 * field redraws itself on a strobe instead of flowing.</p>
 *
 * <p>Free of Minecraft and JOML types for the same reason
 * {@link ThaumaturgySigilGeometry} is — it has to run in a plain JVM test.</p>
 */
final class ThaumaturgyLightning {

    private ThaumaturgyLightning() {}

    /** Straight spans a bolt is broken into. */
    static final int SEGMENTS = 11;

    /** Bolts redraw every this many milliseconds: lightning strobes, it does not flow. */
    static final int FLICKER_MS = 90;

    /** The flicker bucket a given instant falls into. */
    static int step(long nowMs) {
        return (int) Math.floorDiv(nowMs, (long) FLICKER_MS);
    }

    /**
     * Whether this bolt is lit during this flicker step. Striking duty keeps the
     * field alive without every arc burning continuously, which would read as a
     * cage rather than as discharge.
     *
     * @param duty fraction of steps the bolt spends lit, 0..1
     */
    static boolean live(long seed, int step, double duty) {
        return (hash(seed + step * 613L) + 1.0) * 0.5 < duty;
    }

    /**
     * A bolt's stable character in 0..1 — how far it reaches, how hot it runs.
     * Deliberately independent of the flicker step, so one arc keeps its identity
     * from strike to strike instead of being re-rolled every frame.
     */
    static double shape(long seed) {
        return (hash(seed * 7919L) + 1.0) * 0.5;
    }

    /**
     * Vertex {@code i} of a bolt running from {@code s} to {@code e}.
     *
     * <p>The two ends are pinned to the circuit and only the middle snakes — a
     * taper of sin(πt) — so an arc always stays attached to the sigil at one end
     * and to the ground at the other. Most of the offset comes from a per-bolt
     * backbone with a smaller per-step term, so a re-strike wiggles along its
     * previous path instead of teleporting somewhere unrelated.</p>
     *
     * @param out three doubles: x, y, z in the same space as {@code s} and {@code e}
     */
    static void point(double sx, double sy, double sz,
                      double ex, double ey, double ez,
                      int i, long seed, int step, double amplitude, double[] out) {
        double t = (double) i / SEGMENTS;
        double taper = Math.sin(t * Math.PI);
        out[0] = sx + (ex - sx) * t + offset(seed, i, 1, step) * amplitude * taper;
        out[1] = sy + (ey - sy) * t
                + offset(seed, i, 2, step) * amplitude * taper * 0.22;
        out[2] = sz + (ez - sz) * t + offset(seed, i, 3, step) * amplitude * taper;
    }

    private static double offset(long seed, int i, int channel, int step) {
        long base = seed * 131L + i * 7L + channel;
        return hash(base) * 0.72 + hash(base + step * 977L) * 0.28;
    }

    /**
     * Orthonormal basis of the ribbon drawn between two consecutive vertices.
     *
     * <p>{@code side} is perpendicular to both the segment and the ray to the
     * eye, so the ribbon keeps its configured width in screen space instead of
     * collapsing when a span happens to line up with the view. {@code along} is
     * the segment itself, normalised, so the caller can size the quad's long axis
     * separately from its width.</p>
     *
     * @param dx,dy,dz segment direction
     * @param ex,ey,ez direction from the segment's midpoint to the eye
     * @param out      six doubles: side x,y,z then along x,y,z
     */
    static void ribbonBasis(double dx, double dy, double dz,
                            double ex, double ey, double ez, double[] out) {
        double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-9) dl = 1e-9;
        double ux = dx / dl, uy = dy / dl, uz = dz / dl;

        double sx = uy * ez - uz * ey;
        double sy = uz * ex - ux * ez;
        double sz = ux * ey - uy * ex;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-4) {
            // The span points down the sight line. Take the world axis least
            // aligned with it and orthogonalise that: the result can never be
            // shorter than sqrt(2/3) of a unit vector, so no second fallback.
            double ax = 0, ay = 0, az = 0;
            if (Math.abs(ux) <= Math.abs(uy) && Math.abs(ux) <= Math.abs(uz)) {
                ax = 1;
            } else if (Math.abs(uy) <= Math.abs(uz)) {
                ay = 1;
            } else {
                az = 1;
            }
            double dot = ax * ux + ay * uy + az * uz;
            sx = ax - ux * dot;
            sy = ay - uy * dot;
            sz = az - uz * dot;
            sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        }
        out[0] = sx / sl;
        out[1] = sy / sl;
        out[2] = sz / sl;
        out[3] = ux;
        out[4] = uy;
        out[5] = uz;
    }

    /** Deterministic 53-bit hash mapped to −1..1. */
    private static double hash(long n) {
        long x = n * 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 11) * 0x1.0p-52 - 1.0;
    }
}
