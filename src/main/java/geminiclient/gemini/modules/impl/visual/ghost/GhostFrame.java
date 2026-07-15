package geminiclient.gemini.modules.impl.visual.ghost;

/**
 * A single ghost frame capturing the player's pose at a moment in time.
 *
 * <p>Ghost frames are rendered as translucent, additive-blended billboard
 * silhouettes, creating a cinematic afterimage effect.  Each frame
 * fades from full opacity to zero over its lifetime.</p>
 */
public class GhostFrame {

    // ── World-space position ──────────────────────────────────────
    public final double x, y, z;

    // ── Rotation (degrees) ────────────────────────────────────────
    public final float yRot;
    public final float xRot;
    public final float yBodyRot;
    public final float yHeadRot;

    // ── Attack animation ──────────────────────────────────────────
    public final float attackTime;

    // ── Lifetime ──────────────────────────────────────────────────
    public float age;
    public final float life;
    public final long captureTimeMs;

    public GhostFrame(double x, double y, double z,
                      float yRot, float xRot,
                      float yBodyRot, float yHeadRot,
                      float attackTime,
                      float life) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.xRot = xRot;
        this.yBodyRot = yBodyRot;
        this.yHeadRot = yHeadRot;
        this.attackTime = attackTime;
        this.life = life;
        this.age = 0f;
        this.captureTimeMs = System.currentTimeMillis();
    }

    /** Normalized fade alpha: 1.0 → 0.0. */
    public float alpha() {
        if (life <= 0f) return 1f;
        float a = 1f - age / life;
        return Math.max(a, 0f);
    }

    public boolean expired() {
        return age >= life;
    }

    public void tick(float dt) {
        age += dt;
    }
}
