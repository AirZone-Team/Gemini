package geminiclient.gemini.customRenderer.glsl.modules;

/**
 * Spring-damper follow rig for the black hole pet.
 *
 * <p>The pet never rides its anchor rigidly: a shoulder-mounted hole that
 * snapped to the player's every turn would look like a HUD element pinned in
 * the world. Position and disk orientation are therefore integrated as damped
 * springs, so the hole swings wide on a hard turn, overshoots once, and settles
 * — and the accretion disk keeps its angular momentum while the player spins
 * under it.</p>
 *
 * <p>Deliberately free of Minecraft and JOML types so the integration can be
 * exercised in a plain JVM test, the same way {@code SlugGeometry} and
 * {@code ThaumaturgySigilGeometry} are.</p>
 */
public final class BlackHoleFollow {

    /** Above this gap the anchor is treated as a teleport and the pet snaps. */
    public static final float MAX_GRAB_DISTANCE = 6.0f;

    /** Longest step the integrator will run at once; longer frames sub-step. */
    private static final float MAX_TIMESTEP = 1.0f / 60.0f;

    public float x;
    public float y;
    public float z;
    public float vx;
    public float vy;
    public float vz;

    /** Azimuth of the disk plane, radians, unbounded. */
    public float diskYaw;
    public float diskYawVelocity;

    /** How far the disk leans out of the horizontal plane, radians. */
    public float lean;
    public float leanVelocity;

    /** Accumulated idle orbit around the anchor, radians. */
    public float orbitPhase;

    /** Seconds spent chasing an anchor that moved. Drives the flare. */
    public float strain;

    private boolean primed;

    /**
     * Place the pet directly on its anchor. Used on enable, respawn and after a
     * teleport so the first frame has no gap to close.
     */
    public void snapTo(float targetX, float targetY, float targetZ) {
        x = targetX;
        y = targetY;
        z = targetZ;
        vx = vy = vz = 0f;
        strain = 0f;
        primed = true;
    }

    public void reset() {
        primed = false;
        vx = vy = vz = 0f;
        diskYawVelocity = 0f;
        leanVelocity = 0f;
        strain = 0f;
    }

    /**
     * Integrate one frame.
     *
     * @param deltaTime    seconds since the previous frame
     * @param targetX      anchor the pet is chasing
     * @param stiffness    spring constant of the position follow, 1/s²
     * @param damping      velocity bleed, 1/s
     * @param yawTarget    disk azimuth the pet tries to keep, radians
     * @param leanTarget   disk lean from horizontal, radians
     * @param orbitSpeed   idle drift around the anchor, rad/s
     */
    public void step(float deltaTime, float targetX, float targetY, float targetZ,
                     float stiffness, float damping,
                     float yawTarget, float yawStiffness, float yawDamping,
                     float leanTarget, float leanStiffness, float leanDamping,
                     float orbitSpeed) {
        if (deltaTime <= 0f) return;
        if (!primed) snapTo(targetX, targetY, targetZ);

        float gap = distanceTo(targetX, targetY, targetZ);
        if (gap > MAX_GRAB_DISTANCE) {
            // Teleport, dimension change, or a render distance pop: don't fly
            // across the map, which would draw a comet tail out of the pet.
            snapTo(targetX, targetY, targetZ);
        }

        float remaining = deltaTime;
        while (remaining > 1e-6f) {
            float dt = Math.min(remaining, MAX_TIMESTEP);
            remaining -= dt;
            integrate(dt, targetX, targetY, targetZ, stiffness, damping,
                    yawTarget, yawStiffness, yawDamping,
                    leanTarget, leanStiffness, leanDamping);
        }

        orbitPhase += orbitSpeed * deltaTime;
        if (orbitPhase > Math.PI * 2.0) orbitPhase -= Math.PI * 2.0;

        // Strain is how hard the pet is being dragged: it decays when the
        // anchor is calm and spikes on a teleport-adjacent chase.
        float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        float target = Math.clamp(speed / 6.0f + gap / 3.0f, 0f, 1f);
        strain += (target - strain) * Math.min(1f, deltaTime * 4.5f);
    }

    private void integrate(float dt, float tx, float ty, float tz,
                           float stiffness, float damping,
                           float yawTarget, float yawStiffness, float yawDamping,
                           float leanTarget, float leanStiffness, float leanDamping) {
        vx += (tx - x) * stiffness * dt;
        vy += (ty - y) * stiffness * dt;
        vz += (tz - z) * stiffness * dt;
        float bleed = (float) Math.exp(-damping * dt);
        vx *= bleed;
        vy *= bleed;
        vz *= bleed;
        x += vx * dt;
        y += vy * dt;
        z += vz * dt;

        diskYawVelocity += wrapAngle(yawTarget - diskYaw) * yawStiffness * dt;
        diskYawVelocity *= (float) Math.exp(-yawDamping * dt);
        diskYaw += diskYawVelocity * dt;

        leanVelocity += (leanTarget - lean) * leanStiffness * dt;
        leanVelocity *= (float) Math.exp(-leanDamping * dt);
        lean += leanVelocity * dt;
    }

    public float distanceTo(float tx, float ty, float tz) {
        float dx = tx - x, dy = ty - y, dz = tz - z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Speed of the chase in blocks/second, for the accretion flare. */
    public float chaseSpeed() {
        return (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
    }

    /** Shortest signed angle between two unwrapped azimuths. */
    public static float wrapAngle(float angle) {
        float twoPi = (float) (Math.PI * 2.0);
        float wrapped = angle % twoPi;
        if (wrapped > Math.PI) wrapped -= twoPi;
        if (wrapped < -Math.PI) wrapped += twoPi;
        return wrapped;
    }

    /**
     * Orthonormal basis of the disk plane: {@code out[0..2]} = e1 (the node
     * line, which carries the player's heading), {@code out[3..5]} = e2, and
     * {@code out[6..8]} = the rotation axis the jets are threaded on.
     *
     * <p>The lean is applied about e1, so the disk nods where the player looks
     * but keeps its own azimuth between frames.</p>
     */
    public void basis(float[] out) {
        float cy = (float) Math.cos(diskYaw), sy = (float) Math.sin(diskYaw);
        float cl = (float) Math.cos(lean), sl = (float) Math.sin(lean);

        out[0] = cy;
        out[1] = 0f;
        out[2] = -sy;

        out[3] = -sy * cl;
        out[4] = sl;
        out[5] = -cy * cl;

        // axis = e1 × e2, which points where the jets shoot.
        out[6] = sy * sl;
        out[7] = cl;
        out[8] = cy * sl;
    }
}
