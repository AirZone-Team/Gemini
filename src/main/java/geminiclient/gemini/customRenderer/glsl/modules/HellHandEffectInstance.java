package geminiclient.gemini.customRenderer.glsl.modules;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Per-entity-death state for the Hell's Hand Kill Effect (地狱之手).
 *
 * <h3>Timeline (4 stages, ~5.4s total)</h3>
 * <pre>
 * Stage 1: Rift Opens      0.00 – 0.90s — a burning circle tears open at the death spot
 * Stage 2: Hands Reach     0.90 – 2.10s — charred hands climb out and grab the victim
 * Stage 3: Drag to Hell    2.10 – 3.60s — hands yank the victim down into the rift
 * Stage 4: Rift Closes     3.60 – 5.40s — iris-snap shut + smooth fade-out
 * </pre>
 *
 * <p>All motion curves are pure functions of elapsed time and are continuous
 * across stage boundaries, so no popping occurs at hand-offs. Embers are the
 * only stateful sub-system (spawned each frame by the renderer).</p>
 */
public class HellHandEffectInstance {

    /** Timeline stage identifiers for the Hell's Hand mode. */
    public static final int STAGE_RIFT_OPEN   = 1;
    public static final int STAGE_HANDS_REACH = 2;
    public static final int STAGE_DRAG        = 3;
    public static final int STAGE_RIFT_CLOSE  = 4;

    // Timeline boundaries (seconds)
    private static final double T_OPEN_END  = 0.90;
    private static final double T_REACH_END = 2.10;
    private static final double T_DRAG_END  = 3.60;
    private static final double T_CLOSE_END = 5.40;   // total duration

    // ── Core state ─────────────────────────────────────────────────

    public Vec3 position;              // mutable for AoE merge re-centering
    public final long startTimeMs;
    public boolean alive = true;
    public int mergeCount = 1;

    // ── Hand layout (generated once at spawn) ──────────────────────

    /** Number of hands rising from the rift. */
    public final int handCount;
    /** Rim azimuth per hand (radians). */
    public final float[] handAzimuth;
    /** Stagger delay per hand (seconds). */
    public final float[] handDelay;
    /** Size multiplier per hand. */
    public final float[] handScale;
    /** Crack-pattern seed per hand (0..1). */
    public final float[] handSeed;
    /** Vertical grip-point jitter per hand. */
    public final float[] handGripY;

    // ── Ember particle system ──────────────────────────────────────

    private static final int MAX_EMBERS = 420;
    private static final Random RNG = new Random();

    /** [x, y, z, vx, vy, vz, life, maxLife] × MAX_EMBERS */
    private final float[] emberData = new float[MAX_EMBERS * 8];
    private int emberCount;
    private long lastEmberUpdateMs;

    // ── Construction ───────────────────────────────────────────────

    public HellHandEffectInstance(Vec3 position, long startTimeMs, int handCount) {
        this.position = position;
        this.startTimeMs = startTimeMs;
        this.lastEmberUpdateMs = startTimeMs;
        this.handCount = Math.clamp(handCount, 4, 20);

        this.handAzimuth = new float[this.handCount];
        this.handDelay   = new float[this.handCount];
        this.handScale   = new float[this.handCount];
        this.handSeed    = new float[this.handCount];
        this.handGripY   = new float[this.handCount];

        for (int i = 0; i < this.handCount; i++) {
            // Evenly spaced with jitter so hands surround the victim
            handAzimuth[i] = (float) (i * 2.0 * Math.PI / this.handCount
                    + (RNG.nextFloat() - 0.5f) * 0.7);
            handDelay[i]   = RNG.nextFloat() * 0.35f;
            handScale[i]   = 0.85f + RNG.nextFloat() * 0.35f;
            handSeed[i]    = RNG.nextFloat();
            handGripY[i]   = (RNG.nextFloat() - 0.5f) * 0.55f;
        }
    }

    // ── Time queries ───────────────────────────────────────────────

    /** Total elapsed time in seconds (clamped to the timeline length). */
    public double elapsedSec(long nowMs) {
        return (nowMs - startTimeMs) / 1000.0;
    }

    /** Current stage based on elapsed time. Negative = finished. */
    public int currentStage(long nowMs) {
        double t = elapsedSec(nowMs);
        if (t < T_OPEN_END)  return STAGE_RIFT_OPEN;
        if (t < T_REACH_END) return STAGE_HANDS_REACH;
        if (t < T_DRAG_END)  return STAGE_DRAG;
        if (t < T_CLOSE_END) return STAGE_RIFT_CLOSE;
        return -1;
    }

    /** Normalized progress within the current stage (0→1). */
    public float stageProgress(long nowMs) {
        double t = elapsedSec(nowMs);
        return switch (currentStage(nowMs)) {
            case STAGE_RIFT_OPEN   -> (float) (t / T_OPEN_END);
            case STAGE_HANDS_REACH -> (float) ((t - T_OPEN_END) / (T_REACH_END - T_OPEN_END));
            case STAGE_DRAG        -> (float) ((t - T_REACH_END) / (T_DRAG_END - T_REACH_END));
            case STAGE_RIFT_CLOSE  -> (float) ((t - T_DRAG_END) / (T_CLOSE_END - T_DRAG_END));
            default -> 0f;
        };
    }

    // ── Shared animation curves (continuous across stage boundaries) ─

    /**
     * Rift opening amount (0→1, with a slight overshoot bounce).
     * Holds at 1 while the rift is open.
     */
    public float riftOpenAmt(long nowMs) {
        double e = elapsedSec(nowMs);
        if (e >= T_OPEN_END) return 1.0f;
        float x = (float) (e / T_OPEN_END);
        // easeOutBack — the tear snaps open with a tiny overshoot
        float c1 = 1.70158f, c3 = c1 + 1f;
        float p = x - 1f;
        return Math.max(0f, 1f + c3 * p * p * p + c1 * p * p);
    }

    /**
     * Rift closing (iris pinch) amount: 0 until the close stage, then 0→1.
     */
    public float riftCloseAmt(long nowMs) {
        double e = elapsedSec(nowMs);
        if (e < T_DRAG_END) return 0f;
        float x = Math.clamp((float) ((e - T_DRAG_END) / (T_CLOSE_END - 0.9 - T_DRAG_END)), 0f, 1f);
        // easeInOutCubic — starts slow, accelerates, snaps shut
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    /**
     * Master fade multiplier: 1 for most of the timeline, smoothstep to 0
     * over the last ~0.9s so the whole effect dissolves without a pop.
     */
    public float masterFade(long nowMs) {
        double e = elapsedSec(nowMs);
        float x = Math.clamp((float) ((e - (T_CLOSE_END - 0.9)) / 0.9), 0f, 1f);
        return 1f - x * x * (3f - 2f * x);
    }

    /** Global progress 0→1 over the whole timeline (drives shader boiling). */
    public float globalProgress(long nowMs) {
        return Math.clamp((float) (elapsedSec(nowMs) / T_CLOSE_END), 0f, 1f);
    }

    /**
     * Height of the victim above the rift floor (blocks) as a function of
     * time: held up while grabbed, then yanked down with an accelerating
     * curve during the drag stage, then swallowed in the close stage.
     */
    public float victimHeight(long nowMs) {
        double e = elapsedSec(nowMs);
        final float hold = 1.32f;   // grabbed height during reach
        final float low  = 0.16f;   // at the rift floor
        if (e < T_REACH_END) return hold;
        if (e < T_DRAG_END) {
            float t = (float) ((e - T_REACH_END) / (T_DRAG_END - T_REACH_END));
            return hold - t * t * (hold - low);       // easeInQuad: accelerating yank
        }
        if (e < T_DRAG_END + 0.62) {
            float t = (float) ((e - T_DRAG_END) / 0.62);
            return low - t * 0.9f;                     // slides below the floor
        }
        return low - 0.9f;
    }

    /** Victim master alpha: fades in as hands close on it, out when swallowed. */
    public float victimAlpha(long nowMs) {
        double e = elapsedSec(nowMs);
        float in  = Math.clamp((float) ((e - 1.50) / 0.45), 0f, 1f);
        float out = 1f - Math.clamp((float) ((e - 3.35) / 0.55), 0f, 1f);
        return in * out;
    }

    // ── Embers ─────────────────────────────────────────────────────

    /**
     * Update the ember system. Called by the renderer each frame while the
     * effect is visible.
     *
     * @param riftRadius world-space radius of the rift opening (blocks)
     */
    public void updateEmbers(long nowMs, float riftRadius) {
        long dtMs = nowMs - lastEmberUpdateMs;
        lastEmberUpdateMs = nowMs;
        float dt = Math.clamp(dtMs / 1000f, 0f, 0.1f);
        if (dt <= 0f) return;

        double e = elapsedSec(nowMs);
        boolean spawning = e < T_CLOSE_END - 0.7;
        float groundY = (float) position.y + 0.08f;
        float cx = (float) position.x, cz = (float) position.z;

        // Spawn rate ramps with the timeline; drag stage gets a violent burst
        int spawnPerTick;
        if (!spawning) spawnPerTick = 0;
        else if (e < T_OPEN_END)   spawnPerTick = 6;
        else if (e < T_REACH_END)  spawnPerTick = 10;
        else if (e < T_DRAG_END)   spawnPerTick = 14;
        else                       spawnPerTick = 5;

        for (int s = 0; s < spawnPerTick && emberCount < MAX_EMBERS; s++) {
            int off = emberCount * 8;
            double theta = RNG.nextDouble() * Math.PI * 2.0;
            double rr = riftRadius * (0.15 + RNG.nextDouble() * 0.95);
            emberData[off]     = cx + (float) (Math.cos(theta) * rr);
            emberData[off + 1] = groundY + RNG.nextFloat() * 0.1f;
            emberData[off + 2] = cz + (float) (Math.sin(theta) * rr);
            emberData[off + 3] = (RNG.nextFloat() - 0.5f) * 0.5f;
            emberData[off + 4] = 1.1f + RNG.nextFloat() * 2.1f;
            emberData[off + 5] = (RNG.nextFloat() - 0.5f) * 0.5f;
            emberData[off + 6] = 0f;
            emberData[off + 7] = 0.7f + RNG.nextFloat() * 1.1f;
            emberCount++;
        }

        // During the drag stage, a share of embers spirals INWARD and DOWN —
        // the hell mouth "inhales" smoke along with the victim.
        float inhale = (e >= T_REACH_END && e < T_DRAG_END) ? 3.4f
                     : (e >= T_DRAG_END) ? 1.4f : 0f;

        for (int i = 0; i < emberCount; i++) {
            int off = i * 8;
            emberData[off + 6] += dt;
            if (emberData[off + 6] >= emberData[off + 7]) continue;

            if (inhale > 0f) {
                float dx = cx - emberData[off];
                float dz = cz - emberData[off + 2];
                emberData[off + 3] += dx * inhale * dt;
                emberData[off + 5] += dz * inhale * dt;
                emberData[off + 4] -= 2.2f * dt;   // buoyancy dies, smoke falls in
            } else {
                emberData[off + 4] -= 0.9f * dt;   // gentle gravity on sparks
            }

            // Turbulent wobble
            emberData[off + 3] += (float) Math.sin(e * 5.0f + i * 1.3f) * 0.5f * dt;
            emberData[off + 5] += (float) Math.cos(e * 4.4f + i * 1.9f) * 0.5f * dt;

            emberData[off]     += emberData[off + 3] * dt;
            emberData[off + 1] += emberData[off + 4] * dt;
            emberData[off + 2] += emberData[off + 5] * dt;
        }
    }

    /**
     * Fill the shared particle batch: [x,y,z,size,r,g,b,a] × count with
     * G=0.5 (burst mode → GPU hot→ember→smoke ramp).
     */
    public int fillEmberBatch(float[] batch, int maxCount, long nowMs, float alpha) {
        int count = 0;
        for (int i = 0; i < emberCount && count < maxCount; i++) {
            int off = i * 8;
            float life = emberData[off + 6];
            float maxLife = emberData[off + 7];
            if (life >= maxLife) continue;

            float lifeRatio = life / maxLife;
            float pAlpha = Math.min(alpha * (1f - lifeRatio * lifeRatio), 1f);
            if (pAlpha < 0.004f) continue;

            int bo = count * 8;
            batch[bo]     = emberData[off];
            batch[bo + 1] = emberData[off + 1];
            batch[bo + 2] = emberData[off + 2];
            batch[bo + 3] = 0.045f + lifeRatio * 0.06f;   // swells as it cools
            batch[bo + 4] = lifeRatio;                     // ramp position
            batch[bo + 5] = 0f;                            // unused (reserved)
            batch[bo + 6] = 0f;                            // unused (reserved)
            batch[bo + 7] = pAlpha;
            count++;
        }
        return count;
    }

    /** Reset transient state (embers) when re-used. */
    public void reset() {
        emberCount = 0;
    }
}
