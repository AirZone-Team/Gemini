package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import geminiclient.gemini.customRenderer.GeminiRenderPipelines;
import geminiclient.gemini.customRenderer.GeminiTesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GPU-accelerated renderer for the <b>Hell's Hand</b> kill-effect mode.
 *
 * <h3>Pipelines</h3>
 * <ul>
 *   <li>{@link #RIFT_PIPE} — ground plane: molten rift + iris closing</li>
 *   <li>{@link #HAND_PIPE} — charred limb chain (forearm / palm / phalange quads)</li>
 *   <li>{@link #VICTIM_PIPE} — struggling shadow silhouette being dragged in</li>
 *   <li>{@link #EMBER_PIPE} — burning embers torn from the rift lip</li>
 * </ul>
 *
 * <h3>Vertex encoding (POSITION_TEX_COLOR)</h3>
 * <ul>
 *   <li>RIFT:   r = open, g = close, b = boil, a = alpha</li>
 *   <li>HAND:   r = curl, g = per-part seed, b = boil, a = alpha</li>
 *   <li>VICTIM: r = sink, g = struggle, b = boil, a = alpha</li>
 *   <li>EMBER:  r = lifeRatio, g/b unused, a = alpha</li>
 * </ul>
 *
 * <p>All animation curves are computed on the CPU from the instance's
 * elapsed time; the shaders only shade.</p>
 */
public final class HellHandRenderer {

    private HellHandRenderer() {}

    // ════════════════════════════════════════════════════════════════
    //  Pipelines
    // ════════════════════════════════════════════════════════════════

    private static final DepthStencilState EFFECT_DEPTH =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 1.0F);

    private static final ColorTargetState ALPHA_BLEND = new ColorTargetState(new BlendFunction(
            BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA,
            BlendFactor.ONE, BlendFactor.ZERO));

    private static final ColorTargetState ADDITIVE_BLEND = new ColorTargetState(new BlendFunction(
            BlendFactor.SRC_ALPHA, BlendFactor.ONE,
            BlendFactor.ONE, BlendFactor.ZERO));

    /** Molten ground rift — solid dark void with bright flame lip. */
    public static final RenderPipeline RIFT_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_hell_rift"))
            .withVertexShader(getIdentifier("core/kill_effect_hell_rift"))
            .withFragmentShader(getIdentifier("core/kill_effect_hell_rift"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ALPHA_BLEND)
            .withCull(false)
            .build();

    /** Charred hands rising from the rift. */
    public static final RenderPipeline HAND_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_hell_hand"))
            .withVertexShader(getIdentifier("core/kill_effect_hell_hand"))
            .withFragmentShader(getIdentifier("core/kill_effect_hell_hand"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ALPHA_BLEND)
            .withCull(false)
            .build();

    /** Shadow silhouette being dragged to hell. */
    public static final RenderPipeline VICTIM_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_hell_victim"))
            .withVertexShader(getIdentifier("core/kill_effect_hell_victim"))
            .withFragmentShader(getIdentifier("core/kill_effect_hell_victim"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ALPHA_BLEND)
            .withCull(false)
            .build();

    /** Burning embers — own additive pipeline (no Hypernova shader reuse). */
    public static final RenderPipeline EMBER_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_hell_ember"))
            .withVertexShader(getIdentifier("core/kill_effect_hell_ember"))
            .withFragmentShader(getIdentifier("core/kill_effect_hell_ember"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    private static final RenderType RIFT_TYPE   = createRenderType("gemini_kill_hell_rift", RIFT_PIPE);
    private static final RenderType HAND_TYPE   = createRenderType("gemini_kill_hell_hand", HAND_PIPE);
    private static final RenderType VICTIM_TYPE = createRenderType("gemini_kill_hell_victim", VICTIM_PIPE);
    private static final RenderType EMBER_TYPE  = createRenderType("gemini_kill_hell_ember", EMBER_PIPE);

    private static RenderType createRenderType(String name, RenderPipeline pipe) {
        return RenderType.create(name,
                RenderSetup.builder(pipe).sortOnUpload()
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .createRenderSetup());
    }

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(RIFT_PIPE);
        registry.accept(HAND_PIPE);
        registry.accept(VICTIM_PIPE);
        registry.accept(EMBER_PIPE);
    }

    // ════════════════════════════════════════════════════════════════
    //  Camera helpers
    // ════════════════════════════════════════════════════════════════

    private static final Vector3f CAM_UP    = new Vector3f();
    private static final Vector3f CAM_RIGHT = new Vector3f();

    private static void updateCameraVectors() {
        Camera cam = mc.getEntityRenderDispatcher().camera;
        Quaternionf rot = cam.rotation();
        CAM_UP.set(0, 1, 0);
        rot.transform(CAM_UP);
        CAM_RIGHT.set(1, 0, 0);
        rot.transform(CAM_RIGHT);
    }

    /** Pack float RGBA into int ARGB (channels clamped to 0..1). */
    private static int packColor(float r, float g, float b, float a) {
        int ir = (int) (Math.clamp(r, 0f, 1f) * 255f);
        int ig = (int) (Math.clamp(g, 0f, 1f) * 255f);
        int ib = (int) (Math.clamp(b, 0f, 1f) * 255f);
        int ia = (int) (Math.clamp(a, 0f, 1f) * 255f);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static float smoothstep01(float x) {
        x = Math.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /** Emit a square camera-facing billboard quad (positions camera-relative). */
    private static void emitBillboard(BufferBuilder buf, Matrix4f vm,
                                      float cx, float cy, float cz,
                                      float px, float py, float pz,
                                      float halfSize, int rgba) {
        float rx = px - cx, ry = py - cy, rz = pz - cz;
        float rpx = CAM_RIGHT.x * halfSize, rpy = CAM_RIGHT.y * halfSize, rpz = CAM_RIGHT.z * halfSize;
        float upx = CAM_UP.x * halfSize,    upy = CAM_UP.y * halfSize,    upz = CAM_UP.z * halfSize;
        buf.addVertex(vm, rx - rpx - upx, ry - rpy - upy, rz - rpz - upz).setUv(0f, 0f).setColor(rgba);
        buf.addVertex(vm, rx - rpx + upx, ry - rpy + upy, rz - rpz + upz).setUv(0f, 1f).setColor(rgba);
        buf.addVertex(vm, rx + rpx + upx, ry + rpy + upy, rz + rpz + upz).setUv(1f, 1f).setColor(rgba);
        buf.addVertex(vm, rx + rpx - upx, ry + rpy - upy, rz + rpz - upz).setUv(1f, 0f).setColor(rgba);
    }

    // ════════════════════════════════════════════════════════════════
    //  Geometry helpers (camera-relative world coordinates)
    // ════════════════════════════════════════════════════════════════

    /**
     * Emit a tapered quad whose plane contains the segment A→B and whose
     * width axis is perpendicular to both the segment and the view
     * direction — a "ribbon billboard" that stays solid from any angle.
     */
    private static void emitSeg(BufferBuilder buf, Matrix4f vm,
                                float ax, float ay, float az,
                                float bx, float by, float bz,
                                float wa, float wb, int rgba) {
        // Segment direction
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-4f) return;
        dx /= dl; dy /= dl; dz /= dl;

        // Width axis = dir × viewVec(a). Fall back to camera-right when the
        // segment points at/away from the camera (degenerate cross).
        float ux = dy * az - dz * ay;
        float uy = dz * ax - dx * az;
        float uz = dx * ay - dy * ax;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 0.02f) {
            // Project CAM_RIGHT perpendicular to the segment
            ux = CAM_RIGHT.x - dx * (CAM_RIGHT.x * dx + CAM_RIGHT.y * dy + CAM_RIGHT.z * dz);
            uy = CAM_RIGHT.y - dy * (CAM_RIGHT.x * dx + CAM_RIGHT.y * dy + CAM_RIGHT.z * dz);
            uz = CAM_RIGHT.z - dz * (CAM_RIGHT.x * dx + CAM_RIGHT.y * dy + CAM_RIGHT.z * dz);
            ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-6f;
        }
        ux /= ul; uy /= ul; uz /= ul;

        ux *= wa; uy *= wa; uz *= wa;
        float s = wb / Math.max(wa, 1e-5f);
        float vx = ux * s, vy = uy * s, vz = uz * s; // width at B

        buf.addVertex(vm, ax - ux, ay - uy, az - uz).setUv(0f, 0f).setColor(rgba);
        buf.addVertex(vm, ax + ux, ay + uy, az + uz).setUv(0f, 1f).setColor(rgba);
        buf.addVertex(vm, bx + vx, by + vy, bz + vz).setUv(1f, 1f).setColor(rgba);
        buf.addVertex(vm, bx - vx, by - vy, bz - vz).setUv(1f, 0f).setColor(rgba);
    }

    /** Camera-facing billboard with in-plane rotation (cos/sin basis mix). */
    private static void emitTiltedBillboard(BufferBuilder buf, Matrix4f vm,
                                            float px, float py, float pz,
                                            float halfW, float halfH,
                                            float rotCos, float rotSin, int rgba) {
        // Basis: right' = right·cos + up·sin, up' = up·cos − right·sin
        float rx = CAM_RIGHT.x * rotCos - CAM_UP.x * rotSin;
        float ry = CAM_RIGHT.y * rotCos - CAM_UP.y * rotSin;
        float rz = CAM_RIGHT.z * rotCos - CAM_UP.z * rotSin;
        float ux2 = CAM_UP.x * rotCos + CAM_RIGHT.x * rotSin;
        float uy2 = CAM_UP.y * rotCos + CAM_RIGHT.y * rotSin;
        float uz2 = CAM_UP.z * rotCos + CAM_RIGHT.z * rotSin;

        float wpx = rx * halfW, wpy = ry * halfW, wpz = rz * halfW;
        float hpx = ux2 * halfH, hpy = uy2 * halfH, hpz = uz2 * halfH;

        buf.addVertex(vm, px - wpx - hpx, py - wpy - hpy, pz - wpz - hpz).setUv(0f, 0f).setColor(rgba);
        buf.addVertex(vm, px - wpx + hpx, py - wpy + hpy, pz - wpz + hpz).setUv(0f, 1f).setColor(rgba);
        buf.addVertex(vm, px + wpx + hpx, py + wpy + hpy, pz + wpz + hpz).setUv(1f, 1f).setColor(rgba);
        buf.addVertex(vm, px + wpx - hpx, py + wpy - hpy, pz + wpz - hpz).setUv(1f, 0f).setColor(rgba);
    }

    // ════════════════════════════════════════════════════════════════
    //  Main entry
    // ════════════════════════════════════════════════════════════════

    /**
     * Render one Hell's Hand effect instance (rift + hands + victim + embers).
     *
     * @param intensity global intensity multiplier (already merge-scaled)
     */
    public static void draw(PoseStack poseStack, HellHandEffectInstance inst,
                            long nowMs, float intensity) {
        double e = Math.max(0.0, inst.elapsedSec(nowMs));
        if (e >= 5.4) return;

        int stage = inst.currentStage(nowMs);
        if (stage < 0) return;

        float masterFade = inst.masterFade(nowMs);
        float boil = (float) (e % 3.0) / 3.0f;

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        float px = (float) inst.position.x;
        float pz = (float) inst.position.z;
        float groundY = (float) inst.position.y + 0.06f;

        // Slight distance growth keeps the rift readable far away
        float ddx = px - cx, ddz = pz - cz, ddy = groundY - cy;
        float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) + 0.1f;
        float merge = 1f + (inst.mergeCount - 1) * 0.18f;
        float riftHalf = (1.85f + dist * 0.045f) * merge;

        drawRift(vm, cx, cy, cz, px, groundY, pz, riftHalf, inst, nowMs, e, boil, masterFade, intensity);
        drawHands(vm, cx, cy, cz, px, groundY, pz, inst, e, boil, masterFade, intensity,
                riftHalf * 0.64f * Math.min(inst.riftOpenAmt(nowMs), 1.1f));
        drawVictim(vm, cx, cy, cz, px, groundY, pz, inst, nowMs, e, boil, masterFade, intensity);
        drawEmbers(vm, cx, cy, cz, inst, nowMs, riftHalf, masterFade, intensity);
    }

    // ── Rift ───────────────────────────────────────────────────────

    private static void drawRift(Matrix4f vm, float cx, float cy, float cz,
                                 float px, float groundY, float pz,
                                 float riftHalf, HellHandEffectInstance inst,
                                 long nowMs, double e, float boil, float masterFade, float intensity) {
        float open  = inst.riftOpenAmt(nowMs);
        float close = inst.riftCloseAmt(nowMs);
        float alpha = masterFade * Math.min(intensity, 1.4f);

        // Gentle open-out at the very start, plus scale-in
        float size = riftHalf * (0.86f + 0.14f * smoothstep01((float) (e / 0.3f)));

        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int rgba = packColor(open, close, boil, alpha);
        float rx = px - cx, ry = groundY + 0.02f - cy, rz = pz - cz;
        buf.addVertex(vm, rx - size, ry, rz - size).setUv(0f, 0f).setColor(rgba);
        buf.addVertex(vm, rx - size, ry, rz + size).setUv(0f, 1f).setColor(rgba);
        buf.addVertex(vm, rx + size, ry, rz + size).setUv(1f, 1f).setColor(rgba);
        buf.addVertex(vm, rx + size, ry, rz - size).setUv(1f, 0f).setColor(rgba);

        GeminiTesselator.draw(RIFT_TYPE, buf.buildOrThrow());
    }

    // ── Hands ──────────────────────────────────────────────────────

    /** Finger lengths relative to the middle finger: index, middle, ring, pinky. */
    private static final float[] FINGER_LEN = {0.88f, 1.0f, 0.93f, 0.71f};
    /** Knuckle positions across the palm, in fractions of the palm half-width. */
    private static final float[] FINGER_SPREAD = {-0.70f, -0.24f, 0.24f, 0.70f};
    /** How far each finger leans outward while the hand is still open. */
    private static final float[] FINGER_FAN = {-0.17f, -0.05f, 0.06f, 0.19f};
    /** Each finger clenches on its own beat so the grip rolls in. */
    private static final float[] FINGER_PHASE = {0.90f, 1.0f, 1.07f, 0.83f};
    /** Phalanx lengths as fractions of the whole finger. */
    private static final float[] PHALANG_LEN = {0.43f, 0.32f, 0.25f};
    /** Phalanx widths, tapering to the nail (4th entry caps the distal tip). */
    private static final float[] PHALANG_W = {1.0f, 0.85f, 0.70f, 0.52f};
    /** Bend limit per joint in radians at full clench: MCP, PIP, DIP. */
    private static final float[] JOINT_BEND = {1.15f, 1.05f, 0.62f};
    /** Distal joints lag behind the proximal ones during the clench. */
    private static final float[] JOINT_FROM = {0.00f, 0.16f, 0.38f};
    private static final float[] JOINT_TO = {0.80f, 1.0f, 1.0f};

    /** Bend added at joint {@code j} for a given clench amount. */
    private static float jointBend(float curl, int j, float phase) {
        float x = Math.clamp((curl - JOINT_FROM[j]) / (JOINT_TO[j] - JOINT_FROM[j]), 0f, 1f);
        return JOINT_BEND[j] * smoothstep01(x) * phase;
    }

    private static void drawHands(Matrix4f vm, float cx, float cy, float cz,
                                  float px, float groundY, float pz,
                                  HellHandEffectInstance inst,
                                  double e, float boil, float masterFade,
                                  float intensity, float riftLip) {
        final int n = inst.handCount;
        float vHeight = inst.victimHeight(inst.startTimeMs + (long) (e * 1000));
        // Victim centre (camera-relative) — the palms face it
        float vcx = px - cx, vcy = groundY + vHeight - cy, vcz = pz - cz;

        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float rimRadius = riftLip * 0.72f; // hand bases near the lip

        for (int i = 0; i < n; i++) {
            float az  = inst.handAzimuth[i];
            float dly = inst.handDelay[i];
            float hs  = inst.handScale[i];

            float rise = smoothstep01((float) ((e - (0.30 + dly)) / 0.50));
            if (rise <= 0f) continue;
            float ext  = smoothstep01((float) ((e - (0.75 + dly * 0.8)) / 0.65));
            float curl = smoothstep01((float) ((e - (1.45 + dly * 0.6)) / 0.40));
            // Squeeze pulse while dragging the prey down
            if (e > 2.1 && e < 3.6) curl = Math.min(1f, curl + 0.06f * (float) Math.sin(e * 9.0 + i));
            float sinkPhase = smoothstep01((float) ((e - (3.30 + (n - i) * 0.035)) / 0.55));

            float alpha = rise * (1f - sinkPhase) * masterFade * Math.min(intensity, 1.5f) * 0.95f;
            if (alpha < 0.004f) continue;

            float outX = (float) Math.cos(az), outZ = (float) Math.sin(az);
            // The whole limb slides back into the mouth while the prey sinks
            float drop = sinkPhase * 1.50f * hs;

            // Base sits on the rift lip (camera-relative)
            float bx = px + outX * rimRadius - cx;
            float by = groundY - cy - drop;
            float bz = pz + outZ * rimRadius - cz;

            // Grip point: on the victim's flank closest to this hand
            float gx = vcx + outX * 0.42f;
            float gy = vcy + inst.handGripY[i] - drop;
            float gz = vcz + outZ * 0.42f;

            drawOneHand(buf, vm, bx, by, bz, gx, gy, gz, vcx, vcy, vcz,
                    -outX, -outZ, hs, inst.handSeed[i], rise, ext, curl, boil, alpha,
                    (i & 1) == 0 ? 1f : -1f);
        }

        // Nullable build(): all hands can be mid-alpha-ramp (rise guard +
        // early-out in drawOneHand), so the buffer may legitimately be empty.
        MeshData handMesh = buf.build();
        if (handMesh != null) GeminiTesselator.draw(HAND_TYPE, handMesh);
    }

    /**
     * Emit one hand as an articulated chain: forearm → wrist → palm → knuckle
     * line → three phalanges per finger, plus an opposing thumb.
     *
     * <p>Every point is camera-relative. The palm normal points at the victim,
     * so the fingers curl around the prey instead of collapsing onto it, and
     * the clench rolls joint by joint (MCP first, DIP last).</p>
     *
     * @param parity   1 / −1 mirrors the hand between left and right anatomy
     * @param rise     emergence from the lip (0 stump → 1 full arm height)
     * @param ext      reach toward the grip point
     * @param curl     clench (0 open → 1 gripping)
     */
    private static void drawOneHand(BufferBuilder buf, Matrix4f vm,
                                    float bx, float by, float bz,
                                    float gx, float gy, float gz,
                                    float vcx, float vcy, float vcz,
                                    float ix, float iz, float hs, float seed,
                                    float rise, float ext, float curl, float boil,
                                    float alpha, float parity) {
        // Stowed pose: curled stump just above the lip; reaching drags the
        // chain out toward the grip point along the same frame.
        float sx = bx + ix * 0.12f * hs;
        float sy = by + 0.30f * hs * rise;
        float sz = bz + iz * 0.12f * hs;
        float tx = sx + (gx - sx) * ext;
        float ty = sy + (gy - sy) * ext;
        float tz = sz + (gz - sz) * ext;

        // Segment lengths are derived from the live reach, so the fingertips
        // land on the prey exactly at full extension.
        float ax = tx - bx, ay = ty - by, az = tz - bz;
        float reach = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (reach < 0.14f) return;
        float axx = ax / reach, ayy = ay / reach, azz = az / reach;

        float armLen  = reach * 0.56f;
        float palmLen = reach * 0.20f;
        float finLen  = reach * 0.33f;

        // The elbow bows away from the mouth, so the forearm arcs
        float ex = bx + axx * armLen * 0.5f - ix * 0.07f * hs;
        float ey = by + ayy * armLen * 0.5f + 0.02f * hs;
        float ez = bz + azz * armLen * 0.5f - iz * 0.07f * hs;
        float wx = bx + axx * armLen;
        float wy = by + ayy * armLen;
        float wz = bz + azz * armLen;

        // Wrist → knuckles, cocked toward the prey as the hand commits
        float hx = axx + ix * 0.40f * ext;
        float hy = ayy;
        float hz = azz + iz * 0.40f * ext;
        float hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz) + 1e-6f;
        hx /= hl; hy /= hl; hz /= hl;

        float pcx = wx + hx * palmLen * 0.5f;
        float pcy = wy + hy * palmLen * 0.5f;
        float pcz = wz + hz * palmLen * 0.5f;
        float kx = wx + hx * palmLen;
        float ky = wy + hy * palmLen;
        float kz = wz + hz * palmLen;

        // Palm normal: perpendicular to the hand axis, facing the victim — the
        // plane every finger curls through.
        float qx = vcx - kx, qy = vcy - ky, qz = vcz - kz;
        float qd = qx * hx + qy * hy + qz * hz;
        float nx = qx - hx * qd, ny = qy - hy * qd, nz = qz - hz * qd;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-3f) {
            // Knuckle sits on the victim's axis: cross against h's smallest
            // world axis, which is the one direction that can never degenerate.
            float ax0 = Math.abs(hx), ay0 = Math.abs(hy), az0 = Math.abs(hz);
            if (ax0 <= ay0 && ax0 <= az0)      { nx = 0f;  ny = hz;  nz = -hy; }
            else if (ay0 <= az0)               { nx = -hz; ny = 0f;  nz = hx; }
            else                               { nx = hy;  ny = -hx; nz = 0f; }
            nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
        }
        nx /= nl; ny /= nl; nz /= nl;

        // Spread axis: anatomical lateral (perpendicular to axis + palm normal)
        // blended with the screen-lateral axis, so the fan foreshortens in 3D
        // but never collapses into a single silhouette edge-on.
        float lx = ny * hz - nz * hy, ly = nz * hx - nx * hz, lz = nx * hy - ny * hx;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz) + 1e-6f;
        lx /= ll; ly /= ll; lz /= ll;
        float rd = CAM_RIGHT.x * hx + CAM_RIGHT.y * hy + CAM_RIGHT.z * hz;
        float rx = CAM_RIGHT.x - hx * rd, ry = CAM_RIGHT.y - hy * rd, rz = CAM_RIGHT.z - hz * rd;
        float rd2 = rx * nx + ry * ny + rz * nz;
        rx -= nx * rd2; ry -= ny * rd2; rz -= nz * rd2;
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl >= 0.05f) { rx /= rl; ry /= rl; rz /= rl; } else { rx = ry = rz = 0f; }
        float spx = lx * 0.80f * parity + rx * 0.45f;
        float spy = ly * 0.80f * parity + ry * 0.45f;
        float spz = lz * 0.80f * parity + rz * 0.45f;
        float spl = (float) Math.sqrt(spx * spx + spy * spy + spz * spz) + 1e-6f;
        spx /= spl; spy /= spl; spz /= spl;

        int rgbaArm = packColor(curl, 0.12f + seed * 0.8f, boil, alpha);
        // Forearm → wrist pinch → palm flare → knuckle ridge
        emitSeg(buf, vm, bx, by, bz, ex, ey, ez, 0.118f * hs, 0.104f * hs, rgbaArm);
        emitSeg(buf, vm, ex, ey, ez, wx, wy, wz, 0.104f * hs, 0.079f * hs, rgbaArm);
        emitSeg(buf, vm, wx, wy, wz, pcx, pcy, pcz, 0.083f * hs, 0.152f * hs, rgbaArm);
        emitSeg(buf, vm, pcx, pcy, pcz, kx, ky, kz, 0.152f * hs, 0.143f * hs, rgbaArm);

        float palmHalf = 0.155f * hs;

        for (int f = 0; f < 4; f++) {
            float spread = FINGER_SPREAD[f];
            float fseed = (seed + f * 0.23f) % 1f;
            int rgba = packColor(curl, 0.30f + fseed * 0.65f, boil, alpha);

            // The knuckle line arcs: the middle knuckles sit further down it
            float arc = (1f - spread * spread) * 0.042f * hs;
            float jx = kx + spx * spread * palmHalf + hx * arc;
            float jy = ky + spy * spread * palmHalf + hy * arc;
            float jz = kz + spz * spread * palmHalf + hz * arc;

            float len = finLen * FINGER_LEN[f];
            float w0 = 0.054f * hs * (float) Math.sqrt(FINGER_LEN[f]);
            // The fan closes as the hand clenches
            float fan = FINGER_FAN[f] * (1f - 0.55f * curl);
            float stx = hx + spx * fan, sty = hy + spy * fan, stz = hz + spz * fan;
            float stl = (float) Math.sqrt(stx * stx + sty * sty + stz * stz) + 1e-6f;
            stx /= stl; sty /= stl; stz /= stl;

            float cum = 0f;
            float phase = FINGER_PHASE[f];
            for (int j = 0; j < 3; j++) {
                cum += jointBend(curl, j, phase);
                float ca = (float) Math.cos(cum), sa = (float) Math.sin(cum);
                float dx = stx * ca + nx * sa;
                float dy = sty * ca + ny * sa;
                float dz = stz * ca + nz * sa;
                float nx2 = jx + dx * len * PHALANG_LEN[j];
                float ny2 = jy + dy * len * PHALANG_LEN[j];
                float nz2 = jz + dz * len * PHALANG_LEN[j];
                emitSeg(buf, vm, jx, jy, jz, nx2, ny2, nz2,
                        w0 * PHALANG_W[j], w0 * PHALANG_W[j + 1], rgba);
                jx = nx2; jy = ny2; jz = nz2;
            }
        }

        // Thumb: leaves the palm low on the spread side, swings across to
        // oppose the fingers as it adducts into the grip.
        {
            float tseed = (seed + 0.61f) % 1f;
            int rgba = packColor(curl, 0.30f + tseed * 0.65f, boil, alpha);
            float jx = kx - hx * palmLen * 0.55f + spx * palmHalf * 0.95f;
            float jy = ky - hy * palmLen * 0.55f + spy * palmHalf * 0.95f;
            float jz = kz - hz * palmLen * 0.55f + spz * palmHalf * 0.95f;

            float len = finLen * 0.80f;
            float fan = 0.92f - 0.30f * curl;
            float stx = hx * 0.55f + spx * fan, sty = hy * 0.55f, stz = hz * 0.55f + spz * fan;
            float stl = (float) Math.sqrt(stx * stx + sty * sty + stz * stz) + 1e-6f;
            stx /= stl; sty /= stl; stz /= stl;

            float cum = 0f;
            for (int j = 0; j < 2; j++) {
                cum += (j == 0 ? 0.95f : 1.05f) * smoothstep01(curl * (j == 0 ? 1.15f : 0.95f));
                float ca = (float) Math.cos(cum), sa = (float) Math.sin(cum);
                float nx2 = jx + (stx * ca + nx * sa) * len * (j == 0 ? 0.56f : 0.44f);
                float ny2 = jy + (sty * ca + ny * sa) * len * (j == 0 ? 0.56f : 0.44f);
                float nz2 = jz + (stz * ca + nz * sa) * len * (j == 0 ? 0.56f : 0.44f);
                emitSeg(buf, vm, jx, jy, jz, nx2, ny2, nz2,
                        0.062f * hs * (j == 0 ? 1f : 0.80f),
                        0.062f * hs * (j == 0 ? 0.80f : 0.58f), rgba);
                jx = nx2; jy = ny2; jz = nz2;
            }
        }
    }

    // ── Victim ─────────────────────────────────────────────────────

    private static void drawVictim(Matrix4f vm, float cx, float cy, float cz,
                                   float px, float groundY, float pz,
                                   HellHandEffectInstance inst, long nowMs,
                                   double e, float boil, float masterFade, float intensity) {
        float va = inst.victimAlpha(nowMs);
        if (va < 0.01f) return;

        float vHeight = inst.victimHeight(nowMs);
        // Body center (SDF spans ~2.2 blocks)
        float bx = px - cx, by = groundY + vHeight + 0.55f - cy, bz = pz - cz;

        float sink = smoothstep01((float) ((e - 3.30) / 0.65));
        float struggle = (e > 1.6f && e < 3.6f) ? 1f : 0.35f;
        // Convulsion sway around the view axis
        float rotA = (float) Math.sin(e * 6.3) * 0.13f * struggle;

        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int rgba = packColor(sink, struggle, boil, va * masterFade * Math.min(intensity, 1.3f));
        emitTiltedBillboard(buf, vm, bx, by, bz, 0.68f, 0.92f,
                (float) Math.cos(rotA), (float) Math.sin(rotA), rgba);

        GeminiTesselator.draw(VICTIM_TYPE, buf.buildOrThrow());
    }

    // ── Embers ─────────────────────────────────────────────────────

    private static final float[] EMBER_BATCH = new float[420 * 8];

    private static void drawEmbers(Matrix4f vm, float cx, float cy, float cz,
                                   HellHandEffectInstance inst, long nowMs,
                                   float riftHalf,
                                   float masterFade, float intensity) {
        inst.updateEmbers(nowMs, riftHalf * 0.64f);
        int count = inst.fillEmberBatch(EMBER_BATCH, EMBER_BATCH.length / 8,
                nowMs, masterFade * Math.min(intensity, 1.5f));
        if (count == 0) return;

        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < count; i++) {
            int off = i * 8;
            // Batch layout: [x, y, z, halfSize, lifeRatio, -, -, alpha]
            int rgba = packColor(EMBER_BATCH[off + 4], 0f, 0f, EMBER_BATCH[off + 7]);
            emitBillboard(buf, vm, cx, cy, cz,
                    EMBER_BATCH[off], EMBER_BATCH[off + 1], EMBER_BATCH[off + 2],
                    EMBER_BATCH[off + 3], rgba);
        }
        GeminiTesselator.draw(EMBER_TYPE, buf.buildOrThrow());
    }
}
