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
 *   <li>{@link #MAGIC_PIPE} — Summoning array: ground sigil, tower tiers and
 *       armillary cage, one world-space plane per quad (additive, depth-tested)</li>
 *   <li>{@link #BLACK_HOLE_PIPE} — Event horizon + photon ring + lens distortion</li>
 *   <li>{@link #PARTICLE_PIPE} — Accretion particles (additive, depth-tested)</li>
 *   <li>{@link #HYPERNOVA_PIPE} — Explosion flash overlay (no depth, always visible)</li>
 *   <li>{@link #ORB_PIPE} — Volumetric 3D orb (real sphere mesh, ray-marched)</li>
 *   <li>{@link #RAY_PIPE} — Radial light rays for pseudo ray-tracing (depth-tested)</li>
 * </ul>
 *
 * <h3>Vertex encoding (POSITION_TEX_COLOR, all pipelines)</h3>
 * <ul>
 *   <li>UV.x/y — quad corner (0..1). Camera-facing billboards map it to the
 *       effect-local coordinates the shader reads; a summoning-array plane maps
 *       it to its own in-plane axes instead</li>
 *   <li>Color.r — time progress 0→1 (nova, hole, ray) or the summon script's
 *       reveal (magic)</li>
 *   <li>Color.g — normalized identifier: stage/8 (hole), layer/4 (glow),
 *       mode flag 0/0.5/1 (nova, particle), ray index 0..1 (ray), or the
 *       wrapping animation phase (magic)</li>
 *   <li>Color.b — intensity/4 (hole, glow, nova, ray, magic) — magic carries
 *       HDR energy here, so the ×4 rescale happens in the shader</li>
 *   <li>Color.a — master alpha</li>
 * </ul>
 *
 * <p>Every channel is a byte. Nothing that must turn monotonically — an angle,
 * a spin, a long ramp — may be sent through one: it would advance in 1.4° steps
 * and stutter. Those go in the geometry instead; see {@link #emitDisc}.</p>
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
    //  Summoning array (magic circle + tower + armillary cage)
    // ════════════════════════════════════════════════════════════════

    /** Period of the array's looping internal animation (line boil + pulses). */
    private static final long MAGIC_CYCLE_MS = 3600L;

    /** Stacked tiers between the ground sigil and the hole. */
    private static final int MAGIC_TIERS = 9;

    /** Great tilted rings enclosing the tower — the armillary cage. */
    private static final int MAGIC_CAGE_RINGS = 3;

    /** Ground shock rings sweeping outward at any one moment. */
    private static final int MAGIC_WAVES = 3;

    /** Filaments streaming from the array into the hole while it is swallowed. */
    private static final int MAGIC_INFLOW = 3;

    /** Seconds one shock ring takes to cross the array. */
    private static final float MAGIC_WAVE_SEC = 2.6f;

    /** How far the hole opens above the ground sigil's own plane. */
    private static final float MAGIC_HOLE_LIFT = KillEffectInstance.HOLE_VISUAL_LIFT - 0.03f;

    private static final float TAU = (float) (2.0 * Math.PI);

    /** Scratch for {@link ThaumaturgySigilGeometry#axes}. */
    private static final float[] DISC_AXES = new float[6];
    private static final Vector3f DISC_E1 = new Vector3f();
    private static final Vector3f DISC_E2 = new Vector3f();

    /**
     * Draw the summoning array for an effect instance.
     *
     * <p>Every plane is the same shader disc, distinguished only by where the
     * renderer puts it and how far it is allowed to read the summon script:</p>
     * <ul>
     *   <li>the ground sigil and its echo — the full design, drawn ring by ring
     *       by two pens running in opposite directions</li>
     *   <li>nine tower tiers — a pinched column that counter-rotates by tier,
     *       the upper ones capped at the rune band so the stack stays legible</li>
     *   <li>an armillary cage of three great tilted rings, precessing round the
     *       tower so the array has real volume from every angle</li>
     *   <li>shock rings sweeping outward across the ground</li>
     * </ul>
     *
     * <h3>How the hole is born</h3>
     * <p>The array is swallowed plane by plane, top tier first and the ground
     * sigil last, on a front that sweeps down over {@link
     * KillEffectInstance#magicDrain}. Each plane contracts onto the axis,
     * accelerates its turn, flares hot and dies as the front takes it — so at
     * most two planes are ever mid-swallow. That is deliberate: lifting the
     * whole stack onto the hole at once parked nine rings plus the sigil inside
     * one block of each other, and the additive blow-out they produced is where
     * the horizon was supposed to appear. It opened inside a white bulb and
     * read as nothing at all.</p>
     *
     * @param intensityMul global intensity × AoE merge multiplier (1 = default);
     *                     scales the master alpha of every emitted quad.
     */
    public static void drawMagic(PoseStack poseStack, KillEffectInstance inst, long nowMs,
                                  float intensityMul) {
        if (!inst.shouldRenderMagic(nowMs)) return;

        // Cross-stage transition alpha, scaled by the global intensity setting
        float alpha = inst.magicTransitionAlpha(nowMs) * intensityMul;
        if (alpha < 0.001f) return;

        float reveal   = inst.magicReveal(nowMs);
        float spin     = inst.magicSpin(nowMs);
        float rise     = inst.magicRise(nowMs);
        float collapse = inst.magicCollapse(nowMs);
        float drain    = inst.magicDrain(nowMs);
        float surge    = inst.magicSurge(nowMs);
        // Bounded, wrapping: the shader only ever takes sin/cos of this, so a
        // byte channel carries it without stepping.
        float phase    = (nowMs % MAGIC_CYCLE_MS) / (float) MAGIC_CYCLE_MS;

        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        float px = (float) inst.position.x;
        float py = (float) inst.position.y;
        float pz = (float) inst.position.z;
        float x = px - cx;
        float z = pz - cz;
        float groundY = py + 0.03f - cy;
        float holeY = groundY + MAGIC_HOLE_LIFT;

        // Distance-compensated size: the array stays legible at engagement range
        // instead of shrinking to a dot, but is capped so it cannot grow into a
        // continent at long range.
        float dx = px - cx, dy = py - cy, dz = pz - cz;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.01f;
        float base = Math.min(2.6f * (1f + dist * 0.085f), 34f);

        // HDR energy rides the bloom chain: the array brightens into the moment
        // the hole tears open.
        float energy = 1.0f + 1.25f * surge;

        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        // ── Ground sigil: the last thing to go ──────────────────────
        float sigilEaten = swallowed(drain, SLOT_SIGIL);
        float echoEaten  = swallowed(drain, SLOT_ECHO);
        emitDisc(buf, vm, x, mix(groundY, holeY, sigilEaten * sigilEaten), z,
                spin + sigilEaten * 2.4f, 0f,
                base * (1f - 0.94f * sigilEaten) * (1f - 0.22f * collapse),
                reveal, phase, energy * (1f + 1.6f * sigilEaten),
                alpha * (1f - sigilEaten) * (1f - sigilEaten));
        // Echo: half a rune cell around and a hand's width lower, so its rim
        // lines interleave with the primary disc's instead of hiding under them.
        // Deliberately faint: it lies inside the sigil's own footprint, and two
        // copies of the same engraving sum until the gaps between the strokes
        // fill in and the ground becomes one white disc.
        emitDisc(buf, vm, x, mix(groundY - 0.10f, holeY, echoEaten * echoEaten), z,
                spin + TAU / 128f + echoEaten * 2.4f, 0f,
                base * 0.955f * (1f - 0.94f * echoEaten) * (1f - 0.22f * collapse),
                planeReveal(reveal, 0.05f, KillEffectInstance.SCRIPT_RUNE_END),
                phase, energy * 0.5f * (1f + 1.6f * echoEaten),
                alpha * 0.17f * (1f - echoEaten) * (1f - echoEaten));

        if (rise > 0.001f) {
            // The array tightens as it gives way, but keeps its shape: the
            // swallow front moves each plane into the hole in turn, so squashing
            // the whole column flat here would double up the motion.
            float tighten = 1f - 0.15f * collapse;
            drawTowerTiers(buf, vm, x, groundY, z, holeY, base,
                    reveal, spin, rise, drain, surge, phase, energy, alpha, tighten);
            drawArmillaryCage(buf, vm, x, groundY, z, holeY, base,
                    reveal, spin, rise, drain, phase, energy, alpha, tighten);
            if (drain < 0.55f) {
                drawShockWaves(buf, vm, x, groundY, z, base, nowMs,
                        spin, rise, phase, energy, alpha);
            }
            if (drain > 0.001f) {
                drawInflowRings(buf, vm, x, groundY, z, holeY, base, drain,
                        phase, alpha, intensityMul);
            }
        }

        GeminiTesselator.draw(MAGIC_TYPE, buf.buildOrThrow());
    }

    // ── Swallow schedule ───────────────────────────────────────────
    //
    // Plane slots run in the order the hole eats them: the top tier first, the
    // ground sigil last. Two adjacent planes overlap in their swallow windows,
    // which is what keeps the front looking like a front.

    private static final int MAGIC_SLOTS = MAGIC_TIERS + MAGIC_CAGE_RINGS + 2;
    private static final int SLOT_ECHO = MAGIC_TIERS + MAGIC_CAGE_RINGS;
    private static final int SLOT_SIGIL = MAGIC_TIERS + MAGIC_CAGE_RINGS + 1;

    /** Fraction of one plane's own swallow window, out of the whole drain. */
    private static final float SWALLOW_W = 0.22f;

    /** How far plane {@code slot} has been eaten, 0 = untouched, 1 = gone. */
    private static float swallowed(float drain, int slot) {
        float start = slot * (1f - SWALLOW_W) / (MAGIC_SLOTS - 1);
        return Math.clamp((drain - start) / SWALLOW_W, 0f, 1f);
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** The stacked tiers: a vortex of counter-rotating discs above the sigil. */
    private static void drawTowerTiers(BufferBuilder buf, Matrix4f vm,
                                       float x, float groundY, float z, float holeY,
                                       float base, float reveal, float spin, float rise,
                                       float drain, float surge, float phase, float energy,
                                       float alpha, float tighten) {
        for (int i = 0; i < MAGIC_TIERS; i++) {
            float u = i / (float) (MAGIC_TIERS - 1);      // 0 = lowest tier
            // Staggered rise: the column builds from the ground upward.
            float t = Math.clamp((rise - i * 0.055f) / 0.34f, 0f, 1f);
            if (t <= 0f) continue;
            float grown = 1f - (1f - t) * (1f - t) * (1f - t);

            // Eaten from the top down
            float eaten = swallowed(drain, MAGIC_TIERS - 1 - i);
            if (eaten >= 1f) continue;

            // Pinched profile — full at the sigil, narrow at the waist — so the
            // stack reads as a vessel rather than a cone.
            float profile = 1.02f - 0.58f * (float) Math.sin(u * Math.PI * 0.62);
            float radius = base * profile * (1f - 0.94f * eaten) * tighten;
            float height = mix(base * (0.10f + 1.62f * u) * grown,
                    holeY - groundY, eaten * eaten);

            // Counter-rotation alternates by tier and the upper ones turn faster,
            // so the stack shears like a vortex instead of spinning as one disc.
            // Being eaten spins it up further: angular momentum, not decoration.
            float yaw = spin * ((i & 1) == 0 ? 1f : -1f) * (0.55f + 0.85f * u)
                    + i * 0.42f + eaten * 3.2f;
            // Bounded nod only — `phase` rides a byte channel.
            float pitch = 0.05f * (float) Math.sin(phase * TAU + i * 1.1f);

            // Upper tiers carry fewer figures: the lattice and the seals would
            // only tangle when stacked nine deep.
            float ceiling = u < 0.34f ? KillEffectInstance.SCRIPT_LATTICE_END
                    : KillEffectInstance.SCRIPT_RUNE_END;

            emitDisc(buf, vm, x, groundY + height, z,
                    yaw, pitch, radius,
                    planeReveal(reveal, i * 0.012f, ceiling),
                    phase,
                    // Flares as it crosses the horizon, then is gone with it.
                    energy * (1.1f + 0.45f * (1f - u) + 0.6f * surge) * (1f + 1.8f * eaten),
                    // The stack brightens as it rises. The low tiers sit inside
                    // the sigil's own footprint when the camera looks down on it,
                    // and a second copy of the same engraving there does not add
                    // detail — it fills the gaps between the sigil's strokes and
                    // the ground reads as one white disc. The upper tiers own
                    // empty sky, so they can carry the light instead.
                    alpha * grown * (0.16f + 0.30f * u) * (1f - eaten) * (1f - eaten));
        }
    }

    /**
     * Three great rings nodding about the tower's axis. Because each one's
     * reveal stops at the rim boundary, they read as nested ellipses rather
     * than as nine more copies of the sigil — and they are what give the array
     * height and parallax when seen from the side.
     */
    private static void drawArmillaryCage(BufferBuilder buf, Matrix4f vm,
                                          float x, float groundY, float z, float holeY,
                                          float base, float reveal, float spin, float rise,
                                          float drain, float phase, float energy, float alpha,
                                          float tighten) {
        for (int k = 0; k < MAGIC_CAGE_RINGS; k++) {
            float eaten = swallowed(drain, MAGIC_TIERS + k);
            if (eaten >= 1f) continue;

            float yaw = spin * 0.38f + k * (TAU / MAGIC_CAGE_RINGS) + eaten * 2.6f;
            // Bounded breathing on the nod, never a monotonic turn through phase.
            float pitch = ((k & 1) == 0 ? 1f : -1f)
                    * (0.62f + 0.10f * (float) Math.sin(phase * TAU + k));
            float radius = base * (1.30f - 0.06f * k) * (1f - 0.95f * eaten) * tighten;
            float height = mix(base * (0.80f + 0.17f * k) * rise,
                    holeY - groundY, eaten * eaten);

            emitDisc(buf, vm, x, groundY + height, z,
                    yaw, pitch, radius,
                    planeReveal(reveal, 0.02f * k, KillEffectInstance.SCRIPT_RIM_END),
                    phase, energy * 0.85f * (1f + 1.5f * eaten),
                    alpha * 0.34f * rise * (1f - eaten) * (1f - eaten));
        }
    }

    /** Energy sweeping outward across the ground, three rings in a rolling cycle. */
    private static void drawShockWaves(BufferBuilder buf, Matrix4f vm,
                                       float x, float groundY, float z, float base,
                                       long nowMs, float spin, float rise,
                                       float phase, float energy, float alpha) {
        float clock = (nowMs % (long) (MAGIC_WAVE_SEC * 1000f)) / (MAGIC_WAVE_SEC * 1000f);
        for (int w = 0; w < MAGIC_WAVES; w++) {
            float t = (clock + w / (float) MAGIC_WAVES) % 1f;
            float radius = base * (0.30f + 1.55f * t);
            // Swell in at the sigil's rim and die at the outer edge. A ring that
            // is brightest at birth spends its whole life inside the disc it is
            // meant to frame, and that light lands on the engraving.
            float swell = (float) Math.sin(t * Math.PI);
            emitDisc(buf, vm, x, groundY + 0.05f + t * base * 0.24f, z,
                    spin * (0.25f + 0.5f * t), 0f, radius,
                    KillEffectInstance.SCRIPT_RIM_END,
                    phase, energy * 0.7f,
                    alpha * rise * swell * swell * 0.34f);
        }
    }

    /**
     * Filaments of the array's own light running down the last span into the
     * horizon. These start where the swallow front is and land on the hole, so
     * the birth reads as a fall rather than as a fade.
     */
    private static void drawInflowRings(BufferBuilder buf, Matrix4f vm,
                                        float x, float groundY, float z, float holeY,
                                        float base, float drain, float phase,
                                        float alpha, float intensityMul) {
        float holeSpan = Math.min(base * 0.10f, MAGIC_HOLE_LIFT);
        for (int k = 0; k < MAGIC_INFLOW; k++) {
            // Staggered starts across the drain so the stream never stops.
            float t = Math.clamp((drain - k * 0.16f) / 0.5f, 0f, 1f);
            if (t <= 0f || t >= 1f) continue;
            float ease = t * t * (3f - 2f * t);
            float radius = mix(base * (1.75f - 0.2f * k), holeSpan, ease);
            float y = mix(groundY + base * (0.05f + 0.3f * k), holeY, ease);
            float a = alpha * (float) Math.sin(t * Math.PI) * 0.55f * intensityMul;

            emitDisc(buf, vm, x, y, z, drain * 5.5f + k, 0f, radius,
                    KillEffectInstance.SCRIPT_RIM_END,
                    phase, 1.4f + 2.6f * ease, a);
        }
    }

    /**
     * A plane's own share of the summon script: it starts drawing at
     * {@code delay} and never passes {@code ceiling}, so a plane capped at a
     * script boundary shows exactly the figures that live below it.
     */
    private static float planeReveal(float reveal, float delay, float ceiling) {
        return Math.clamp((reveal - delay) / (1f - delay), 0f, 1f) * ceiling;
    }

    /**
     * Emit one plane of the array: a disc lying in the world XZ plane, spun by
     * {@code yaw} about the vertical and nodded by {@code pitch} about its own
     * turning axis, so the lean precesses as the plane turns.
     *
     * <p>The turn is in the geometry on purpose. A vertex-colour channel is a
     * byte, so an angle sent through it advances in 1.4° steps and the array
     * would stutter instead of sweeping.</p>
     */
    private static void emitDisc(BufferBuilder buf, Matrix4f vm,
                                 float x, float y, float z,
                                 float yaw, float pitch, float half,
                                 float reveal, float phase, float energy, float alpha) {
        if (alpha < 0.004f || half <= 0f) return;
        ThaumaturgySigilGeometry.axes(yaw, pitch, DISC_AXES);
        DISC_E1.set(DISC_AXES[0], DISC_AXES[1], DISC_AXES[2]);
        DISC_E2.set(DISC_AXES[3], DISC_AXES[4], DISC_AXES[5]);
        emitOrientedQuad(buf, vm, x, y, z, DISC_E1, DISC_E2, half,
                packColor(reveal, phase, energy / 4f, alpha));
    }

    /**
     * Unit quad centred on {@code x,y,z}, spanning {@code ±half} along two
     * orthonormal world-space axes. UV.x runs along {@code e1}, UV.y along
     * {@code e2}, which is how the disc shader gets its own plane coordinates.
     */
    private static void emitOrientedQuad(BufferBuilder buf, Matrix4f vm,
                                         float x, float y, float z,
                                         Vector3f e1, Vector3f e2,
                                         float half, int rgba) {
        float ax = e1.x * half, ay = e1.y * half, az = e1.z * half;
        float bx = e2.x * half, by = e2.y * half, bz = e2.z * half;
        buf.addVertex(vm, x - ax - bx, y - ay - by, z - az - bz).setUv(0f, 0f).setColor(rgba);
        buf.addVertex(vm, x - ax + bx, y - ay + by, z - az + bz).setUv(0f, 1f).setColor(rgba);
        buf.addVertex(vm, x + ax + bx, y + ay + by, z + az + bz).setUv(1f, 1f).setColor(rgba);
        buf.addVertex(vm, x + ax - bx, y + ay - by, z + az - bz).setUv(1f, 0f).setColor(rgba);
    }

    // ════════════════════════════════════════════════════════════════
    //  Black Hole drawing
    // ════════════════════════════════════════════════════════════════

    /**
     * Draw the depth-tested half of the black hole: photon ring, photon-sphere
     * glow, lensed starfield. The opaque shadow, the lensing of the real scene
     * and the accretion disk are the {@link KillEffectPostProcessor} BLACK_HOLE
     * pass's job; this quad exists so blocks in front of the hole hide it.
     *
     * <p>During the tower→BH crossfade the hole appears as a small faint dot
     * that grows and brightens as the array is driven into it.</p>
     *
     * @param intensityMul global intensity × AoE merge multiplier (1 = default);
     *                     scales the master alpha so the Intensity setting and
     *                     merged kills actually reach the black hole visuals.
     */
    public static void drawBlackHole(PoseStack poseStack, KillEffectInstance inst, long nowMs,
                                      float intensityMul) {
        if (!inst.shouldRenderBlackHole(nowMs)) return;

        int stage = inst.currentStage(nowMs);
        float progress = inst.stageProgress(nowMs);

        // Cross-stage transition alpha (intensity applied at the final clamp —
        // the forming branch below force-ramps alpha, so scaling here is lost)
        float alpha = inst.blackHoleTransitionAlpha(nowMs);
        if (alpha < 0.001f) return;

        // ── Pre-appearance during tower→BH transition ────────────────
        float brightness = 1f;

        if (stage == KillEffectInstance.STAGE_MAGIC_TOWER) {
            brightness = 0.3f + ((progress - (1f - KillEffectInstance.XFADE_TOWER))
                    / KillEffectInstance.XFADE_TOWER) * 0.7f;

        } else if (stage == KillEffectInstance.STAGE_BLACK_HOLE) {
            // During the crossfade, brighten rapidly
            if (progress < KillEffectInstance.XFADE_HOLE) {
                float t = progress / KillEffectInstance.XFADE_HOLE;
                brightness = 0.5f + t * 0.5f;
            } else {
                brightness = 1f;
            }
            alpha = progress < 0.15f ? Math.max(alpha, progress / 0.15f) : Math.max(alpha, 1f);

        } else if (stage == KillEffectInstance.STAGE_COLLAPSE) {
            brightness = 1f + progress * 3f;
        }

        alpha = Math.clamp(alpha * intensityMul, 0f, 1f);
        if (alpha < 0.001f) return;

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        float px = (float) inst.position.x;
        float py = (float) inst.position.y + KillEffectInstance.HOLE_VISUAL_LIFT;
        float pz = (float) inst.position.z;

        BufferBuilder buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int rgba = packColor(progress, stage / 8f, brightness / 4f, alpha);
        // KillEffectInstance owns the size curve so the screen-space shadow in
        // KillEffectPostProcessor covers exactly the same disk.
        float halfSize = inst.blackHoleSizeWorld(nowMs) * KillEffectInstance.HOLE_UV_SPAN;

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
     *
     * @param intensityMul global intensity × AoE merge multiplier (1 = default);
     *                     scales the master alpha of every emitted quad.
     */
    public static void drawHypernova(PoseStack poseStack, KillEffectInstance inst, long nowMs,
                                      float intensityMul) {
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

        alpha *= intensityMul;
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
        float lightSize = dist * 0.21f;
        float flashTime = 0.33f + 0.045f * (float)Math.sin(progress * Math.PI * 4.0);
        // The white core rides over the wider fireball only during detonation.
        float flashIntensity = 1.8f + (float)Math.sin(progress * Math.PI * 3.5f)
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
        // The nova shader rescales this channel by ×4, like every other one that
        // carries HDR. Packing it raw left the whole fireball four times hotter
        // than intended, which ACES then flattened into a screen-wide white sheet.
        int novaRgba = packColor(novaProgress, 0.5f, novaIntensity / 4f, alpha);
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
