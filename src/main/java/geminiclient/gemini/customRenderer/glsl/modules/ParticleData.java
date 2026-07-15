package geminiclient.gemini.customRenderer.glsl.modules;

/**
 * Lightweight particle state for the instanced particle system.
 *
 * <p>Particles are simulated on the CPU each tick and batched into
 * a single vertex buffer for GPU rendering.  The fragment shader
 * selects the visual shape based on {@link #type}.</p>
 */
public class ParticleData {

    // ── World-space position ─────────────────────────────────────
    public float x, y, z;

    // ── Velocity ─────────────────────────────────────────────────
    public float vx, vy, vz;

    // ── Lifecycle ─────────────────────────────────────────────────
    public float age;
    public float life;

    // ── Visual ───────────────────────────────────────────────────
    public float size;
    public float r, g, b, a;

    // ── Type (0=RUNE, 1=HEXAGON, 2=TRIANGLE, 3=FEATHER, 4=STARLIGHT) ─
    public byte type;

    // ── Internal ──────────────────────────────────────────────────
    public boolean alive = true;

    public ParticleData(float x, float y, float z,
                        float vx, float vy, float vz,
                        float life, float size,
                        float r, float g, float b, float a,
                        byte type) {
        this.x = x; this.y = y; this.z = z;
        this.vx = vx; this.vy = vy; this.vz = vz;
        this.life = life;
        this.age = 0f;
        this.size = size;
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.type = type;
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
        return true;
    }

    /** Fade alpha based on remaining life. */
    public float alpha() {
        if (life <= 0f) return 0f;
        float f = 1f - age / life;
        // Smooth cubic fade-out
        return f * f * f;
    }
}
