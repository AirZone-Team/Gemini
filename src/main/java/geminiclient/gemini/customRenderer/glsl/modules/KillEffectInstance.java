package geminiclient.gemini.customRenderer.glsl.modules;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Per-entity-death state for the Hypernova Kill Effect.
 *
 * <h3>Timeline (10 stages, ~14.5s total)</h3>
 * <pre>
 * Stage 1: Magic Circle Birth   0.0 – 0.8s   — particles begin emerging in sky
 * Stage 2: Magic Tower          0.8 – 2.4s   — sky particles continue appearing
 * Stage 3: Black Hole Forming   2.4 – 3.4s   — sky particles pulled toward BH
 * Stage 4: Accretion            3.4 – 5.0s   — particles spiral into black hole
 * Stage 5: Collapse             5.0 – 6.0s   — extreme pull, rapid consumption
 * Stage 6: Void                 6.0 – 7.5s   — dead silence, tension builds, BH gone
 * Stage 7: Flash                7.5 – 8.2s   — dramatic multi-ring light pulse
 * Stage 8: Hypernova            8.2 –10.5s   — enhanced shockwave + fireball + nebula
 * Stage 9: Afterglow           10.5 –12.5s   — smooth fade-out transition
 * Stage 10: Fade-out           12.5 –14.5s   — post-afterglow dissolve; all planes fade
 *                                              via reversed smoothstep alpha to zero
 * </pre>
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

    // Timeline boundaries (seconds)
    private static final double T_CIRCLE_END    = 0.8;
    private static final double T_TOWER_END     = 2.4;
    private static final double T_HOLE_END      = 3.4;
    private static final double T_ACCRETION_END = 5.0;
    private static final double T_COLLAPSE_END  = 6.0;
    private static final double T_VOID_END      = 7.5;   // silence before flash
    private static final double T_FLASH_END     = 8.2;   // enhanced: 0.7s pulse
    private static final double T_NOVA_END      = 10.5;  // enhanced: 2.3s explosion
    private static final double T_AFTERGLOW_END = 12.5;  // enhanced: 2.0s fade
    private static final double T_FADE_OUT_END  = 14.5;  // 2.0s post-afterglow dissolve

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

    private static final int MAX_BURST = 1200;

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
            // Ease-in: quick fade out
            float t = (progress - 0.8f) / 0.2f;
            alpha = (float)Math.pow(2.0, 10.0 * (t - 1.0)); // easeInExpo
        }
        return Math.clamp(alpha, 0f, 1f);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Cross-stage transition (magic tower ↔ black hole)
    // ═══════════════════════════════════════════════════════════════

    /** Transition overlap: fraction of each stage where both effects render. */
    private static final float TRANSITION_OVERLAP = 0.30f; // 30% overlap

    /**
     * Whether magic (circle/tower) should be visible at this moment.
     * Includes the first part of the black hole stage for crossfade.
     */
    public boolean shouldRenderMagic(long nowMs) {
        int stage = currentStage(nowMs);
        if (stage == STAGE_MAGIC_CIRCLE || stage == STAGE_MAGIC_TOWER) return true;
        // Extend into the first TRANSITION_OVERLAP of black hole forming
        if (stage == STAGE_BLACK_HOLE && stageProgress(nowMs) < TRANSITION_OVERLAP) return true;
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
        // Pre-appear during the last TRANSITION_OVERLAP of the tower stage
        if (stage == STAGE_MAGIC_TOWER && stageProgress(nowMs) > 1.0f - TRANSITION_OVERLAP) return true;
        return false;
    }

    /**
     * Alpha for magic rendering during tower→BH cross-stage transition.
     *   - Tower stage: 1.0, dropping to 0 in the last TRANSITION_OVERLAP
     *   - BH stage first TRANSITION_OVERLAP: continues dropping from previous value to 0
     */
    public float magicTransitionAlpha(long nowMs) {
        int stage = currentStage(nowMs);
        float progress = stageProgress(nowMs);

        if (stage == STAGE_MAGIC_TOWER) {
            if (progress > 1.0f - TRANSITION_OVERLAP) {
                // Fade out during the last portion of tower stage
                return 1.0f - (progress - (1.0f - TRANSITION_OVERLAP)) / TRANSITION_OVERLAP;
            }
            return 1.0f;
        }
        if (stage == STAGE_BLACK_HOLE) {
            if (progress < TRANSITION_OVERLAP) {
                // Continue fading out during the first portion of BH stage
                return 1.0f - (1.0f - TRANSITION_OVERLAP + progress) / (1.0f + TRANSITION_OVERLAP);
            }
            return 0f;
        }
        // Stage 1 (circle) or others — full alpha (stage-internal fade handled by transitionAlpha)
        return 1.0f;
    }

    /**
     * Alpha for black hole rendering during tower→BH cross-stage transition.
     *   - Tower stage last TRANSITION_OVERLAP: 0 → 0.3 (pre-appear, faint)
     *   - BH stage: 0.3 → 1.0 over the first TRANSITION_OVERLAP, then 1.0
     *   - BH stage: captured during collapse by transitionAlpha fade-out
     */
    public float blackHoleTransitionAlpha(long nowMs) {
        int stage = currentStage(nowMs);
        float progress = stageProgress(nowMs);

        if (stage == STAGE_MAGIC_TOWER) {
            if (progress > 1.0f - TRANSITION_OVERLAP) {
                // BH starts appearing faintly during the last portion of tower stage
                float t = (progress - (1.0f - TRANSITION_OVERLAP)) / TRANSITION_OVERLAP;
                return t * 0.35f; // max 0.35 during tower fade-out
            }
            return 0f;
        }
        if (stage == STAGE_BLACK_HOLE) {
            if (progress < TRANSITION_OVERLAP) {
                // Ramp from 0.35 to 1.0 during the first portion of BH stage
                float t = progress / TRANSITION_OVERLAP;
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
        float cy = (float) position.y + 1.5f; // match black hole visual center
        float cz = (float) position.z;

        // ── Disk distribution: particles in equatorial plane (XZ) ──
        double theta = RNG.nextDouble() * Math.PI * 2.0;

        // Three-layer radius distribution (weighted toward inner, hotter regions)
        double radius;
        float layerPick = RNG.nextFloat();
        if (layerPick < 0.35f) {
            // Inner hot ring: tight around ISCO
            radius = 0.3 + RNG.nextDouble() * 1.8;
        } else if (layerPick < 0.75f) {
            // Mid warm ring: main disk body
            radius = 1.5 + RNG.nextDouble() * 3.5;
        } else {
            // Outer cool ring: extended disk
            radius = 4.0 + RNG.nextDouble() * 4.0;
        }

        // ── Flat disk (XZ plane) with very thin vertical scatter ──
        float diskThickness = 0.15f + (float)(radius * 0.04); // thicker at edges
        float yOffset = (RNG.nextFloat() - 0.5f) * 2f * diskThickness;

        particleData[off]     = cx + (float)(Math.cos(theta) * radius);
        particleData[off + 1] = cy + yOffset;
        particleData[off + 2] = cz + (float)(Math.sin(theta) * radius);

        // ── Keplerian orbital velocity (tangential, ∝ 1/√r) ──
        // Inner particles orbit faster, creating differential rotation
        double orbitalSpeed = 0.8 / Math.sqrt(Math.max(radius, 0.2)) * 4.0;
        // Tangential direction: perpendicular to radial in XZ plane
        double vx = -Math.sin(theta) * orbitalSpeed;
        double vz =  Math.cos(theta) * orbitalSpeed;
        // Small radial drift inward (accretion flow)
        double radialDrift = -0.15 / Math.max(radius, 0.3);

        particleData[off + 3] = (float)(vx + Math.cos(theta) * radialDrift);
        particleData[off + 4] = (float)(RNG.nextDouble() * 0.2 - 0.1); // tiny vertical bounce
        particleData[off + 5] = (float)(vz + Math.sin(theta) * radialDrift);

        particleData[off + 6] = 0f;
        particleData[off + 7] = 1.2f + RNG.nextFloat() * 2.0f; // 1.2–3.2s life

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
        float cy = (float) position.y + 1.5f; // black hole visual center
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

                float tvx = dirX * targetSpeed;
                float tvy = dirY * targetSpeed;
                float tvz = dirZ * targetSpeed;

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
                if (d < 0.25f) {
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
                batch[bo + 3] = 0.03f * (1f - lifeRatio * 0.7f); // original size
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

        float drag = (float) Math.exp(-1.9 * dt);
        float grav = 2.2f * dt; // gentle pull-down for ember arcs

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
        int count = Math.min(MAX_BURST, 650 + (int)(clampedScale * 120f));
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
            float speed = (5.0f + RNG.nextFloat() * RNG.nextFloat() * 24.0f) * speedScale;

            burstData[off]     = cx + dx * 0.3f;
            burstData[off + 1] = cy + dy * 0.3f;
            burstData[off + 2] = cz + dz * 0.3f;
            burstData[off + 3] = dx * speed;
            burstData[off + 4] = dy * speed;
            burstData[off + 5] = dz * speed;
            burstData[off + 6] = 0f;
            burstData[off + 7] = 1.3f + RNG.nextFloat() * 1.9f; // 1.3–3.2s
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

            float size = 0.045f * (1f + lifeRatio * 1.4f);

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
