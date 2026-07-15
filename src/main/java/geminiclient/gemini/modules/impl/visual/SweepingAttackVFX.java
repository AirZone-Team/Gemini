package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.SweepAttackInstance;
import geminiclient.gemini.customRenderer.glsl.modules.SweepAttackRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import net.minecraft.world.phys.Vec3;

/**
 * Sweeping Attack VFX (横扫攻击特效)
 *
 * <h3>10-layer cinematic slash effect</h3>
 * <ol>
 *   <li>Triple-layer arc (blue / purple / white SDF)</li>
 *   <li>Speed lines (radiating ribbons)</li>
 *   <li>Energy particles (camera-facing billboards)</li>
 *   <li>Photon ring (expanding rainbow SDF)</li>
 *   <li>Post-processing (distortion + chromatic aberration)</li>
 * </ol>
 *
 * <h3>Animation timeline (~1.2s)</h3>
 * <ul>
 *   <li>0.00–0.15s: Arc sweep-in</li>
 *   <li>0.00–0.80s: Particles active</li>
 *   <li>0.00–1.00s: Photon ring expansion</li>
 *   <li>0.00–1.20s: Post-processing fade</li>
 * </ul>
 *
 * <h3>Performance</h3>
 * <p>Up to 4 simultaneous effects.  ~256 arc vertices + ~64 particle
 * quads per effect.  Target: ~0.5ms GPU per effect @ 1080p60.</p>
 */
public class SweepingAttackVFX extends Module {

    // ── Config values ──────────────────────────────────────────────

    /** Maximum simultaneous effects (1–8). */
    private final FloatValue maxEffects = new FloatValue("Max Effects", 4f, 1f, 8f);

    /** Global effect intensity multiplier. */
    private final FloatValue intensity  = new FloatValue("Intensity", 1.0f, 0.1f, 2.0f);

    /** Arc sweep speed multiplier. */
    private final FloatValue speed      = new FloatValue("Speed", 1.0f, 0.3f, 3.0f);

    /** Enable particle system. */
    private final BoolValue  enableParticles = new BoolValue("Particles", true);

    /** Enable photon ring. */
    private final BoolValue  enableRing      = new BoolValue("Photon Ring", true);

    /** Enable post-processing (distortion + chromatic aberration). */
    private final BoolValue  enablePost      = new BoolValue("Post-FX", true);

    /** Distortion strength. */
    private final FloatValue distortionStr   = new FloatValue("Distortion", 0.4f, 0f, 1f);

    /** Chromatic aberration strength. */
    private final FloatValue chromaticStr    = new FloatValue("Chromatic", 0.3f, 0f, 1f);

    /** Arc primary color. */
    private final ColorValue arcColor        = new ColorValue("Arc Color", 0xFF44CCFF);

    // ── Constants ─────────────────────────────────────────────────

    private static final int MAX_EFFECTS = 8;

    // ── Effect slots ───────────────────────────────────────────────

    private final SweepAttackInstance[] effects = new SweepAttackInstance[MAX_EFFECTS];
    private int effectCount;

    // ── Constructor ────────────────────────────────────────────────

    public SweepingAttackVFX() {
        super("SweepingAttackVFX", ModuleEnum.Visual);
        addValue(maxEffects, intensity, speed, enableParticles, enableRing,
                enablePost, distortionStr, chromaticStr, arcColor);
    }

    @Override
    public void onDisabled() {
        clearAllEffects();
    }

    // ── Sweep attack trigger (called from MixinPlayer.doSweepAttack) ──

    /**
     * Spawn a sweep attack effect. Called from the doSweepAttack mixin.
     *
     * @param player    the attacking player
     * @param targetX   X position of the attack target (for direction)
     * @param targetZ   Z position of the attack target (for direction)
     */
    public void spawnSweepEffect(net.minecraft.world.entity.player.Player player,
                                  double targetX, double targetZ) {
        if (mc.player == null || mc.level == null) return;

        // Direction from player to target
        double dx = targetX - player.getX();
        double dz = targetZ - player.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        float dirX, dirZ;
        if (len > 0.001) {
            dirX = (float)(dx / len);
            dirZ = (float)(dz / len);
        } else {
            // Fallback: use player look direction
            float yaw = (float) Math.toRadians(player.getYRot());
            dirX = (float) -Math.sin(yaw);
            dirZ = (float) Math.cos(yaw);
        }

        // Arc spans ~120 degrees centered on the slash direction
        float baseAngle = (float) Math.atan2(dirZ, dirX);
        float arcRange = (float) Math.toRadians(120);
        float arcStart = baseAngle - arcRange * 0.5f;
        float arcEnd   = baseAngle + arcRange * 0.5f;

        spawnEffect(player.position(), dirX, dirZ, arcStart, arcEnd);
    }

    // ── Update ─────────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        long nowMs = System.currentTimeMillis();
        float dt = 0.05f; // ~20tps

        for (int i = 0; i < effectCount; i++) {
            SweepAttackInstance inst = effects[i];
            if (!inst.alive) continue;

            // Update particles
            if (enableParticles.enabled) {
                inst.updateParticles(dt);
            }

            // Mark finished effects
            if (!inst.isAlive(nowMs)) {
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

        int col = arcColor.getColor();
        float cr = ((col >> 16) & 0xFF) / 255f;
        float cg = ((col >> 8) & 0xFF) / 255f;
        float cb = (col & 0xFF) / 255f;

        for (int i = 0; i < effectCount; i++) {
            SweepAttackInstance inst = effects[i];
            if (!inst.alive) continue;

            SweepAttackRenderer.draw(
                    event.poseStack(), inst, nowMs,
                    globalIntensity, cr, cg, cb);
        }
    }

    // ── Post-processing ────────────────────────────────────────────

    /**
     * Run post-processing passes. Call from MixinGameRenderer.
     */
    public void processPost() {
        if (!enablePost.enabled) return;
        if (effectCount == 0) return;

        long nowMs = System.currentTimeMillis();
        float maxDistort = 0f;
        float maxChrom = 0f;

        for (int i = 0; i < effectCount; i++) {
            SweepAttackInstance inst = effects[i];
            if (!inst.alive) continue;

            float progress = inst.effectProgress(nowMs);
            // Post-processing fades out over the effect lifetime
            float fade = 1f - smoothstep(0.5f, 1.0f, progress);

            maxDistort = Math.max(maxDistort, distortionStr.getValue() * fade);
            maxChrom   = Math.max(maxChrom, chromaticStr.getValue() * fade);
        }

        SweepAttackRenderer.processPost(maxDistort, maxChrom);
    }

    // ── Internal ───────────────────────────────────────────────────

    private void spawnEffect(Vec3 position, float dirX, float dirZ,
                              float arcStart, float arcEnd) {
        int max = (int) maxEffects.getValue();
        while (effectCount >= max) {
            removeOldestEffect();
        }

        int slot = effectCount;
        if (slot >= MAX_EFFECTS) return;

        effects[slot] = new SweepAttackInstance(position, dirX, dirZ, arcStart, arcEnd);
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

    /** Whether any active effects exist. */
    public boolean hasActiveEffects() {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) return true;
        }
        return false;
    }

    // ── Helpers ────────────────────────────────────────────────────

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }
}
