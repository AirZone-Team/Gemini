package geminiclient.gemini.modules.impl.visual;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectInstance;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.EntityRemoveEvent;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;

/**
 * Hypernova Kill Effect (极超新星击杀特效)
 *
 * <h3>Cinematic 10-stage death effect (~14.5s total)</h3>
 * <ol>
 *   <li>Magic Circle Birth   (0.0–0.8s) — golden rune circle emerges, sky particles begin appearing</li>
 *   <li>Magic Tower          (0.8–2.4s) — 12 stacked circles rise, more particles emerge in sky</li>
 *   <li>Black Hole Forming   (2.4–3.4s) — event horizon + photon ring, particles pulled inward</li>
 *   <li>Accretion            (3.4–5.0s) — particles spiral inward at high speed</li>
 *   <li>Collapse             (5.0–6.0s) — hole shrinks, extreme particle pull, energy builds</li>
 *   <li>Void                 (6.0–7.5s) — dead silence, all particles consumed, tension builds</li>
 *   <li>Flash                (7.5–8.2s) — dramatic multi-ring light pulse from the singularity</li>
 *   <li>Hypernova            (8.2–10.5s) — enhanced shockwave, fireball, nebula, lightning</li>
 *   <li>Afterglow            (10.5–12.5s) — smooth fade-out with lingering glow</li>
 *   <li>Fade-out             (12.5–14.5s) — post-afterglow smooth dissolve; all intersecting
 *       planes (billboard + horizontal + vertical cross) fade via reversed smoothstep alpha,
 *       eliminating any abrupt visual cutoff</li>
 * </ol>
 *
 * <h3>Performance</h3>
 * <p>Up to 8 simultaneous effects. Particles pooled. GPU-accelerated via
 * custom {@link RenderPipeline RenderPipelines}. Target budget: ~4ms @ 1080p60
 * on RTX 2060-class hardware.</p>
 */
public class KillEffect extends Module {

    // ── Config values ──────────────────────────────────────────────

    /** Maximum simultaneous effects (1–16). */
    private final FloatValue maxEffects    = new FloatValue("Max Effects", 8f, 1f, 16f);

    /** Global effect intensity multiplier. */
    private final FloatValue intensity     = new FloatValue("Intensity", 1.0f, 0.1f, 2.0f);

    /** Magic circle color (golden by default). */
    private final ColorValue circleColor   = new ColorValue("Circle Color", 0xFFFFB833);

    /** Enable particle accretion system. */
    private final BoolValue  enableParticles = new BoolValue("Particles", true);

    /** Enable camera shake during black hole + hypernova. */
    private final BoolValue  cameraShake    = new BoolValue("Camera Shake", true);

    /** Bloom strength for post-processing (0=none, 1=normal). */
    private final FloatValue bloomStrength  = new FloatValue("Bloom", 0.7f, 0f, 2.0f);

    // ── Constants ─────────────────────────────────────────────────

    private static final int MAX_EFFECTS = 16;
    private static final int MAX_PARTICLE_BATCH = 4096;

    // ── Effect slots ───────────────────────────────────────────────

    private final KillEffectInstance[] effects = new KillEffectInstance[MAX_EFFECTS];
    private int effectCount;

    // Particle batch buffer (reused each frame)
    private final float[] particleBatch = new float[MAX_PARTICLE_BATCH * 8];

    // Camera shake state
    private float shakeIntensity;

    // ── Constructor ────────────────────────────────────────────────

    public KillEffect() {
        super("KillEffect", ModuleEnum.Visual);
        addValue(maxEffects, intensity, circleColor, enableParticles, cameraShake, bloomStrength);
    }

    @Override
    public void onDisabled() {
        clearAllEffects();
    }

    // ── AoE aggregation state ──────────────────────────────────────

    private long lastSpawnTimeMs;
    private double lastSpawnX, lastSpawnY, lastSpawnZ;
    private int  mergeCount; // number of kills merged into current effect

    private static final long   MERGE_WINDOW_MS = 50;
    private static final double MERGE_RADIUS    = 3.0;

    // ── Entity death detection ─────────────────────────────────────

    @EventTarget
    public void onEntityRemove(EntityRemoveEvent event) {
        if (!event.dead()) return; // only trigger on actual death

        if (mc.player == null || mc.level == null) return;
        if (mc.player.tickCount <= 1) return; // don't trigger on world load

        // Don't trigger on player's own death (wouldn't see it anyway)
        if (event.entity() == mc.player) return;

        double px = event.entity().position().x;
        double py = event.entity().position().y;
        double pz = event.entity().position().z;
        long now = System.currentTimeMillis();

        // ── Spatial aggregation: merge nearby kills within time window ─
        double dx = px - lastSpawnX;
        double dy = py - lastSpawnY;
        double dz = pz - lastSpawnZ;
        double distSq = dx*dx + dy*dy + dz*dz;

        if (mergeCount > 0
                && (now - lastSpawnTimeMs) < MERGE_WINDOW_MS
                && distSq < MERGE_RADIUS * MERGE_RADIUS) {
            // Merge into existing effect: update weighted center + increment count
            mergeCount++;
            float w = 1.0f / mergeCount;
            lastSpawnX = lastSpawnX * (1f - w) + px * w;
            lastSpawnY = lastSpawnY * (1f - w) + py * w;
            lastSpawnZ = lastSpawnZ * (1f - w) + pz * w;
            lastSpawnTimeMs = now;

            // Update the most recent effect's merge count + position
            for (int i = effectCount - 1; i >= 0; i--) {
                if (effects[i] != null && effects[i].alive) {
                    effects[i].mergeCount = mergeCount;
                    effects[i].position = new net.minecraft.world.phys.Vec3(lastSpawnX, lastSpawnY, lastSpawnZ);
                    break;
                }
            }
        } else {
            // New effect
            mergeCount = 1;
            lastSpawnX = px;
            lastSpawnY = py;
            lastSpawnZ = pz;
            lastSpawnTimeMs = now;

            net.minecraft.world.phys.Vec3 pos =
                    new net.minecraft.world.phys.Vec3(px, py, pz);
            spawnEffect(pos);
        }
    }

    // ── Update ─────────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        long nowMs = System.currentTimeMillis();
        float dt = 0.05f; // ~20tps

        // Update particles and shake
        float shakeDecay = 0.9f;
        shakeIntensity *= shakeDecay;
        if (shakeIntensity < 0.001f) shakeIntensity = 0f;

        for (int i = 0; i < effectCount; i++) {
            KillEffectInstance inst = effects[i];
            if (!inst.alive) continue;

            int stage = inst.currentStage(nowMs);

            // Update particles for stages 1-5: sky floating (1-2) → BH accretion (3-5)
            if (enableParticles.enabled && stage >= 1 && stage <= KillEffectInstance.STAGE_COLLAPSE) {
                inst.updateParticles(dt, stage, nowMs);
            }

            // Camera shake: collapse → void (lingering) → flash → hypernova
            if (cameraShake.enabled) {
                if (stage == KillEffectInstance.STAGE_COLLAPSE) {
                    float progress = inst.stageProgress(nowMs);
                    shakeIntensity = Math.max(shakeIntensity, progress * 3.0f);
                } else if (stage == KillEffectInstance.STAGE_VOID) {
                    // Faint lingering tremor during the silent wait
                    float progress = inst.stageProgress(nowMs);
                    float voidShake = (1f - progress) * 1.0f;
                    shakeIntensity = Math.max(shakeIntensity, voidShake);
                } else if (stage == KillEffectInstance.STAGE_FLASH) {
                    float progress = inst.stageProgress(nowMs);
                    // Sharp spike at start, rapid decay
                    float flashShake = (1f - progress) * 6.0f;
                    shakeIntensity = Math.max(shakeIntensity, flashShake);
                } else if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
                    float progress = inst.stageProgress(nowMs);
                    float novaShake = (1f - progress) * 5.0f;
                    shakeIntensity = Math.max(shakeIntensity, novaShake);
                }
            }

            // Mark finished effects
            if (stage < 0) {
                inst.alive = false;
            }
        }

        compactEffects();
    }

    // ── Render ─────────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (effectCount == 0) return;

        long nowMs = System.currentTimeMillis();
        float globalIntensity = intensity.getValue();

        for (int i = 0; i < effectCount; i++) {
            KillEffectInstance inst = effects[i];
            if (!inst.alive) continue;

            int stage = inst.currentStage(nowMs);
            if (stage < 0) continue;

            // AoE merge: scale intensity with merge count
            float mergeScale = 1.0f + (inst.mergeCount - 1) * 0.25f;
            float scaledIntensity = globalIntensity * mergeScale;

            // ── Dispatch to appropriate renderers ────────────────
            // Magic circle/tower: may overlap with BH for smooth transition
            if (inst.shouldRenderMagic(nowMs)) {
                KillEffectRenderer.drawMagic(event.poseStack(), inst, nowMs);
            }

            // Black hole: may overlap with tower for smooth transition
            if (inst.shouldRenderBlackHole(nowMs)) {
                KillEffectRenderer.drawBlackHole(event.poseStack(), inst, nowMs);
            }

            // Draw particles for all stages that have them (stages 1-5):
            //   - Stage 1-2: sky particles floating in the sky
            //   - Stage 3-5: particles accreting into the black hole
            if (enableParticles.enabled && stage >= 1
                    && stage <= KillEffectInstance.STAGE_COLLAPSE
                    && inst.getParticleCount() > 0) {
                int particleCount = inst.fillParticleBatch(
                        particleBatch, MAX_PARTICLE_BATCH, nowMs, scaledIntensity);
                if (particleCount > 0) {
                    KillEffectRenderer.drawParticles(
                            event.poseStack(), particleBatch, particleCount);
                }
            }

            // Hypernova + Afterglow + Fade-out (smooth dissolve)
            if (stage == KillEffectInstance.STAGE_HYPERNOVA
                    || stage == KillEffectInstance.STAGE_AFTERGLOW
                    || stage == KillEffectInstance.STAGE_FADE_OUT) {
                KillEffectRenderer.drawHypernova(event.poseStack(), inst, nowMs);
            }
        }
    }

    // ── Internal ───────────────────────────────────────────────────

    private void spawnEffect(net.minecraft.world.phys.Vec3 position) {
        // Enforce max effects limit
        int max = (int) maxEffects.getValue();
        while (effectCount >= max) {
            removeOldestEffect();
        }

        // Find free slot
        int slot = effectCount;
        if (slot >= MAX_EFFECTS) return;

        effects[slot] = new KillEffectInstance(position, System.currentTimeMillis());
        effectCount++;
    }

    private void removeOldestEffect() {
        if (effectCount == 0) return;
        long oldestTime = Long.MAX_VALUE;
        int oldestIdx = -1;
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive && effects[i].startTimeMs < oldestTime) {
                oldestTime = effects[i].startTimeMs;
                oldestIdx = i;
            }
        }
        if (oldestIdx >= 0) {
            effects[oldestIdx].alive = false;
        }
    }

    /** Compact the effects array, removing dead entries. */
    private void compactEffects() {
        int w = 0;
        for (int r = 0; r < effectCount; r++) {
            if (!effects[r].alive) continue;
            if (r != w) {
                effects[w] = effects[r];
                effects[r] = null;
            }
            w++;
        }
        effectCount = w;
    }

    private void clearAllEffects() {
        for (int i = 0; i < effectCount; i++) {
            effects[i] = null;
        }
        effectCount = 0;
    }

    // ── Public queries ─────────────────────────────────────────────

    /** Whether any active effects exist (for post-processing gate). */
    public boolean hasActiveEffects() {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) return true;
        }
        return false;
    }

    /** Current bloom strength (from config, 0=none). */
    public float getBloomStrength() {
        return bloomStrength.getValue();
    }

    /** Current camera shake intensity (used by mixin hook, if implemented). */
    public float getShakeIntensity() {
        return shakeIntensity;
    }

    /**
     * Get world-space position of the primary active effect (for post-processing).
     * Returns null if no effect is active.
     */
    public double[] getPrimaryEffectCenter() {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) {
                return new double[]{
                    effects[i].position.x,
                    effects[i].position.y + 1.5, // raised to visual center
                    effects[i].position.z
                };
            }
        }
        return null;
    }

    /**
     * Get the progress (0→1) within the current stage of the primary effect.
     * Returns 0 if no effect is active.
     */
    public float getPrimaryProgress(long nowMs) {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) {
                return effects[i].stageProgress(nowMs);
            }
        }
        return 0f;
    }

    /**
     * Get the current effect stage for adjusting post-processing parameters.
     * Returns -1 if no effect is active.
     */
    public int getPrimaryStage(long nowMs) {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) {
                return effects[i].currentStage(nowMs);
            }
        }
        return -1;
    }

    /**
     * Get the merge count of the primary active effect.
     * Used to scale post-processing intensity for AoE kills.
     */
    public int getPrimaryMergeCount() {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) {
                return effects[i].mergeCount;
            }
        }
        return 1;
    }

    /**
     * Get the start time (ms) of the primary active effect.
     * Returns 0 if no effect is active.
     */
    public long getPrimaryStartTime() {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) {
                return effects[i].startTimeMs;
            }
        }
        return 0L;
    }
}
