package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.SkyLanternRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.SkyLanternRenderer.FrameCtx;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * 孔明灯 (Sky Lantern) — paper lanterns rising into the night sky.
 *
 * <h3>Visual layers</h3>
 * <ol>
 *   <li>Paper shell — rounded dome with Fresnel edge glow (paper backlight)</li>
 *   <li>Bamboo frame — thin hoops at equator/shoulder/opening</li>
 *   <li>Candle tray + suspension strings</li>
 *   <li>Inner flame — small bright white-yellow</li>
 *   <li>Outer flame — medium orange-red with flicker</li>
 *   <li>Glow sprite — large radial halo</li>
 *   <li>Ember sparks — tiny particles drifting upward from the flame</li>
 * </ol>
 *
 */
public class SkyLantern extends Module {

    // ── Config ───────────────────────────────────────────────────

    private final FloatValue spawnDelay   = new FloatValue("Spawn Delay", 2f, 0.1f, 8f);
    private final FloatValue maxAliveMs   = new FloatValue("Lifetime", 8000f, 2000f, 20000f);
    private final FloatValue riseSpeed    = new FloatValue("Rise Speed", 0.6f, 0.1f, 2.0f);
    private final FloatValue swayAmount   = new FloatValue("Sway", 0.5f, 0.0f, 1.5f);
    private final FloatValue flamePower   = new FloatValue("Flame Power", 0.8f, 0.2f, 1.5f);
    private final ColorValue paperColor   = new ColorValue("Paper Color", 0xFFFF9933);
    private final BoolValue  showEmbers   = new BoolValue("Embers", true);

    // ── Constants ────────────────────────────────────────────────

    private static final int MAX_LANTERNS   = 20;
    private static final int MAX_EMBERS     = 160;  // ~8 per lantern
    private static final int EMBER_LIFETIME = 800;  // ms

    // ── Per-lantern state (primitive arrays) ─────────────────────

    private final float[] lx      = new float[MAX_LANTERNS];
    private final float[] ly      = new float[MAX_LANTERNS];
    private final float[] lz      = new float[MAX_LANTERNS];
    private final float[] lpy     = new float[MAX_LANTERNS];
    private final float[] lswayX  = new float[MAX_LANTERNS];
    private final float[] lswayZ  = new float[MAX_LANTERNS];
    private final float[] lphase  = new float[MAX_LANTERNS];
    private final long[]  lstart  = new long[MAX_LANTERNS];
    private final boolean[] lalive = new boolean[MAX_LANTERNS];
    private int lanternCount;

    // Ember ring buffers
    private final float[]  emberX      = new float[MAX_EMBERS];
    private final float[]  emberY      = new float[MAX_EMBERS];
    private final float[]  emberZ      = new float[MAX_EMBERS];
    private final float[]  emberVX     = new float[MAX_EMBERS];
    private final float[]  emberVY     = new float[MAX_EMBERS];
    private final float[]  emberVZ     = new float[MAX_EMBERS];
    private final long[]   emberTime   = new long[MAX_EMBERS];
    private final boolean[] emberAlive = new boolean[MAX_EMBERS];
    private int emberHead;
    private int emberCount;

    // Batch arrays
    private final float[] bodyBatch  = new float[MAX_LANTERNS * 8];
    private final float[] innerBatch = new float[MAX_LANTERNS * 8];
    private final float[] outerBatch = new float[MAX_LANTERNS * 8];
    private final float[] glowBatch  = new float[MAX_LANTERNS * 8];
    private final float[] emberBatch = new float[MAX_EMBERS * 8];

    private int tickCounter;

    // ── Constructor ──────────────────────────────────────────────

    public SkyLantern() {
        super("SkyLantern", ModuleEnum.Visual);
        addValue(spawnDelay, maxAliveMs, riseSpeed, swayAmount, flamePower, paperColor, showEmbers);
    }

    @Override public void onDisabled() {
        for (int i = 0; i < lanternCount; i++) lalive[i] = false;
        lanternCount = 0;
        Arrays.fill(emberAlive, false);
        emberCount = 0;
        emberHead = 0;
        tickCounter = 0;
    }

    // ── Update ───────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.tickCount <= 1) { onDisabled(); return; }

        long now = System.currentTimeMillis();
        float aliveMs = maxAliveMs.getValue();
        float rise = riseSpeed.getValue() / 20f;
        float sway = swayAmount.getValue();
        float fp   = flamePower.getValue();

        // Update lanterns
        for (int i = 0; i < lanternCount; i++) {
            if (!lalive[i]) continue;
            float life = (now - lstart[i]) / aliveMs;
            if (life >= 1f) { lalive[i] = false; continue; }

            lpy[i] = ly[i];
            ly[i] += rise;
            lphase[i] += 0.025f + i * 0.003f;
            float s = sway * (0.6f + life * 0.4f);
            lswayX[i] = (float) Math.sin(lphase[i] * 1.1f) * s;
            lswayZ[i] = (float) Math.cos(lphase[i] * 0.85f + 1.2f) * s;

            // Spawn embers from this lantern's flame position
            if (showEmbers.enabled) {
                float flameY = ly[i] - 0.57f * (0.35f + 0.4f); // approx lantern base
                int emberRate = (int)(2 + fp * 3); // 2-5 per tick depending on flame power
                for (int e = 0; e < emberRate; e++) {
                    spawnEmber(lx[i] + lswayX[i], flameY, lz[i] + lswayZ[i], now);
                }
            }
        }

        // Update embers
        for (int i = 0; i < MAX_EMBERS; i++) {
            if (!emberAlive[i]) continue;
            float age = (now - emberTime[i]) / (float) EMBER_LIFETIME;
            if (age >= 1f) { emberAlive[i] = false; emberCount--; continue; }
            emberX[i] += emberVX[i];
            emberY[i] += emberVY[i];
            emberZ[i] += emberVZ[i];
            // Slight upward acceleration (hot air)
            emberVY[i] += 0.0003f;
        }

        compactLanterns();

        tickCounter++;
        if (tickCounter % Math.max(1, (int)(spawnDelay.getValue() * 20f)) == 0) {
            spawnLantern(now);
        }
    }

    // ── Render ───────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (lanternCount == 0 || mc.player == null) return;

        float pTicks = event.partialTick();
        long now = System.currentTimeMillis();
        float aliveMs = maxAliveMs.getValue();
        float fp = flamePower.getValue();
        boolean embers = showEmbers.enabled;

        int argb = paperColor.getColor();
        float cr = ((argb >> 16) & 0xFF) / 255f;
        float cg = ((argb >> 8)  & 0xFF) / 255f;
        float cb = ( argb        & 0xFF) / 255f;

        FrameCtx ctx = new FrameCtx();

        int bodyN = 0, innerN = 0, outerN = 0, glowN = 0, emberN = 0;

        for (int i = 0; i < lanternCount; i++) {
            if (!lalive[i]) continue;

            float life = (now - lstart[i]) / aliveMs;
            float alpha = computeAlpha(life);
            if (alpha <= 0.001f) continue;

            // Interpolated position
            float bx = lx[i] + lswayX[i];
            float by = lpy[i] + (ly[i] - lpy[i]) * pTicks;
            float bz = lz[i] + lswayZ[i];

            // Distance-based size attenuation
            float dx = bx - ctx.camX, dy = by - ctx.camY, dz = bz - ctx.camZ;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float size = 0.35f + 0.4f * Mth.clamp(1f - dist / 30f, 0f, 1f);

            // Flame base position (bottom of lantern)
            float flameY = by - 0.57f * size;

            // ── Lantern body batch ──
            int bo = bodyN * 8;
            bodyBatch[bo] = bx; bodyBatch[bo+1] = by; bodyBatch[bo+2] = bz;
            bodyBatch[bo+3] = size;
            bodyBatch[bo+4] = cr; bodyBatch[bo+5] = cg; bodyBatch[bo+6] = cb;
            bodyBatch[bo+7] = alpha;
            bodyN++;

            // ── Inner flame (small white-yellow core) ──
            {
                float flicker = 0.8f + (float)Math.sin(now*0.025 + i*3.1f)*0.15f
                                    + (float)Math.cos(now*0.037 - i*2.3f)*0.1f;
                float iSize  = size * 0.08f * fp * flicker;     // world-space
                float iAlpha = alpha * flicker * Mth.clamp(fp, 0.3f, 1f);
                int io = innerN * 8;
                innerBatch[io] = bx; innerBatch[io+1] = flameY; innerBatch[io+2] = bz;
                innerBatch[io+3] = iSize;
                innerBatch[io+4] = 1f;     innerBatch[io+5] = 0.92f; innerBatch[io+6] = 0.55f;
                innerBatch[io+7] = iAlpha;
                innerN++;
            }

            // ── Outer flame (medium orange-red with drift) ──
            {
                float flicker = 0.7f + (float)Math.sin(now*0.018 + i*1.7f)*0.2f
                                    + (float)Math.cos(now*0.029 - i*2.1f)*0.15f;
                float oSize  = size * 0.14f * fp * flicker;     // world-space
                float oAlpha = alpha * 0.75f * flicker * Mth.clamp(fp, 0.3f, 1f);
                int oo = outerN * 8;
                outerBatch[oo] = bx; outerBatch[oo+1] = flameY + oSize * 0.3f; outerBatch[oo+2] = bz;
                outerBatch[oo+3] = oSize;
                outerBatch[oo+4] = 1f;     outerBatch[oo+5] = 0.55f; outerBatch[oo+6] = 0.10f;
                outerBatch[oo+7] = oAlpha;
                outerN++;
            }

            // ── Glow sprite (large radial halo) ──
            {
                float gSize  = size * 0.6f * fp;                // world-space
                float gAlpha = alpha * 0.12f * fp;
                int go = glowN * 8;
                glowBatch[go] = bx; glowBatch[go+1] = flameY; glowBatch[go+2] = bz;
                glowBatch[go+3] = gSize;
                glowBatch[go+4] = 1f;      glowBatch[go+5] = 0.65f; glowBatch[go+6] = 0.15f;
                glowBatch[go+7] = gAlpha;
                glowN++;
            }
        }

        // ── Embers ──
        if (embers) {
            for (int i = 0; i < MAX_EMBERS && emberN < MAX_EMBERS; i++) {
                if (!emberAlive[i]) continue;
                float age = (now - emberTime[i]) / (float) EMBER_LIFETIME;
                if (age >= 1f) continue;
                float eAlpha = (1f - age) * 0.8f;
                float eSize = 0.015f * (1f - age * 0.5f);
                int eo = emberN * 8;
                emberBatch[eo] = emberX[i]; emberBatch[eo+1] = emberY[i]; emberBatch[eo+2] = emberZ[i];
                emberBatch[eo+3] = eSize;
                // Cool from yellow to orange-red
                emberBatch[eo+4] = 1f;
                emberBatch[eo+5] = 0.7f - age * 0.5f;
                emberBatch[eo+6] = 0.2f - age * 0.15f;
                emberBatch[eo+7] = eAlpha;
                emberN++;
            }
        }

        // ── Issue draws (5-6 calls total, independent of particle count) ──
        SkyLanternRenderer.drawLanterns(bodyBatch, bodyN, ctx);
        SkyLanternRenderer.drawFlameInner(innerBatch, innerN, ctx);
        SkyLanternRenderer.drawFlameOuter(outerBatch, outerN, ctx);
        SkyLanternRenderer.drawGlowSprites(glowBatch, glowN, ctx);
        if (emberN > 0) SkyLanternRenderer.drawEmbers(emberBatch, emberN, ctx);
    }

    // ── Internal ─────────────────────────────────────────────────

    private void spawnLantern(long now) {
        if (mc.player == null) return;
        if (lanternCount >= MAX_LANTERNS) return;
        int i = lanternCount++;
        lalive[i] = true;
        lstart[i] = now;
        var p = mc.player.position();
        lx[i] = (float)(p.x + rand(-8.0, 8.0));
        ly[i] = lpy[i] = (float)(p.y + rand(1.0, 3.5));
        lz[i] = (float)(p.z + rand(-8.0, 8.0));
        lswayX[i] = 0; lswayZ[i] = 0;
        lphase[i] = rand(0f, (float)(Math.PI * 2));
    }

    private void spawnEmber(float x, float y, float z, long now) {
        if (emberCount >= MAX_EMBERS) return;
        // Find a free slot
        int slot = emberHead;
        for (int tries = 0; tries < MAX_EMBERS; tries++) {
            if (!emberAlive[slot]) break;
            slot = (slot + 1) % MAX_EMBERS;
        }
        if (emberAlive[slot]) return; // full

        emberAlive[slot] = true;
        emberCount++;
        emberHead = (slot + 1) % MAX_EMBERS;

        emberX[slot] = x + rand(-0.02f, 0.02f);
        emberY[slot] = y;
        emberZ[slot] = z + rand(-0.02f, 0.02f);
        emberVX[slot] = rand(-0.008f, 0.008f);
        emberVY[slot] = rand(0.015f, 0.04f);
        emberVZ[slot] = rand(-0.008f, 0.008f);
        emberTime[slot] = now;
    }

    private void compactLanterns() {
        int w = 0;
        for (int r = 0; r < lanternCount; r++) {
            if (!lalive[r]) continue;
            if (r != w) {
                lx[w]=lx[r]; ly[w]=ly[r]; lz[w]=lz[r];
                lpy[w]=lpy[r]; lswayX[w]=lswayX[r]; lswayZ[w]=lswayZ[r];
                lphase[w]=lphase[r]; lstart[w]=lstart[r];
                lalive[w]=true; lalive[r]=false;
            }
            w++;
        }
        lanternCount = w;
    }

    private static float computeAlpha(float life) {
        if (life >= 1f) return 0f;
        if (life < 0.06f) return life / 0.06f;
        if (life < 0.78f) return 1f;
        return 1f - (life - 0.78f) / 0.22f;
    }

    private static float rand(double min, double max) {
        return (float)(min + Math.random() * (max - min));
    }
}
