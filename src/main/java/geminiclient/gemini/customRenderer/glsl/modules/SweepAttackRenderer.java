package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GPU-accelerated Sweeping Attack VFX renderer.
 *
 * <h3>Render passes</h3>
 * <ol>
 *   <li>Arc — triple-layer SDF energy arc (blue/purple/white)</li>
 *   <li>Speed Lines — radiating ribbon lines</li>
 *   <li>Particles — camera-facing energy billboards</li>
 *   <li>Photon Ring — expanding rainbow ring</li>
 *   <li>Post — distortion + chromatic aberration</li>
 * </ol>
 *
 * <h3>Pipeline architecture</h3>
 * <pre>
 * SWEEP_ARC_PIPE      — arc (sweep_arc shader, QUADS, depth write)
 * SWEEP_RING_PIPE     — photon ring (sweep_arc shader, QUADS, no depth write)
 * SWEEP_PARTICLE_PIPE — particles + speed lines (sweep_particle shader, QUADS)
 * SWEEP_DISTORT_PIPE  — post distortion (sweep_post shader, fullscreen)
 * SWEEP_CHROMATIC_PIPE— post chromatic (sweep_post shader, fullscreen)
 * </pre>
 */
public final class SweepAttackRenderer {

    private SweepAttackRenderer() {}

    // ════════════════════════════════════════════════════════════════
    //  Pipelines
    // ════════════════════════════════════════════════════════════════

    /** Depth state with depth write enabled (for arc, particles). */
    private static final DepthStencilState DEPTH_WRITE =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1.0F, -1.0F);

    /** Depth state with depth write disabled (for ring overlay). */
    private static final DepthStencilState DEPTH_NO_WRITE =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F);

    private static final ColorTargetState ADDITIVE_BLEND = new ColorTargetState(new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE,
            SourceFactor.ONE, DestFactor.ZERO));

    private static final ColorTargetState TRANSLUCENT_BLEND = new ColorTargetState(BlendFunction.TRANSLUCENT);

    /** Arc pipeline — additive blend, depth-tested + writes depth. */
    public static final RenderPipeline SWEEP_ARC_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/sweep_arc"))
            .withVertexShader(getIdentifier("core/sweep_arc"))
            .withFragmentShader(getIdentifier("core/sweep_arc"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DEPTH_WRITE)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Particle + Speed line pipeline — additive blend, depth-tested + writes depth. */
    public static final RenderPipeline SWEEP_PARTICLE_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/sweep_particle"))
            .withVertexShader(getIdentifier("core/sweep_particle"))
            .withFragmentShader(getIdentifier("core/sweep_particle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DEPTH_WRITE)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Ring pipeline — additive blend, depth-tested but no depth write (overlay). */
    public static final RenderPipeline SWEEP_RING_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/sweep_ring"))
            .withVertexShader(getIdentifier("core/sweep_arc"))
            .withFragmentShader(getIdentifier("core/sweep_arc"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DEPTH_NO_WRITE)
            .withColorTargetState(ADDITIVE_BLEND)
            .withCull(false)
            .build();

    /** Distortion post-process pipeline. */
    public static final RenderPipeline SWEEP_DISTORT_PIPE = RenderPipeline.builder(
                    RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(getIdentifier("pipeline/sweep_distort"))
            .withVertexShader(getIdentifier("core/sweep_post"))
            .withFragmentShader(getIdentifier("core/sweep_post"))
            .withShaderDefine("SWEEP_DISTORT")
            .withUniform("SweepPostUniforms", UniformType.UNIFORM_BUFFER)
            .withSampler("SceneSampler")
            .withColorTargetState(TRANSLUCENT_BLEND)
            .withCull(false)
            .build();

    /** Chromatic aberration post-process pipeline. */
    public static final RenderPipeline SWEEP_CHROMATIC_PIPE = RenderPipeline.builder(
                    RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(getIdentifier("pipeline/sweep_chromatic"))
            .withVertexShader(getIdentifier("core/sweep_post"))
            .withFragmentShader(getIdentifier("core/sweep_post"))
            .withShaderDefine("SWEEP_CHROMATIC")
            .withUniform("SweepPostUniforms", UniformType.UNIFORM_BUFFER)
            .withSampler("SceneSampler")
            .withColorTargetState(TRANSLUCENT_BLEND)
            .withCull(false)
            .build();

    // ════════════════════════════════════════════════════════════════
    //  Uniform layout (std140, 64 bytes = 4 × vec4)
    // ════════════════════════════════════════════════════════════════

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()  // Params:    time, sweepProgress, effectProgress, intensity
            .putVec4()  // DirPack:   dirX, dirZ, arcStart, arcEnd
            .putVec4()  // RingPack:  ringRadius, particleAlpha, lightningAlpha, 0
            .putVec4()  // ColorPack: r, g, b, 0
            .get();

    private static final int POST_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()  // Params:   fbWidth, fbHeight, time, 0
            .putVec4()  // Strength: distortStr, chromaticStr, 0, 0
            .get();

    // ════════════════════════════════════════════════════════════════
    //  Resources
    // ════════════════════════════════════════════════════════════════

    private static GpuBuffer uniforms;
    private static GpuBuffer postUniforms;
    private static TextureTarget sceneCopy;

    // ════════════════════════════════════════════════════════════════
    //  Registration
    // ════════════════════════════════════════════════════════════════

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(SWEEP_ARC_PIPE);
        registry.accept(SWEEP_PARTICLE_PIPE);
        registry.accept(SWEEP_RING_PIPE);
        registry.accept(SWEEP_DISTORT_PIPE);
        registry.accept(SWEEP_CHROMATIC_PIPE);
    }

    // ════════════════════════════════════════════════════════════════
    //  Initialization
    // ════════════════════════════════════════════════════════════════

    private static void ensureInit() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(
                    () -> "SweepUniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    UNIFORM_SIZE);
        }
        if (postUniforms == null) {
            postUniforms = RenderSystem.getDevice().createBuffer(
                    () -> "SweepPostUniforms",
                    GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                    POST_UNIFORM_SIZE);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Camera vectors
    // ════════════════════════════════════════════════════════════════

    private static final Vector3f CAM_UP    = new Vector3f();
    private static final Vector3f CAM_RIGHT = new Vector3f();

    private static void updateCameraVectors() {
        Camera cam = mc.getEntityRenderDispatcher().camera;
        var rot = cam.rotation();
        CAM_UP.set(0, 1, 0);
        rot.transform(CAM_UP);
        CAM_RIGHT.set(1, 0, 0);
        rot.transform(CAM_RIGHT);
    }

    // ════════════════════════════════════════════════════════════════
    //  3D Drawing
    // ════════════════════════════════════════════════════════════════

    /**
     * Draw all 3D sweep attack layers.
     *
     * @param poseStack current pose stack
     * @param inst      the attack instance
     * @param nowMs     current time in milliseconds
     * @param intensity global intensity multiplier
     * @param colorR    arc color red (0-1)
     * @param colorG    arc color green (0-1)
     * @param colorB    arc color blue (0-1)
     */
    public static void draw(PoseStack poseStack, SweepAttackInstance inst,
                            long nowMs, float intensity,
                            float colorR, float colorG, float colorB) {
        ensureInit();
        updateCameraVectors();

        float sweepProg   = inst.sweepProgress(nowMs);
        float effectProg  = inst.effectProgress(nowMs);
        float particleA   = inst.particleAlpha(nowMs);
        float ringR       = inst.ringRadius(nowMs);
        float ringA       = inst.ringAlpha(nowMs);
        float lightningA  = inst.lightningAlpha(nowMs);
        float arcA        = inst.arcAlpha(nowMs);

        // Write shared uniforms
        float time = nowMs / 1000f;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(uniforms, false, true)) {
            Std140Builder b = Std140Builder.intoBuffer(view.data());
            b.putVec4(time, sweepProg, effectProg, intensity);
            b.putVec4(inst.dirX, inst.dirZ, inst.arcStart, inst.arcEnd);
            b.putVec4(ringR, particleA, lightningA, 0f);
            b.putVec4(colorR, colorG, colorB, 0f);
        }

        // Draw layers
        drawArc(poseStack, inst, sweepProg, intensity, arcA);
        drawSpeedLines(poseStack, inst, intensity, arcA);
        drawParticles(poseStack, inst, particleA, intensity);
        drawPhotonRing(poseStack, inst, ringR, ringA, intensity);
    }

    // ── Layer 1: Arc ─────────────────────────────────────────────

    private static final int ARC_SEGMENTS = 64;

    private static void drawArc(PoseStack poseStack, SweepAttackInstance inst,
                                float sweepProg, float intensity, float arcAlpha) {
        if (sweepProg < 0.001f || arcAlpha < 0.005f) return;

        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        float radius = 2.0f;
        float arcEnd = inst.arcStart + (inst.arcEnd - inst.arcStart) * sweepProg;

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < ARC_SEGMENTS; i++) {
            float t0 = (float) i / ARC_SEGMENTS;
            float t1 = (float) (i + 1) / ARC_SEGMENTS;
            float a0 = inst.arcStart + (arcEnd - inst.arcStart) * t0;
            float a1 = inst.arcStart + (arcEnd - inst.arcStart) * t1;

            float innerR = radius * 0.6f;
            float outerR = radius * 1.2f;

            // Glow multiplier — brighter in the middle of the arc
            float glow0 = 0.5f + 0.5f * (float) Math.sin(t0 * Math.PI);
            float glow1 = 0.5f + 0.5f * (float) Math.sin(t1 * Math.PI);

            int rgba0 = packColor(glow0, 0f, 0f, intensity * arcAlpha); // Color.g = 0 → arc mode
            int rgba1 = packColor(glow1, 0f, 0f, intensity * arcAlpha);

            // Inner-left, inner-right, outer-right, outer-left
            buf.addVertex(vm,
                    (float)(inst.x + Math.cos(a0) * innerR) - cx,
                    (float)(inst.y) - cy,
                    (float)(inst.z + Math.sin(a0) * innerR) - cz
            ).setUv(t0, 0f).setColor(rgba0);

            buf.addVertex(vm,
                    (float)(inst.x + Math.cos(a0) * outerR) - cx,
                    (float)(inst.y) - cy,
                    (float)(inst.z + Math.sin(a0) * outerR) - cz
            ).setUv(t0, 1f).setColor(rgba0);

            buf.addVertex(vm,
                    (float)(inst.x + Math.cos(a1) * outerR) - cx,
                    (float)(inst.y) - cy,
                    (float)(inst.z + Math.sin(a1) * outerR) - cz
            ).setUv(t1, 1f).setColor(rgba1);

            buf.addVertex(vm,
                    (float)(inst.x + Math.cos(a1) * innerR) - cx,
                    (float)(inst.y) - cy,
                    (float)(inst.z + Math.sin(a1) * innerR) - cz
            ).setUv(t1, 0f).setColor(rgba1);
        }

        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) { mesh.close(); return; }
        drawMesh(mesh, SWEEP_ARC_PIPE, System.currentTimeMillis() / 1000f);
    }

    // ── Layer 2: Speed Lines ─────────────────────────────────────

    private static final int SPEED_LINE_COUNT = 24;

    private static void drawSpeedLines(PoseStack poseStack, SweepAttackInstance inst,
                                       float intensity, float arcAlpha) {
        float sweepProg = inst.sweepProgress(System.currentTimeMillis());
        if (sweepProg < 0.01f || arcAlpha < 0.005f) return;

        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        float lineAlpha = intensity * sweepProg * 0.6f * arcAlpha;
        if (lineAlpha < 0.005f) return;

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float angleRange = inst.arcEnd - inst.arcStart;
        if (angleRange < 0) angleRange += (float)(2.0 * Math.PI);

        for (int i = 0; i < SPEED_LINE_COUNT; i++) {
            float t = (float) i / SPEED_LINE_COUNT;
            float angle = inst.arcStart + angleRange * t;

            // Line extends from arc outward
            float startR = 1.5f;
            float endR = 3.0f + (float) Math.sin(t * Math.PI * 4.0) * 0.5f;
            float width = 0.02f + (float) Math.sin(t * Math.PI * 3.0 + 1.0) * 0.01f;

            // Direction perpendicular to the radial direction
            float perpX = (float) -Math.sin(angle);
            float perpZ = (float) Math.cos(angle);

            // Radial direction
            float radX = (float) Math.cos(angle);
            float radZ = (float) Math.sin(angle);

            float sx = (float)(inst.x + radX * startR);
            float sz = (float)(inst.z + radZ * startR);
            float ex = (float)(inst.x + radX * endR);
            float ez = (float)(inst.z + radZ * endR);

            // Color.g = 1 → speed line mode
            int rgba = packColor(1f, 1f, 0f, lineAlpha);

            // Quad: start-left, start-right, end-right, end-left
            buf.addVertex(vm, sx - perpX * width - cx, (float) inst.y - cy, sz - perpZ * width - cz)
                    .setUv(0f, -1f).setColor(rgba);
            buf.addVertex(vm, sx + perpX * width - cx, (float) inst.y - cy, sz + perpZ * width - cz)
                    .setUv(0f, 1f).setColor(rgba);
            buf.addVertex(vm, ex + perpX * width - cx, (float) inst.y - cy, ez + perpZ * width - cz)
                    .setUv(1f, 1f).setColor(rgba);
            buf.addVertex(vm, ex - perpX * width - cx, (float) inst.y - cy, ez - perpZ * width - cz)
                    .setUv(1f, -1f).setColor(rgba);
        }

        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) { mesh.close(); return; }
        drawMesh(mesh, SWEEP_PARTICLE_PIPE, System.currentTimeMillis() / 1000f);
    }

    // ── Layer 3: Particles ───────────────────────────────────────

    private static void drawParticles(PoseStack poseStack, SweepAttackInstance inst,
                                      float particleAlpha, float intensity) {
        if (particleAlpha < 0.005f) return;

        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int drawn = 0;
        for (int i = 0; i < inst.particleCount; i++) {
            if (inst.particleLife[i] <= 0) continue;

            float lifeRatio = inst.particleLife[i] / Math.max(inst.particleMaxLife[i], 0.001f);
            float fadeAlpha = lifeRatio * lifeRatio * particleAlpha; // pow(life, 2)
            if (fadeAlpha < 0.005f) continue;

            float halfSize = inst.particleSize[i] * 0.5f;
            float rx = (float)(inst.particleX[i]) - cx;
            float ry = (float)(inst.particleY[i]) - cy;
            float rz = (float)(inst.particleZ[i]) - cz;

            // Billboard
            float rpx = CAM_RIGHT.x * halfSize;
            float rpy = CAM_RIGHT.y * halfSize;
            float rpz = CAM_RIGHT.z * halfSize;
            float upx = CAM_UP.x * halfSize;
            float upy = CAM_UP.y * halfSize;
            float upz = CAM_UP.z * halfSize;

            // Color.g = 0 → particle mode
            int rgba = packColor(lifeRatio, 0f, 0f, fadeAlpha * intensity);

            buf.addVertex(vm, rx - rpx - upx, ry - rpy - upy, rz - rpz - upz)
                    .setUv(0f, 0f).setColor(rgba);
            buf.addVertex(vm, rx - rpx + upx, ry - rpy + upy, rz - rpz + upz)
                    .setUv(0f, 1f).setColor(rgba);
            buf.addVertex(vm, rx + rpx + upx, ry + rpy + upy, rz + rpz + upz)
                    .setUv(1f, 1f).setColor(rgba);
            buf.addVertex(vm, rx + rpx - upx, ry + rpy - upy, rz + rpz - upz)
                    .setUv(1f, 0f).setColor(rgba);

            drawn++;
        }

        if (drawn == 0) return;

        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) { mesh.close(); return; }
        drawMesh(mesh, SWEEP_PARTICLE_PIPE, System.currentTimeMillis() / 1000f);
    }

    // ── Layer 4: Photon Ring ─────────────────────────────────────

    private static void drawPhotonRing(PoseStack poseStack, SweepAttackInstance inst,
                                        float ringRadius, float ringAlpha, float intensity) {
        if (ringRadius < 0.01f || intensity < 0.001f || ringAlpha < 0.005f) return;

        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        // Draw a quad centered at the attack position, sized to contain the ring
        float quadSize = ringRadius + 0.5f;
        float px = (float) inst.x - cx;
        float py = (float) inst.y - cy;
        float pz = (float) inst.z - cz;

        // Color.g = 1 → ring mode
        int rgba = packColor(intensity, 1f, 0f, ringAlpha);

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        // Horizontal billboard (XZ plane)
        buf.addVertex(vm, px - quadSize, py, pz - quadSize).setUv(0f, 0f).setColor(rgba);
        buf.addVertex(vm, px - quadSize, py, pz + quadSize).setUv(0f, 1f).setColor(rgba);
        buf.addVertex(vm, px + quadSize, py, pz + quadSize).setUv(1f, 1f).setColor(rgba);
        buf.addVertex(vm, px + quadSize, py, pz - quadSize).setUv(1f, 0f).setColor(rgba);

        MeshData mesh = buf.buildOrThrow();
        drawMesh(mesh, SWEEP_RING_PIPE, System.currentTimeMillis() / 1000f);
    }

    // ════════════════════════════════════════════════════════════════
    //  Mesh drawing
    // ════════════════════════════════════════════════════════════════

    private static void drawMesh(MeshData mesh, RenderPipeline pipeline, float time) {
        try {
            GpuBuffer vertices = pipeline.getVertexFormat()
                    .uploadImmediateVertexBuffer(mesh.vertexBuffer());

            GpuBuffer indices;
            VertexFormat.IndexType indexType;
            if (mesh.indexBuffer() == null) {
                var autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
                indices = autoIndices.getBuffer(mesh.drawState().indexCount());
                indexType = autoIndices.type();
            } else {
                indices = pipeline.getVertexFormat()
                        .uploadImmediateIndexBuffer(mesh.indexBuffer());
                indexType = mesh.drawState().indexType();
            }

            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(
                            new Matrix4f(),
                            new Vector4f(1f, 1f, 1f, 1f),
                            new Vector3f(time, 0f, 0f),
                            new Matrix4f());

            RenderTarget mainTarget = mc.getMainRenderTarget();
            GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
                    ? RenderSystem.outputColorTextureOverride
                    : mainTarget.getColorTextureView();
            GpuTextureView depthTexture = mainTarget.useDepth
                    ? (RenderSystem.outputDepthTextureOverride != null
                        ? RenderSystem.outputDepthTextureOverride
                        : mainTarget.getDepthTextureView())
                    : null;

            var encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "SweepAttack",
                    colorTexture,
                    OptionalInt.empty(),
                    depthTexture,
                    OptionalDouble.empty())) {

                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicTransforms);

                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, indexType);
                pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
            }
        } finally {
            mesh.close();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Post-processing
    // ════════════════════════════════════════════════════════════════

    /**
     * Run post-processing passes (distortion + chromatic aberration).
     * Call from MixinGameRenderer after all 3D rendering is complete.
     *
     * @param distortionStr  distortion strength (0=none)
     * @param chromaticStr   chromatic aberration strength (0=none)
     */
    public static void processPost(float distortionStr, float chromaticStr) {
        if (distortionStr <= 0.001f && chromaticStr <= 0.001f) return;

        ensureInit();
        RenderTarget fb = mc.getMainRenderTarget();
        if (fb.getColorTexture() == null || fb.getColorTextureView() == null) return;

        int fbW = mc.getWindow().getWidth();
        int fbH = mc.getWindow().getHeight();

        if (sceneCopy == null) sceneCopy = new TextureTarget("SweepScene", fbW, fbH, false);
        if (sceneCopy.width != fbW || sceneCopy.height != fbH) sceneCopy.resize(fbW, fbH);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        float time = System.currentTimeMillis() / 1000f;

        // Write post uniforms
        try (GpuBuffer.MappedView view = encoder.mapBuffer(postUniforms, false, true)) {
            Std140Builder b = Std140Builder.intoBuffer(view.data());
            b.putVec4(fbW, fbH, time, 0f);
            b.putVec4(distortionStr, chromaticStr, 0f, 0f);
        }

        // Copy scene
        encoder.copyTextureToTexture(
                fb.getColorTexture(), sceneCopy.getColorTexture(),
                0, 0, 0, 0, 0, fbW, fbH);

        GpuTextureView sceneView = sceneCopy.getColorTextureView();

        // Distortion pass
        if (distortionStr > 0.001f) {
            runPostPass(encoder, SWEEP_DISTORT_PIPE, sceneView, "Sweep Distort");
        }

        // Chromatic pass
        if (chromaticStr > 0.001f) {
            runPostPass(encoder, SWEEP_CHROMATIC_PIPE, sceneView, "Sweep Chromatic");
        }
    }

    private static void runPostPass(CommandEncoder encoder, RenderPipeline pipe,
                                     GpuTextureView sceneSrc, String label) {
        RenderTarget fb = mc.getMainRenderTarget();
        try (RenderPass pass = encoder.createRenderPass(
                () -> label,
                fb.getColorTextureView(),
                OptionalInt.empty())) {
            pass.setPipeline(pipe);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("SweepPostUniforms", postUniforms);
            pass.bindTexture("SceneSampler", sceneSrc,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0, 3);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Cleanup
    // ════════════════════════════════════════════════════════════════

    public static void destroy() {
        if (uniforms != null) { uniforms.close(); uniforms = null; }
        if (postUniforms != null) { postUniforms.close(); postUniforms = null; }
        sceneCopy = null;
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════

    private static int packColor(float r, float g, float b, float a) {
        int ir = (int) (clamp01(r) * 255f);
        int ig = (int) (clamp01(g) * 255f);
        int ib = (int) (clamp01(b) * 255f);
        int ia = (int) (clamp01(a) * 255f);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
