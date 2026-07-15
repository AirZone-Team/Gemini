package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.*;

import java.lang.Math;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * Instanced particle renderer — all particles batched into one buffer.
 *
 * <p>Manual pipeline rendering (like MagicHaloRenderer).</p>
 */
public final class InstancedParticleRenderer {

    private InstancedParticleRenderer() {}

    // ── Pipeline ─────────────────────────────────────────────────

    private static final DepthStencilState PARTICLE_DEPTH =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F);

    private static final ColorTargetState PARTICLE_BLEND = new ColorTargetState(new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE,
            SourceFactor.ONE, DestFactor.ZERO));

    public static final RenderPipeline INSTANCED_PARTICLE_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/particle_instanced"))
            .withVertexShader(getIdentifier("core/particle_instanced"))
            .withFragmentShader(getIdentifier("core/particle_instanced"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(PARTICLE_DEPTH)
            .withColorTargetState(PARTICLE_BLEND)
            .withCull(false)
            .build();

    // ── Registration ──────────────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(INSTANCED_PARTICLE_PIPE);
    }

    // ══════════════════════════════════════════════════════════════
    //  Drawing
    // ══════════════════════════════════════════════════════════════

    private static final Vector3f CAM_UP    = new Vector3f();
    private static final Vector3f CAM_RIGHT = new Vector3f();

    private static void updateCameraVectors() {
        var cam = mc.getEntityRenderDispatcher().camera;
        var rot = cam.rotation();
        CAM_UP.set(0, 1, 0);
        rot.transform(CAM_UP);
        CAM_RIGHT.set(1, 0, 0);
        rot.transform(CAM_RIGHT);
    }

    public static final int MAX_PARTICLES = 6000;

    public static void draw(PoseStack poseStack,
                            List<ParticleData> particles,
                            float intensity) {
        if (particles.isEmpty()) return;

        updateCameraVectors();
        var cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        var vm = poseStack.last().pose();

        var buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int drawn = 0;
        for (ParticleData p : particles) {
            if (!p.alive) continue;
            if (drawn >= MAX_PARTICLES) break;

            float fadeAlpha = p.alpha();
            if (fadeAlpha < 0.005f) continue;

            float halfSize = p.size * 0.5f;
            float rx = p.x - cx;
            float ry = p.y - cy;
            float rz = p.z - cz;

            float rpx = CAM_RIGHT.x * halfSize;
            float rpy = CAM_RIGHT.y * halfSize;
            float rpz = CAM_RIGHT.z * halfSize;
            float upx = CAM_UP.x * halfSize;
            float upy = CAM_UP.y * halfSize;
            float upz = CAM_UP.z * halfSize;

            float typeEncoded = p.type * 0.2f; // 0.0, 0.2, 0.4, 0.6, 0.8

            int rgba = packColor(
                    p.age / Math.max(p.life, 0.001f),
                    typeEncoded,
                    intensity * p.a,
                    fadeAlpha * p.a);

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

        drawMesh(mesh, System.currentTimeMillis() / 1000f);
    }

    // ══════════════════════════════════════════════════════════════
    //  Manual mesh draw
    // ══════════════════════════════════════════════════════════════

    private static void drawMesh(MeshData mesh, float time) {
        try {
            var vertices = INSTANCED_PARTICLE_PIPE.getVertexFormat()
                    .uploadImmediateVertexBuffer(mesh.vertexBuffer());

            GpuBuffer indices;
            VertexFormat.IndexType indexType;
            if (mesh.indexBuffer() == null) {
                var autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
                indices = autoIndices.getBuffer(mesh.drawState().indexCount());
                indexType = autoIndices.type();
            } else {
                indices = INSTANCED_PARTICLE_PIPE.getVertexFormat()
                        .uploadImmediateIndexBuffer(mesh.indexBuffer());
                indexType = mesh.drawState().indexType();
            }

            var dynamicTransforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(
                            new Matrix4f(),
                            new Vector4f(1f, 1f, 1f, 1f),
                            new Vector3f(time, 0f, 0f),       // ModelOffset.x = time
                            new Matrix4f());

            var mainTarget = mc.getMainRenderTarget();
            var colorTexture = RenderSystem.outputColorTextureOverride != null
                    ? RenderSystem.outputColorTextureOverride
                    : mainTarget.getColorTextureView();
            var depthTexture = mainTarget.useDepth
                    ? (RenderSystem.outputDepthTextureOverride != null
                        ? RenderSystem.outputDepthTextureOverride
                        : mainTarget.getDepthTextureView())
                    : null;

            var encoder = RenderSystem.getDevice().createCommandEncoder();
            try (var pass = encoder.createRenderPass(
                    () -> "InstancedParticle",
                    colorTexture,
                    OptionalInt.empty(),
                    depthTexture,
                    OptionalDouble.empty())) {

                pass.setPipeline(INSTANCED_PARTICLE_PIPE);
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

    // ── Helpers ───────────────────────────────────────────────────

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
