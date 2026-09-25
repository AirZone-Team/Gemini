package geminiclient.gemini.customRenderer.glsl.modules;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Per-entity-death state for the Thaumaturgy Strike Kill Effect (奇术打击).
 *
 * <h3>Timeline (6 stages, ~11.8s total)</h3>
 * <pre>
 * Stage 1: Sigil Summon     0.00 – 3.60s — a giant violet magic circle is drawn element by element
 * Stage 2: Six Pillars      3.60 – 5.80s — six light columns slam down around the victim
 * Stage 3: Charge            5.80 – 6.80s — the pillars drain inward, the sigil spins up
 * Stage 4: Grand Pillar      6.80 – 7.90s — one colossal column falls through the sigil's centre
 * Stage 5: Nova Sphere       7.90 – 10.20s — a blinding light sphere swells at the impact point
 * Stage 6: Dissolve         10.20 – 11.80s — the sphere implodes, the sigil dims and the sky clears
 * </pre>
 *
 * <p>Every envelope is a closed-form function of elapsed time and is C0-continuous
 * across stage boundaries, so nothing pops at a hand-off. The spark system is the
 * only stateful sub-system; it is advanced once per tick.</p>
 */
public class ThaumaturgyEffectInstance {

    /** Timeline stage identifiers for the Thaumaturgy Strike mode. */
    public static final int STAGE_SIGIL_SUMMON = 1;
    public static final int STAGE_SIX_PILLARS  = 2;
    public static final int STAGE_CHARGE       = 3;
    public static final int STAGE_GRAND_PILLAR = 4;
    public static final int STAGE_NOVA_SPHERE  = 5;
    public static final int STAGE_DISSOLVE     = 6;

    // Timeline boundaries (seconds)
    private static final double T_SUMMON_END  = 3.60;
    private static final double T_PILLARS_END = 5.80;
    private static final double T_CHARGE_END  = 6.80;
    private static final double T_GRAND_END   = 7.90;
    private static final double T_SPHERE_END  = 10.20;
    private static final double T_TOTAL       = 11.80;

    /** The summon script eases in after this delay and finishes this long after it. */
    private static final double SCRIPT_START = 0.15;
    private static final double SCRIPT_LENGTH = 2.75;

    /**
     * Where each figure sits in {@link #sigilReveal}: the outer rim rings are
     * drawn first, then the rune band, the triquetra, the star seals and last
     * the sun rosette. The fragment shader reads the same four boundaries, and
     * the renderer caps a plane's reveal at one of them to leave that plane
     * showing only the figures below it.
     */
    public static final float SCRIPT_RIM_END = 0.22f;
    public static final float SCRIPT_RUNE_END = 0.42f;
    public static final float SCRIPT_TRI_END = 0.68f;
    public static final float SCRIPT_SEAL_END = 0.88f;

    /** Upper bound on the configurable pillar count. */
    public static final int MAX_PILLARS = 12;

    /** Radius of the hexagonal pillar ring, in blocks. */
    public static final float PILLAR_RING_RADIUS = 7.5f;

    /** Seconds the centre column takes to fall from the sigil to the ground. */
    private static final double GRAND_FALL_SEC = 0.30;

    // ── Core state ─────────────────────────────────────────────────

    public Vec3 position;              // mutable for AoE merge re-centering
    public final long startTimeMs;
    public boolean alive = true;
    public int mergeCount = 1;

    // ── Pillar layout (generated once at spawn) ────────────────────

    public final int pillarCount;
    /** Azimuth of each pillar on the ring (radians). */
    public final float[] pillarAzimuth;
    /** Descent start offset per pillar (seconds). */
    public final float[] pillarDelay;
    /** Width multiplier per pillar. */
    public final float[] pillarWidth;
    /** Per-pillar shimmer seed (0..1). */
    public final float[] pillarSeed;

    // ── Spark system ───────────────────────────────────────────────

    private static final int MAX_SPARKS = 900;
    private static final Random RNG = new Random();

    /** [x, y, z, vx, vy, vz, life, maxLife, kind] × MAX_SPARKS */
    private final float[] sparkData = new float[MAX_SPARKS * 9];
    private int sparkCount;

    /** Spark kinds, decoded by {@link #fillSparkBatch}. */
    private static final int KIND_MOTE   = 0;   // slow rising violet mote
    private static final int KIND_IMPACT = 1;   // fast radial burst off a pillar foot
    private static final int KIND_INWARD = 2;   // energy streaming into the centre
    private static final int KIND_DEBRIS = 3;   // long arcs thrown by the nova sphere

    // ── Construction ───────────────────────────────────────────────

    public ThaumaturgyEffectInstance(Vec3 position, long startTimeMs, int pillarCount) {
        this.position = position;
        this.startTimeMs = startTimeMs;
        this.pillarCount = Math.clamp(pillarCount, 3, MAX_PILLARS);

        pillarAzimuth = new float[this.pillarCount];
        pillarDelay   = new float[this.pillarCount];
        pillarWidth   = new float[this.pillarCount];
        pillarSeed    = new float[this.pillarCount];

        for (int i = 0; i < this.pillarCount; i++) {
            // Even ring spacing with a touch of jitter so the hexagon never
            // looks stamped out, plus a left-to-right strike order.
            pillarAzimuth[i] = (float) (i * 2.0 * Math.PI / this.pillarCount)
                    + (RNG.nextFloat() - 0.5f) * 0.22f;
            pillarDelay[i]   = i * 0.155f + RNG.nextFloat() * 0.09f;
            pillarWidth[i]   = 0.86f + RNG.nextFloat() * 0.30f;
            pillarSeed[i]    = RNG.nextFloat();
        }
    }

    // ── Time queries ───────────────────────────────────────────────

    /** Total elapsed time in seconds. */
    public double elapsedSec(long nowMs) {
        return (nowMs - startTimeMs) / 1000.0;
    }

    /** Current stage based on elapsed time. Negative = finished. */
    public int currentStage(long nowMs) {
        double t = elapsedSec(nowMs);
        if (t < T_SUMMON_END)  return STAGE_SIGIL_SUMMON;
        if (t < T_PILLARS_END) return STAGE_SIX_PILLARS;
        if (t < T_CHARGE_END)  return STAGE_CHARGE;
        if (t < T_GRAND_END)   return STAGE_GRAND_PILLAR;
        if (t < T_SPHERE_END)  return STAGE_NOVA_SPHERE;
        if (t < T_TOTAL)       return STAGE_DISSOLVE;
        return -1;
    }

    /** Normalized progress within the current stage (0→1). */
    public float stageProgress(long nowMs) {
        double t = elapsedSec(nowMs);
        return switch (currentStage(nowMs)) {
            case STAGE_SIGIL_SUMMON -> (float) (t / T_SUMMON_END);
            case STAGE_SIX_PILLARS  -> (float) ((t - T_SUMMON_END) / (T_PILLARS_END - T_SUMMON_END));
            case STAGE_CHARGE       -> (float) ((t - T_PILLARS_END) / (T_CHARGE_END - T_PILLARS_END));
            case STAGE_GRAND_PILLAR -> (float) ((t - T_CHARGE_END) / (T_GRAND_END - T_CHARGE_END));
            case STAGE_NOVA_SPHERE  -> (float) ((t - T_GRAND_END) / (T_SPHERE_END - T_GRAND_END));
            case STAGE_DISSOLVE     -> (float) ((t - T_SPHERE_END) / (T_TOTAL - T_SPHERE_END));
            default -> 0f;
        };
    }

    /** Global progress 0→1 over the whole timeline. */
    public float globalProgress(long nowMs) {
        return Math.clamp((float) (elapsedSec(nowMs) / T_TOTAL), 0f, 1f);
    }

    /** Total timeline length in seconds. */
    public static double totalDuration() {
        return T_TOTAL;
    }

    private static float smoothstep01(float x) {
        x = Math.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    // ── Sigil envelopes ────────────────────────────────────────────

    /**
     * Summon script progress 0→1. The fragment shader reads it as a drawing
     * order — rim rings, rune band, triquetra, star seals, sun rosette — so the
     * circle is drawn figure by figure rather than wiped on. Deliberately no
     * easeOutBack overshoot here: the script's slices are absolute, and a value
     * above 1 would clip the last group's finish.
     */
    public float sigilReveal(long nowMs) {
        double t = Math.max(0.0, elapsedSec(nowMs) - SCRIPT_START);
        return smoothstep01((float) (t / SCRIPT_LENGTH));
    }

    /** Accumulated sigil rotation (radians). C1-continuous through the spin-up. */
    public float sigilSpin(long nowMs) {
        double t = Math.max(0.0, elapsedSec(nowMs));
        double x = Math.max(0.0, t - T_PILLARS_END);
        return (float) (t * 0.40 + x * x * 0.42);
    }

    /** Altitude of the sigil above the death point (blocks). Rises, then holds. */
    public float sigilAltitude(long nowMs) {
        return 22f + 11f * smoothstep01((float) (elapsedSec(nowMs) / 2.4));
    }

    /**
     * How far the sigil's plane is nodded away from dead horizontal, in radians.
     * The disc arrives tilted ~22° and settles to ~6°, and because the renderer
     * nods about the disc's own turning axis, that lean precesses as it turns —
     * together the two read as a physical plane hanging in the sky. Kept modest
     * because a 28-block radius multiplies the angle into rim height: 22° is
     * already 11 blocks between the disc's low and high sides.
     */
    public float sigilLean(long nowMs) {
        double e = elapsedSec(nowMs);
        float settle = 1f - smoothstep01((float) ((e - 0.9) / 2.6));
        return 0.10f + 0.28f * settle;
    }

    /** Sigil alpha: eases in with the reveal, dissolves at the very end. */
    public float sigilAlpha(long nowMs) {
        double e = elapsedSec(nowMs);
        float in = smoothstep01((float) (e / 0.55));
        float out = 1f - smoothstep01((float) ((e - T_SPHERE_END) / (T_TOTAL - 0.35 - T_SPHERE_END)));
        return Math.min(in, out);
    }

    /** Extra brightness the sigil gains while the spell charges (0→1→0). */
    public float sigilSurge(long nowMs) {
        double e = elapsedSec(nowMs);
        float up = smoothstep01((float) ((e - T_PILLARS_END) / (T_GRAND_END - T_PILLARS_END)));
        float down = 1f - smoothstep01((float) ((e - T_GRAND_END) / 1.1));
        return Math.min(up, down);
    }

    // ── Six pillar envelopes ───────────────────────────────────────

    /**
     * Descent of pillar {@code i} (0 = still in the sigil, 1 = driven into
     * the ground). Accelerating fall, then pinned at 1.
     */
    public float pillarDescent(int i, long nowMs) {
        if (i >= pillarCount) return 0f;
        double start = T_SUMMON_END + 0.05 + pillarDelay[i];
        float x = (float) ((elapsedSec(nowMs) - start) / 0.40);
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        return x * x;
    }

    /** Seconds since pillar {@code i} hit the ground; negative before impact. */
    public double pillarImpactAge(int i, long nowMs) {
        double start = T_SUMMON_END + 0.05 + pillarDelay[i];
        return elapsedSec(nowMs) - (start + 0.40);
    }

    /** Alpha of the six-pillar ring: full while they strike, drained during charge. */
    public float pillarAlpha(long nowMs) {
        return 1f - smoothstep01(chargeRaw(nowMs));
    }

    /** Charge progress 0→1 across stage 3 (clamped outside the window). */
    public float chargeRaw(long nowMs) {
        return Math.clamp((float) ((elapsedSec(nowMs) - T_PILLARS_END)
                / (T_CHARGE_END - T_PILLARS_END)), 0f, 1f);
    }

    /** How far the ring pillars lean inward while draining (0→1). */
    public float pillarInward(long nowMs) {
        return smoothstep01(chargeRaw(nowMs));
    }

    // ── Grand pillar envelopes ─────────────────────────────────────

    /** Descent of the colossal centre column (0→1, accelerating). */
    public float grandDescent(long nowMs) {
        float x = (float) ((elapsedSec(nowMs) - T_CHARGE_END) / GRAND_FALL_SEC);
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        return x * x;
    }

    /** Seconds since the centre column hit the ground; negative before impact. */
    public double grandImpactAge(long nowMs) {
        return elapsedSec(nowMs) - (T_CHARGE_END + GRAND_FALL_SEC);
    }

    /** Alpha of the centre column: slams in, dies as the sphere takes over. */
    public float grandAlpha(long nowMs) {
        double e = elapsedSec(nowMs);
        float in = smoothstep01((float) ((e - T_CHARGE_END) / 0.22));
        float out = 1f - smoothstep01((float) ((e - (T_GRAND_END + 0.95)) / 0.90));
        return Math.min(in, out);
    }

    // ── Nova sphere envelopes ──────────────────────────────────────

    /**
     * Sphere growth 0→1→0: explosive expansion, a swelled hold, then an
     * implosion back to a point by the end of the timeline.
     */
    public float sphereGrow(long nowMs) {
        double e = elapsedSec(nowMs);
        if (e <= T_GRAND_END) return 0f;
        double x = e - T_GRAND_END;
        float grow = (float) (1.0 - Math.exp(-x * 3.6));
        float shrink = smoothstep01((float) (1.0 - (x - 2.05) / 1.75));
        return Math.min(grow, shrink);
    }

    /** Sphere radius in blocks at the current growth. */
    public float sphereRadius(long nowMs) {
        return 1.6f + 22f * sphereGrow(nowMs);
    }

    /** Sphere alpha: blinding at birth, dimming as it collapses. */
    public float sphereAlpha(long nowMs) {
        double e = elapsedSec(nowMs);
        if (e <= T_GRAND_END) return 0f;
        float in = smoothstep01((float) ((e - T_GRAND_END) / 0.16));
        float out = 1f - smoothstep01((float) ((e - (T_GRAND_END + 2.55)) / 1.35));
        return Math.min(in, out);
    }

    /** Temperature of the sphere: 1 = white-hot, decaying to violet as it dies. */
    public float sphereHeat(long nowMs) {
        return 1f - 0.55f * smoothstep01(chargePost(nowMs));
    }

    /** Normalized progress through the sphere + dissolve tail (0 at birth, 1 at death). */
    public float chargePost(long nowMs) {
        return Math.clamp((float) ((elapsedSec(nowMs) - T_GRAND_END)
                / (T_TOTAL - T_GRAND_END)), 0f, 1f);
    }

    // ── Master fade ────────────────────────────────────────────────

    /**
     * Global multiplier for the post-processing chain: 1 for most of the
     * timeline, smoothstep to 0 over the last ~1.2s so the tone-map ramps
     * out with the effect instead of popping off when it ends.
     */
    public float masterFade(long nowMs) {
        double e = elapsedSec(nowMs);
        return 1f - smoothstep01((float) ((e - (T_TOTAL - 1.2)) / 1.2));
    }

    // ── Sparks ─────────────────────────────────────────────────────

    /**
     * Advance the spark system. Called once per tick while the effect lives.
     *
     * @param dt seconds since the last update
     */
    public void updateSparks(float dt, long nowMs) {
        if (dt <= 0f) return;
        double e = elapsedSec(nowMs);
        if (e < 0 || e >= T_TOTAL) return;

        int stage = currentStage(nowMs);
        float cx = (float) position.x;
        float cy = (float) position.y;
        float cz = (float) position.z;

        // ── Emission per phase ────────────────────────────────────
        if (stage == STAGE_SIGIL_SUMMON) {
            emit(dt, 26, () -> spawnMote(cx, cy, cz));
        } else if (stage == STAGE_SIX_PILLARS) {
            emit(dt, 14, () -> spawnMote(cx, cy, cz));
            for (int i = 0; i < pillarCount; i++) {
                double age = pillarImpactAge(i, nowMs);
                if (age < 0 || age >= 0.55) continue;
                int pillar = i;
                emit(dt, 34, () -> spawnImpact(cx, cy, cz, pillar));
            }
        } else if (stage == STAGE_CHARGE) {
            emit(dt, 46, () -> spawnInward(cx, cy, cz));
        } else if (stage == STAGE_GRAND_PILLAR) {
            emit(dt, 30, () -> spawnInward(cx, cy, cz));
            if (grandDescent(nowMs) >= 1f) emit(dt, 40, () -> spawnImpact(cx, cy, cz, -1));
        } else if (stage == STAGE_NOVA_SPHERE) {
            emit(dt, 34, () -> spawnDebris(cx, cy, cz, nowMs));
        }

        // ── Integration ───────────────────────────────────────────
        float drag = (float) Math.exp(-0.85 * dt);
        for (int i = 0; i < sparkCount; i++) {
            int off = i * 9;
            sparkData[off + 6] += dt;
            if (sparkData[off + 6] >= sparkData[off + 7]) continue;

            int kind = (int) sparkData[off + 8];
            if (kind == KIND_MOTE) {
                sparkData[off + 4] += (1.9f - sparkData[off + 4]) * Math.min(dt * 1.6f, 1f);
                sparkData[off + 3] += (float) Math.sin(e * 2.4f + i * 0.7f) * 0.9f * dt;
                sparkData[off + 5] += (float) Math.cos(e * 2.1f + i * 1.1f) * 0.9f * dt;
            } else if (kind == KIND_IMPACT) {
                sparkData[off + 4] = sparkData[off + 4] * drag + 3.4f * dt;
                sparkData[off + 3] *= drag;
                sparkData[off + 5] *= drag;
            } else if (kind == KIND_INWARD) {
                // Accelerate toward the centre; consumed within a block of it.
                float dx = cx - sparkData[off];
                float dz = cz - sparkData[off + 2];
                float dy = cy + 1.2f - sparkData[off + 1];
                float pull = 5.2f;
                sparkData[off + 3] += dx * pull * dt;
                sparkData[off + 4] += dy * pull * dt;
                sparkData[off + 5] += dz * pull * dt;
                if (dx * dx + dy * dy + dz * dz < 1.2f) {
                    sparkData[off + 6] = sparkData[off + 7];
                    continue;
                }
            } else {
                sparkData[off + 4] = sparkData[off + 4] * drag - 1.15f * dt;
                sparkData[off + 3] *= drag;
                sparkData[off + 5] *= drag;
            }

            sparkData[off]     += sparkData[off + 3] * dt;
            sparkData[off + 1] += sparkData[off + 4] * dt;
            sparkData[off + 2] += sparkData[off + 5] * dt;
        }

        compactSparks();
    }

    private interface Spawn { void spawn(); }

    private void emit(float dt, int perSecond, Spawn spawn) {
        int n = (int) Math.min(MAX_SPARKS - sparkCount, Math.ceil(perSecond * dt));
        for (int i = 0; i < n; i++) {
            spawn.spawn();
            if (sparkCount >= MAX_SPARKS) break;
        }
    }

    private void spawnMote(float cx, float cy, float cz) {
        int off = sparkCount * 9;
        double theta = RNG.nextDouble() * Math.PI * 2.0;
        double r = 2.0 + RNG.nextDouble() * 16.0;
        sparkData[off]     = cx + (float) (Math.cos(theta) * r);
        sparkData[off + 1] = cy + RNG.nextFloat() * 3.0f;
        sparkData[off + 2] = cz + (float) (Math.sin(theta) * r);
        sparkData[off + 3] = (RNG.nextFloat() - 0.5f) * 0.5f;
        sparkData[off + 4] = 1.2f + RNG.nextFloat() * 2.4f;
        sparkData[off + 5] = (RNG.nextFloat() - 0.5f) * 0.5f;
        sparkData[off + 6] = 0f;
        sparkData[off + 7] = 1.6f + RNG.nextFloat() * 2.2f;
        sparkData[off + 8] = KIND_MOTE;
        sparkCount++;
    }

    private void spawnImpact(float cx, float cy, float cz, int pillar) {
        int off = sparkCount * 9;
        double theta;
        double r;
        if (pillar >= 0) {
            theta = pillarAzimuth[pillar] + (RNG.nextFloat() - 0.5f) * 0.9;
            r = PILLAR_RING_RADIUS;
        } else {
            theta = RNG.nextDouble() * Math.PI * 2.0;
            r = 0.6;
        }
        float ox = (float) (Math.cos(theta) * r);
        float oz = (float) (Math.sin(theta) * r);
        sparkData[off]     = cx + ox;
        sparkData[off + 1] = cy + 0.15f + RNG.nextFloat() * 0.3f;
        sparkData[off + 2] = cz + oz;
        float speed = 3.4f + RNG.nextFloat() * 6.2f;
        sparkData[off + 3] = (float) Math.cos(theta) * speed;
        sparkData[off + 4] = 1.4f + RNG.nextFloat() * 4.6f;
        sparkData[off + 5] = (float) Math.sin(theta) * speed;
        sparkData[off + 6] = 0f;
        sparkData[off + 7] = 0.7f + RNG.nextFloat() * 1.0f;
        sparkData[off + 8] = KIND_IMPACT;
        sparkCount++;
    }

    private void spawnInward(float cx, float cy, float cz) {
        int off = sparkCount * 9;
        double theta = RNG.nextDouble() * Math.PI * 2.0;
        double r = 4.5 + RNG.nextDouble() * 7.5;
        sparkData[off]     = cx + (float) (Math.cos(theta) * r);
        sparkData[off + 1] = cy + 0.4f + RNG.nextFloat() * 9.0f;
        sparkData[off + 2] = cz + (float) (Math.sin(theta) * r);
        sparkData[off + 3] = (RNG.nextFloat() - 0.5f) * 1.2f;
        sparkData[off + 4] = (RNG.nextFloat() - 0.5f) * 1.2f;
        sparkData[off + 5] = (RNG.nextFloat() - 0.5f) * 1.2f;
        sparkData[off + 6] = 0f;
        sparkData[off + 7] = 0.55f + RNG.nextFloat() * 0.65f;
        sparkData[off + 8] = KIND_INWARD;
        sparkCount++;
    }

    private void spawnDebris(float cx, float cy, float cz, long nowMs) {
        int off = sparkCount * 9;
        double theta = RNG.nextDouble() * Math.PI * 2.0;
        double cosPhi = RNG.nextDouble() * 2.0 - 1.0;
        double sinPhi = Math.sqrt(1.0 - cosPhi * cosPhi);
        float dx = (float) (sinPhi * Math.cos(theta));
        float dy = (float) (cosPhi * 0.55 + 0.55);
        float dz = (float) (sinPhi * Math.sin(theta));
        float speed = 5.5f + RNG.nextFloat() * RNG.nextFloat() * 22.0f;
        float r = sphereRadius(nowMs) * 0.85f;
        sparkData[off]     = cx + dx * r;
        sparkData[off + 1] = cy + r * 0.55f + dy * r * 0.5f;
        sparkData[off + 2] = cz + dz * r;
        sparkData[off + 3] = dx * speed;
        sparkData[off + 4] = dy * speed;
        sparkData[off + 5] = dz * speed;
        sparkData[off + 6] = 0f;
        sparkData[off + 7] = 1.1f + RNG.nextFloat() * 1.5f;
        sparkData[off + 8] = KIND_DEBRIS;
        sparkCount++;
    }

    /** Drop spent sparks so the array stays dense enough to iterate cheaply. */
    private void compactSparks() {
        int w = 0;
        for (int r = 0; r < sparkCount; r++) {
            int ro = r * 9;
            if (sparkData[ro + 6] >= sparkData[ro + 7]) continue;
            if (w != r) {
                int wo = w * 9;
                System.arraycopy(sparkData, ro, sparkData, wo, 9);
            }
            w++;
        }
        sparkCount = w;
    }

    /**
     * Fill a batch with live sparks: {@code [x, y, z, size, lifeRatio, kindNorm, 0, alpha]}.
     *
     * <p>{@code kindNorm} is {@code kind * 0.25}, matching the branch thresholds in
     * {@code kill_effect_thaum_spark.frag}. The reserved value 1.0 means "ground
     * shock ring", which {@link ThaumaturgyRenderer} emits directly rather than
     * storing as a spark.</p>
     *
     * @return the number of sparks written
     */
    public int fillSparkBatch(float[] batch, int maxCount, long nowMs, float alpha) {
        int count = 0;
        for (int i = 0; i < sparkCount && count < maxCount; i++) {
            int off = i * 9;
            float life = sparkData[off + 6];
            float maxLife = sparkData[off + 7];
            if (life >= maxLife) continue;

            float t = life / maxLife;
            float pAlpha = Math.min(alpha * (1f - t * t), 1f);
            if (pAlpha < 0.004f) continue;

            int kind = (int) sparkData[off + 8];
            // The nova is ~16 blocks wide, so its debris reads bigger than the
            // tight mote streamers around the sigil.
            float size = switch (kind) {
                case KIND_IMPACT -> 0.10f + t * 0.10f;
                case KIND_INWARD -> 0.075f + t * 0.045f;
                case KIND_DEBRIS -> 0.15f + t * 0.22f;
                default -> 0.09f + t * 0.05f;
            };

            int bo = count * 8;
            batch[bo]     = sparkData[off];
            batch[bo + 1] = sparkData[off + 1];
            batch[bo + 2] = sparkData[off + 2];
            batch[bo + 3] = size;
            batch[bo + 4] = t;
            batch[bo + 5] = kind * 0.25f;
            batch[bo + 6] = 0f;
            batch[bo + 7] = pAlpha;
            count++;
        }
        return count;
    }
}
