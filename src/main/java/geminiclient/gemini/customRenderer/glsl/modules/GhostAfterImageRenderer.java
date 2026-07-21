package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.IndexType;

import geminiclient.gemini.customRenderer.GeminiTesselator;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;

import com.mojang.blaze3d.PrimitiveTopology;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import geminiclient.gemini.modules.impl.visual.ghost.GhostFrame;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.*;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Optional;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * Ghost AfterImage renderer — SDF silhouette billboards.
 *
 * <p>Manual pipeline rendering.  Tint colour is packed into vertex color
 * (GBA channels) and fade into R.  ModelOffset.x carries time.</p>
 */
public final class GhostAfterImageRenderer {

    private GhostAfterImageRenderer() {}

    // ── Pipeline ─────────────────────────────────────────────────

    private static final DepthStencilState GHOST_DEPTH =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 1.0F);

    private static final ColorTargetState GHOST_BLEND = new ColorTargetState(new BlendFunction(
            BlendFactor.SRC_ALPHA, BlendFactor.ONE,
            BlendFactor.ONE, BlendFactor.ZERO));

    public static final RenderPipeline GHOST_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/ghost_afterimage"))
            .withVertexShader(getIdentifier("core/ghost_afterimage"))
            .withFragmentShader(getIdentifier("core/ghost_afterimage"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(GHOST_DEPTH)
            .withColorTargetState(GHOST_BLEND)
            .withCull(false)
            .build();

    // ── Registration ──────────────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(GHOST_PIPE);
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

    public static void drawGhosts(PoseStack poseStack,
                                  List<GhostFrame> ghosts,
                                  float tintR, float tintG, float tintB) {
        if (ghosts.isEmpty()) return;

        updateCameraVectors();
        var cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        var vm = poseStack.last().pose();

        var buf = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (GhostFrame ghost : ghosts) {
            float fade = ghost.alpha();
            if (fade < 0.005f) continue;

            float halfW = 0.7f;
            float halfH = 0.95f;

            float gx = (float) ghost.x - cx;
            float gy = (float) ghost.y + 0.9f - cy;
            float gz = (float) ghost.z - cz;

            float rpx = CAM_RIGHT.x * halfW;
            float rpy = CAM_RIGHT.y * halfW;
            float rpz = CAM_RIGHT.z * halfW;
            float upx = CAM_UP.x * halfH;
            float upy = CAM_UP.y * halfH;
            float upz = CAM_UP.z * halfH;

            // Color: R=fade, G=tintR, B=tintG, A=tintB
            int rgba = packColor(fade, tintR, tintG, tintB);

            buf.addVertex(vm, gx - rpx - upx, gy - rpy - upy, gz - rpz - upz)
                    .setUv(0f, 0f).setColor(rgba);
            buf.addVertex(vm, gx - rpx + upx, gy - rpy + upy, gz - rpz + upz)
                    .setUv(0f, 1f).setColor(rgba);
            buf.addVertex(vm, gx + rpx + upx, gy + rpy + upy, gz + rpz + upz)
                    .setUv(1f, 1f).setColor(rgba);
            buf.addVertex(vm, gx + rpx - upx, gy + rpy - upy, gz + rpz - upz)
                    .setUv(1f, 0f).setColor(rgba);
        }

        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) { mesh.close(); return; }

        drawMesh(mesh, System.currentTimeMillis() / 1000f);
    }

    // ══════════════════════════════════════════════════════════════
    //  Manual mesh draw
    // ══════════════════════════════════════════════════════════════

    private static void drawMesh(MeshData mesh, float time) {
        try {
            var vertices = GeminiTesselator.uploadVertexBuffer(GHOST_PIPE.getVertexFormatBinding(0), mesh.vertexBuffer());

            GpuBuffer indices;
            IndexType indexType;
            if (mesh.indexBuffer() == null) {
                var autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
                indices = autoIndices.getBuffer(mesh.drawState().indexCount());
                indexType = autoIndices.type();
            } else {
                indices = GeminiTesselator.uploadIndexBuffer(GHOST_PIPE.getVertexFormatBinding(0), mesh.indexBuffer());
                indexType = mesh.drawState().indexType();
            }

            var dynamicTransforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(
                            new Matrix4f(),
                            new Vector4f(1f, 1f, 1f, 1f),
                            new Vector3f(time, 0f, 0f),       // ModelOffset.x = time
                            new Matrix4f());

            var mainTarget = mc.gameRenderer.mainRenderTarget();
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
                    () -> "GhostAfterImage",
                    colorTexture,
                    Optional.empty(),
                    depthTexture,
                    OptionalDouble.empty())) {

                pass.setPipeline(GHOST_PIPE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicTransforms);

                pass.setVertexBuffer(0, vertices.slice());
                pass.setIndexBuffer(indices, indexType);
                pass.drawIndexed(mesh.drawState().indexCount(), 1, 0, 0, 0);
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
