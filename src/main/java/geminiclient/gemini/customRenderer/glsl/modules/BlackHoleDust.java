package geminiclient.gemini.customRenderer.glsl.modules;

/**
 * Matter falling into the pet's hole.
 *
 * <p>Each mote rides a decaying circular orbit rather than being integrated
 * under gravity: its state is a radius, an azimuth and a decay rate, so the
 * field stays bounded no matter how long the module has been running or how
 * large the frame-time spikes get. Angular speed follows the same
 * {@code ω ∝ n^-3/2} Kepler law the disk shader rotates its filaments with, so
 * the motes and the plasma they trail along move as one body.</p>
 *
 * <p>Positions are stored relative to the hole and one step behind, which is
 * what lets the renderer stretch each mote along the path it actually flew
 * instead of guessing a tangent.</p>
 *
 * <p>No Minecraft or JOML types, so the orbital bookkeeping can be exercised in
 * a plain JVM test.</p>
 */
public final class BlackHoleDust {

    /** Radii are measured in Schwarzschild radii; this is where motes are born. */
    public static final float SPAWN_RADIUS = 5.6f;

    public final int capacity;
    public int count;

    /** Current and previous local position, blocks from the hole. */
    public final float[] x;
    public final float[] y;
    public final float[] z;
    public final float[] previousX;
    public final float[] previousY;
    public final float[] previousZ;

    /** Orbital state. */
    private final float[] radius;
    private final float[] azimuth;
    private final float[] decay;
    private final float[] phase;
    private final float[] wobble;

    /** 0 at the outer edge → 1 where the mote disappears over the horizon. */
    public final float[] temperature;
    public final float[] glow;

    private long seed = 0x5EED1234L;

    /** False until every mote has been placed once, so no trail is invented. */
    private boolean positioned;

    public BlackHoleDust(int capacity) {
        this.capacity = Math.max(1, capacity);
        int n = this.capacity;
        x = new float[n];
        y = new float[n];
        z = new float[n];
        previousX = new float[n];
        previousY = new float[n];
        previousZ = new float[n];
        radius = new float[n];
        azimuth = new float[n];
        decay = new float[n];
        phase = new float[n];
        wobble = new float[n];
        temperature = new float[n];
        glow = new float[n];
    }

    /** Re-scatter the field, used when the module enables or the count grows. */
    public void reseed(int activeCount) {
        count = Math.clamp(activeCount, 0, capacity);
        positioned = false;
        for (int i = 0; i < count; i++) {
            // Spread the first generation along the whole inflow path instead of
            // letting everything arrive at the horizon in one synchronized ring.
            radius[i] = 1.0f + (SPAWN_RADIUS - 1.0f) * nextRandom();
            azimuth[i] = nextRandom() * (float) (Math.PI * 2.0);
            decay[i] = 0.05f + nextRandom() * 0.16f;
            phase[i] = nextRandom() * (float) (Math.PI * 2.0);
            wobble[i] = (nextRandom() - 0.5f) * 0.16f;
            temperature[i] = 0f;
            glow[i] = 0f;
        }
    }

    /**
     * Advance one frame.
     *
     * @param deltaTime  seconds
     * @param horizonRadius the pet's Schwarzschild radius in blocks
     * @param spinSpeed  angular speed of the inner disk, rad/s
     * @param innerEdge  radius where a mote is swallowed, in Rs
     * @param outerEdge  radius where the visible disk ends, in Rs
     * @param basis      nine floats: e1, e2, axis, as built by {@link BlackHoleFollow#basis}
     */
    public void step(float deltaTime, float horizonRadius, float spinSpeed,
                     float innerEdge, float outerEdge, float[] basis) {
        if (deltaTime <= 0f || count == 0) return;
        float e1x = basis[0], e1y = basis[1], e1z = basis[2];
        float e2x = basis[3], e2y = basis[4], e2z = basis[5];
        float ax = basis[6], ay = basis[7], az = basis[8];
        float span = Math.max(outerEdge - innerEdge, 0.001f);

        for (int i = 0; i < count; i++) {
            float n = radius[i];
            // Kepler: one orbit near the horizon takes a fraction of the time
            // one out at the edge takes.
            float omega = spinSpeed * 3.2f * (float) Math.pow(Math.max(n, 1.05f), -1.5);
            azimuth[i] += omega * deltaTime;

            // Viscous inflow: angular momentum leaks away and the orbit shrinks,
            // slowly far out and then very fast near the capture radius.
            float inward = decay[i] * deltaTime / Math.max(n * 0.42f, 0.42f);
            n -= inward;
            if (n <= innerEdge || !Float.isFinite(n)) {
                n = SPAWN_RADIUS * (0.82f + 0.3f * nextRandom());
                azimuth[i] = nextRandom() * (float) (Math.PI * 2.0);
                decay[i] = 0.05f + nextRandom() * 0.16f;
                wobble[i] = (nextRandom() - 0.5f) * 0.16f;
            }
            radius[i] = n;

            float heat = 1f - Math.clamp((n - innerEdge) / span, 0f, 1f);
            temperature[i] = heat * heat;
            // A mote flashes once just before it is gone, which is what sells
            // the horizon as a one-way surface.
            glow[i] = 0.25f + temperature[i] * 0.75f
                    + (n < innerEdge * 1.18f ? 1.6f : 0f);

            previousX[i] = x[i];
            previousY[i] = y[i];
            previousZ[i] = z[i];

            float cos = (float) Math.cos(azimuth[i]);
            float sin = (float) Math.sin(azimuth[i]);
            float height = wobble[i] * (float) Math.sin(phase[i] + n * 0.8f);
            float r = n * horizonRadius;
            x[i] = (e1x * cos + e2x * sin) * r + ax * height * horizonRadius;
            y[i] = (e1y * cos + e2y * sin) * r + ay * height * horizonRadius;
            z[i] = (e1z * cos + e2z * sin) * r + az * height * horizonRadius;

            if (!positioned) {
                // The first frame has no history. Without this the trail would
                // be measured from the origin of the field, one giant streak.
                previousX[i] = x[i];
                previousY[i] = y[i];
                previousZ[i] = z[i];
            }
        }
        positioned = true;
    }

    /** Deterministic so a test run reproduces a given generation exactly. */
    private float nextRandom() {
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        return ((seed >>> 40) & 0xFFFFFF) / (float) 0x1000000;
    }
}
