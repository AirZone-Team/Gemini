package geminiclient.gemini.customRenderer.glsl.modules;

import geminiclient.gemini.customRenderer.GeminiTesselator;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;

import com.mojang.blaze3d.PrimitiveTopology;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
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
 * GPU-accelerated Hypernova Kill Effect renderer.
 *
 * <h3>Pipelines</h3>
 * <ul>
 *   <li>{@link #MAGIC_PIPE} — Magic circle + tower (additive, depth-tested)</li>
 *   <li>{@link #BLACK_HOLE_PIPE} — Event horizon + photon ring + lens distortion</li>
 *   <li>{@link #PARTICLE_PIPE} — Accretion particles (additive, depth-tested)</li>
 *   <li>{@link #HYPERNOVA_PIPE} — Explosion flash overlay (no depth, always visible)</li>
 *   <li>{@link #ORB_PIPE} — Volumetric 3D orb (real sphere mesh, ray-marched)</li>
 *   <li>{@link #RAY_PIPE} — Radial light rays for pseudo ray-tracing (depth-tested)</li>
 * </ul>
 *
 * <h3>Vertex encoding (POSITION_TEX_COLOR, all pipelines)</h3>
 * <ul>
 *   <li>UV.x/y — billboard quad corner (0..1), maps to effect-local coordinates in FS</li>
 *   <li>Color.r — time progress 0→1 (within current stage)</li>
 *   <li>Color.g — normalized identifier: stage/8 (magic, hole), layer/4 (glow),
 *       mode flag 0/0.5/1 (nova, particle), ray index 0..1 (ray)</li>
 *   <li>Color.b — intensity/4 (hole, glow, nova, ray) or raw ≤1 (magic);
 *       shaders rescale by ×4 where applicable (bytes can only hold 0..1)</li>
 *   <li>Color.a — master alpha</li>
 * </ul>
 */
public final class KillEffectRenderer {

    private KillEffectRenderer() {}

    // ════════════════════════════════════════════════════════════════
    //  Shared depth state
    // ════════════════════════════════════════════════════════════════

    private static final DepthStencilState EFFECT_DEPTH =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 1.0F);

    /** No depth testing — always pass (for flash / explosion overlays). */
    private static final DepthStencilState NO_DEPTH =
            new DepthStencilState(CompareOp.ALWAYS_PASS, false, -1.0F, -1.0F);

    /** Blending for additive glow effects. */
    private static final ColorTargetState ADDITIVE_BLEND = new ColorTargetState(new BlendFunction(
            BlendFactor.SRC_ALPHA, BlendFactor.ONE,
            BlendFactor.ONE, BlendFactor.ZERO));

    /** Blending for alpha-blended effects. */
    private static final ColorTargetState ALPHA_BLEND = new ColorTargetState(new BlendFunction(
            BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA,
            BlendFactor.ONE, BlendFactor.ZERO));

    // ════════════════════════════════════════════════════════════════
    //  Pipelines
    // ════════════════════════════════════════════════════════════════

    /** Magic circle + tower — additive gold glow. */
    public static final RenderPipeline MAGIC_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_magic"))
            .withVertexShader(getIdentifier("core/kill_effect_magic"))
            .withFragmentShader(getIdentifier("core/kill_effect_magic"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Black hole: event horizon + photon ring + gravitational lensing. */
    public static final RenderPipeline BLACK_HOLE_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_hole"))
            .withVertexShader(getIdentifier("core/kill_effect_hole"))
            .withFragmentShader(getIdentifier("core/kill_effect_hole"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Accretion particles + general particle rendering. */
    public static final RenderPipeline PARTICLE_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_particle"))
            .withVertexShader(getIdentifier("core/kill_effect_particle"))
            .withFragmentShader(getIdentifier("core/kill_effect_particle"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Hypernova explosion + flash: no depth test — always visible. */
    public static final RenderPipeline HYPERNOVA_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_nova"))
            .withVertexShader(getIdentifier("core/kill_effect_nova"))
            .withFragmentShader(getIdentifier("core/kill_effect_nova"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(NO_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /**
     * Volumetric orb: real 3D sphere mesh with per-pixel ray-marched volume.
     *
     * <p>Renders an actual UV-sphere tessellation in world space (not a
     * camera-facing billboard).  The fragment shader intersects the view
     * ray with the sphere analytically and integrates an emissive density
     * field front-to-back — bright core, soft limb, correct from every
     * viewing angle, including from inside the ball (culling disabled;
     * both shell hemispheres integrate and the result is halved).</p>
     *
     * <p>Depth testing ({@link #EFFECT_DEPTH}) keeps the ball correctly
     * occluded by blocks and entities.</p>
     */
    public static final RenderPipeline ORB_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_orb"))
            .withVertexShader(getIdentifier("core/kill_effect_orb"))
            .withFragmentShader(getIdentifier("core/kill_effect_orb"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /**
     * Radial light rays: depth-tested elongated billboards for pseudo ray-tracing.
     *
     * <p>Each ray is a thin quad extended outward from the effect center.
     * Depth testing ({@link #EFFECT_DEPTH}) means rays terminate naturally
     * at block/entity surfaces, producing realistic occlusion.</p>
     */
    public static final RenderPipeline RAY_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_ray"))
            .withVertexShader(getIdentifier("core/kill_effect_ray"))
            .withFragmentShader(getIdentifier("core/kill_effect_ray"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    // ════════════════════════════════════════════════════════════════
    //  Render types
    // ════════════════════════════════════════════════════════════════

    private static final RenderType MAGIC_TYPE       = createRenderType("gemini_kill_magic", MAGIC_PIPE);
    private static final RenderType BLACK_HOLE_TYPE  = createRenderType("gemini_kill_hole", BLACK_HOLE_PIPE);
    private static final RenderType PARTICLE_TYPE    = createRenderType("gemini_kill_particle", PARTICLE_PIPE);
    private static final RenderType HYPERNOVA_TYPE   = createRenderType("gemini_kill_nova", HYPERNOVA_PIPE);
    private static final RenderType ORB_TYPE         = createRenderType("gemini_kill_orb", ORB_PIPE);
    private static final RenderType RAY_TYPE         = createRenderType("gemini_kill_ray", RAY_PIPE);

    private static RenderType createRenderType(String name, RenderPipeline pipe) {
        return RenderType.create(name,
                RenderSetup.builder(pipe).sortOnUpload()
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .createRenderSetup());
    }

    // ════════════════════════════════════════════════════════════════
    //  Registration
    // ════════════════════════════════════════════════════════════════

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(MAGIC_PIPE);
        registry.accept(BLACK_HOLE_PIPE);
        registry.accept(PARTICLE_PIPE);
        registry.accept(HYPERNOVA_PIPE);
        registry.accept(ORB_PIPE);
        registry.accept(RAY_PIPE);
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

    // ════════════════════════════════════════════════════════════════
    //  Billboard quad emitter
    // ════════════════════════════════════════════════════════════════

    /**
     * Emit a single camera-facing billboard quad.
     *
     * @param buf     target buffer builder
     * @param vm      view matrix (pose stack last pose)
     * @param cx,cy,cz camera position
     * @param px,py,pz billboard center (world space)
     * @param halfSize half the billboard edge length
     * @param u0,v0,u1,v1 UV range
     * @param rgba    packed ARGB color
     */
    private static void emitBillboard(BufferBuilder buf, Matrix4f vm,
                                       float cx, float cy, float cz,
                                       float px, float py, float pz,
                                       float halfSize,
                                       float u0, float v0, float u1, float v1,
                                       int rgba) {
        float rx = px - cx, ry = py - cy, rz = pz - cz;

        float rpx = CAM_RIGHT.x * halfSize, rpy = CAM_RIGHT.y * halfSize, rpz = CAM_RIGHT.z * halfSize;
        float upx = CAM_UP.x    * halfSize, upy = CAM_UP.y    * halfSize, upz = CAM_UP.z    * halfSize;

        // v0: (-right, -up)   v1: (-right, +up)
        // v2: (+right, +up)   v3: (+right, -up)
        buf.addVertex(vm, rx - rpx - upx, ry - rpy - upy, rz - rpz - upz)
                .setUv(u0, v0).setColor(rgba);
        buf.addVertex(vm, rx - rpx + upx, ry - rpy + upy, rz - rpz + upz)
                .setUv(u0, v1).setColor(rgba);
        buf.addVertex(vm, rx + rpx + upx, ry + rpy + upy, rz + rpz + upz)
                .setUv(u1, v1).setColor(rgba);
        buf.addVertex(vm, rx + rpx - upx, ry + rpy - upy, rz + rpz - upz)
                .setUv(u1, v0).setColor(rgba);
    }

    /** Pack float RGBA into int ARGB. Inputs are clamped to [0,1] — the vertex
     *  color is 4 bytes, so values &gt; 1 would overflow into the adjacent
     *  channel's bits. Encode stage/layer IDs normalized (see call sites). */
    private static int packColor(float r, float g, float b, float a) {
        int ir = (int)(Math.clamp(r, 0f, 1f) * 255f);
        int ig = (int)(Math.clamp(g, 0f, 1f) * 255f);
        int ib = (int)(Math.clamp(b, 0f, 1f) * 255f);
        int ia = (int)(Math.clamp(a, 0f, 1f) * 255f);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    // ════════════════════════════════════════════════════════════════
    //  Magic Circle + Tower drawing
    // ════════════════════════════════════════════════════════════════

    /**
     * Draw magic circle and tower for an effect instance.
     *
     * <p>Magic circle: single large ground-plane billboard with rune pattern.
     * Tower: up to 12 stacked billboards with decreasing scale and alpha.
     *
     * <p>During the tower→BH transition (last 30% of tower stage, first 30% of BH stage),
     * the tower layers compress vertically (squash effect) and fade out, while the
     * shader color.vertex g is set to stage 2 so the fragment shader renders the tower
     * pattern even during BH stage.</p>
     */
    public static void drawMagic(PoseStack poseStack, KillEffectInstance inst, long nowMs) {
        if (!inst.shouldRenderMagic(nowMs)) return;

        int stage = inst.currentStage(nowMs);
        float progress = inst.stageProgress(nowMs);

        // During BH stage transition: override stage to render magic pattern
        float shaderStage;
        if (stage == KillEffectInstance.STAGE_BLACK_HOLE) {
            shaderStage = KillEffectInstance.STAGE_MAGIC_TOWER; // render tower pattern
        } else {
            shaderStage = (float) stage;
        }

        // Cross-stage transition alpha
        float alpha = inst.magicTransitionAlpha(nowMs);

        float px = (float) inst.position.x;
        float py = (float) inst.position.y;
        float pz = (float) inst.position.z;

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        // Distance-based size scaling
        float dx = px - cx, dy = py - cy, dz = pz - cz;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.01f;
        float baseSize = 1.5f * (1f + dist * 0.08f);

        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        if (stage == KillEffectInstance.STAGE_MAGIC_CIRCLE) {
            // Single magic circle on the ground, facing upward (horizontal)
            float size = baseSize * (0.5f + progress * 0.5f); // grow from 50% to 100%
            float circleAlpha = alpha * (progress < 0.2f ? progress / 0.2f : 1f); // fade in
            float yOffset = 0.02f; // slightly above ground

            int rgba = packColor(progress, 1f / 8f, 0.8f, circleAlpha);

            // Draw horizontal billboard (use up=world-up mapping for UV)
            float rx = px - cx, ry = py + yOffset - cy, rz = pz - cz;
            buf.addVertex(vm, rx - size, ry, rz - size).setUv(0f, 0f).setColor(rgba);
            buf.addVertex(vm, rx - size, ry, rz + size).setUv(0f, 1f).setColor(rgba);
            buf.addVertex(vm, rx + size, ry, rz + size).setUv(1f, 1f).setColor(rgba);
            buf.addVertex(vm, rx + size, ry, rz - size).setUv(1f, 0f).setColor(rgba);

        } else {
            // ── Tower stage (includes tower→BH transition) ────────────
            int towerLayers = 12;
            float transT = 0f;
            boolean isTransition = false;

            // Detect tower→BH transition
            if (stage == KillEffectInstance.STAGE_MAGIC_TOWER && progress > 1f - 0.30f) {
                isTransition = true;
                transT = (progress - (1f - 0.30f)) / 0.30f;
            } else if (stage == KillEffectInstance.STAGE_BLACK_HOLE) {
                isTransition = true;
                transT = 1f; // fully into transition — tower is almost gone
            }

            // ── Circle→tower crossfade ──────────────────────────────
            // The ground circle from stage 1 keeps rendering during the
            // first 25% of the tower stage, fading out as the tower rises —
            // otherwise it would vanish instantly at the stage boundary.
            if (stage == KillEffectInstance.STAGE_MAGIC_TOWER && progress < 0.25f) {
                float circleFade = 1.0f - progress / 0.25f;
                float size = baseSize * (1.0f + progress * 0.4f); // slight expand as it dissolves
                int circleRgba = packColor(1f, 1f / 8f, 0.8f, alpha * circleFade);
                float crx = px - cx, cry = py + 0.02f - cy, crz = pz - cz;
                buf.addVertex(vm, crx - size, cry, crz - size).setUv(0f, 0f).setColor(circleRgba);
                buf.addVertex(vm, crx - size, cry, crz + size).setUv(0f, 1f).setColor(circleRgba);
                buf.addVertex(vm, crx + size, cry, crz + size).setUv(1f, 1f).setColor(circleRgba);
                buf.addVertex(vm, crx + size, cry, crz - size).setUv(1f, 0f).setColor(circleRgba);
            }

            for (int i = 0; i < towerLayers; i++) {
                float layerProgress = (float)i / towerLayers;
                float scale = (float) Math.pow(0.9, i);
                float height = i * 0.3f * baseSize;
                float layerAlpha = (float) Math.pow(0.82, i) * alpha;
                float yOffset = 0.02f + height;

                // Staggered rise animation
                float riseDelay = i * 0.05f;
                float riseProgress = Math.clamp((progress - riseDelay) / 0.15f, 0f, 1f);
                layerAlpha *= riseProgress;

                // ── Tower compression during transition (squash effect) ──
                if (isTransition) {
                    // Compress Y: layers squash toward the ground
                    float squash = 1.0f - transT * 0.85f;
                    yOffset = 0.02f + height * squash;

                    // Fade from bottom (layer 0) to top (layer N) during compression
                    float bottomFade = 1.0f - layerProgress * transT * 0.7f;
                    layerAlpha *= bottomFade;
                }

                float size = baseSize * scale;

                int rgba = packColor(progress, shaderStage / 8f, 0.8f, layerAlpha);

                float rx = px - cx, ry = py + yOffset - cy, rz = pz - cz;
                buf.addVertex(vm, rx - size, ry, rz - size).setUv(0f, 0f).setColor(rgba);
                buf.addVertex(vm, rx - size, ry, rz + size).setUv(0f, 1f).setColor(rgba);
                buf.addVertex(vm, rx + size, ry, rz + size).setUv(1f, 1f).setColor(rgba);
                buf.addVertex(vm, rx + size, ry, rz - size).setUv(1f, 0f).setColor(rgba);
            }
        }

        GeminiTesselator.draw(MAGIC_TYPE, buf.buildOrThrow());
    }

    // ════════════════════════════════════════════════════════════════
    //  Black Hole drawing
    // ════════════════════════════════════════════════════════════════

    /**
     * Draw the black hole: event horizon, photon ring, gravitational lensing.
     * Rendered as a single large camera-facing billboard.
     *
     * <p>During the tower→BH transition (last 30% of tower stage), the black hole
     * appears as a small faint dot that grows and brightens as the tower compresses.</p>
     */
    public static void drawBlackHole(PoseStack poseStack, KillEffectInstance inst, long nowMs) {
        if (!inst.shouldRenderBlackHole(nowMs)) return;

        int stage = inst.currentStage(nowMs);
        float progress = inst.stageProgress(nowMs);

        // Cross-stage transition alpha
        float alpha = inst.blackHoleTransitionAlpha(nowMs);
        if (alpha < 0.001f) return;

        // ── Pre-appearance during tower→BH transition ────────────────
        float holeSize = 1.5f;
        float brightness = 1f;

        if (stage == KillEffectInstance.STAGE_MAGIC_TOWER) {
            // During tower's last portion: black hole pre-appears as a tiny dot
            float t = (progress - (1f - 0.30f)) / 0.30f; // 0→1 within the overlap
            // Exponential growth: very small at first, growing to 30% of full size
            holeSize = 0.02f + t * t * 0.28f;
            brightness = 0.3f + t * 0.7f;

        } else if (stage == KillEffectInstance.STAGE_BLACK_HOLE) {
            // Normal forming: grow from small to full
            holeSize = 0.3f + progress * 1.2f;

            // During first 30% (transition completion), rapid growth
            if (progress < 0.30f) {
                float t = progress / 0.30f;
                holeSize = Math.max(holeSize, 0.05f + t * 0.5f);
                brightness = 0.5f + t * 0.5f;
            } else {
                brightness = 1f;
            }
            alpha = progress < 0.15f ? Math.max(alpha, progress / 0.15f) : Math.max(alpha, 1f);

        } else if (stage == KillEffectInstance.STAGE_COLLAPSE) {
            holeSize = 1.5f * (1f - progress * 0.8f);
            brightness = 1f + progress * 3f;

        } else {
            // Accretion: full size
            holeSize = 1.5f;
        }

        alpha = Math.clamp(alpha, 0f, 1f);

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        float px = (float) inst.position.x;
        float py = (float) inst.position.y + 1.5f; // raised above ground
        float pz = (float) inst.position.z;

        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int rgba = packColor(progress, stage / 8f, brightness / 4f, alpha);
        // Leave enough UV room for the wide disk and its lensed far-side arc.
        float halfSize = holeSize * 3.25f;

        emitBillboard(buf, vm, cx, cy, cz, px, py, pz, halfSize,
                0f, 0f, 1f, 1f, rgba);

        GeminiTesselator.draw(BLACK_HOLE_TYPE, buf.buildOrThrow());
    }

    // ════════════════════════════════════════════════════════════════
    //  Particle drawing
    // ════════════════════════════════════════════════════════════════

    /**
     * Draw accretion particles as small camera-facing billboards.
     *
     * @param batch  flat array [x,y,z,size,r,g,b,a] × count
     * @param count  number of particles in the batch
     */
    public static void drawParticles(PoseStack poseStack, float[] batch, int count) {
        if (count == 0) return;

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < count; i++) {
            int off = i * 8;
            float px = batch[off], py = batch[off + 1], pz = batch[off + 2];
            float sz = batch[off + 3];
            float cr = batch[off + 4], cg = batch[off + 5], cb = batch[off + 6];
            float ca = batch[off + 7];

            int rgba = packColor(cr, cg, cb, ca);
            emitBillboard(buf, vm, cx, cy, cz, px, py, pz, sz,
                    0f, 0f, 1f, 1f, rgba);
        }

        GeminiTesselator.draw(PARTICLE_TYPE, buf.buildOrThrow());
    }

    // ════════════════════════════════════════════════════════════════
    //  Hypernova explosion drawing
    // ════════════════════════════════════════════════════════════════

    /**
     * Render a blinding 3D light source at the explosion center.
     *
     * <p>Layered composition of three effects:</p>
     * <ol>
     *   <li>{@link #HYPERNOVA_PIPE} — full-screen flash billboard (no depth,
     *       always visible overlay) for the base flash + shockwave pattern,
     *       plus a horizontal ground shock ring during hypernova</li>
     *   <li>{@link #ORB_PIPE} — depth-tested volumetric 3D sphere with
     *       per-pixel ray-marched density integration (bright core, soft
     *       limb, self-absorption); a real ball in the world, not a
     *       camera-facing billboard</li>
     *   <li>{@link #RAY_PIPE} — depth-tested radial light rays for pseudo
     *       ray-tracing, terminating naturally at occluding surfaces</li>
     * </ol>
     *
     * <p>The combination creates a physically plausible blinding light that
     * interacts correctly with the 3D environment.</p>
     */
    public static void drawHypernova(PoseStack poseStack, KillEffectInstance inst, long nowMs) {
        int stage = inst.currentStage(nowMs);
        if (stage != KillEffectInstance.STAGE_HYPERNOVA
                && stage != KillEffectInstance.STAGE_AFTERGLOW
                && stage != KillEffectInstance.STAGE_FADE_OUT) return;

        float progress = inst.stageProgress(nowMs);
        float alpha;

        if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            alpha = 1.0f - (float)Math.pow(2.0, -6.0 * progress);
        } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
            float fade = 1.0f - progress;
            alpha = 0.08f + fade * fade * fade * 0.92f;
        } else {
            alpha = 0.08f * inst.getFadeOutAlpha(nowMs);
        }

        if (alpha < 0.005f) return;

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        float px = (float) inst.position.x;
        float py = (float) inst.position.y + 2.5f;
        float pz = (float) inst.position.z;

        // Distance-based sizing
        float dx = px - cx, dy = py - cy, dz = pz - cz;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.1f;

        // ── Layer 0: Ground shock ring (horizontal quad) ─────────────
        // A flat quad on the ground running the nova shader's flash-mode
        // expanding ring (that path has an edge mask, so no square edge).
        // Alpha eases to zero before the stage ends to avoid a cutoff pop.
        if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            float groundY = (float) inst.position.y + 0.04f;
            float ringSize = dist * 0.58f * (1.0f + progress * 2.1f);
            float fade = 1.0f - progress;
            float ringAlpha = alpha * fade * (float)Math.sqrt(fade);

            int ringRgba = packColor(progress * 0.62f, 0f, 2.4f / 4f, ringAlpha);
            float echoProgress = Math.clamp((progress - 0.12f) / 0.88f, 0f, 1f);
            float echoSize = ringSize * 0.72f;
            int echoRgba = packColor(echoProgress * 0.58f, 0f, 1.55f / 4f, ringAlpha * 0.62f);

            BufferBuilder ringBuf = GeminiTesselator.getInstance()
                    .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float rx = px - cx, ry = groundY - cy, rz = pz - cz;
            ringBuf.addVertex(vm, rx - ringSize, ry, rz - ringSize).setUv(0f, 0f).setColor(ringRgba);
            ringBuf.addVertex(vm, rx - ringSize, ry, rz + ringSize).setUv(0f, 1f).setColor(ringRgba);
            ringBuf.addVertex(vm, rx + ringSize, ry, rz + ringSize).setUv(1f, 1f).setColor(ringRgba);
            ringBuf.addVertex(vm, rx + ringSize, ry, rz - ringSize).setUv(1f, 0f).setColor(ringRgba);
            ringBuf.addVertex(vm, rx - echoSize, ry + 0.015f, rz - echoSize).setUv(0f, 0f).setColor(echoRgba);
            ringBuf.addVertex(vm, rx - echoSize, ry + 0.015f, rz + echoSize).setUv(0f, 1f).setColor(echoRgba);
            ringBuf.addVertex(vm, rx + echoSize, ry + 0.015f, rz + echoSize).setUv(1f, 1f).setColor(echoRgba);
            ringBuf.addVertex(vm, rx + echoSize, ry + 0.015f, rz - echoSize).setUv(1f, 0f).setColor(echoRgba);
            GeminiTesselator.draw(HYPERNOVA_TYPE, ringBuf.buildOrThrow());
        }

        // ── Layer 1: Full-screen flash overlay (no depth, always visible) ──
        float novaSize = dist * 0.44f;
        float lightSize = dist * 0.29f;
        float flashTime = 0.33f + 0.045f * (float)Math.sin(progress * Math.PI * 4.0);
        // The white core rides over the wider fireball only during detonation.
        float flashIntensity = 3.85f + (float)Math.sin(progress * Math.PI * 3.5f)
                * 0.15f * (1.0f - progress);

        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        // Submit the actual nova mode before the central flash. Previously only
        // mode 0 was sent, leaving the fireball/nebula/lightning branch dormant.
        // Hold the settled nova frame through afterglow/fade instead of
        // restarting its expansion when stage-local progress resets to zero.
        float novaProgress = stage == KillEffectInstance.STAGE_HYPERNOVA ? progress : 1.0f;
        float novaIntensity = stage == KillEffectInstance.STAGE_HYPERNOVA ? 1.0f
                : stage == KillEffectInstance.STAGE_AFTERGLOW ? 0.65f : 0.25f;
        int novaRgba = packColor(novaProgress, 0.5f, novaIntensity, alpha);
        emitBillboard(buf, vm, cx, cy, cz, px, py, pz, novaSize,
                0f, 0f, 1f, 1f, novaRgba);

        if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            float corePulse = 0.72f + 0.28f * (float)Math.sin(progress * Math.PI * 5.0);
            int flashRgba = packColor(flashTime, 0f, flashIntensity / 4f,
                    alpha * Math.clamp(corePulse, 0.55f, 1.0f));
            emitBillboard(buf, vm, cx, cy, cz, px, py, pz, lightSize,
                    0f, 0f, 1f, 1f, flashRgba);
        }

        GeminiTesselator.draw(HYPERNOVA_TYPE, buf.buildOrThrow());

        // ── Layer 2: Volumetric orb — real 3D sphere, ray-marched ─────
        drawGlowSphere(poseStack, px, py, pz, progress, alpha, stage);

        // ── Layer 3: Depth-tested radial rays (pseudo ray-tracing) ──────
        drawRadialRays(vm, cx, cy, cz, px, py, pz, dist, progress, alpha, stage);
    }

    // ════════════════════════════════════════════════════════════════
    //  Volumetric orb (real 3D sphere, ray-marched in the fragment shader)
    // ════════════════════════════════════════════════════════════════

    /** Sphere tessellation: latitude bands × longitude segments. */
    private static final int SPHERE_LAT = 14;
    private static final int SPHERE_LON = 24;

    /** Cached unit-sphere vertex positions, 4 per quad (QUADS mode). */
    private static float[][] unitSphereVerts;

    /** Lazily build the unit sphere tessellation (lat-long grid). */
    private static float[][] getUnitSphere() {
        if (unitSphereVerts != null) return unitSphereVerts;
        float[][] v = new float[SPHERE_LAT * SPHERE_LON * 4][3];
        int idx = 0;
        for (int i = 0; i < SPHERE_LAT; i++) {
            double t0 = Math.PI * i / SPHERE_LAT;
            double t1 = Math.PI * (i + 1) / SPHERE_LAT;
            for (int j = 0; j < SPHERE_LON; j++) {
                double p0 = 2.0 * Math.PI * j / SPHERE_LON;
                double p1 = 2.0 * Math.PI * (j + 1) / SPHERE_LON;
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
            (float)(Math.sin(theta) * Math.cos(phi)),
            (float) Math.cos(theta),
            (float)(Math.sin(theta) * Math.sin(phi))
        };
    }

    /**
     * Draw the volumetric orb: a real sphere mesh in world space whose
     * fragment shader ray-marches the view ray through an emissive
     * density field (bright core, soft limb, Beer-law self-absorption).
     *
     * <p>The pose stack is translated to the effect center so the mesh
     * is emitted in effect-local space; the shader recovers the sphere
     * center in view space from {@code ModelViewMat * vec4(0,0,0,1)}.
     * No billboarding — the ball has real volume and perspective from
     * every angle, and works when the camera is inside it.</p>
     *
     * <p>Stage envelopes are continuous across boundaries:
     * hypernova grows 1.5→7 blocks white-hot; afterglow shrinks 7→4
     * cooling to ember; fade-out dies with the fade smoothstep.</p>
     */
    private static void drawGlowSphere(PoseStack poseStack,
                                        float px, float py, float pz,
                                        float progress, float alpha, int stage) {
        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;

        // ── Stage envelopes (continuous across stage boundaries) ──
        float radius;   // world-space sphere radius (blocks)
        float heat;     // 1 = white-hot, 0 = cool ember
        float boost;    // intensity envelope
        if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            float expansion = 1.0f - (float)Math.pow(1.0f - progress, 2.4f);
            radius = 2.0f + expansion * 8.0f;     // 2 → 10
            heat   = 1.0f - progress * 0.48f;     // 1.0 → 0.52
            boost  = 0.75f + progress * 1.35f;    // 0.75 → 2.1
        } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
            float decay = 1.0f - progress;
            radius = 10.0f - progress * 5.5f;     // 10 → 4.5
            heat   = 0.52f - progress * 0.30f;    // 0.52 → 0.22
            boost  = 0.32f + decay * decay * 1.78f; // 2.1 → 0.32
        } else {
            // Fade-out: dies with the same reversed smoothstep as the
            // master alpha — the 0.3 floor matches afterglow's end.
            float fade = 1.0f - progress * progress * (3.0f - 2.0f * progress);
            radius = 1.0f + 3.5f * fade;          // 4.5 → 1
            heat   = 0.22f * fade;
            boost  = 0.32f * fade;
        }

        float intensity = boost * 2.2f * alpha;

        int rgba = packColor(progress, heat, intensity / 4f, alpha);

        // Emit the sphere in effect-local space (pose stack carries offset)
        poseStack.pushPose();
        poseStack.translate(px - cx, py - cy, pz - cz);
        Matrix4f svm = poseStack.last().pose();

        float[][] sphere = getUnitSphere();
        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
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

    // ════════════════════════════════════════════════════════════════
    //  Radial light rays (pseudo ray-tracing)
    // ════════════════════════════════════════════════════════════════

    /** Number of radial rays emitted per frame. */
    private static final int RAY_COUNT = 32;

    /** Ray length as a fraction of glow ball ambient radius. */
    private static final float RAY_LENGTH_FACTOR = 2.75f;

    /** Ray width at the tip (blocks). */
    private static final float RAY_TIP_WIDTH = 0.25f;

    /**
     * Emit radial light rays extending outward from the effect center.
     *
     * <p>Each ray is an elongated billboard quad oriented along a radial
     * direction in screen space.  Rays are depth-tested, so they terminate
     * naturally at block/entity surfaces — this is the "pseudo ray-tracing"
     * effect: light rays that correctly interact with 3D geometry.</p>
     *
     * <p>Rays are brightest during HYPERNOVA, dim during AFTERGLOW, and
     * nearly invisible during FADE_OUT.</p>
     */
    private static void drawRadialRays(Matrix4f vm,
                                        float cx, float cy, float cz,
                                        float px, float py, float pz,
                                        float dist, float progress, float alpha, int stage) {

        // Only emit rays during hypernova (they're dramatic and expensive-looking)
        // During afterglow: fewer, dimmer rays
        // During fade-out: skip entirely
        int rayCount;
        float rayAlpha;
        float rayIntensity;

        if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            rayCount = RAY_COUNT;
            float pulse = 0.82f + 0.18f * (float)Math.sin(progress * Math.PI * 9.0f);
            rayAlpha = alpha * (0.55f + progress * 0.45f) * pulse;
            rayIntensity = 1.0f + progress * 1.5f;
        } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
            // Continue from hypernova's end (alpha × 1.0, intensity 1.8) and
            // decay quadratically; count stays constant so rays fade
            // uniformly instead of vanishing in steps.
            rayCount = RAY_COUNT;
            float decay = 1.0f - progress;
            rayAlpha = alpha * decay * decay;
            rayIntensity = 2.5f * decay * decay;
        } else {
            // Fade-out: no rays (already decayed to zero during afterglow)
            return;
        }

        if (rayAlpha < 0.005f) return;

        // Ray geometry: origin at effect center, extending outward
        float rayLength = dist * RAY_LENGTH_FACTOR * (0.8f + progress * 0.5f);
        float rayWidth = RAY_TIP_WIDTH * (1.0f + progress * 0.5f);

        // Compute view-space origin
        float ox = px - cx;
        float oy = py - cy;
        float oz = pz - cz;

        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < rayCount; i++) {
            float angle = (float)(2.0 * Math.PI * i / rayCount);
            float screenDirX = (float)Math.cos(angle);
            float screenDirY = (float)Math.sin(angle);

            // Per-ray intensity variation (pseudo-random but deterministic)
            float variation = 0.65f + 0.35f * (float)Math.sin(i * 2.7 + 1.3);
            float rayI = rayIntensity * variation;

            // Distance along ray encoded in vertexColor.r
            // The ray shader uses UV.x for position along ray and UV.y for cross-section
            int rgba = packColor(
                    progress,       // R = time for shader
                    (float)i / rayCount, // G = ray index (0..1)
                    rayI / 4f,      // B = intensity (normalized)
                    rayAlpha);      // A = alpha

            emitRayQuad(buf, vm, ox, oy, oz,
                    screenDirX, screenDirY,
                    rayLength, rayWidth,
                    0f, 0f, 1f, 1f, rgba);
        }

        GeminiTesselator.draw(RAY_TYPE, buf.buildOrThrow());
    }

    // ════════════════════════════════════════════════════════════════
    //  Elongated billboard helper (for radial rays)
    // ════════════════════════════════════════════════════════════════

    /**
     * Emit a camera-facing quad elongated along a specific screen-space direction.
     *
     * <p>Unlike {@link #emitBillboard} which creates a square quad, this
     * creates a rectangle stretched along {@code (screenDirX, screenDirY)}
     * in screen space.  Used for radial light rays.</p>
     *
     * @param buf          target buffer builder
     * @param vm           view matrix (pose stack last pose)
     * @param ox,oy,oz     ray origin in view space (position - camera)
     * @param screenDirX   screen-space direction X component (normalized)
     * @param screenDirY   screen-space direction Y component (normalized)
     * @param length       total ray length in world units
     * @param width        ray cross-sectional width in world units
     * @param u0,v0,u1,v1  UV range for the quad
     * @param rgba         packed ARGB color
     */
    private static void emitRayQuad(BufferBuilder buf, Matrix4f vm,
                                     float ox, float oy, float oz,
                                     float screenDirX, float screenDirY,
                                     float length, float width,
                                     float u0, float v0, float u1, float v1,
                                     int rgba) {
        // Map screen-space direction to world-space offsets via camera basis
        float worldDX = CAM_RIGHT.x * screenDirX + CAM_UP.x * screenDirY;
        float worldDY = CAM_RIGHT.y * screenDirX + CAM_UP.y * screenDirY;
        float worldDZ = CAM_RIGHT.z * screenDirX + CAM_UP.z * screenDirY;

        // Perpendicular direction in screen space: rotate 90° clockwise
        // (screenDirX, screenDirY) → (-screenDirY, screenDirX)
        float perpDX = CAM_RIGHT.x * (-screenDirY) + CAM_UP.x * screenDirX;
        float perpDY = CAM_RIGHT.y * (-screenDirY) + CAM_UP.y * screenDirX;
        float perpDZ = CAM_RIGHT.z * (-screenDirY) + CAM_UP.z * screenDirX;

        float halfLen = length * 0.5f;
        float halfW = width * 0.5f;

        // Quad centered at origin, elongated along (worldDX, worldDY, worldDZ)
        // v0=(-dir,-perp)  v1=(-dir,+perp)  v2=(+dir,+perp)  v3=(+dir,-perp)
        buf.addVertex(vm, ox - worldDX * halfLen - perpDX * halfW,
                          oy - worldDY * halfLen - perpDY * halfW,
                          oz - worldDZ * halfLen - perpDZ * halfW)
                .setUv(u0, v0).setColor(rgba);
        buf.addVertex(vm, ox - worldDX * halfLen + perpDX * halfW,
                          oy - worldDY * halfLen + perpDY * halfW,
                          oz - worldDZ * halfLen + perpDZ * halfW)
                .setUv(u0, v1).setColor(rgba);
        buf.addVertex(vm, ox + worldDX * halfLen + perpDX * halfW,
                          oy + worldDY * halfLen + perpDY * halfW,
                          oz + worldDZ * halfLen + perpDZ * halfW)
                .setUv(u1, v1).setColor(rgba);
        buf.addVertex(vm, ox + worldDX * halfLen - perpDX * halfW,
                          oy + worldDY * halfLen - perpDY * halfW,
                          oz + worldDZ * halfLen - perpDZ * halfW)
                .setUv(u1, v0).setColor(rgba);
    }
}
