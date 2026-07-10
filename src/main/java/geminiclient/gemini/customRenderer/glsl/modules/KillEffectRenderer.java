package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
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
 *   <li>{@link #GLOW_BALL_PIPE} — Volumetric glow sphere (depth-tested, 4 layers)</li>
 *   <li>{@link #RAY_PIPE} — Radial light rays for pseudo ray-tracing (depth-tested)</li>
 * </ul>
 *
 * <h3>Vertex encoding (POSITION_TEX_COLOR, all pipelines)</h3>
 * <ul>
 *   <li>UV.x/y — billboard quad corner (0..1), maps to effect-local coordinates in FS</li>
 *   <li>Color.r — time progress 0→1 (within current stage)</li>
 *   <li>Color.g — stage identifier (1–7)</li>
 *   <li>Color.b — effect-specific intensity parameter</li>
 *   <li>Color.a — master alpha</li>
 * </ul>
 */
public final class KillEffectRenderer {

    private KillEffectRenderer() {}

    // ════════════════════════════════════════════════════════════════
    //  Shared depth state
    // ════════════════════════════════════════════════════════════════

    private static final DepthStencilState EFFECT_DEPTH =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F);

    /** No depth testing — always pass (for flash / explosion overlays). */
    private static final DepthStencilState NO_DEPTH =
            new DepthStencilState(CompareOp.ALWAYS_PASS, false, -1.0F, -1.0F);

    /** Blending for additive glow effects. */
    private static final ColorTargetState ADDITIVE_BLEND = new ColorTargetState(new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE,
            SourceFactor.ONE, DestFactor.ZERO));

    /** Blending for alpha-blended effects. */
    private static final ColorTargetState ALPHA_BLEND = new ColorTargetState(new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.ONE, DestFactor.ZERO));

    // ════════════════════════════════════════════════════════════════
    //  Pipelines
    // ════════════════════════════════════════════════════════════════

    /** Magic circle + tower — additive gold glow. */
    public static final RenderPipeline MAGIC_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_magic"))
            .withVertexShader(getIdentifier("core/kill_effect_magic"))
            .withFragmentShader(getIdentifier("core/kill_effect_magic"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Black hole: event horizon + photon ring + gravitational lensing. */
    public static final RenderPipeline BLACK_HOLE_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_hole"))
            .withVertexShader(getIdentifier("core/kill_effect_hole"))
            .withFragmentShader(getIdentifier("core/kill_effect_hole"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Accretion particles + general particle rendering. */
    public static final RenderPipeline PARTICLE_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_particle"))
            .withVertexShader(getIdentifier("core/kill_effect_particle"))
            .withFragmentShader(getIdentifier("core/kill_effect_particle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(EFFECT_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Hypernova explosion + flash: no depth test — always visible. */
    public static final RenderPipeline HYPERNOVA_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_nova"))
            .withVertexShader(getIdentifier("core/kill_effect_nova"))
            .withFragmentShader(getIdentifier("core/kill_effect_nova"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(NO_DEPTH)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /**
     * Volumetric glow ball: depth-tested multi-layer sphere with pseudo ray-tracing.
     *
     * <p>Uses depth testing ({@link #EFFECT_DEPTH}) so block/entity geometry
     * correctly occludes the glow.  Emitted in 4 concentric layers:
     * dense core → mid halo → outer aura → ambient sphere.
     * Each layer renders a different fragment shader path via
     * {@code vertexColor.g} layer selector.</p>
     */
    public static final RenderPipeline GLOW_BALL_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_glow"))
            .withVertexShader(getIdentifier("core/kill_effect_glow"))
            .withFragmentShader(getIdentifier("core/kill_effect_glow"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
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
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/kill_effect_ray"))
            .withVertexShader(getIdentifier("core/kill_effect_ray"))
            .withFragmentShader(getIdentifier("core/kill_effect_ray"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
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
    private static final RenderType GLOW_BALL_TYPE   = createRenderType("gemini_kill_glow", GLOW_BALL_PIPE);
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
        registry.accept(GLOW_BALL_PIPE);
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

    /** Pack float RGBA into int ARGB. */
    private static int packColor(float r, float g, float b, float a) {
        int ir = (int)(r * 255f);
        int ig = (int)(g * 255f);
        int ib = (int)(b * 255f);
        int ia = (int)(a * 255f);
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

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        if (stage == KillEffectInstance.STAGE_MAGIC_CIRCLE) {
            // Single magic circle on the ground, facing upward (horizontal)
            float size = baseSize * (0.5f + progress * 0.5f); // grow from 50% to 100%
            float circleAlpha = alpha * (progress < 0.2f ? progress / 0.2f : 1f); // fade in
            float yOffset = 0.02f; // slightly above ground

            int rgba = packColor(progress, 1f, 0.8f, circleAlpha);

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

                int rgba = packColor(progress, shaderStage, 0.8f, layerAlpha);

                float rx = px - cx, ry = py + yOffset - cy, rz = pz - cz;
                buf.addVertex(vm, rx - size, ry, rz - size).setUv(0f, 0f).setColor(rgba);
                buf.addVertex(vm, rx - size, ry, rz + size).setUv(0f, 1f).setColor(rgba);
                buf.addVertex(vm, rx + size, ry, rz + size).setUv(1f, 1f).setColor(rgba);
                buf.addVertex(vm, rx + size, ry, rz - size).setUv(1f, 0f).setColor(rgba);
            }
        }

        MAGIC_TYPE.draw(buf.buildOrThrow());
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

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int rgba = packColor(progress, (float)stage, brightness, alpha);
        float halfSize = holeSize * 2f; // billboard covers area around hole

        emitBillboard(buf, vm, cx, cy, cz, px, py, pz, halfSize,
                0f, 0f, 1f, 1f, rgba);

        BLACK_HOLE_TYPE.draw(buf.buildOrThrow());
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

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

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

        PARTICLE_TYPE.draw(buf.buildOrThrow());
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
     *       always visible overlay) for the base flash + shockwave pattern</li>
     *   <li>{@link #GLOW_BALL_PIPE} — depth-tested multi-layer volumetric
     *       glow sphere (dense core → mid halo → outer aura → ambient sphere)
     *       that properly occludes behind blocks and entities</li>
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

        // ── Layer 1: Full-screen flash overlay (no depth, always visible) ──
        float lightSize = dist * 0.55f * 0.45f;
        float flashTime = 0.35f + 0.04f * (float)Math.sin(progress * Math.PI * 3.0);
        float flashIntensity = 3.5f + (float)Math.sin(progress * Math.PI * 2.5f) * 0.5f;

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int flashRgba = packColor(flashTime, 0f, flashIntensity, alpha);
        emitBillboard(buf, vm, cx, cy, cz, px, py, pz, lightSize,
                0f, 0f, 1f, 1f, flashRgba);

        HYPERNOVA_TYPE.draw(buf.buildOrThrow());

        // ── Layer 2: Depth-tested volumetric glow ball ──────────────────
        drawGlowBall(vm, cx, cy, cz, px, py, pz, dist, progress, alpha, stage);

        // ── Layer 3: Depth-tested radial rays (pseudo ray-tracing) ──────
        drawRadialRays(vm, cx, cy, cz, px, py, pz, dist, progress, alpha, stage);
    }

    // ════════════════════════════════════════════════════════════════
    //  Volumetric glow ball
    // ════════════════════════════════════════════════════════════════

    /** Number of concentric glow layers emitted per frame. */
    private static final int GLOW_LAYERS = 4;

    /**
     * Emit multi-layer depth-tested glow ball billboards.
     *
     * <p>Four concentric layers at different scales create a volumetric
     * self-illuminated sphere.  Each layer uses a different shader path
     * selected by {@code vertexColor.g}:
     * <ul>
     *   <li>0.0 — dense core: ultra-bright, tight gaussian, white-hot</li>
     *   <li>0.5 — mid glow: intense halo + Fresnel ring + ray streaks</li>
     *   <li>1.0 — outer glow: soft volumetric aura, wide falloff</li>
     *   <li>1.5 — ambient sphere: far-reaching subtle ambient light</li>
     * </ul>
     *
     * <p>Depth testing ensures correct occlusion: glow disappears behind
     * blocks but shines through transparent surfaces and around corners.</p>
     */
    private static void drawGlowBall(Matrix4f vm,
                                      float cx, float cy, float cz,
                                      float px, float py, float pz,
                                      float dist, float progress, float alpha, int stage) {

        // Layer presets: {scale, layerId, intensityScale}
        // Scales multiply by dist to maintain visual size at any distance
        float[][] layerPresets = {
            // {sizeScale, layerId,  intensityScale}
            { 0.08f, 0.0f, 2.5f },  // dense core
            { 0.22f, 0.5f, 1.6f },  // mid glow
            { 0.55f, 1.0f, 0.7f },  // outer aura
            { 1.30f, 1.5f, 0.2f },  // ambient sphere
        };

        // Stage-specific intensity modulation
        float stageBoost = 1.0f;
        if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            // Hypernova: rapid intensity ramp for explosive feel
            stageBoost = 0.3f + progress * 1.2f;
        } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
            // Afterglow: decaying pulse
            float decay = 1.0f - progress;
            stageBoost = 0.15f + decay * decay * 0.85f;
        } else {
            // Fade-out: smooth dissolve
            stageBoost = 0.08f * alpha;
        }

        for (int i = 0; i < GLOW_LAYERS; i++) {
            float[] preset = layerPresets[i];
            float sizeScale = preset[0];
            float layerId = preset[1];
            float intensityScale = preset[2];

            float halfSize = dist * sizeScale * (1.0f + progress * 0.3f);
            float layerIntensity = intensityScale * stageBoost * alpha;
            float layerAlpha = alpha * (i == 0 ? 1.0f :
                                        i == 1 ? 0.85f :
                                        i == 2 ? 0.55f : 0.25f);

            // Pulse the core layer for organic feel
            if (i == 0) {
                layerIntensity *= 1.0f + 0.08f * (float)Math.sin(progress * Math.PI * 25.0);
            }

            BufferBuilder buf = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            int rgba = packColor(
                    progress,        // R = time progress for shader
                    layerId,         // G = layer selector
                    layerIntensity,  // B = intensity
                    layerAlpha);     // A = alpha

            emitBillboard(buf, vm, cx, cy, cz, px, py, pz, halfSize,
                    0f, 0f, 1f, 1f, rgba);

            GLOW_BALL_TYPE.draw(buf.buildOrThrow());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Radial light rays (pseudo ray-tracing)
    // ════════════════════════════════════════════════════════════════

    /** Number of radial rays emitted per frame. */
    private static final int RAY_COUNT = 20;

    /** Ray length as a fraction of glow ball ambient radius. */
    private static final float RAY_LENGTH_FACTOR = 2.2f;

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
            rayAlpha = alpha * (0.3f + progress * 0.7f);
            rayIntensity = 0.6f + progress * 1.2f;
        } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
            // Fewer, fading rays during afterglow
            rayCount = RAY_COUNT / 2;
            float decay = 1.0f - progress;
            rayAlpha = alpha * decay * decay * 0.4f;
            rayIntensity = decay * 0.5f;
        } else {
            // Fade-out: no rays
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

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

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
                    rayI,           // B = intensity
                    rayAlpha);      // A = alpha

            emitRayQuad(buf, vm, ox, oy, oz,
                    screenDirX, screenDirY,
                    rayLength, rayWidth,
                    0f, 0f, 1f, 1f, rgba);
        }

        RAY_TYPE.draw(buf.buildOrThrow());
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
