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
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import geminiclient.gemini.modules.impl.visual.trail.TrailPoint;
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
 * GPU-accelerated Ribbon Trail renderer.
 *
 * <p>Manual pipeline rendering (like MagicHaloRenderer).  Time is passed
 * via {@code DynamicTransforms.ModelOffset.x} so the fragment shader can
 * animate noise, rainbow, and glow.</p>
 */
public final class TrailRenderer {

    private TrailRenderer() {}

    // ── Pipeline ─────────────────────────────────────────────────

    private static final DepthStencilState TRAIL_DEPTH =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F);

    private static final ColorTargetState TRAIL_BLEND = new ColorTargetState(new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE,
            SourceFactor.ONE, DestFactor.ZERO));

    public static final RenderPipeline TRAIL_PIPE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/trail_ribbon"))
            .withVertexShader(getIdentifier("core/trail_ribbon"))
            .withFragmentShader(getIdentifier("core/trail_ribbon"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(TRAIL_DEPTH)
            .withColorTargetState(TRAIL_BLEND)
            .withCull(false)
            .build();

    // ── Registration ──────────────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(TRAIL_PIPE);
    }

    // ══════════════════════════════════════════════════════════════
    //  Drawing
    // ══════════════════════════════════════════════════════════════

    private static final Vector3f CAM_UP    = new Vector3f();
    private static final Vector3f CAM_RIGHT = new Vector3f();
    private static final Vector3f CAM_LOOK  = new Vector3f();

    private static void updateCameraVectors() {
        Camera cam = mc.getEntityRenderDispatcher().camera;
        Quaternionf rot = cam.rotation();
        CAM_UP.set(0, 1, 0);
        rot.transform(CAM_UP);
        CAM_RIGHT.set(1, 0, 0);
        rot.transform(CAM_RIGHT);
        CAM_LOOK.set(0, 0, -1);
        rot.transform(CAM_LOOK);
    }

    public static void draw(PoseStack poseStack,
                            List<TrailPoint> points,
                            float width, float intensity, float alpha) {
        if (points.size() < 2 || width <= 0f || alpha < 0.005f) return;

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int n = points.size();

        for (int i = 0; i < n - 1; i++) {
            TrailPoint a = points.get(i);
            TrailPoint b = points.get(i + 1);

            float u0 = (float) i / (float) (n - 1);
            float u1 = (float) (i + 1) / (float) (n - 1);

            float ageA = 1f - a.alpha();
            float ageB = 1f - b.alpha();

            float dx = (float) (b.pos.x - a.pos.x);
            float dy = (float) (b.pos.y - a.pos.y);
            float dz = (float) (b.pos.z - a.pos.z);
            float segLen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float motionBlend = Math.min(segLen * 20f, 1f);

            // ── Ribbon cross-direction: perpendicular to movement AND camera look ──
            // Fix: check for NaN — if dir is parallel to camLook, cross = (0,0,0)
            Vector3d dir = new Vector3d(dx, dy, dz);
            double dirLen = dir.length();
            if (dirLen < 1e-8) {
                dir.set(CAM_RIGHT.x, CAM_RIGHT.y, CAM_RIGHT.z);
            } else {
                dir.mul(1.0 / dirLen);
            }

            Vector3d look = new Vector3d(CAM_LOOK.x, CAM_LOOK.y, CAM_LOOK.z);
            Vector3d cross = dir.cross(look, new Vector3d());
            double crossLen = cross.length();

            float rx, ry, rz;
            if (crossLen < 1e-8) {
                // dir ≈ look → fall back to camera right
                rx = CAM_RIGHT.x * width;
                ry = CAM_RIGHT.y * width;
                rz = CAM_RIGHT.z * width;
            } else {
                cross.mul(1.0 / crossLen);
                rx = (float) cross.x * width;
                ry = (float) cross.y * width;
                rz = (float) cross.z * width;
            }

            float wax = (float) a.pos.x - cx;
            float way = (float) a.pos.y - cy;
            float waz = (float) a.pos.z - cz;
            float wbx = (float) b.pos.x - cx;
            float wby = (float) b.pos.y - cy;
            float wbz = (float) b.pos.z - cz;

            float segAlpha = (a.alpha() * 0.5f + b.alpha() * 0.5f) * alpha;

            int rgbaA = packColor(ageA, motionBlend, intensity, segAlpha);
            int rgbaB = packColor(ageB, motionBlend, intensity, segAlpha);

            // a-left, a-right, b-right, b-left
            buf.addVertex(vm, wax - rx, way - ry, waz - rz).setUv(u0, -1f).setColor(rgbaA);
            buf.addVertex(vm, wax + rx, way + ry, waz + rz).setUv(u0,  1f).setColor(rgbaA);
            buf.addVertex(vm, wbx + rx, wby + ry, wbz + rz).setUv(u1,  1f).setColor(rgbaB);
            buf.addVertex(vm, wbx - rx, wby - ry, wbz - rz).setUv(u1, -1f).setColor(rgbaB);
        }

        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) { mesh.close(); return; }

        drawMesh(mesh, System.currentTimeMillis() / 1000f);
    }

    // ══════════════════════════════════════════════════════════════
    //  Manual mesh drawing (like MagicHaloRenderer)
    // ══════════════════════════════════════════════════════════════

    private static void drawMesh(MeshData mesh, float time) {
        try {
            GpuBuffer vertices = TRAIL_PIPE.getVertexFormat()
                    .uploadImmediateVertexBuffer(mesh.vertexBuffer());

            GpuBuffer indices;
            VertexFormat.IndexType indexType;
            if (mesh.indexBuffer() == null) {
                var autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
                indices = autoIndices.getBuffer(mesh.drawState().indexCount());
                indexType = autoIndices.type();
            } else {
                indices = TRAIL_PIPE.getVertexFormat()
                        .uploadImmediateIndexBuffer(mesh.indexBuffer());
                indexType = mesh.drawState().indexType();
            }

            // Identity transforms — positions are camera-relative
            // ModelOffset.x carries animation time for the fragment shader.
            // It is NOT added to Position in the vertex shader, so the
            // large epoch-second value (~1.7e9) doesn't offset geometry.
            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(
                            new Matrix4f(),                          // ModelViewMat = identity
                            new Vector4f(1f, 1f, 1f, 1f),           // ColorModulator
                            new Vector3f(time, 0f, 0f),              // ModelOffset.x = time
                            new Matrix4f());                          // TextureMat = identity

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
                    () -> "TrailRibbon",
                    colorTexture,
                    OptionalInt.empty(),
                    depthTexture,
                    OptionalDouble.empty())) {

                pass.setPipeline(TRAIL_PIPE);
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
