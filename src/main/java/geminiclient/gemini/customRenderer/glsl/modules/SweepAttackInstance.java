package geminiclient.gemini.customRenderer.glsl.modules;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Single sweeping attack VFX instance.
 *
 * <h3>Animation timeline (1.2s total)</h3>
 * <ul>
 *   <li>0.00–0.15s: Arc sweep-in (0→1, fast expansion)</li>
 *   <li>0.00–0.80s: Particles active (alpha 1→0)</li>
 *   <li>0.00–1.00s: Photon ring expansion (0→3.0 radius)</li>
 *   <li>0.00–1.20s: Lightning decay + overall fade-out</li>
 * </ul>
 */
public class SweepAttackInstance {

    // ── Constants ─────────────────────────────────────────────────
    private static final float TOTAL_DURATION_MS = 1800f;  // extended for fade-out
    private static final float SWEEP_DURATION_MS = 150f;
    private static final float PARTICLE_DURATION_MS = 1000f;
    private static final float RING_DURATION_MS = 1200f;
    private static final float LIGHTNING_DURATION_MS = 1400f;
    private static final float RING_MAX_RADIUS = 3.0f;
    private static final float FADE_OUT_START_MS = 1000f;  // when fade-out begins

    // ── Particle data ─────────────────────────────────────────────
    public static final int MAX_PARTICLES = 64;

    public final float[] particleX     = new float[MAX_PARTICLES];
    public final float[] particleY     = new float[MAX_PARTICLES];
    public final float[] particleZ     = new float[MAX_PARTICLES];
    public final float[] particleVX    = new float[MAX_PARTICLES];
    public final float[] particleVY    = new float[MAX_PARTICLES];
    public final float[] particleVZ    = new float[MAX_PARTICLES];
    public final float[] particleLife  = new float[MAX_PARTICLES];
    public final float[] particleMaxLife = new float[MAX_PARTICLES];
    public final float[] particleSize  = new float[MAX_PARTICLES];
    public int particleCount;

    // ── Lightning bolt data ───────────────────────────────────────
    public static final int MAX_BOLTS = 6;
    public static final int MAX_BOLT_SEGMENTS = 5;

    /** Bolt endpoints: [bolt][segment] */
    public final float[][] boltX = new float[MAX_BOLTS][MAX_BOLT_SEGMENTS];
    public final float[][] boltY = new float[MAX_BOLTS][MAX_BOLT_SEGMENTS];
    public final float[][] boltZ = new float[MAX_BOLTS][MAX_BOLT_SEGMENTS];
    public int boltCount;

    // ── Core state ────────────────────────────────────────────────
    public final long startTimeMs;
    public final double x, y, z;
    public final float dirX, dirZ;
    public final float arcStart, arcEnd;
    public boolean alive;

    private static final Random RAND = new Random();

    // ── Constructor ───────────────────────────────────────────────

    public SweepAttackInstance(Vec3 position, float dirX, float dirZ,
                                float arcStart, float arcEnd) {
        this.startTimeMs = System.currentTimeMillis();
        this.x = position.x;
        this.y = position.y + 1.0; // waist height
        this.z = position.z;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.arcStart = arcStart;
        this.arcEnd = arcEnd;
        this.alive = true;

        spawnParticles();
        spawnLightning();
    }

    // ── Animation queries ─────────────────────────────────────────

    /** Overall effect age in seconds. */
    public float ageSeconds(long nowMs) {
        return (nowMs - startTimeMs) / 1000f;
    }

    /** Overall progress 0→1 over TOTAL_DURATION_MS. */
    public float effectProgress(long nowMs) {
        return clamp((nowMs - startTimeMs) / TOTAL_DURATION_MS, 0f, 1f);
    }

    /** Arc sweep progress 0→1 (fast expansion in first 150ms). */
    public float sweepProgress(long nowMs) {
        return clamp((nowMs - startTimeMs) / SWEEP_DURATION_MS, 0f, 1f);
    }

    /** Particle alpha — smooth decay over PARTICLE_DURATION_MS. */
    public float particleAlpha(long nowMs) {
        float t = clamp((nowMs - startTimeMs) / PARTICLE_DURATION_MS, 0f, 1f);
        return 1f - smoothstep(0.4f, 1.0f, t);
    }

    /** Photon ring radius — expands from 0 to RING_MAX_RADIUS. */
    public float ringRadius(long nowMs) {
        float t = clamp((nowMs - startTimeMs) / RING_DURATION_MS, 0f, 1f);
        return smoothstep(0f, 1f, t) * RING_MAX_RADIUS;
    }

    /** Photon ring alpha — fades out in the last phase. */
    public float ringAlpha(long nowMs) {
        float elapsed = nowMs - startTimeMs;
        if (elapsed < FADE_OUT_START_MS) return 1f;
        float t = clamp((elapsed - FADE_OUT_START_MS) / (TOTAL_DURATION_MS - FADE_OUT_START_MS), 0f, 1f);
        return 1f - smoothstep(0.0f, 0.8f, t);
    }

    /** Lightning alpha — decays over LIGHTNING_DURATION_MS. */
    public float lightningAlpha(long nowMs) {
        float t = clamp((nowMs - startTimeMs) / LIGHTNING_DURATION_MS, 0f, 1f);
        return 1f - smoothstep(0.3f, 1.0f, t);
    }

    /** Arc alpha — full during sweep + active phase, smooth fade-out at the end. */
    public float arcAlpha(long nowMs) {
        float elapsed = nowMs - startTimeMs;
        // Fade-in during sweep (0-150ms)
        float fadeIn = clamp(elapsed / SWEEP_DURATION_MS, 0f, 1f);
        // Fade-out starting at FADE_OUT_START_MS
        float fadeOut = 1f;
        if (elapsed > FADE_OUT_START_MS) {
            float t = clamp((elapsed - FADE_OUT_START_MS) / (TOTAL_DURATION_MS - FADE_OUT_START_MS), 0f, 1f);
            fadeOut = 1f - smoothstep(0.0f, 0.9f, t);
        }
        return fadeIn * fadeOut;
    }

    /** Whether the effect is still alive. */
    public boolean isAlive(long nowMs) {
        return (nowMs - startTimeMs) < TOTAL_DURATION_MS;
    }

    // ── Particle spawn ────────────────────────────────────────────

    private void spawnParticles() {
        particleCount = MAX_PARTICLES;
        float angleRange = arcEnd - arcStart;
        if (angleRange < 0) angleRange += (float)(2.0 * Math.PI);

        for (int i = 0; i < MAX_PARTICLES; i++) {
            // Position: along the arc
            float t = RAND.nextFloat();
            float angle = arcStart + angleRange * t;
            float radius = 1.5f + RAND.nextFloat() * 1.0f;

            particleX[i] = (float)(x + Math.cos(angle) * radius);
            particleY[i] = (float)(y + RAND.nextFloat() * 0.5);
            particleZ[i] = (float)(z + Math.sin(angle) * radius);

            // Velocity: slash direction + random cone
            float speed = 3.0f + RAND.nextFloat() * 5.0f;
            float spread = 0.5f + RAND.nextFloat() * 0.5f;
            particleVX[i] = dirX * speed + (RAND.nextFloat() - 0.5f) * spread * speed;
            particleVY[i] = (RAND.nextFloat() - 0.3f) * speed * 0.5f;
            particleVZ[i] = dirZ * speed + (RAND.nextFloat() - 0.5f) * spread * speed;

            // Lifetime
            particleMaxLife[i] = 0.3f + RAND.nextFloat() * 0.5f;
            particleLife[i] = particleMaxLife[i];

            // Size
            particleSize[i] = 0.08f + RAND.nextFloat() * 0.12f;
        }
    }

    // ── Lightning spawn ───────────────────────────────────────────

    private void spawnLightning() {
        boltCount = MAX_BOLTS;
        float angleRange = arcEnd - arcStart;
        if (angleRange < 0) angleRange += (float)(2.0 * Math.PI);

        for (int b = 0; b < MAX_BOLTS; b++) {
            // Start from a random point on the arc
            float baseAngle = arcStart + angleRange * RAND.nextFloat();
            float baseRadius = 1.2f + RAND.nextFloat() * 0.8f;

            float cx = (float)(x + Math.cos(baseAngle) * baseRadius);
            float cy = (float)(y + 0.5f + RAND.nextFloat() * 1.0f);
            float cz = (float)(z + Math.sin(baseAngle) * baseRadius);

            int segments = 3 + RAND.nextInt(3); // 3-5 segments
            for (int s = 0; s < MAX_BOLT_SEGMENTS; s++) {
                if (s < segments) {
                    boltX[b][s] = cx;
                    boltY[b][s] = cy;
                    boltZ[b][s] = cz;

                    // Random offset for next segment
                    cx += (RAND.nextFloat() - 0.5f) * 0.8f;
                    cy += (RAND.nextFloat() - 0.3f) * 0.6f;
                    cz += (RAND.nextFloat() - 0.5f) * 0.8f;
                } else {
                    // Duplicate last point for unused segments
                    boltX[b][s] = boltX[b][segments - 1];
                    boltY[b][s] = boltY[b][segments - 1];
                    boltZ[b][s] = boltZ[b][segments - 1];
                }
            }
        }
    }

    // ── Particle update ───────────────────────────────────────────

    public void updateParticles(float dt) {
        for (int i = 0; i < particleCount; i++) {
            particleLife[i] -= dt;
            if (particleLife[i] <= 0) continue;

            particleX[i] += particleVX[i] * dt;
            particleY[i] += particleVY[i] * dt;
            particleZ[i] += particleVZ[i] * dt;

            // Gravity
            particleVY[i] -= 3.0f * dt;

            // Drag
            particleVX[i] *= 0.98f;
            particleVZ[i] *= 0.98f;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
