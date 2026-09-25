package geminiclient.gemini.modules.impl.visual;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import geminiclient.gemini.customRenderer.glsl.modules.HellHandEffectInstance;
import geminiclient.gemini.customRenderer.glsl.modules.HellHandRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectInstance;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.ThaumaturgyEffectInstance;
import geminiclient.gemini.customRenderer.glsl.modules.ThaumaturgyRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.EntityRemoveEvent;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.ListValue;

/**
 * Kill Effect module — selectable cinematic death effects.
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li><b>Hypernova</b> — the 10-stage stellar annihilation (~29s):
 *       summoning array → black hole → accretion → collapse → void →
 *       flash → hypernova → afterglow → fade-out (details below).</li>
 *   <li><b>Hell Hand</b> (地狱之手, ~5.4s) — a molten rift tears open at the
 *       death spot; charred hands climb out, seize the victim's shadow and
 *       yank it down into hell; the rift then iris-snap shuts and dissolves.
 *       All motion curves are continuous across stages.</li>
 *   <li><b>Thaumaturgy Strike</b> (奇术打击, ~9.8s) — a colossal violet sigil
 *       unfurls in the sky, six light columns slam down around the victim, the
 *       ring drains inward, then one giant column punches through the sigil's
 *       centre and breeds a blinding sphere of light that swells and implodes.
 *       All motion curves are continuous across stages.</li>
 * </ul>
 *
 * <h3>Hypernova timeline (10 stages, ~29.0s total)</h3>
 * <ol>
 *   <li>Magic Circle Birth   (0.0–3.2s) — the golden sigil draws itself ring by ring,
 *       sky particles begin appearing</li>
 *   <li>Magic Tower          (3.2–9.6s) — nine counter-rotating tiers rise inside a
 *       precessing armillary cage; the summon script completes</li>
 *   <li>Black Hole Forming   (9.6–12.0s) — event horizon + photon ring, the array is
 *       driven into it, particles pulled inward</li>
 *   <li>Accretion            (12.0–15.6s) — particles spiral inward at high speed</li>
 *   <li>Collapse             (15.6–17.4s) — hole shrinks, extreme particle pull, energy builds</li>
 *   <li>Void                 (17.4–19.2s) — dead silence, all particles consumed, tension builds</li>
 *   <li>Flash                (19.2–20.0s) — dramatic multi-ring light pulse from the singularity</li>
 *   <li>Hypernova            (20.0–24.3s) — sustained shockwaves, fireball, nebula, lightning</li>
 *   <li>Afterglow            (24.3–26.8s) — cooling remnant with lingering glow</li>
 *   <li>Fade-out             (26.8–29.0s) — post-afterglow smooth dissolve; all intersecting
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

    /** Effect style. */
    public static final String MODE_HYPERNOVA    = "Hypernova";
    public static final String MODE_HELL_HAND    = "Hell Hand";
    public static final String MODE_THAUMATURGY  = "Thaumaturgy Strike";
    private final ListValue mode = new ListValue("Mode", MODE_HYPERNOVA,
            new String[]{MODE_HYPERNOVA, MODE_HELL_HAND, MODE_THAUMATURGY});

    /** Number of hands for Hell Hand mode. */
    private final FloatValue handCount = new FloatValue("Hands", 12f, 4f, 20f,
            () -> mode.is(MODE_HELL_HAND));

    /** Number of light columns for Thaumaturgy Strike mode. */
    private final FloatValue pillarCount = new FloatValue("Pillars", 6f, 3f,
            (float) ThaumaturgyEffectInstance.MAX_PILLARS,
            () -> mode.is(MODE_THAUMATURGY));

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

    private final HellHandEffectInstance[] hellEffects = new HellHandEffectInstance[MAX_EFFECTS];
    private int hellEffectCount;

    private final ThaumaturgyEffectInstance[] thaumEffects = new ThaumaturgyEffectInstance[MAX_EFFECTS];
    private int thaumEffectCount;

    // Particle batch buffer (reused each frame)
    private final float[] particleBatch = new float[MAX_PARTICLE_BATCH * 8];

    // Camera shake state
    private float shakeIntensity;

    // ── Constructor ────────────────────────────────────────────────

    public KillEffect() {
        super("KillEffect", ModuleEnum.Visual);
        addValue(mode, handCount, pillarCount, maxEffects, intensity, circleColor,
                enableParticles, cameraShake, bloomStrength);
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
            mergeIntoMostRecent(mergeCount, lastSpawnX, lastSpawnY, lastSpawnZ);
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

            // Update explosion burst debris: spawned at the flash peak,
            // flies outward through hypernova, dissipates in afterglow
            if (enableParticles.enabled && stage >= KillEffectInstance.STAGE_FLASH
                    && stage <= KillEffectInstance.STAGE_AFTERGLOW) {
                float mergeScale = 1.0f + (inst.mergeCount - 1) * 0.25f;
                inst.updateBurstParticles(dt, stage, nowMs, mergeScale);
            }

            // Camera shake: collapse → void (lingering) → flash → hypernova
            if (cameraShake.enabled) {
                if (stage == KillEffectInstance.STAGE_BLACK_HOLE) {
                    // One jolt as the horizon tears open. Without it the birth is
                    // silent — a disk inflating on a steady frame reads as a
                    // fade-in no matter how the array around it behaves.
                    double since = inst.elapsedSec(nowMs) - KillEffectInstance.T_TOWER_END;
                    if (since >= 0 && since < 1.0) {
                        float x = (float) (since * 6.0);
                        shakeIntensity = Math.max(shakeIntensity, 3.6f * x * (float) Math.exp(1.0 - x));
                    }
                } else if (stage == KillEffectInstance.STAGE_COLLAPSE) {
                    float progress = inst.stageProgress(nowMs);
                    shakeIntensity = Math.max(shakeIntensity, progress * 4.0f);
                } else if (stage == KillEffectInstance.STAGE_VOID) {
                    // Continue from collapse's 3.0 peak, quadratic decay —
                    // no step down at the boundary
                    float progress = inst.stageProgress(nowMs);
                    float d = 1f - progress;
                    shakeIntensity = Math.max(shakeIntensity, 4.0f * d * d);
                } else if (stage == KillEffectInstance.STAGE_FLASH) {
                    float progress = inst.stageProgress(nowMs);
                    // Bell-curve attack/release, synced with the light pulse —
                    // starts near zero instead of snapping to full strength
                    float t = progress;
                    float bell = t < 0.4f
                        ? (float)Math.exp(-((t - 0.4f) * (t - 0.4f)) / 0.04f)
                        : (float)Math.exp(-((t - 0.4f) * (t - 0.4f)) / 0.12f);
                    shakeIntensity = Math.max(shakeIntensity, 8.5f * bell);
                } else if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
                    float progress = inst.stageProgress(nowMs);
                    // Sustained sub-bass rumble with several diminishing blast pulses.
                    float attack = Math.min(progress / 0.05f, 1f);
                    attack = attack * attack * (3f - 2f * attack);
                    float sustain = 1.0f - progress * 0.62f;
                    float pulse = 0.82f + 0.18f
                            * Math.abs((float)Math.sin(progress * Math.PI * 7.0f));
                    float novaShake = sustain * pulse * 7.5f * attack;
                    shakeIntensity = Math.max(shakeIntensity, novaShake);
                }
            }

            // Mark finished effects
            if (stage < 0) {
                inst.alive = false;
            }
        }

        // ── Hell Hand mode ───────────────────────────────────────
        // Embers are frame-updated inside HellHandRenderer.draw (render-paced);
        // tick only handles shake + lifetime here.
        for (int i = 0; i < hellEffectCount; i++) {
            HellHandEffectInstance h = hellEffects[i];
            if (!h.alive) continue;

            int st = h.currentStage(nowMs);

            if (cameraShake.enabled && st >= 0) {
                float p = h.stageProgress(nowMs);
                if (st == HellHandEffectInstance.STAGE_DRAG) {
                    // Ground rumbles as the prey is dragged down
                    float pulse = 0.78f + 0.22f * (float) Math.sin(p * 22.0);
                    shakeIntensity = Math.max(shakeIntensity, 2.4f * pulse * (0.4f + 0.6f * p));
                } else if (st == HellHandEffectInstance.STAGE_RIFT_CLOSE) {
                    // Closing jolt, decaying
                    shakeIntensity = Math.max(shakeIntensity, 1.7f * (1f - p) * (1f - p));
                }
            }

            if (st < 0) h.alive = false;
        }
        compactHellEffects();

        // ── Thaumaturgy Strike mode ──────────────────────────────
        for (int i = 0; i < thaumEffectCount; i++) {
            ThaumaturgyEffectInstance t = thaumEffects[i];
            if (!t.alive) continue;

            if (t.currentStage(nowMs) < 0) {
                t.alive = false;
                continue;
            }

            if (enableParticles.enabled) t.updateSparks(dt, nowMs);

            if (cameraShake.enabled) {
                float shake = 0f;
                // A jolt per column as it drives into the ground
                for (int p = 0; p < t.pillarCount; p++) {
                    double age = t.pillarImpactAge(p, nowMs);
                    if (age >= 0 && age < 0.35) {
                        shake = Math.max(shake, 1.9f * (float) Math.exp(-age * 9.0));
                    }
                }
                // The centre slam dominates everything before it
                double grand = t.grandImpactAge(nowMs);
                if (grand >= 0) shake = Math.max(shake, 9.0f * (float) Math.exp(-grand * 2.1));
                shakeIntensity = Math.max(shakeIntensity,
                        shake * (1f + (t.mergeCount - 1) * 0.18f));
            }
        }
        compactThaumEffects();

        compactEffects();
    }

    // ── Render ─────────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (effectCount == 0 && hellEffectCount == 0 && thaumEffectCount == 0) return;

        long nowMs = System.currentTimeMillis();
        float globalIntensity = intensity.getValue();

        // ── Thaumaturgy Strike instances ──────────────────────────
        for (int i = 0; i < thaumEffectCount; i++) {
            ThaumaturgyEffectInstance t = thaumEffects[i];
            if (!t.alive) continue;
            if (t.currentStage(nowMs) < 0) continue;

            float mergeScale = 1.0f + (t.mergeCount - 1) * 0.25f;
            ThaumaturgyRenderer.draw(event.poseStack(), t, nowMs, globalIntensity * mergeScale);
        }

        // ── Hell Hand instances ─────────────────────────────────
        for (int i = 0; i < hellEffectCount; i++) {
            HellHandEffectInstance h = hellEffects[i];
            if (!h.alive) continue;
            if (h.currentStage(nowMs) < 0) continue;

            float mergeScale = 1.0f + (h.mergeCount - 1) * 0.25f;
            HellHandRenderer.draw(event.poseStack(), h, nowMs, globalIntensity * mergeScale);
        }

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
                KillEffectRenderer.drawMagic(event.poseStack(), inst, nowMs, scaledIntensity);
            }

            // Black hole: may overlap with tower for smooth transition
            if (inst.shouldRenderBlackHole(nowMs)) {
                KillEffectRenderer.drawBlackHole(event.poseStack(), inst, nowMs, scaledIntensity);
            }

            // Draw particles for all stages that have them (stages 1-5):
            //   - Stage 1-2: sky particles floating in the sky
            //   - Stage 3-5: particles accreting into the black hole
            if (enableParticles.enabled && stage >= 1
                    && stage <= KillEffectInstance.STAGE_COLLAPSE
                    && inst.getParticleCount() > 0) {
                // Ease survivors to zero over the last 30% of collapse —
                // without this the whole accretion field hard-cuts at the
                // COLLAPSE→VOID boundary (render gate closes there).
                float particleAlpha = scaledIntensity;
                if (stage == KillEffectInstance.STAGE_COLLAPSE) {
                    float cp = inst.stageProgress(nowMs);
                    if (cp > 0.7f) {
                        float t = (cp - 0.7f) / 0.3f;
                        particleAlpha *= 1f - t * t * (3f - 2f * t);
                    }
                }
                int particleCount = inst.fillParticleBatch(
                        particleBatch, MAX_PARTICLE_BATCH, nowMs, particleAlpha);
                if (particleCount > 0) {
                    KillEffectRenderer.drawParticles(
                            event.poseStack(), particleBatch, particleCount);
                }
            }

            // Explosion burst debris (stages 7-9): hot fragments flying
            // outward from the detonation, cooling and dissipating
            if (enableParticles.enabled && stage >= KillEffectInstance.STAGE_FLASH
                    && stage <= KillEffectInstance.STAGE_AFTERGLOW
                    && inst.getBurstCount() > 0) {
                int burstCount = inst.fillBurstBatch(
                        particleBatch, MAX_PARTICLE_BATCH, nowMs, scaledIntensity);
                if (burstCount > 0) {
                    KillEffectRenderer.drawParticles(
                            event.poseStack(), particleBatch, burstCount);
                }
            }

            // Hypernova + Afterglow + Fade-out (smooth dissolve)
            if (stage == KillEffectInstance.STAGE_HYPERNOVA
                    || stage == KillEffectInstance.STAGE_AFTERGLOW
                    || stage == KillEffectInstance.STAGE_FADE_OUT) {
                KillEffectRenderer.drawHypernova(event.poseStack(), inst, nowMs, scaledIntensity);
            }
        }
    }

    // ── Internal ───────────────────────────────────────────────────

    /** Fold an aggregated kill into the newest living instance of the active mode. */
    private void mergeIntoMostRecent(int count, double x, double y, double z) {
        net.minecraft.world.phys.Vec3 pos = new net.minecraft.world.phys.Vec3(x, y, z);
        if (mode.is(MODE_HELL_HAND)) {
            for (int i = hellEffectCount - 1; i >= 0; i--) {
                if (hellEffects[i] == null || !hellEffects[i].alive) continue;
                hellEffects[i].mergeCount = count;
                hellEffects[i].position = pos;
                return;
            }
        } else if (mode.is(MODE_THAUMATURGY)) {
            for (int i = thaumEffectCount - 1; i >= 0; i--) {
                if (thaumEffects[i] == null || !thaumEffects[i].alive) continue;
                thaumEffects[i].mergeCount = count;
                thaumEffects[i].position = pos;
                return;
            }
        } else {
            for (int i = effectCount - 1; i >= 0; i--) {
                if (effects[i] == null || !effects[i].alive) continue;
                effects[i].mergeCount = count;
                effects[i].position = pos;
                return;
            }
        }
    }

    private void spawnEffect(net.minecraft.world.phys.Vec3 position) {
        // Enforce max effects limit (across every mode pool)
        int max = (int) maxEffects.getValue();
        while (effectCount + hellEffectCount + thaumEffectCount >= max) {
            if (!removeOldestEffect()) break;
        }

        if (mode.is(MODE_HELL_HAND)) {
            if (hellEffectCount >= MAX_EFFECTS) return;
            hellEffects[hellEffectCount] = new HellHandEffectInstance(
                    position, System.currentTimeMillis(), (int) handCount.getValue());
            hellEffectCount++;
            return;
        }

        if (mode.is(MODE_THAUMATURGY)) {
            if (thaumEffectCount >= MAX_EFFECTS) return;
            thaumEffects[thaumEffectCount] = new ThaumaturgyEffectInstance(
                    position, System.currentTimeMillis(), (int) pillarCount.getValue());
            thaumEffectCount++;
            return;
        }

        // Find free slot
        int slot = effectCount;
        if (slot >= MAX_EFFECTS) return;

        effects[slot] = new KillEffectInstance(position, System.currentTimeMillis());
        effectCount++;
    }

    /** Removes the globally oldest effect. Returns false when the pool is empty. */
    private boolean removeOldestEffect() {
        long oldestTime = Long.MAX_VALUE;
        int oldestIdx = -1;
        int oldestPool = -1; // 0 = hypernova, 1 = hell hand, 2 = thaumaturgy
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive && effects[i].startTimeMs < oldestTime) {
                oldestTime = effects[i].startTimeMs;
                oldestIdx = i;
                oldestPool = 0;
            }
        }
        for (int i = 0; i < hellEffectCount; i++) {
            if (hellEffects[i] != null && hellEffects[i].alive && hellEffects[i].startTimeMs < oldestTime) {
                oldestTime = hellEffects[i].startTimeMs;
                oldestIdx = i;
                oldestPool = 1;
            }
        }
        for (int i = 0; i < thaumEffectCount; i++) {
            if (thaumEffects[i] != null && thaumEffects[i].alive && thaumEffects[i].startTimeMs < oldestTime) {
                oldestTime = thaumEffects[i].startTimeMs;
                oldestIdx = i;
                oldestPool = 2;
            }
        }
        if (oldestIdx < 0) return false;
        switch (oldestPool) {
            case 1 -> hellEffects[oldestIdx].alive = false;
            case 2 -> thaumEffects[oldestIdx].alive = false;
            default -> effects[oldestIdx].alive = false;
        }
        return true;
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

    /** Compact the Hell Hand array, removing dead entries. */
    private void compactHellEffects() {
        int w = 0;
        for (int r = 0; r < hellEffectCount; r++) {
            if (!hellEffects[r].alive) {
                hellEffects[r] = null;
                continue;
            }
            if (r != w) {
                hellEffects[w] = hellEffects[r];
                hellEffects[r] = null;
            }
            w++;
        }
        hellEffectCount = w;
    }

    /** Compact the Thaumaturgy array, removing dead entries. */
    private void compactThaumEffects() {
        int w = 0;
        for (int r = 0; r < thaumEffectCount; r++) {
            if (!thaumEffects[r].alive) {
                thaumEffects[r] = null;
                continue;
            }
            if (r != w) {
                thaumEffects[w] = thaumEffects[r];
                thaumEffects[r] = null;
            }
            w++;
        }
        thaumEffectCount = w;
    }

    private void clearAllEffects() {
        for (int i = 0; i < effectCount; i++) {
            effects[i] = null;
        }
        effectCount = 0;
        for (int i = 0; i < hellEffectCount; i++) {
            hellEffects[i] = null;
        }
        hellEffectCount = 0;
        for (int i = 0; i < thaumEffectCount; i++) {
            thaumEffects[i] = null;
        }
        thaumEffectCount = 0;
    }

    // ── Public queries ─────────────────────────────────────────────

    /** Whether any active effects exist (for post-processing gate). */
    public boolean hasActiveEffects() {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) return true;
        }
        for (int i = 0; i < hellEffectCount; i++) {
            if (hellEffects[i] != null && hellEffects[i].alive) return true;
        }
        for (int i = 0; i < thaumEffectCount; i++) {
            if (thaumEffects[i] != null && thaumEffects[i].alive) return true;
        }
        return false;
    }

    /** Whether the currently selected mode is the Hell Hand timeline. */
    public boolean isHellHandMode() {
        return mode.is(MODE_HELL_HAND);
    }

    /** Whether the currently selected mode is the Thaumaturgy Strike timeline. */
    public boolean isThaumaturgyMode() {
        return mode.is(MODE_THAUMATURGY);
    }

    /** The first alive Hell Hand instance (for post-processing), or null. */
    public HellHandEffectInstance getPrimaryHellEffect() {
        for (int i = 0; i < hellEffectCount; i++) {
            if (hellEffects[i] != null && hellEffects[i].alive) return hellEffects[i];
        }
        return null;
    }

    /** The first alive Thaumaturgy instance (for post-processing), or null. */
    public ThaumaturgyEffectInstance getPrimaryThaumEffect() {
        for (int i = 0; i < thaumEffectCount; i++) {
            if (thaumEffects[i] != null && thaumEffects[i].alive) return thaumEffects[i];
        }
        return null;
    }

    /**
     * Hell-mode chain fade for post-processing: multiplies bloom and the
     * ACES chain so the tone-map ramps out with the effect instead of
     * popping off when it ends. Returns 1.0 for non-Hell modes.
     */
    public float getHellChainFade(long nowMs) {
        if (!mode.is(MODE_HELL_HAND)) return 1f;
        HellHandEffectInstance h = getPrimaryHellEffect();
        return h == null ? 1f : h.masterFade(nowMs);
    }

    /**
     * Thaumaturgy-mode chain fade. Same contract as {@link #getHellChainFade}:
     * 1.0 unless this mode is active.
     */
    public float getThaumChainFade(long nowMs) {
        if (!mode.is(MODE_THAUMATURGY)) return 1f;
        ThaumaturgyEffectInstance t = getPrimaryThaumEffect();
        return t == null ? 1f : t.masterFade(nowMs);
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
                    effects[i].position.y + KillEffectInstance.HOLE_VISUAL_LIFT, // visual center
                    effects[i].position.z
                };
            }
        }
        HellHandEffectInstance h = getPrimaryHellEffect();
        if (h != null) {
            return new double[]{
                h.position.x,
                h.position.y + 0.9, // rift lip / victim grab height
                h.position.z
            };
        }
        ThaumaturgyEffectInstance t = getPrimaryThaumEffect();
        if (t != null) {
            return new double[]{
                t.position.x,
                t.position.y + 2.2, // base of the nova sphere
                t.position.z
            };
        }
        return null;
    }

    /**
     * Apparent shadow radius of the primary effect's black hole, in blocks.
     * The screen-space post pass must shade exactly the disk the world-space
     * billboard draws, so it reads the size from the instance instead of
     * carrying its own constant. Returns 0 when no hole is open.
     */
    public float getPrimaryBlackHoleShadowRadius(long nowMs) {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) {
                int stage = effects[i].currentStage(nowMs);
                if (stage < KillEffectInstance.STAGE_BLACK_HOLE
                        || stage > KillEffectInstance.STAGE_COLLAPSE) return 0f;
                return effects[i].blackHoleShadowRadiusWorld(nowMs);
            }
        }
        return 0f;
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
        HellHandEffectInstance h = getPrimaryHellEffect();
        if (h != null) return h.mergeCount;
        ThaumaturgyEffectInstance t = getPrimaryThaumEffect();
        if (t != null) return t.mergeCount;
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
        HellHandEffectInstance h = getPrimaryHellEffect();
        if (h != null) return h.startTimeMs;
        ThaumaturgyEffectInstance t = getPrimaryThaumEffect();
        if (t != null) return t.startTimeMs;
        return 0L;
    }
}
