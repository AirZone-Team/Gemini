package geminiclient.gemini.customRenderer.glsl.modules;

/**
 * Lightweight particle state for the instanced particle system.
 *
 * <p>Particles are simulated on the CPU each tick and batched into a single
 * vertex buffer for GPU rendering. {@link #type} picks the sigil drawn by
 * {@code particle_instanced.frag.slang}; {@link #rotation} is folded into the
 * billboard basis rather than sent as an angle, because the shader's shape
 * fields are isotropic and a quantised angle would stutter.</p>
 */
public class ParticleData {

    // ── World-space position ─────────────────────────────────────
    public float x, y, z;

    // ── Velocity ────────────────────────────────────────────────
    public float vx, vy, vz;

    // ── Lifecycle ─────────────────────────────────────────────────
    public float age;
    public float life;

    // ── Visual ───────────────────────────────────────────────────
    public float size;

    /** Sigil palette, sent verbatim as the vertex colour. */
    public float r, g, b;

    /** Billboard roll in radians, advanced by {@link #spinSpeed} each tick. */
    public float rotation;
    public float spinSpeed;

    /** 0..1 decorrelation seed so simultaneous sigils do not animate in lockstep. */
    public float seed;

    // ── Type (0=RUNE, 1=HEXAGON, 2=TRIANGLE, 3=FEATHER, 4=STARLIGHT) ─
    public byte type;

    /** Glyph/facet pick inside a shape, 0..7. */
    public byte variant;

    /** Trailing echo layers, 1..4. */
    public byte echoCount;

    /** Whether this particle throws radial rays for its first beat. */
    public boolean burst;

    // ── Internal ──────────────────────────────────────────────────
    public boolean alive = true;

    public ParticleData(float x, float y, float z,
                        float vx, float vy, float vz,
                        float life, float size,
                        float r, float g, float b,
                        float rotation, float spinSpeed, float seed,
                        byte type, byte variant, byte echoCount, boolean burst) {
        this.x = x; this.y = y; this.z = z;
        this.vx = vx; this.vy = vy; this.vz = vz;
        this.life = life;
        this.age = 0f;
        this.size = size;
        this.r = r; this.g = g; this.b = b;
        this.rotation = rotation;
        this.spinSpeed = spinSpeed;
        this.seed = seed;
        this.type = type;
        this.variant = variant;
        this.echoCount = echoCount;
        this.burst = burst;
    }

    /** Per-tick update. Returns false if expired. */
    public boolean tick(float dt) {
        age += dt;
        if (age >= life) {
            alive = false;
            return false;
        }
        x += vx * dt;
        y += vy * dt;
        z += vz * dt;
        rotation += spinSpeed * dt;
        return true;
    }

    /** Life progress 0..1, packed into the vertex alpha. */
    public float progress() {
        return life <= 0f ? 1f : age / life;
    }
}
