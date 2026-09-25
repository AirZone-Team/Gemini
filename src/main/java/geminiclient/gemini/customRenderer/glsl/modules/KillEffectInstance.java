package geminiclient.gemini.customRenderer.glsl.modules;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Per-entity-death state for the Hypernova Kill Effect.
 *
 * <h3>Timeline (10 stages, ~29.0s total)</h3>
 * <pre>
 * Stage 1: Magic Circle Birth   0.0 – 3.2s   — the ground sigil draws itself, ring
 *                                              by ring, and sky particles emerge
 * Stage 2: Magic Tower          3.2 – 9.6s   — tiers rise, the armillary cage closes
 *                                              round them, the script completes
 * Stage 3: Black Hole Forming   9.6 –12.0s   — sky particles pulled toward BH
 * Stage 4: Accretion            12.0–15.6s   — particles spiral into black hole
 * Stage 5: Collapse             15.6–17.4s   — extreme pull, rapid consumption
 * Stage 6: Void                 17.4–19.2s   — dead silence, tension builds, BH gone
 * Stage 7: Flash                19.2–20.0s   — dramatic multi-ring light pulse
 * Stage 8: Hypernova            20.0–24.3s   — sustained shockwaves + fireball + nebula
 * Stage 9: Afterglow           24.3–26.8s   — cooling stellar remnant
 * Stage 10: Fade-out           26.8–29.0s   — post-afterglow dissolve; all planes fade
 *                                              via reversed smoothstep alpha to zero
 * </pre>
 *
 * <p>Stages 1-2 are the summoning proper and deliberately carry most of the
 * runtime: the array is laid down by travelling pen tips, so the sequence has to
 * be long enough to read as a ritual rather than as a flash of gold.</p>
 */
public class KillEffectInstance {

    /** Timeline stage identifiers — matches fragment shader stage dispatch. */
    public static final int STAGE_MAGIC_CIRCLE  = 1;
    public static final int STAGE_MAGIC_TOWER   = 2;
    public static final int STAGE_BLACK_HOLE    = 3;
    public static final int STAGE_ACCRETION     = 4;
    public static final int STAGE_COLLAPSE      = 5;
    public static final int STAGE_VOID          = 6;   // silence / tension (NEW)
    public static final int STAGE_FLASH         = 7;   // was 6
    public static final int STAGE_HYPERNOVA     = 8;   // was 7
    public static final int STAGE_AFTERGLOW     = 9;   // was 8
    public static final int STAGE_FADE_OUT     = 10;  // post-afterglow smooth dissolve

    // Timeline boundaries (seconds). Public so the timeline tests can assert
    // against the real numbers instead of restating them.
    public static final double T_CIRCLE_END    = 3.2;    // 3.2s for the sigil to draw itself
    public static final double T_TOWER_END     = 9.6;    // 6.4s of rising tower
    public static final double T_HOLE_END      = 12.0;   // 2.4s of formation
    public static final double T_ACCRETION_END = 15.6;   // 3.6s of feeding
    public static final double T_COLLAPSE_END  = 17.4;   // 1.8s of collapse
    public static final double T_VOID_END      = 19.2;   // silence before flash
    public static final double T_FLASH_END     = 20.0;   // 0.8s pulse
    public static final double T_NOVA_END      = 24.3;   // 4.3s sustained explosion
    public static final double T_AFTERGLOW_END = 26.8;   // 2.5s cooling remnant
    public static final double T_FADE_OUT_END  = 29.0;   // 2.2s post-afterglow dissolve

    // ── Core state ─────────────────────────────────────────────────

    public Vec3 position;          // mutable for AoE merge re-centering
    public final long startTimeMs;
    public boolean alive = true;

    /** Number of kills merged into this effect (1 = single, >1 = AoE enhanced). */
    public int mergeCount = 1;

    // ── Particle system (accretion phase) ──────────────────────────

    private static final int MAX_PARTICLES = 3000;
    private static final Random RNG = new Random();

    /** [x, y, z, vx, vy, vz, life, maxLife] × MAX_PARTICLES */
    private final float[] particleData = new float[MAX_PARTICLES * 8];
    /** true = sky particle (pre-BH floating), false = accretion particle (BH orbital) */
    private final boolean[] particleIsSky = new boolean[MAX_PARTICLES];
    private int particleCount;

    // ── Burst particle system (hypernova explosion debris) ─────────

    private static final int MAX_BURST = 1800;

    /** [x, y, z, vx, vy, vz, life, maxLife] × MAX_BURST */
    private final float[] burstData = new float[MAX_BURST * 8];
    private int burstCount;
    private boolean burstSpawned;

    // ── Construction ───────────────────────────────────────────────

    public KillEffectInstance(Vec3 position, long startTimeMs) {
        this.position = position;
        this.startTimeMs = startTimeMs;
    }

    // ── Time queries ───────────────────────────────────────────────

    /** Total elapsed time in seconds. */
    public double elapsedSec(long nowMs) {
        return (nowMs - startTimeMs) / 1000.0;
    }

    /** Current stage based on elapsed time. */
    public int currentStage(long nowMs) {
        double t = elapsedSec(nowMs);
        if (t < T_CIRCLE_END)    return STAGE_MAGIC_CIRCLE;
        if (t < T_TOWER_END)     return STAGE_MAGIC_TOWER;
        if (t < T_HOLE_END)      return STAGE_BLACK_HOLE;
        if (t < T_ACCRETION_END) return STAGE_ACCRETION;
        if (t < T_COLLAPSE_END)  return STAGE_COLLAPSE;
        if (t < T_VOID_END)      return STAGE_VOID;
        if (t < T_FLASH_END)     return STAGE_FLASH;
        if (t < T_NOVA_END)      return STAGE_HYPERNOVA;
        if (t < T_AFTERGLOW_END) return STAGE_AFTERGLOW;
        if (t < T_FADE_OUT_END)  return STAGE_FADE_OUT;
        return -1; // truly finished
    }

    /** Normalized progress within the current stage (0→1). */
    public float stageProgress(long nowMs) {
        double t = elapsedSec(nowMs);
        int stage = currentStage(nowMs);
        return switch (stage) {
            case STAGE_MAGIC_CIRCLE  -> (float)(t / T_CIRCLE_END);
            case STAGE_MAGIC_TOWER   -> (float)((t - T_CIRCLE_END) / (T_TOWER_END - T_CIRCLE_END));
            case STAGE_BLACK_HOLE    -> (float)((t - T_TOWER_END) / (T_HOLE_END - T_TOWER_END));
            case STAGE_ACCRETION     -> (float)((t - T_HOLE_END) / (T_ACCRETION_END - T_HOLE_END));
            case STAGE_COLLAPSE      -> (float)((t - T_ACCRETION_END) / (T_COLLAPSE_END - T_ACCRETION_END));
            case STAGE_VOID          -> (float)((t - T_COLLAPSE_END) / (T_VOID_END - T_COLLAPSE_END));
            case STAGE_FLASH         -> (float)((t - T_VOID_END) / (T_FLASH_END - T_VOID_END));
            case STAGE_HYPERNOVA     -> (float)((t - T_FLASH_END) / (T_NOVA_END - T_FLASH_END));
            case STAGE_AFTERGLOW     -> (float)((t - T_NOVA_END) / (T_AFTERGLOW_END - T_NOVA_END));
            case STAGE_FADE_OUT     -> (float)((t - T_AFTERGLOW_END) / (T_FADE_OUT_END - T_AFTERGLOW_END));
            default -> 0f;
        };
    }

    /**
     * Cross-fade alpha for smooth transitions between stages.
     * Fades in over first 20% of a stage, fades out over last 20%.
     * Returns 0→1 float for use as alpha multiplier.
     */
    public float transitionAlpha(long nowMs, boolean fadeIn, boolean fadeOut) {
        float progress = stageProgress(nowMs);
        float alpha = 1f;

        if (fadeIn && progress < 0.2f) {
            // Ease-out: quick fade in
            float t = progress / 0.2f;
            alpha = 1.0f - (float)Math.pow(2.0, -10.0 * t); // easeOutExpo
        }
        if (fadeOut && progress > 0.8f) {
            // Ease-in decay 1.0 → 0.0 across the tail window: zero slope at
            // the window start (continuous with the pre-80% plateau), steep
            // only at the very end. (The old formula jumped to ~0 the moment
            // the window opened — the effect vanished at 80% instead of 100%.)
            float t = (progress - 0.8f) / 0.2f;
            alpha = 1.0f - t * t;
        }
        return Math.clamp(alpha, 0f, 1f);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Summoning array (magic circle → tower → black hole)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Where each figure sits in {@link #magicReveal}: the rim rings are laid
     * down first, then the rune band, the great lattice, the satellite seals
     * and last the central rosette. The fragment shader reads the same four
     * boundaries, and the renderer caps a plane's reveal at one of them to
     * leave that plane showing only the figures below it — which is how
     * eighteen stacked planes stay legible instead of tangling into a pile of
     * near-identical circles.
     */
    public static final float SCRIPT_RIM_END = 0.16f;
    public static final float SCRIPT_RUNE_END = 0.34f;
    public static final float SCRIPT_LATTICE_END = 0.60f;
    public static final float SCRIPT_SEAL_END = 0.82f;

    /** The summon script opens this long after the kill... */
    private static final double SCRIPT_START = 0.25;
    /** ...and closes this long before the tower begins collapsing, so every
     *  figure is on the sky before the summon resolves. */
    private static final double SCRIPT_END = T_TOWER_END - 1.30;

    /**
     * Wall-clock timing of the handover from array to hole, per side. The two
     * stages are very different lengths now, so the overlap is timed in seconds
     * rather than as one shared fraction of each stage.
     *
     * <p>The array is deliberately the last thing to go: it is eaten over
     * {@link #MAGIC_DIE_SEC} while the hole is already open, so the swallow is
     * visible against a dark disk instead of inside the array's own glare.</p>
     */
    private static final double XFADE_TOWER_SEC = 0.55;   // the array starts giving way
    private static final double HOLE_OPEN_SEC   = 0.45;   // the hole reaches full strength
    private static final double MAGIC_DIE_SEC   = 1.15;   // the array is fully swallowed

    /** The same windows as fractions of the stage they sit in. */
    public static final float XFADE_TOWER =
            (float) (XFADE_TOWER_SEC / (T_TOWER_END - T_CIRCLE_END));
    public static final float XFADE_HOLE =
            (float) (HOLE_OPEN_SEC / (T_HOLE_END - T_TOWER_END));
    public static final float MAGIC_DIE =
            (float) (MAGIC_DIE_SEC / (T_HOLE_END - T_TOWER_END));

    /** Absolute time the array has vanished; the hole owns the frame from here. */
    public static final double T_MAGIC_END = T_TOWER_END + MAGIC_DIE_SEC;

    /**
     * The horizon does not inflate — it tears open. It snaps from a dot past
     * its cruising size in {@link #HOLE_SNAP_SEC}, then falls back onto it over
     * {@link #HOLE_SETTLE_SEC}. A disk that merely grows reads as a fade-in;
     * one that overshoots and settles reads as something that <em>happened</em>.
     */
    private static final double HOLE_SNAP_SEC = 0.34;
    private static final double HOLE_SETTLE_SEC = 0.40;
    /** Size at rest, and the peak it overshoots to on the snap. */
    private static final float HOLE_CRUISE = 1.5f;
    private static final float HOLE_OVERSHOOT = 2.1f;

    /** When the horizon stops overshooting and settles at its cruising size. */
    public static final double T_HOLE_SETTLED = T_TOWER_END + HOLE_SNAP_SEC + HOLE_SETTLE_SEC;

    /** Summon-script progress: 0 before the first pen lands, 1 once the core is lit. */
    public float magicReveal(long nowMs) {
        return smoothstep01((float) ((elapsedSec(nowMs) - SCRIPT_START) / (SCRIPT_END - SCRIPT_START)));
    }

    /**
     * Accumulated rotation of the array in radians, accelerating over the last
     * two seconds of the summon so the sky feels wound up as the hole opens.
     *
     * <p>The turn lives in the planes' geometry, never in a vertex-colour
     * channel: those are bytes, and an angle sent through one advances in 1.4°
     * jumps.</p>
     */
    public float magicSpin(long nowMs) {
        double t = Math.max(0.0, elapsedSec(nowMs));
        double x = Math.max(0.0, t - (T_TOWER_END - 2.0));
        return (float) (t * 0.52 + x * x * 0.30);
    }

    /** How far the tower has risen: 0 = only the ground sigil, 1 = the full array. */
    public float magicRise(long nowMs) {
        return smoothstep01((float) ((elapsedSec(nowMs) - T_CIRCLE_END) / 2.8));
    }

    /**
     * How far the array has given way: 0 through the summon, 0→1 across the
     * last of the tower stage, then pinned. Drives the vertical squash and the
     * radial pinch that pulls every plane onto the axis.
     */
    public float magicCollapse(long nowMs) {
        int stage = currentStage(nowMs);
        if (stage == STAGE_MAGIC_TOWER) {
            float progress = stageProgress(nowMs);
            if (progress <= 1f - XFADE_TOWER) return 0f;
            return smoothstep01((progress - (1f - XFADE_TOWER)) / XFADE_TOWER);
        }
        return stage >= STAGE_BLACK_HOLE ? 1f : 0f;
    }

    /**
     * Position of the swallow front, 0→1, travelling from above the top tier
     * down through the ground sigil over the whole handover window.
     *
     * <p>Linear on purpose: this is a front moving through space, not an
     * envelope easing in and out, and an eased front lingers where the planes
     * are densest and turns the pile-up back into a glare.</p>
     */
    public float magicDrain(long nowMs) {
        double start = T_TOWER_END - XFADE_TOWER_SEC;
        return Math.clamp((float) ((elapsedSec(nowMs) - start) / (T_MAGIC_END - start)), 0f, 1f);
    }

    /** Energy flare centred on the moment the horizon tears open. */
    public float magicSurge(long nowMs) {
        double x = (elapsedSec(nowMs) - (T_TOWER_END + 0.15)) / 1.0;
        return (float) Math.exp(-x * x);
    }

    private static float smoothstep01(float x) {
        float t = Math.clamp(x, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * Whether magic (circle/tower) should be visible at this moment.
     * Includes the whole swallow window of the black hole stage, so the array
     * is seen being eaten rather than simply switching off.
     */
    public boolean shouldRenderMagic(long nowMs) {
        int stage = currentStage(nowMs);
        if (stage == STAGE_MAGIC_CIRCLE || stage == STAGE_MAGIC_TOWER) return true;
        if (stage == STAGE_BLACK_HOLE && stageProgress(nowMs) < MAGIC_DIE) return true;
        return false;
    }

    /**
     * Whether the black hole should be visible at this moment.
     * Includes the last part of the tower stage for crossfade.
     * Excludes VOID stage — BH has completely collapsed.
     */
    public boolean shouldRenderBlackHole(long nowMs) {
        int stage = currentStage(nowMs);
        if (stage >= STAGE_BLACK_HOLE && stage <= STAGE_COLLAPSE) return true;
        // Pre-appear during the last part of the tower stage
        if (stage == STAGE_MAGIC_TOWER && stageProgress(nowMs) > 1.0f - XFADE_TOWER) return true;
        return false;
    }

    // ── Black hole geometry ────────────────────────────────────────

    /**
     * The hole does not open at the death point: it floats this many blocks
     * above it, and the accretion disk, the particle sink and the summoning
     * array's collapse all centre on the same spot.
     */
    public static final float HOLE_VISUAL_LIFT = 1.5f;

    /**
     * Half-extent of the world-space hole billboard, as a multiple of
     * {@link #blackHoleSizeWorld}. Mirrors {@code HOLE_UV_SPAN} in
     * kill_effect_hole.frag.
     */
    public static final float HOLE_UV_SPAN = 3.25f;

    /**
     * Apparent shadow radius of the hole in billboard UV units, where the quad
     * spans [-1,1]. The shader's Schwarzschild radius is 0.22 UV and light
     * bending widens the shadow to 2.6×r_s — the value that actually reads as
     * "the black disk" on screen.
     */
    public static final float HOLE_SHADOW_UV = 0.22f * 2.6f;

    /**
     * Animated hole scale in blocks. Stages 3-5 grow / hold / shrink it; the
     * tower→hole overlap starts it as a dot.
     *
     * <p>This is the <em>only</em> place the hole's size curve lives. The
     * world-space billboard derives its half-extent from it and the screen-space
     * post pass derives its shadow radius from {@link
     * #blackHoleShadowRadiusWorld} — the shaders no longer animate the radius a
     * second time, which used to leave the two renders disagreeing mid-collapse
     * with the screen-space disk large enough to swallow the billboard's photon
     * ring.</p>
     */
    public float blackHoleSizeWorld(long nowMs) {
        int stage = currentStage(nowMs);
        float progress = stageProgress(nowMs);

        if (stage == STAGE_MAGIC_TOWER) {
            // Pre-appears as a tiny dot while the tower compresses
            float t = Math.max((progress - (1f - XFADE_TOWER)) / XFADE_TOWER, 0f);
            return 0.02f + t * t * 0.28f;
        }
        if (stage == STAGE_BLACK_HOLE) {
            double since = elapsedSec(nowMs) - T_TOWER_END;
            if (since < HOLE_SNAP_SEC) {
                // Tear open: from the pre-appearing dot to past cruising size
                float t = smoothstep01((float) (since / HOLE_SNAP_SEC));
                return 0.3f + (HOLE_OVERSHOOT - 0.3f) * t;
            }
            if (since < HOLE_SNAP_SEC + HOLE_SETTLE_SEC) {
                // ...and fall back onto it, which is what sells the overshoot
                float t = smoothstep01((float) ((since - HOLE_SNAP_SEC) / HOLE_SETTLE_SEC));
                return HOLE_OVERSHOOT + (HOLE_CRUISE - HOLE_OVERSHOOT) * t;
            }
            return HOLE_CRUISE;
        }
        if (stage == STAGE_COLLAPSE) {
            return HOLE_CRUISE * (1f - progress * 0.8f);
        }
        return HOLE_CRUISE; // accretion: full size
    }

    /** Apparent shadow (event-horizon) radius of the hole in blocks. */
    public float blackHoleShadowRadiusWorld(long nowMs) {
        return blackHoleSizeWorld(nowMs) * HOLE_UV_SPAN * HOLE_SHADOW_UV;
    }

    /**
     * Alpha for magic rendering during tower→BH cross-stage transition.
     *   - Tower stage: 1.0, dropping to 0.5 over the last XFADE_TOWER
     *   - BH stage's first MAGIC_DIE: continues dropping 0.5 → 0, so the array
     *     outlives the hole's opening and is visibly swallowed by it
     *     (linear within each window, and the two windows meet on 0.5 at the
     *     stage boundary, so there is no step)
     */
    public float magicTransitionAlpha(long nowMs) {
        int stage = currentStage(nowMs);
        float progress = stageProgress(nowMs);

        if (stage == STAGE_MAGIC_TOWER) {
            if (progress > 1.0f - XFADE_TOWER) {
                // Drop 1.0 → 0.5 during the last portion of tower stage
                float t = (progress - (1.0f - XFADE_TOWER)) / XFADE_TOWER;
                return 1.0f - 0.5f * t;
            }
            return 1.0f;
        }
        if (stage == STAGE_BLACK_HOLE) {
            if (progress < MAGIC_DIE) {
                // Continue 0.5 → 0.0 across the whole swallow window
                float t = progress / MAGIC_DIE;
                return 0.5f * (1.0f - t);
            }
            return 0f;
        }
        // Stage 1 (circle) or others — full alpha (stage-internal fade handled by transitionAlpha)
        return 1.0f;
    }

    /**
     * Alpha for black hole rendering during tower→BH cross-stage transition.
     *   - Tower stage's last XFADE_TOWER: 0 → 0.35 (pre-appear, faint)
     *   - BH stage's first XFADE_HOLE: 0.35 → 1.0, then 1.0
     *   - BH stage: captured during collapse by transitionAlpha fade-out
     */
    public float blackHoleTransitionAlpha(long nowMs) {
        int stage = currentStage(nowMs);
        float progress = stageProgress(nowMs);

        if (stage == STAGE_MAGIC_TOWER) {
            if (progress > 1.0f - XFADE_TOWER) {
                // BH starts appearing faintly during the last portion of tower stage
                float t = (progress - (1.0f - XFADE_TOWER)) / XFADE_TOWER;
                return t * 0.35f; // max 0.35 during tower fade-out
            }
            return 0f;
        }
        if (stage == STAGE_BLACK_HOLE) {
            if (progress < XFADE_HOLE) {
                // Ramp from 0.35 to 1.0 during the first portion of BH stage
                float t = progress / XFADE_HOLE;
                return 0.35f + (1.0f - 0.35f) * t;
            }
            // After transition: use stage-internal alpha (collapse fade-out, etc.)
            return transitionAlpha(nowMs, false, stage == STAGE_COLLAPSE);
        }
        // For accretion/collapse stages: use built-in transitionAlpha
        if (stage == STAGE_ACCRETION || stage == STAGE_COLLAPSE) {
            return transitionAlpha(nowMs, false, stage == STAGE_COLLAPSE);
        }
        return 0f;
    }

    /**
     * Fade-out alpha for post-afterglow smooth dissolve.
     *
     * <p>Uses a reversed smoothstep curve: alpha = 1 - 3t² + 2t³,
     * producing a gentle ease-out that starts slowly and accelerates
     * gracefully at the end. Over the 2-second fade-out window this
     * eliminates any perceptible pop.</p>
     *
     * @return alpha multiplier (1.0 → 0.0) during STAGE_FADE_OUT, 1.0 otherwise
     */
    public float getFadeOutAlpha(long nowMs) {
        if (currentStage(nowMs) != STAGE_FADE_OUT) return 1.0f;
        float t = stageProgress(nowMs);
        // Reversed smoothstep: starts at 1.0, ends at 0.0 with zero derivatives at both ends
        return 1.0f - t * t * (3.0f - 2.0f * t);
    }

    /** Get stage start time in seconds. */
    public double stageStartSec(long nowMs) {
        double t = elapsedSec(nowMs);
        int stage = currentStage(nowMs);
        return switch (stage) {
            case STAGE_MAGIC_CIRCLE  -> 0.0;
            case STAGE_MAGIC_TOWER   -> T_CIRCLE_END;
            case STAGE_BLACK_HOLE    -> T_TOWER_END;
            case STAGE_ACCRETION     -> T_HOLE_END;
            case STAGE_COLLAPSE      -> T_ACCRETION_END;
            case STAGE_VOID          -> T_COLLAPSE_END;
            case STAGE_FLASH         -> T_VOID_END;
            case STAGE_HYPERNOVA     -> T_FLASH_END;
            case STAGE_AFTERGLOW     -> T_NOVA_END;
            case STAGE_FADE_OUT     -> T_AFTERGLOW_END;
            default -> 0.0;
        };
    }

    // ── Particle management ────────────────────────────────────────

    /** Get the number of active particles. */
    public int getParticleCount() {
        return particleCount;
    }

    /**
     * Spawn a single sky particle at a high altitude (天空粒子).
     * Used during pre-BH stages (magic circle + tower) for the "particles emerge
     * in the sky" effect. These particles float gently until the black hole
     * pulls them in.
     */
    private void spawnSkyParticle(int index) {
        int off = index * 8;
        float px = (float) position.x;
        float py = (float) position.y;
        float pz = (float) position.z;

        double theta = RNG.nextDouble() * Math.PI * 2.0;
        double radius = 3.0 + RNG.nextDouble() * 16.0; // wide XZ scatter

        particleData[off]     = px + (float)(Math.cos(theta) * radius);
        particleData[off + 1] = py + 8.0f + RNG.nextFloat() * 25.0f; // 8–33 blocks above
        particleData[off + 2] = pz + (float)(Math.sin(theta) * radius);

        // Gentle floating velocity
        particleData[off + 3] = (RNG.nextFloat() - 0.5f) * 0.6f;
        particleData[off + 4] = (RNG.nextFloat() - 0.5f) * 0.3f;
        particleData[off + 5] = (RNG.nextFloat() - 0.5f) * 0.6f;

        particleData[off + 6] = 0f;
        particleData[off + 7] = 2.0f + RNG.nextFloat() * 4.0f; // 2–6s life

        particleIsSky[index] = true;
    }

    /**
     * Respawn a single accretion particle in the equatorial disk plane.
     *
     * <p>Particles orbit in a geometrically thin, optically thick disk
     * in the XZ plane (horizontal), with a slight vertical scatter.
     * Three temperature layers are produced:</p>
     * <ul>
     *   <li>Inner (hot): radius 0.3–1.5 — white/blue, fast orbit</li>
     *   <li>Mid (warm):  radius 1.5–4.0 — orange/yellow, medium orbit</li>
     *   <li>Outer (cool): radius 4.0–8.0 — red, slow orbit</li>
     * </ul>
     */
    private void respawnAccretionParticle(int index) {
        int off = index * 8;
        float cx = (float) position.x;
        float cy = (float) position.y + HOLE_VISUAL_LIFT; // match black hole visual center
        float cz = (float) position.z;

        // ── Disk distribution: particles in equatorial plane (XZ) ──
        double theta = RNG.nextDouble() * Math.PI * 2.0;

        // Three-layer radius distribution (weighted toward inner, hotter regions)
        double radius;
        float layerPick = RNG.nextFloat();
        if (layerPick < 0.42f) {
            // Inner hot ring: tight around ISCO
            radius = 0.75 + RNG.nextDouble() * 1.55;
        } else if (layerPick < 0.82f) {
            // Mid warm ring: main disk body
            radius = 1.8 + RNG.nextDouble() * 3.8;
        } else {
            // Outer cool ring: extended disk
            radius = 5.0 + RNG.nextDouble() * 4.5;
        }

        // ── Flat disk (XZ plane) with very thin vertical scatter ──
        float diskThickness = 0.08f + (float)(radius * 0.028); // thin inner disk, subtly flared rim
        float yOffset = (RNG.nextFloat() - 0.5f) * 2f * diskThickness;

        particleData[off]     = cx + (float)(Math.cos(theta) * radius);
        particleData[off + 1] = cy + yOffset;
        particleData[off + 2] = cz + (float)(Math.sin(theta) * radius);

        // ── Keplerian orbital velocity (tangential, ∝ 1/√r) ──
        // Inner particles orbit faster, creating differential rotation
        double orbitalSpeed = 5.2 / Math.sqrt(Math.max(radius, 0.35));
        // Tangential direction: perpendicular to radial in XZ plane
        double vx = -Math.sin(theta) * orbitalSpeed;
        double vz =  Math.cos(theta) * orbitalSpeed;
        // Small radial drift inward (accretion flow)
        double radialDrift = -0.32 / Math.max(radius, 0.45);

        particleData[off + 3] = (float)(vx + Math.cos(theta) * radialDrift);
        particleData[off + 4] = (float)(RNG.nextDouble() * 0.2 - 0.1); // tiny vertical bounce
        particleData[off + 5] = (float)(vz + Math.sin(theta) * radialDrift);

        particleData[off + 6] = 0f;
        particleData[off + 7] = 2.2f + RNG.nextFloat() * 3.2f;

        particleIsSky[index] = false;
    }

    /**
     * Update particle positions based on the current stage.
     *
     * <h3>Pre-BH (stages 1-2)</h3>
     * Continuously spawns sky particles at high altitude with gentle floating.
     *
     * <h3>BH stages (3-5)</h3>
     * Applied strong gravitational pull toward the black hole center.
     * Expired sky particles respawn as accretion particles to maintain
     * the inward-spiraling ring.
     *
     * @param dt    delta time in seconds
     * @param stage current effect stage (1-8, or -1 for finished)
     * @param nowMs current system time for stage calculations
     */
    public void updateParticles(float dt, int stage, long nowMs) {
        if (stage < 1 || stage > STAGE_COLLAPSE) return;

        float cx = (float) position.x;
        float cy = (float) position.y + HOLE_VISUAL_LIFT; // black hole visual center
        float cz = (float) position.z;

        boolean isPreBH = stage == STAGE_MAGIC_CIRCLE || stage == STAGE_MAGIC_TOWER;
        boolean isBH = stage >= STAGE_BLACK_HOLE && stage <= STAGE_COLLAPSE;

        // ── Continuous sky particle spawning during pre-BH ────────────
        if (isPreBH && particleCount < MAX_PARTICLES) {
            int spawnPerTick = 30;
            for (int s = 0; s < spawnPerTick && particleCount < MAX_PARTICLES; s++) {
                spawnSkyParticle(particleCount);
                particleCount++;
            }
        }

        // ── Gravitational pull per stage ──────────────────────────────
        // Using linear blend toward center (not 1/r²) so distant
        // particles visibly rush inward instead of being stuck.
        // Speed cap prevents teleporting.
        float accelBase;  // base inertia factor (higher = faster convergence)
        float speedCap;   // max velocity (blocks/s)
        if (stage == STAGE_BLACK_HOLE) {
            float progress = stageProgress(nowMs);
            accelBase = 15f + progress * 60f;   // 15 → 75
            speedCap  = 10f + progress * 40f;   // 10 → 50 blocks/s
        } else if (stage == STAGE_ACCRETION) {
            accelBase = 80f;
            speedCap  = 55f;
        } else {
            // STAGE_COLLAPSE — extreme pull
            accelBase = 150f;
            speedCap  = 80f;
        }

        for (int i = 0; i < particleCount; i++) {
            int off = i * 8;
            float life = particleData[off + 6] + dt;
            particleData[off + 6] = life;

            // Kill expired particles
            if (life >= particleData[off + 7]) {
                if (isBH) {
                    respawnAccretionParticle(i);
                } else if (isPreBH) {
                    spawnSkyParticle(i); // respawn as new sky particle
                }
                continue;
            }

            if (isPreBH) {
                // ── Gentle floating motion (sky particles) ──────────
                // Lose the Japanese RNG drift (makes particles jittery).
                // Use smooth sinusoidal drift for a more natural look.
                float driftX = (float)Math.sin(i * 1.7f + life * 0.8f) * 0.15f;
                float driftY = (float)Math.cos(i * 2.3f + life * 0.6f) * 0.08f;
                float driftZ = (float)Math.cos(i * 1.9f + life * 0.7f) * 0.15f;

                particleData[off]     += driftX * dt;
                particleData[off + 1] += driftY * dt;
                particleData[off + 2] += driftZ * dt;

            } else if (isBH) {
                // ── Linear pull toward black hole center ────────────
                float dx = cx - particleData[off];
                float dy = cy - particleData[off + 1];
                float dz = cz - particleData[off + 2];
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (dist < 0.01f) {
                    // Reached the singularity — consume
                    particleData[off + 6] = particleData[off + 7];
                    continue;
                }

                // Direction unit vector
                float invDist = 1.0f / dist;
                float dirX = dx * invDist;
                float dirY = dy * invDist;
                float dirZ = dz * invDist;

                // Linear pull: acceleration ∝ distance (farther = stronger pull)
                // Gives visible inward rush from any distance.
                float accelMag = accelBase * (0.5f + 0.5f * Math.clamp(dist / 8f, 0f, 1f));

                // Current velocity
                float vx = particleData[off + 3];
                float vy = particleData[off + 4];
                float vz = particleData[off + 5];

                // Blend current velocity toward target velocity (direction * cap)
                // rather than pure acceleration — this preserves some orbital
                // motion for visual richness while ensuring convergence.
                float targetSpeed = Math.min(speedCap, accelMag * dist * 0.3f);
                float blend = Math.clamp(accelBase * dt * 0.15f, 0.01f, 0.3f);

                float tvx;
                float tvy;
                float tvz;

                if (!particleIsSky[i] && stage != STAGE_COLLAPSE) {
                    // Preserve a Keplerian orbit once matter has joined the disk.
                    float horizontalRadius = (float)Math.sqrt(dx * dx + dz * dz);
                    float invHorizontal = 1.0f / Math.max(horizontalRadius, 0.05f);
                    float radialX = -dx * invHorizontal;
                    float radialZ = -dz * invHorizontal;
                    float tangentX = -radialZ;
                    float tangentZ = radialX;
                    float orbitSpeed = 5.8f / (float)Math.sqrt(Math.max(horizontalRadius, 0.4f));
                    float inwardSpeed = 0.45f + 0.65f / Math.max(horizontalRadius, 0.6f);

                    tvx = tangentX * orbitSpeed - radialX * inwardSpeed;
                    tvy = dy * 2.8f;
                    tvz = tangentZ * orbitSpeed - radialZ * inwardSpeed;
                    blend = Math.clamp(dt * 3.8f, 0.04f, 0.22f);
                } else {
                    // Sky streams and the final collapse plunge toward the horizon.
                    tvx = dirX * targetSpeed;
                    tvy = dirY * targetSpeed;
                    tvz = dirZ * targetSpeed;
                }

                particleData[off + 3] = vx + (tvx - vx) * blend;
                particleData[off + 4] = vy + (tvy - vy) * blend;
                particleData[off + 5] = vz + (tvz - vz) * blend;

                // Position update
                particleData[off]     += particleData[off + 3] * dt;
                particleData[off + 1] += particleData[off + 4] * dt;
                particleData[off + 2] += particleData[off + 5] * dt;

                // Consume particles at the event horizon
                float d = (float) Math.sqrt(
                    (particleData[off] - cx) * (particleData[off] - cx) +
                    (particleData[off + 1] - cy) * (particleData[off + 1] - cy) +
                    (particleData[off + 2] - cz) * (particleData[off + 2] - cz));
                if (d < 0.38f) {
                    particleData[off + 6] = particleData[off + 7]; // expire
                }
            }
        }
    }

    /** Get a flat array of particle positions for batched rendering: [x,y,z,size,r,g,b,a] × count */
    public int fillParticleBatch(float[] batch, int maxCount, long nowMs, float alpha) {
        if (particleCount == 0) return 0;

        int count = 0;
        for (int i = 0; i < particleCount && count < maxCount; i++) {
            int off = i * 8;
            float life = particleData[off + 6];
            float maxLife = particleData[off + 7];
            if (life >= maxLife) continue;

            float lifeRatio = life / maxLife;
            float pAlpha = alpha * (1f - lifeRatio) * (lifeRatio < 0.1f ? lifeRatio / 0.1f : 1f);

            int bo = count * 8;

            if (particleIsSky[i]) {
                // ── Sky particle: larger, brighter, celestial tone ──
                batch[bo]     = particleData[off];     // x
                batch[bo + 1] = particleData[off + 1]; // y
                batch[bo + 2] = particleData[off + 2]; // z
                batch[bo + 3] = 0.06f * (1f - lifeRatio * 0.4f); // larger size
                batch[bo + 4] = lifeRatio * 0.3f;       // R → clamped low → white/starry range
                batch[bo + 5] = 1.0f;                   // G → sky mode flag (shader uses cool tone)
                batch[bo + 6] = 0f;                     // B → reserved
                batch[bo + 7] = pAlpha;                 // A → master alpha
            } else {
                // ── Accretion particle: smaller, hot debris tone ──
                batch[bo]     = particleData[off];     // x
                batch[bo + 1] = particleData[off + 1]; // y
                batch[bo + 2] = particleData[off + 2]; // z
                batch[bo + 3] = 0.045f * (1f - lifeRatio * 0.55f);
                batch[bo + 4] = lifeRatio;             // R → lifeRatio (GPU warm color ramp)
                batch[bo + 5] = 0f;                    // G → accretion mode
                batch[bo + 6] = 0f;                    // B → reserved
                batch[bo + 7] = pAlpha;                // A → master alpha
            }
            count++;
        }
        return count;
    }

    /** Reset particle system for reuse. */
    public void resetParticles() {
        particleCount = 0;
    }

    // ── Burst particles (explosion debris) ─────────────────────────

    /** Get the number of spawned burst particles. */
    public int getBurstCount() {
        return burstCount;
    }

    /**
     * Update explosion burst debris. The burst is spawned once, at the
     * flash peak (30% into STAGE_FLASH), so the debris is already flying
     * outward when the hypernova detonates.
     *
     * <p>Integration: exponential drag + gentle gravity; particles never
     * respawn — they dissipate (alpha → 0, size grows as they cool).</p>
     *
     * @param dt         delta time in seconds (~0.05 per tick)
     * @param stage      current effect stage
     * @param nowMs      current system time
     * @param mergeScale AoE merge multiplier (scales count + speed)
     */
    public void updateBurstParticles(float dt, int stage, long nowMs, float mergeScale) {
        if (!burstSpawned && stage == STAGE_FLASH && stageProgress(nowMs) >= 0.30f) {
            spawnBurst(mergeScale);
        }
        if (burstCount == 0) return;

        float drag = (float) Math.exp(-1.15 * dt);
        float grav = 1.65f * dt; // long, cinematic ember arcs

        for (int i = 0; i < burstCount; i++) {
            int off = i * 8;
            float life = burstData[off + 6] + dt;
            burstData[off + 6] = life;
            if (life >= burstData[off + 7]) continue; // dead — fill skips it

            burstData[off + 3] *= drag;
            burstData[off + 4] = burstData[off + 4] * drag - grav;
            burstData[off + 5] *= drag;

            burstData[off]     += burstData[off + 3] * dt;
            burstData[off + 1] += burstData[off + 4] * dt;
            burstData[off + 2] += burstData[off + 5] * dt;
        }
    }

    /**
     * Spawn the explosion burst: uniform sphere directions with a slight
     * upward bias, quadratic speed distribution (many slow embers, a few
     * fast streaks).
     */
    private void spawnBurst(float mergeScale) {
        burstSpawned = true;
        float clampedScale = Math.min(mergeScale, 3f);
        int count = Math.min(MAX_BURST, 1050 + (int)(clampedScale * 220f));
        float speedScale = (float) Math.sqrt(clampedScale);

        float cx = (float) position.x;
        float cy = (float) position.y + 1.6f;
        float cz = (float) position.z;

        for (int i = 0; i < count; i++) {
            int off = i * 8;

            // Uniform random direction on the sphere, slight upward bias
            double theta = RNG.nextDouble() * Math.PI * 2.0;
            double cosPhi = RNG.nextDouble() * 2.0 - 1.0;
            double sinPhi = Math.sqrt(1.0 - cosPhi * cosPhi);
            float dx = (float)(sinPhi * Math.cos(theta));
            float dy = (float)(cosPhi * 0.75 + 0.30);
            float dz = (float)(sinPhi * Math.sin(theta));

            // Quadratic distribution: r² speeds → many slow, few fast
            float speed = (6.5f + RNG.nextFloat() * RNG.nextFloat() * 31.0f) * speedScale;

            burstData[off]     = cx + dx * 0.3f;
            burstData[off + 1] = cy + dy * 0.3f;
            burstData[off + 2] = cz + dz * 0.3f;
            burstData[off + 3] = dx * speed;
            burstData[off + 4] = dy * speed;
            burstData[off + 5] = dz * speed;
            burstData[off + 6] = 0f;
            burstData[off + 7] = 3.4f + RNG.nextFloat() * 2.4f;
        }
        burstCount = count;
    }

    /**
     * Fill the shared batch with burst debris: [x,y,z,size,r,g,b,a] × count.
     * Encoded as mode G=0.5 (burst) — the particle shader applies the HDR
     * hot→ember ramp. Alpha decays quadratically (dissipation); size grows
     * slightly as the debris cools and scatters.
     */
    public int fillBurstBatch(float[] batch, int maxCount, long nowMs, float alpha) {
        if (burstCount == 0) return 0;

        int count = 0;
        for (int i = 0; i < burstCount && count < maxCount; i++) {
            int off = i * 8;
            float life = burstData[off + 6];
            float maxLife = burstData[off + 7];
            if (life >= maxLife) continue;

            float lifeRatio = life / maxLife;
            float decay = 1f - lifeRatio;
            float pAlpha = Math.min(alpha * decay * decay, 1f);
            if (pAlpha < 0.004f) continue;

            float size = 0.055f * (1f + lifeRatio * 1.6f);

            int bo = count * 8;
            batch[bo]     = burstData[off];
            batch[bo + 1] = burstData[off + 1];
            batch[bo + 2] = burstData[off + 2];
            batch[bo + 3] = size;
            batch[bo + 4] = lifeRatio;  // R → color ramp
            batch[bo + 5] = 0.5f;       // G → burst mode
            batch[bo + 6] = 0f;         // B → reserved
            batch[bo + 7] = pAlpha;     // A → master alpha
            count++;
        }
        return count;
    }
}
