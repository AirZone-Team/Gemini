package geminiclient.gemini.customRenderer.glsl.modules;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Immutable spawn data plus lightweight CPU particles for one sweeping attack.
 * Timing and emission settings are snapshotted so changing the GUI never makes
 * an already-running slash jump or pop.
 */
public final class SweepAttackInstance {

    public static final int MAX_PARTICLES = 128;
    public static final int MAX_BOLTS = 12;
    public static final int MAX_BOLT_SEGMENTS = 7;

    public final float[] particleX = new float[MAX_PARTICLES];
    public final float[] particleY = new float[MAX_PARTICLES];
    public final float[] particleZ = new float[MAX_PARTICLES];
    public final float[] particleVX = new float[MAX_PARTICLES];
    public final float[] particleVY = new float[MAX_PARTICLES];
    public final float[] particleVZ = new float[MAX_PARTICLES];
    public final float[] particleLife = new float[MAX_PARTICLES];
    public final float[] particleMaxLife = new float[MAX_PARTICLES];
    public final float[] particleSize = new float[MAX_PARTICLES];
    public int particleCount;

    public final float[][] boltX = new float[MAX_BOLTS][MAX_BOLT_SEGMENTS];
    public final float[][] boltY = new float[MAX_BOLTS][MAX_BOLT_SEGMENTS];
    public final float[][] boltZ = new float[MAX_BOLTS][MAX_BOLT_SEGMENTS];
    public final int[] boltSegments = new int[MAX_BOLTS];
    public int boltCount;

    public final long startTimeMs;
    public final double x;
    public final double y;
    public final double z;
    public final float dirX;
    public final float dirZ;
    public final float arcStart;
    public final float arcEnd;
    public final float seed;
    public boolean alive = true;

    private final float totalDurationMs;
    private final float sweepDurationMs;
    private final float particleDurationMs;
    private final float ringDurationMs;
    private final float fadeOutStartMs;
    private final float particleGravity;

    public SweepAttackInstance(
            Vec3 position,
            float dirX,
            float dirZ,
            float arcStart,
            float arcEnd,
            float heightOffset,
            int durationMs,
            float animationSpeed,
            int particles,
            float particleSpeed,
            float particleSpread,
            float particleSize,
            float gravity,
            int lightningBolts
    ) {
        this.startTimeMs = System.currentTimeMillis();
        this.x = position.x;
        this.y = position.y + heightOffset;
        this.z = position.z;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.arcStart = arcStart;
        this.arcEnd = arcEnd;

        float speed = Math.max(0.1f, animationSpeed);
        this.totalDurationMs = Math.max(120f, durationMs / speed);
        this.sweepDurationMs = Math.max(55f, totalDurationMs * 0.18f);
        this.particleDurationMs = totalDurationMs * 0.72f;
        this.ringDurationMs = totalDurationMs * 0.78f;
        this.fadeOutStartMs = totalDurationMs * 0.48f;
        this.particleGravity = gravity;

        long mixedSeed = startTimeMs
                ^ Double.doubleToLongBits(position.x * 17.0 + position.z * 31.0)
                ^ ((long) Float.floatToIntBits(arcStart) << 32);
        Random random = new Random(mixedSeed);
        this.seed = random.nextFloat() * 1024f;

        spawnParticles(random, particles, particleSpeed, particleSpread, particleSize);
        spawnLightning(random, lightningBolts);
    }

    public float ageSeconds(long nowMs) {
        return Math.max(0f, nowMs - startTimeMs) / 1000f;
    }

    public float effectProgress(long nowMs) {
        return clamp((nowMs - startTimeMs) / totalDurationMs, 0f, 1f);
    }

    public float sweepProgress(long nowMs) {
        float t = clamp((nowMs - startTimeMs) / sweepDurationMs, 0f, 1f);
        return 1f - (float) Math.pow(1f - t, 3.2);
    }

    public float particleAlpha(long nowMs) {
        float t = clamp((nowMs - startTimeMs) / particleDurationMs, 0f, 1f);
        return 1f - smoothstep(0.25f, 1f, t);
    }

    public float ringProgress(long nowMs) {
        float t = clamp((nowMs - startTimeMs) / ringDurationMs, 0f, 1f);
        return 1f - (float) Math.pow(1f - t, 2.4);
    }

    public float ringAlpha(long nowMs) {
        float t = effectProgress(nowMs);
        return smoothstep(0f, 0.08f, t) * (1f - smoothstep(0.52f, 1f, t));
    }

    public float lightningAlpha(long nowMs) {
        float t = effectProgress(nowMs);
        float flicker = 0.78f + 0.22f * (float) Math.sin(ageSeconds(nowMs) * 54f + seed);
        return (1f - smoothstep(0.18f, 0.82f, t)) * flicker;
    }

    public float arcAlpha(long nowMs) {
        float elapsed = nowMs - startTimeMs;
        float fadeIn = smoothstep(0f, Math.min(90f, sweepDurationMs), elapsed);
        float fadeOut = 1f;
        if (elapsed > fadeOutStartMs) {
            fadeOut = 1f - smoothstep(fadeOutStartMs, totalDurationMs, elapsed);
        }
        return fadeIn * fadeOut;
    }

    public float burstAlpha(long nowMs) {
        float t = effectProgress(nowMs);
        return (1f - smoothstep(0.08f, 0.46f, t))
                * smoothstep(0f, 0.035f, t);
    }

    public boolean isAlive(long nowMs) {
        return nowMs - startTimeMs < totalDurationMs;
    }

    private void spawnParticles(Random random, int requestedCount, float speed,
                                float spread, float size) {
        particleCount = Math.min(Math.max(requestedCount, 0), MAX_PARTICLES);
        float angleRange = positiveAngleRange();

        for (int i = 0; i < particleCount; i++) {
            float t = random.nextFloat();
            float angle = arcStart + angleRange * t;
            float radius = 0.7f + random.nextFloat() * 1.45f;

            particleX[i] = (float) (x + Math.cos(angle) * radius);
            particleY[i] = (float) (y + (random.nextFloat() - 0.35f) * 0.55f);
            particleZ[i] = (float) (z + Math.sin(angle) * radius);

            float velocity = speed * (0.62f + random.nextFloat() * 0.76f);
            float cone = Math.max(0f, spread);
            particleVX[i] = dirX * velocity + (random.nextFloat() - 0.5f) * cone * velocity;
            particleVY[i] = (random.nextFloat() - 0.2f) * velocity * cone * 0.55f;
            particleVZ[i] = dirZ * velocity + (random.nextFloat() - 0.5f) * cone * velocity;

            particleMaxLife[i] = 0.24f + random.nextFloat() * 0.62f;
            particleLife[i] = particleMaxLife[i];
            particleSize[i] = size * (0.55f + random.nextFloat() * 0.9f);
        }
    }

    private void spawnLightning(Random random, int requestedBolts) {
        boltCount = Math.min(Math.max(requestedBolts, 0), MAX_BOLTS);
        float angleRange = positiveAngleRange();

        for (int b = 0; b < boltCount; b++) {
            float baseAngle = arcStart + angleRange * random.nextFloat();
            float baseRadius = 0.8f + random.nextFloat() * 1.55f;
            float tangentX = (float) -Math.sin(baseAngle);
            float tangentZ = (float) Math.cos(baseAngle);

            float cx = (float) (x + Math.cos(baseAngle) * baseRadius);
            float cy = (float) (y + (random.nextFloat() - 0.25f) * 0.7f);
            float cz = (float) (z + Math.sin(baseAngle) * baseRadius);

            int segments = 4 + random.nextInt(MAX_BOLT_SEGMENTS - 3);
            boltSegments[b] = segments;
            for (int s = 0; s < segments; s++) {
                boltX[b][s] = cx;
                boltY[b][s] = cy;
                boltZ[b][s] = cz;
                float stride = 0.24f + random.nextFloat() * 0.32f;
                cx += tangentX * stride + (random.nextFloat() - 0.5f) * 0.34f;
                cy += (random.nextFloat() - 0.45f) * 0.38f;
                cz += tangentZ * stride + (random.nextFloat() - 0.5f) * 0.34f;
            }
        }
    }

    public void updateParticles(float dt) {
        for (int i = 0; i < particleCount; i++) {
            particleLife[i] -= dt;
            if (particleLife[i] <= 0f) continue;

            particleX[i] += particleVX[i] * dt;
            particleY[i] += particleVY[i] * dt;
            particleZ[i] += particleVZ[i] * dt;
            particleVY[i] -= particleGravity * dt;

            float drag = (float) Math.pow(0.965f, dt * 20f);
            particleVX[i] *= drag;
            particleVY[i] *= drag;
            particleVZ[i] *= drag;
        }
    }

    private float positiveAngleRange() {
        float range = arcEnd - arcStart;
        if (range < 0f) range += (float) (Math.PI * 2.0);
        return range;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / Math.max(edge1 - edge0, 0.0001f), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
