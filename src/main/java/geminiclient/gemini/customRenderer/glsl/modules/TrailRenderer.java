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
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import geminiclient.gemini.modules.impl.visual.trail.TrailPoint;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/** GPU renderer for the procedural Trail module. */
public final class TrailRenderer {

    public record TrailConfig(
            int primaryColor, int secondaryColor, int accentColor,
            int style, int colorMode, int orientation, int quality,
            float width, float tailWidth, float waveAmount, float waveFrequency,
            float opacity, float brightness, float glow, float coreWidth,
            float edgeGlow, float distortion, float detailScale,
            float sparkleDensity, float pulseStrength, float flowSpeed,
            boolean softBlend, boolean throughWalls
    ) {}

    private TrailRenderer() {}

    private static final DepthStencilState DEPTH =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F);
    private static final DepthStencilState XRAY =
            new DepthStencilState(CompareOp.ALWAYS_PASS, false, -1.0F, -1.0F);
    private static final ColorTargetState ADDITIVE = new ColorTargetState(new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO));
    private static final ColorTargetState SOFT = new ColorTargetState(new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA));

    public static final RenderPipeline TRAIL_PIPE = createPipeline("trail_ribbon", DEPTH, ADDITIVE);
    private static final RenderPipeline TRAIL_SOFT = createPipeline("trail_ribbon_soft", DEPTH, SOFT);
    private static final RenderPipeline TRAIL_XRAY = createPipeline("trail_ribbon_xray", XRAY, ADDITIVE);
    private static final RenderPipeline TRAIL_SOFT_XRAY =
            createPipeline("trail_ribbon_soft_xray", XRAY, SOFT);

    private static RenderPipeline createPipeline(
            String location, DepthStencilState depth, ColorTargetState blend) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(getIdentifier("pipeline/" + location))
                .withVertexShader(getIdentifier("core/trail_ribbon"))
                .withFragmentShader(getIdentifier("core/trail_ribbon"))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthStencilState(depth)
                .withColorTargetState(blend)
                .withCull(false)
                .build();
    }

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(TRAIL_PIPE);
        registry.accept(TRAIL_SOFT);
        registry.accept(TRAIL_XRAY);
        registry.accept(TRAIL_SOFT_XRAY);
    }

    private static final Vector3f CAM_UP = new Vector3f();
    private static final Vector3f CAM_RIGHT = new Vector3f();
    private static final Vector3f CAM_LOOK = new Vector3f();

    private static void updateCameraVectors(Camera camera) {
        Quaternionf rotation = camera.rotation();
        CAM_UP.set(0, 1, 0);
        rotation.transform(CAM_UP);
        CAM_RIGHT.set(1, 0, 0);
        rotation.transform(CAM_RIGHT);
        CAM_LOOK.set(0, 0, -1);
        rotation.transform(CAM_LOOK);
    }

    public static void draw(PoseStack poseStack, List<TrailPoint> points, TrailConfig config) {
        if (points.size() < 2 || config.width <= 0f || config.opacity < 0.005f) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;
        updateCameraVectors(camera);

        final int size = points.size();
        final float time = (System.currentTimeMillis() % 1_000_000L) / 1000f;
        final Vector3d[] centers = new Vector3d[size];
        final Vector3d[] sides = new Vector3d[size];

        for (int i = 0; i < size; i++) {
            float u = i / (float) (size - 1);
            Vector3d center = new Vector3d(points.get(i).pos.x, points.get(i).pos.y, points.get(i).pos.z);
            if (config.waveAmount > 0.0001f) {
                float envelope = (float) Math.sin(Math.PI * u);
                float wave = (float) Math.sin(
                        time * config.flowSpeed * 2.0f + u * config.waveFrequency * Math.PI * 2.0f);
                center.add(CAM_UP.x * wave * config.waveAmount * envelope,
                        CAM_UP.y * wave * config.waveAmount * envelope,
                        CAM_UP.z * wave * config.waveAmount * envelope);
            }
            centers[i] = center;
        }

        for (int i = 0; i < size; i++) {
            Vector3d before = centers[Math.max(0, i - 1)];
            Vector3d after = centers[Math.min(size - 1, i + 1)];
            Vector3d tangent = new Vector3d(after).sub(before);
            if (tangent.lengthSquared() < 1.0e-10) tangent.set(CAM_RIGHT.x, CAM_RIGHT.y, CAM_RIGHT.z);
            else tangent.normalize();

            Vector3d side;
            if (config.orientation == 1) {
                side = new Vector3d(0.0, 1.0, 0.0);
            } else if (config.orientation == 2) {
                side = tangent.cross(new Vector3d(0.0, 1.0, 0.0), new Vector3d());
            } else {
                side = tangent.cross(new Vector3d(CAM_LOOK.x, CAM_LOOK.y, CAM_LOOK.z), new Vector3d());
            }
            if (side.lengthSquared() < 1.0e-10) side.set(CAM_RIGHT.x, CAM_RIGHT.y, CAM_RIGHT.z);
            else side.normalize();

            float u = i / (float) (size - 1);
            float taper = 1.0f + (config.tailWidth - 1.0f) * smoothstep(u);
            sides[i] = side.mul(config.width * taper);
        }

        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < size - 1; i++) {
            TrailPoint a = points.get(i);
            TrailPoint b = points.get(i + 1);
            Vector3d ca = centers[i];
            Vector3d cb = centers[i + 1];
            Vector3d sa = sides[i];
            Vector3d sb = sides[i + 1];
            float u0 = i / (float) (size - 1);
            float u1 = (i + 1) / (float) (size - 1);
            float segmentLength = (float) ca.distance(cb);
            float motion = Math.min(segmentLength * 18f, 1f);
            float segmentAlpha = (a.alpha() + b.alpha()) * 0.5f * config.opacity;
            int colorA = packColor(1f - a.alpha(), motion, config.brightness / 3f, segmentAlpha);
            int colorB = packColor(1f - b.alpha(), motion, config.brightness / 3f, segmentAlpha);

            vertex(buffer, matrix, ca, sa, -1, cameraX, cameraY, cameraZ, u0, -1f, colorA);
            vertex(buffer, matrix, ca, sa,  1, cameraX, cameraY, cameraZ, u0,  1f, colorA);
            vertex(buffer, matrix, cb, sb,  1, cameraX, cameraY, cameraZ, u1,  1f, colorB);
            vertex(buffer, matrix, cb, sb, -1, cameraX, cameraY, cameraZ, u1, -1f, colorB);
        }

        MeshData mesh = buffer.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) {
            mesh.close();
            return;
        }

        RenderPipeline pipeline = config.softBlend
                ? (config.throughWalls ? TRAIL_SOFT_XRAY : TRAIL_SOFT)
                : (config.throughWalls ? TRAIL_XRAY : TRAIL_PIPE);
        drawMesh(mesh, pipeline, time, config);
    }

    private static void vertex(
            BufferBuilder buffer, Matrix4f matrix, Vector3d center, Vector3d side, int sign,
            float cameraX, float cameraY, float cameraZ, float u, float v, int color) {
        buffer.addVertex(matrix,
                        (float) (center.x + side.x * sign) - cameraX,
                        (float) (center.y + side.y * sign) - cameraY,
                        (float) (center.z + side.z * sign) - cameraZ)
                .setUv(u, v)
                .setColor(color);
    }

    private static void drawMesh(
            MeshData mesh, RenderPipeline pipeline, float time, TrailConfig config) {
        try {
            GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
            GpuBuffer indices;
            VertexFormat.IndexType indexType;
            if (mesh.indexBuffer() == null) {
                var autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
                indices = autoIndices.getBuffer(mesh.drawState().indexCount());
                indexType = autoIndices.type();
            } else {
                indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
                indexType = mesh.drawState().indexType();
            }

            Vector4f primary = color(config.primaryColor);
            Vector4f secondary = color(config.secondaryColor);
            Vector4f accent = color(config.accentColor);
            Matrix4f material = new Matrix4f();
            material.m00(secondary.x).m01(secondary.y).m02(secondary.z).m03(config.glow);
            material.m10(accent.x).m11(accent.y).m12(accent.z).m13(config.coreWidth);
            material.m20(config.style).m21(config.colorMode).m22(config.flowSpeed).m23(config.detailScale);
            material.m30(config.distortion).m31(config.sparkleDensity)
                    .m32(config.edgeGlow).m33(config.pulseStrength);

            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                    new Matrix4f(),
                    primary,
                    new Vector3f(time, config.quality, 0f),
                    material);

            RenderTarget target = mc.getMainRenderTarget();
            GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
                    ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
            GpuTextureView depthTexture = target.useDepth
                    ? (RenderSystem.outputDepthTextureOverride != null
                    ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
                    : null;

            var encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "TrailRibbon", colorTexture, OptionalInt.empty(),
                    depthTexture, OptionalDouble.empty())) {
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

    private static Vector4f color(int argb) {
        return new Vector4f(
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                ((argb >>> 24) & 0xFF) / 255f);
    }

    private static int packColor(float r, float g, float b, float a) {
        int ir = Math.round(clamp01(r) * 255f);
        int ig = Math.round(clamp01(g) * 255f);
        int ib = Math.round(clamp01(b) * 255f);
        int ia = Math.round(clamp01(a) * 255f);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static float smoothstep(float value) {
        float t = clamp01(value);
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
