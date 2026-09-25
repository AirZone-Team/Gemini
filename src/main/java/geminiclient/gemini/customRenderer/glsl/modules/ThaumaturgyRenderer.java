package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
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
 * GPU-accelerated renderer for the <b>Thaumaturgy Strike</b> kill-effect mode
 * (奇术打击).
 *
 * <h3>Pipelines</h3>
 * <ul>
 *   <li>{@link #SIGIL_PIPE} — the giant sky sigil, a stack of world-space planes</li>
 *   <li>{@link #PILLAR_PIPE} — light columns, three crossed vertical quads each</li>
 *   <li>{@link #ORB_PIPE} — the nova sphere, a ray-marched 3D ball</li>
 *   <li>{@link #SPARK_PIPE} — motes, impact debris and ground shock rings</li>
 * </ul>
 *
 * <h3>Vertex encodings (POSITION_TEX_COLOR)</h3>
 * <ul>
 *   <li>SIGIL:  r = reveal, g = animation phase (0→1 loop), b = energy/4,
 *       a = alpha; each plane's rotation is in its geometry, not a channel</li>
 *   <li>PILLAR: r = descent, g = fract(flow), b = energy/4, a = alpha;
 *       UV.x = pillar index + across, UV.y = along (0 sigil → 1 ground)</li>
 *   <li>ORB:    r = progress, g = heat, b = intensity/4, a = alpha; UV.x = radius</li>
 *   <li>SPARK:  r = lifeRatio, g = kind × 0.25 (1 = ground ring), b = 0, a = alpha</li>
 * </ul>
 *
 * <p>All motion curves come from {@link ThaumaturgyEffectInstance}; this class
 * only turns them into geometry each frame.</p>
 */
public final class ThaumaturgyRenderer {

    private ThaumaturgyRenderer() {}

    private static final float TAU = (float) (2.0 * Math.PI);

    /** Period of the sigil's looping internal animation (boil + radial pulse). */
    private static final long SIGIL_CYCLE_MS = 4000L;

    /** Vertical crossed faces per light column; see {@link #emitColumn}. */
    private static final int COLUMN_FACES = 3;

    /**
     * Columns overlap additively, so each of the {@link #COLUMN_FACES} faces
     * carries a share of the beam's HDR energy rather than its full strength.
     */
    private static final float COLUMN_FACE_ENERGY = 0.5f;

    // ════════════════════════════════════════════════════════════════
    //  Pipelines
    // ════════════════════════════════════════════════════════════════

    private static final DepthStencilState EFFECT_DEPTH =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 1.0F);

    private static final ColorTargetState ADDITIVE_BLEND = new ColorTargetState(new BlendFunction(
            BlendFactor.SRC_ALPHA, BlendFactor.ONE,
            BlendFactor.ONE, BlendFactor.ZERO));

    /** Giant violet sigil in the sky. */
    public static final RenderPipeline SIGIL_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_thaum_sigil"))
            .withVertexShader(getIdentifier("core/kill_effect_thaum_circle"))
            .withFragmentShader(getIdentifier("core/kill_effect_thaum_circle"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Falling light columns. */
    public static final RenderPipeline PILLAR_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_thaum_pillar"))
            .withVertexShader(getIdentifier("core/kill_effect_thaum_pillar"))
            .withFragmentShader(getIdentifier("core/kill_effect_thaum_pillar"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Volumetric nova sphere. */
    public static final RenderPipeline ORB_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_thaum_orb"))
            .withVertexShader(getIdentifier("core/kill_effect_thaum_orb"))
            .withFragmentShader(getIdentifier("core/kill_effect_thaum_orb"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Sparks, debris and ground rings. */
    public static final RenderPipeline SPARK_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_thaum_spark"))
            .withVertexShader(getIdentifier("core/kill_effect_thaum_spark"))
            .withFragmentShader(getIdentifier("core/kill_effect_thaum_spark"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Discharge arcs crawling down the light columns. */
    public static final RenderPipeline BOLT_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_thaum_bolt"))
            .withVertexShader(getIdentifier("core/kill_effect_thaum_bolt"))
            .withFragmentShader(getIdentifier("core/kill_effect_thaum_bolt"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    private static final RenderType SIGIL_TYPE   = createRenderType("gemini_kill_thaum_sigil", SIGIL_PIPE);
    private static final RenderType PILLAR_TYPE  = createRenderType("gemini_kill_thaum_pillar", PILLAR_PIPE);
    private static final RenderType ORB_TYPE     = createRenderType("gemini_kill_thaum_orb", ORB_PIPE);
    private static final RenderType SPARK_TYPE   = createRenderType("gemini_kill_thaum_spark", SPARK_PIPE);
    private static final RenderType BOLT_TYPE    = createRenderType("gemini_kill_thaum_bolt", BOLT_PIPE);

    private static RenderType createRenderType(String name, RenderPipeline pipe) {
        return RenderType.create(name,
                RenderSetup.builder(pipe).sortOnUpload()
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .createRenderSetup());
    }

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(SIGIL_PIPE);
        registry.accept(PILLAR_PIPE);
        registry.accept(ORB_PIPE);
        registry.accept(SPARK_PIPE);
        registry.accept(BOLT_PIPE);
    }

    // ════════════════════════════════════════════════════════════════
    //  Camera + packing helpers
    // ════════════════════════════════════════════════════════════════

    private static final Vector3f CAM_UP    = new Vector3f();
    private static final Vector3f CAM_RIGHT = new Vector3f();
    /** Scratch for {@link ThaumaturgySigilGeometry#axes}. */
    private static final float[] SIGIL_AXES = new float[6];
    /** Rotated billboard axes for the sigil — see {@link #drawSigil}. */
    private static final Vector3f SIGIL_E1  = new Vector3f();
    private static final Vector3f SIGIL_E2  = new Vector3f();

    private static void updateCameraVectors() {
        Camera cam = mc.getEntityRenderDispatcher().camera;
        Quaternionf rot = cam.rotation();
        CAM_UP.set(0, 1, 0);
        rot.transform(CAM_UP);
        CAM_RIGHT.set(1, 0, 0);
        rot.transform(CAM_RIGHT);
    }

    /** Channels are bytes, so anything above 1 has to be pre-divided. */
    private static int packColor(float r, float g, float b, float a) {
        int ir = (int) (Math.clamp(r, 0f, 1f) * 255f);
        int ig = (int) (Math.clamp(g, 0f, 1f) * 255f);
        int ib = (int) (Math.clamp(b, 0f, 1f) * 255f);
        int ia = (int) (Math.clamp(a, 0f, 1f) * 255f);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static BufferBuilder begin() {
        return GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    /** Square camera-facing billboard at camera-relative position. */
    private static void emitBillboard(BufferBuilder buf, Matrix4f vm,
                                      float px, float py, float pz,
                                      float halfSize, int rgba) {
        emitQuad(buf, vm, px, py, pz, CAM_RIGHT, CAM_UP, halfSize, rgba);
    }

    /**
     * Unit quad centred on {@code px,py,pz}, spanning {@code ±half} along two
     * <b>orthonormal</b> axes. The camera-facing overload above is the special
     * case; the sigil hands in world-space axes instead, which is the only way
     * it can turn smoothly — a vertex-colour channel is a byte, so a rotation
     * sent through the shader would advance in 1.4° jumps.
     */
    private static void emitQuad(BufferBuilder buf, Matrix4f vm,
                                 float px, float py, float pz,
                                 Vector3f e1, Vector3f e2,
                                 float half, int rgba) {
        float ax = e1.x * half, ay = e1.y * half, az = e1.z * half;
        float bx = e2.x * half, by = e2.y * half, bz = e2.z * half;
        buf.addVertex(vm, px - ax - bx, py - ay - by, pz - az - bz).setUv(0f, 0f).setColor(rgba);
        buf.addVertex(vm, px - ax + bx, py - ay + by, pz - az + bz).setUv(0f, 1f).setColor(rgba);
        buf.addVertex(vm, px + ax + bx, py + ay + by, pz + az + bz).setUv(1f, 1f).setColor(rgba);
        buf.addVertex(vm, px + ax - bx, py + ay - by, pz + az - bz).setUv(1f, 0f).setColor(rgba);
    }

    /** Flat quad on the ground plane at camera-relative position. */
    private static void emitGroundQuad(BufferBuilder buf, Matrix4f vm,
                                       float px, float py, float pz,
                                       float halfSize, int rgba) {
        buf.addVertex(vm, px - halfSize, py, pz - halfSize).setUv(0f, 0f).setColor(rgba);
        buf.addVertex(vm, px - halfSize, py, pz + halfSize).setUv(0f, 1f).setColor(rgba);
        buf.addVertex(vm, px + halfSize, py, pz + halfSize).setUv(1f, 1f).setColor(rgba);
        buf.addVertex(vm, px + halfSize, py, pz - halfSize).setUv(1f, 0f).setColor(rgba);
    }

    /**
     * Emit a light column as {@link #COLUMN_FACES} crossed quads around the
     * vertical axis at {@code bx,bz}.
     *
     * <p>Fixed world-facing planes rather than one quad that always turns its
     * full width to the camera: the beam then has parallax — its silhouette
     * shifts and its internal streaks slide as you move — while at least one
     * face stays within 30° of facing you, so the beam never thins below 87%
     * of its width. Additive blending hides the seams where faces overlap.</p>
     *
     * @param yTop     world Y where the beam leaves the sigil
     * @param yGround  world Y the beam is driven into (UV.y = 1 there)
     * @param yTip     current leading edge of the fall; the quad stops here
     * @param pillarIdx carried in UV.x so the shader can vary each column
     */
    private static void emitColumn(BufferBuilder buf, Matrix4f vm,
                                   float cx, float cy, float cz,
                                   float bx, float yGround, float bz,
                                   float yTop, float yTip, float halfWidth,
                                   float pillarIdx, int rgba) {
        float ox = bx - cx, oz = bz - cz;
        float topY = yTop - cy;
        float tipY = yTip - cy;

        float span = Math.max(yTop - yGround, 0.001f);
        // UV.y: 0 at the sigil end, 1 at the ground end. The quad is cut at
        // the descending tip, so its bottom edge carries v = descent.
        float vTip = Math.clamp((yTop - yTip) / span, 0f, 1f);

        float u0 = pillarIdx, u1 = pillarIdx + 1f;
        // Horizontal direction from the column to the eye.
        float vx = cx - bx, vz = cz - bz;
        float vl = (float) Math.sqrt(vx * vx + vz * vz);
        if (vl > 1e-3f) {
            vx /= vl;
            vz /= vl;
        }
        int alphaByte = (rgba >>> 24) & 0xFF;

        // Offset per column so no two beams share the same preferred axis.
        float base = pillarIdx * 0.6f;
        for (int f = 0; f < COLUMN_FACES; f++) {
            float theta = base + f * ((float) Math.PI / COLUMN_FACES);
            float wx = (float) Math.cos(theta);
            float wz = (float) Math.sin(theta);
            // Fade a face as it swings edge-on: its projected width is already
            // collapsing, and leaving it at full brightness is what scalloped
            // the column's outline. Summed over the three faces this stays
            // within ±5% of a constant, so the beam does not pulse as you orbit.
            float facing = Math.abs(-wz * vx + wx * vz);
            int faceRgba = (rgba & 0x00FFFFFF)
                    | ((int) (alphaByte * (0.35f + 0.65f * facing)) << 24);

            float wpx = wx * halfWidth;
            float wpz = wz * halfWidth;
            buf.addVertex(vm, ox - wpx, topY, oz - wpz).setUv(u0, 0f).setColor(faceRgba);
            buf.addVertex(vm, ox + wpx, topY, oz + wpz).setUv(u1, 0f).setColor(faceRgba);
            buf.addVertex(vm, ox + wpx, tipY, oz + wpz).setUv(u1, vTip).setColor(faceRgba);
            buf.addVertex(vm, ox - wpx, tipY, oz - wpz).setUv(u0, vTip).setColor(faceRgba);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Main entry
    // ════════════════════════════════════════════════════════════════

    /**
     * Render one Thaumaturgy Strike instance.
     *
     * @param intensity global intensity × AoE merge multiplier
     */
    public static void draw(PoseStack poseStack, ThaumaturgyEffectInstance inst,
                            long nowMs, float intensity) {
        if (inst.currentStage(nowMs) < 0) return;

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        float px = (float) inst.position.x;
        float py = (float) inst.position.y;
        float pz = (float) inst.position.z;

        float energy = Math.clamp(intensity, 0f, 2.0f);
        float flow = (nowMs % 1000L) / 1000f;

        drawSigil(vm, cx, cy, cz, px, py, pz, inst, nowMs, energy);
        drawPillars(vm, cx, cy, cz, px, py, pz, inst, nowMs, energy, flow);
        drawLightning(vm, cx, cy, cz, px, py, pz, inst, nowMs, energy, flow);
        drawNovaSphere(poseStack, cx, cy, cz, px, py, pz, inst, nowMs, energy);
        drawSparks(vm, cx, cy, cz, px, py, pz, inst, nowMs, energy);
    }

    // ── Sigil ──────────────────────────────────────────────────────

    /**
     * The sigil is a stack of planes hanging in the sky, not a decal on the
     * screen: one primary disc, an echo just beneath it that gives the pair
     * thickness, and two inner rings nodding on tilted planes that precess
     * against the main turn. Every plane is oriented in world space, so the
     * camera's own height and angle change what the spell looks like.
     */
    private static void drawSigil(Matrix4f vm,
                                  float cx, float cy, float cz,
                                  float px, float py, float pz,
                                  ThaumaturgyEffectInstance inst, long nowMs, float energy) {
        float alpha = inst.sigilAlpha(nowMs);
        if (alpha < 0.004f) return;

        float x = px - cx;
        float y = py + inst.sigilAltitude(nowMs) - cy;
        float z = pz - cz;

        float reveal = inst.sigilReveal(nowMs);
        float surge = inst.sigilSurge(nowMs);
        float spin = inst.sigilSpin(nowMs);
        float phase = (nowMs % SIGIL_CYCLE_MS) / (float) SIGIL_CYCLE_MS;

        float half = 24f + 4f * surge;
        float bright = (1.15f + 0.85f * surge) * energy;
        float lean = inst.sigilLean(nowMs);
        // Bounded wobble only: `phase` rides a byte channel, so the inner rings
        // may sway on a sin of it but never turn monotonically through it.
        float nod = 0.10f * (float) Math.sin(phase * TAU);

        BufferBuilder buf = begin();
        // Only the primary plane is drawn out in full. The rest stop part way up
        // the summon script, which is what keeps four overlapping copies of the
        // triquetra from turning the sky into a tangle of near-identical circles.
        emitDisc(buf, vm, x, y, z, spin, lean, half,
                reveal, phase, bright, alpha);
        // Echo: half a rune-cell around, so its rim lines interleave with the
        // primary disc's instead of hiding underneath them.
        emitDisc(buf, vm, x, y - 2.4f, z, spin + TAU / 88f, lean, half * 0.90f,
                planeReveal(reveal, 0.08f, ThaumaturgyEffectInstance.SCRIPT_RIM_END),
                phase, bright * 0.55f, alpha * 0.30f);
        emitDisc(buf, vm, x, y, z, -spin * 0.62f, 0.42f + nod, half * 0.62f,
                planeReveal(reveal, 0.20f, ThaumaturgyEffectInstance.SCRIPT_RUNE_END),
                phase, bright * 0.70f, alpha * 0.45f);
        emitDisc(buf, vm, x, y, z, spin * 1.4f + 0.9f, -0.36f - nod, half * 0.40f,
                planeReveal(reveal, 0.34f, ThaumaturgyEffectInstance.SCRIPT_RUNE_END),
                phase, bright * 0.75f, alpha * 0.38f);
        GeminiTesselator.draw(SIGIL_TYPE, buf.buildOrThrow());
    }

    /**
     * A plane's own progress through the summon script: it starts drawing at
     * {@code delay} and never passes {@code ceiling}, so a plane capped at a
     * script boundary shows exactly the figures that live below it.
     */
    private static float planeReveal(float reveal, float delay, float ceiling) {
        return Math.clamp((reveal - delay) / (1f - delay), 0f, 1f) * ceiling;
    }

    /** One sigil plane; skipped when its share of the alpha has faded out. */
    private static void emitDisc(BufferBuilder buf, Matrix4f vm,
                                 float x, float y, float z,
                                 float yaw, float pitch, float half,
                                 float reveal, float phase, float bright, float alpha) {
        if (alpha < 0.004f) return;
        ThaumaturgySigilGeometry.axes(yaw, pitch, SIGIL_AXES);
        SIGIL_E1.set(SIGIL_AXES[0], SIGIL_AXES[1], SIGIL_AXES[2]);
        SIGIL_E2.set(SIGIL_AXES[3], SIGIL_AXES[4], SIGIL_AXES[5]);
        emitQuad(buf, vm, x, y, z, SIGIL_E1, SIGIL_E2, half,
                packColor(reveal, phase, bright / 4f, alpha));
    }

    // ── Light columns ──────────────────────────────────────────────

    private static void drawPillars(Matrix4f vm, float cx, float cy, float cz,
                                    float px, float py, float pz,
                                    ThaumaturgyEffectInstance inst, long nowMs,
                                    float energy, float flow) {
        float pillarAlpha = inst.pillarAlpha(nowMs);
        float grandAlpha = inst.grandAlpha(nowMs);
        if (pillarAlpha < 0.004f && grandAlpha < 0.004f) return;

        float yGround = py + 0.05f;
        // The columns now start inside the sigil plane itself, so they read as
        // beams pushed out of the disc rather than pasted in front of it.
        float yTop = py + inst.sigilAltitude(nowMs) + 1.5f;
        float inward = inst.pillarInward(nowMs);
        float merge = 1f + (inst.mergeCount - 1) * 0.14f;

        BufferBuilder buf = begin();
        int emitted = 0;

        if (pillarAlpha >= 0.004f) {
            for (int i = 0; i < inst.pillarCount; i++) {
                float descent = inst.pillarDescent(i, nowMs);
                if (descent <= 0.001f) continue;

                float az = inst.pillarAzimuth[i];
                float radius = ThaumaturgyEffectInstance.PILLAR_RING_RADIUS
                        * (1f - 0.85f * inward) * merge;
                float bx = px + (float) Math.cos(az) * radius;
                float bz = pz + (float) Math.sin(az) * radius;

                float pulse = 0.88f + 0.12f * (float) Math.sin(flow * TAU * 3f + i * 1.7f);
                float halfWidth = 1.35f * inst.pillarWidth[i] * merge;
                float a = pillarAlpha * (0.75f + 0.25f * descent);

                int rgba = packColor(descent, flow,
                        1.25f * energy * pulse * COLUMN_FACE_ENERGY / 4f, a);
                emitColumn(buf, vm, cx, cy, cz, bx, yGround, bz,
                        yTop, yTop - (yTop - yGround) * descent, halfWidth, i, rgba);
                emitted++;
            }
        }

        if (grandAlpha >= 0.004f) {
            float descent = inst.grandDescent(nowMs);
            float pulse = 0.92f + 0.08f * (float) Math.sin(flow * TAU * 5f);
            float halfWidth = (5.2f + 1.6f * descent) * merge;
            int idx = ThaumaturgyEffectInstance.MAX_PILLARS; // its own variation seed
            int rgba = packColor(descent, flow,
                    2.1f * energy * pulse * COLUMN_FACE_ENERGY / 4f, grandAlpha);
            emitColumn(buf, vm, cx, cy, cz, px, yGround, pz,
                    yTop, yTop - (yTop - yGround) * descent, halfWidth, idx, rgba);
            emitted++;
        }

        if (emitted == 0) return;
        GeminiTesselator.draw(PILLAR_TYPE, buf.buildOrThrow());
    }

    // ── Lightning ──────────────────────────────────────────────────

    /** Discharge arcs per ring column, and around the colossal centre one. */
    private static final int COLUMN_BOLTS = 2;
    private static final int GRAND_BOLTS = 7;

    private static final double[] BOLT_A = new double[3];
    private static final double[] BOLT_B = new double[3];
    private static final double[] BOLT_BASIS = new double[6];

    /**
     * Discharge arcs crawling down every live column.
     *
     * <p>The paths come from {@link ThaumaturgyLightning}: a hash redraws the
     * whole field on a 90 ms strobe, so the arcs snap and re-strike along
     * similar paths instead of flowing, which is what separates lightning from
     * ornament. They are generated in world space rather than painted on the
     * beam so they can reach out past its silhouette.</p>
     */
    private static void drawLightning(Matrix4f vm, float cx, float cy, float cz,
                                      float px, float py, float pz,
                                      ThaumaturgyEffectInstance inst, long nowMs,
                                      float energy, float flow) {
        if (inst.currentStage(nowMs) < ThaumaturgyEffectInstance.STAGE_SIX_PILLARS) return;

        float pillarAlpha = inst.pillarAlpha(nowMs);
        float grandAlpha = inst.grandAlpha(nowMs);
        if (pillarAlpha < 0.01f && grandAlpha < 0.01f) return;

        float yGround = py + 0.05f;
        float yTop = py + inst.sigilAltitude(nowMs) + 1.5f;
        float inward = inst.pillarInward(nowMs);
        float merge = 1f + (inst.mergeCount - 1) * 0.14f;
        int step = ThaumaturgyLightning.step(nowMs);

        BufferBuilder buf = begin();
        int spans = 0;

        if (pillarAlpha >= 0.01f) {
            for (int i = 0; i < inst.pillarCount; i++) {
                float descent = inst.pillarDescent(i, nowMs);
                if (descent <= 0.02f) continue;

                float az = inst.pillarAzimuth[i];
                float radius = ThaumaturgyEffectInstance.PILLAR_RING_RADIUS
                        * (1f - 0.85f * inward) * merge;
                double bx = px + Math.cos(az) * radius;
                double bz = pz + Math.sin(az) * radius;
                double yTip = yTop - (yTop - yGround) * descent;

                spans += emitBolts(buf, vm, cx, cy, cz,
                        bx, yTop, bz, bx, yTip, bz,
                        1.15f * merge * inst.pillarWidth[i], 0.15f,
                        300L + i, COLUMN_BOLTS, step,
                        pillarAlpha * 0.8f, energy, flow);
            }
        }

        if (grandAlpha >= 0.01f) {
            float descent = inst.grandDescent(nowMs);
            // Before it lands the field crowds the leading edge; afterwards the
            // whole column is live, so the arcs run all the way into the ground.
            double yTip = descent >= 1f ? yGround : yTop - (yTop - yGround) * descent;
            spans += emitBolts(buf, vm, cx, cy, cz,
                    px, yTop, pz, px, yTip, pz,
                    3.6f * merge, 0.30f,
                    77L, GRAND_BOLTS, step, grandAlpha, energy, flow);
        }

        if (spans == 0) return;
        MeshData mesh = buf.build();
        if (mesh != null) GeminiTesselator.draw(BOLT_TYPE, mesh);
    }

    /**
     * @param amplitude how far an arc may snake away from the column, in blocks
     * @param halfWidth ribbon half-width, in blocks
     * @param seedBase  identifies the column, so each one keeps its own paths
     * @return the number of spans emitted
     */
    private static int emitBolts(BufferBuilder buf, Matrix4f vm,
                                 float cx, float cy, float cz,
                                 double sx, double sy, double sz,
                                 double ex, double ey, double ez,
                                 double amplitude, float halfWidth,
                                 long seedBase, int count, int step,
                                 float alpha, float energy, float flow) {
        int spans = 0;
        int segments = ThaumaturgyLightning.SEGMENTS;
        for (int b = 0; b < count; b++) {
            long seed = seedBase * 31L + b;
            if (!ThaumaturgyLightning.live(seed, step, 0.6)) continue;
            // Every arc is a little different in reach and in heat, so a column
            // reads as a storm rather than as one pattern repeated.
            double shape = ThaumaturgyLightning.shape(seed);
            int rgba = packColor((float) shape, flow,
                    (1.35f + 0.5f * (float) shape) * energy / 4f,
                    alpha * (0.7f + 0.3f * (float) shape));

            for (int i = 0; i < segments; i++) {
                ThaumaturgyLightning.point(sx, sy, sz, ex, ey, ez,
                        i, seed, step, amplitude, BOLT_A);
                ThaumaturgyLightning.point(sx, sy, sz, ex, ey, ez,
                        i + 1, seed, step, amplitude, BOLT_B);
                emitRibbon(buf, vm, cx, cy, cz, BOLT_A, BOLT_B,
                        (float) i / segments, (float) (i + 1) / segments, halfWidth, rgba);
                spans++;
            }
        }
        return spans;
    }

    /**
     * One span of a bolt: a quad whose long axis is the span itself and whose
     * short axis lies across the screen, so an arc aimed straight at the eye
     * still has width instead of vanishing.
     */
    private static void emitRibbon(BufferBuilder buf, Matrix4f vm,
                                   float cx, float cy, float cz,
                                   double[] a, double[] b,
                                   float v0, float v1, float halfWidth, int rgba) {
        double ax = a[0] - cx, ay = a[1] - cy, az = a[2] - cz;
        double bx = b[0] - cx, by = b[1] - cy, bz = b[2] - cz;

        ThaumaturgyLightning.ribbonBasis(bx - ax, by - ay, bz - az,
                (ax + bx) * 0.5, (ay + by) * 0.5, (az + bz) * 0.5, BOLT_BASIS);

        double sx = BOLT_BASIS[0] * halfWidth;
        double sy = BOLT_BASIS[1] * halfWidth;
        double sz = BOLT_BASIS[2] * halfWidth;

        buf.addVertex(vm, (float) (ax + sx), (float) (ay + sy), (float) (az + sz))
                .setUv(0f, v0).setColor(rgba);
        buf.addVertex(vm, (float) (bx + sx), (float) (by + sy), (float) (bz + sz))
                .setUv(1f, v0).setColor(rgba);
        buf.addVertex(vm, (float) (bx - sx), (float) (by - sy), (float) (bz - sz))
                .setUv(1f, v1).setColor(rgba);
        buf.addVertex(vm, (float) (ax - sx), (float) (ay - sy), (float) (az - sz))
                .setUv(0f, v1).setColor(rgba);
    }

    // ── Nova sphere ────────────────────────────────────────────────

    private static final int SPHERE_LAT = 14;
    private static final int SPHERE_LON = 24;
    private static float[][] unitSphereVerts;

    private static float[][] getUnitSphere() {
        if (unitSphereVerts != null) return unitSphereVerts;
        float[][] v = new float[SPHERE_LAT * SPHERE_LON * 4][3];
        int idx = 0;
        for (int i = 0; i < SPHERE_LAT; i++) {
            double t0 = Math.PI * i / SPHERE_LAT;
            double t1 = Math.PI * (i + 1) / SPHERE_LAT;
            for (int j = 0; j < SPHERE_LON; j++) {
                double p0 = TAU * j / SPHERE_LON;
                double p1 = TAU * (j + 1) / SPHERE_LON;
                v[idx++] = sphPoint(t0, p0);
                v[idx++] = sphPoint(t0, p1);
                v[idx++] = sphPoint(t1, p1);
                v[idx++] = sphPoint(t1, p0);
            }
        }
        unitSphereVerts = v;
        return v;
    }

    private static float[] sphPoint(double theta, double phi) {
        return new float[]{
                (float) (Math.sin(theta) * Math.cos(phi)),
                (float) Math.cos(theta),
                (float) (Math.sin(theta) * Math.sin(phi))
        };
    }

    private static void drawNovaSphere(PoseStack poseStack,
                                       float cx, float cy, float cz,
                                       float px, float py, float pz,
                                       ThaumaturgyEffectInstance inst, long nowMs,
                                       float energy) {
        float alpha = inst.sphereAlpha(nowMs);
        float radius = inst.sphereRadius(nowMs);
        if (alpha < 0.004f || radius < 0.2f) return;

        float progress = inst.chargePost(nowMs);
        float heat = inst.sphereHeat(nowMs);
        float boost = (0.9f + 1.5f * (1f - progress)) * energy;

        int rgba = packColor(progress, heat, boost / 4f, alpha);

        // The ball sits low on the ground: lifted by less than half its radius so
        // the impact point stays buried in it, and so a fully swollen ball stops
        // at the sigil plane instead of swallowing the disc it was summoned from.
        poseStack.pushPose();
        poseStack.translate(px - cx, py - cy + radius * 0.45f, pz - cz);
        Matrix4f svm = poseStack.last().pose();

        float[][] sphere = getUnitSphere();
        BufferBuilder buf = begin();
        for (int i = 0; i < sphere.length; i += 4) {
            for (int k = 0; k < 4; k++) {
                float[] v = sphere[i + k];
                buf.addVertex(svm, v[0] * radius, v[1] * radius, v[2] * radius)
                        .setUv(radius, 0f).setColor(rgba);
            }
        }
        GeminiTesselator.draw(ORB_TYPE, buf.buildOrThrow());
        poseStack.popPose();
    }

    // ── Sparks + ground rings ──────────────────────────────────────

    private static final float[] SPARK_BATCH = new float[900 * 8];

    private static void drawSparks(Matrix4f vm, float cx, float cy, float cz,
                                   float px, float py, float pz,
                                   ThaumaturgyEffectInstance inst, long nowMs,
                                   float energy) {
        float masterFade = inst.masterFade(nowMs);
        BufferBuilder buf = begin();
        int emitted = 0;

        // Ground shock rings: one per landed column, plus the centre slam
        emitted += drawGroundRings(buf, vm, cx, cy, cz, px, py, pz, inst, nowMs, energy);

        // Free sparks
        float sparkAlpha = Math.clamp(masterFade * Math.min(energy, 1.3f), 0f, 1f);
        int count = inst.fillSparkBatch(SPARK_BATCH, SPARK_BATCH.length / 8, nowMs, sparkAlpha);
        for (int i = 0; i < count; i++) {
            int off = i * 8;
            int rgba = packColor(SPARK_BATCH[off + 4], SPARK_BATCH[off + 5], 0f, SPARK_BATCH[off + 7]);
            emitBillboard(buf, vm,
                    SPARK_BATCH[off] - cx, SPARK_BATCH[off + 1] - cy, SPARK_BATCH[off + 2] - cz,
                    SPARK_BATCH[off + 3], rgba);
            emitted++;
        }

        if (emitted == 0) return;
        MeshData mesh = buf.build();
        if (mesh != null) GeminiTesselator.draw(SPARK_TYPE, mesh);
    }

    /** How long a scorch ring stays lit after the column that made it lands. */
    private static final double RING_LIFE_SEC = 1.15;

    /**
     * Expanding rings scorched into the ground where each column strikes.
     *
     * @return the number of quads emitted
     */
    private static int drawGroundRings(BufferBuilder buf, Matrix4f vm,
                                       float cx, float cy, float cz,
                                       float px, float py, float pz,
                                       ThaumaturgyEffectInstance inst, long nowMs,
                                       float energy) {
        if (inst.currentStage(nowMs) < ThaumaturgyEffectInstance.STAGE_SIX_PILLARS) return 0;

        float masterFade = inst.masterFade(nowMs);
        if (masterFade < 0.004f) return 0;

        float merge = 1f + (inst.mergeCount - 1) * 0.14f;
        int emitted = 0;

        for (int i = 0; i < inst.pillarCount; i++) {
            float az = inst.pillarAzimuth[i];
            float rr = ThaumaturgyEffectInstance.PILLAR_RING_RADIUS * merge;
            emitted += emitRing(buf, vm, cx, cy, cz,
                    px + (float) Math.cos(az) * rr, py, pz + (float) Math.sin(az) * rr,
                    inst.pillarImpactAge(i, nowMs), 1f, inst.pillarSeed[i], masterFade, energy);
        }

        // Centre slam: much wider, and it keeps burning through the sphere stage
        emitted += emitRing(buf, vm, cx, cy, cz, px, py, pz,
                inst.grandImpactAge(nowMs), 2.6f, 0.5f, masterFade, energy);
        return emitted;
    }

    /**
     * @param age   seconds since the column that made this ring landed; negative before impact
     * @param scale ring size multiplier
     * @param seed  per-ring variation so neighbouring rings are not identical
     * @return 1 when a quad was emitted
     */
    private static int emitRing(BufferBuilder buf, Matrix4f vm,
                                float cx, float cy, float cz,
                                float ox, float py, float oz,
                                double age, float scale, float seed,
                                float masterFade, float energy) {
        if (age < 0 || age > RING_LIFE_SEC) return 0;

        float t = (float) (age / RING_LIFE_SEC);
        float alpha = Math.clamp((1f - t) * (1f - t) * 0.85f * masterFade
                * Math.min(energy, 1.6f), 0f, 1f);
        if (alpha < 0.004f) return 0;

        float half = (1.6f + t * 9.0f) * scale * (0.85f + seed * 0.3f);
        emitGroundQuad(buf, vm, ox - cx, py + 0.07f - cy, oz - cz, half,
                packColor(t, 1f, 0f, alpha));
        return 1;
    }
}
