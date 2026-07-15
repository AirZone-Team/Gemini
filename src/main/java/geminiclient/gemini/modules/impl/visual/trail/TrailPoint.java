package geminiclient.gemini.modules.impl.visual.trail;

import net.minecraft.world.phys.Vec3;

/**
 * Single point in a ribbon trail.
 *
 * <p>Each point records the world-space position at capture time and
 * carries a lifetime for fade-out calculations.</p>
 */
public class TrailPoint {

    /** World-space position at capture time. */
    public final Vec3 pos;

    /** Elapsed time since capture (seconds). */
    public float age;

    /** Total lifetime before the point expires (seconds). */
    public final float life;

    public TrailPoint(Vec3 pos, float life) {
        this.pos = pos;
        this.life = life;
        this.age = 0f;
    }

    /** Normalized fade alpha: 1.0 at birth → 0.0 at death. */
    public float alpha() {
        if (life <= 0f) return 1f;
        float a = 1f - age / life;
        return a < 0f ? 0f : a;
    }

    /** Whether this point has exceeded its lifetime. */
    public boolean expired() {
        return age >= life;
    }

    /** Advance age by delta (called each tick). */
    public void tick(float dt) {
        age += dt;
    }
}
