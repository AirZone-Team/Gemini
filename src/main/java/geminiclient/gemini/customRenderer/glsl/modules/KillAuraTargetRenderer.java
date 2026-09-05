package geminiclient.gemini.customRenderer.glsl.modules;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import geminiclient.gemini.customRenderer.GeminiTesselator;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GPU renderer for the Sorcery Array — the KillAura target indicator.
 *
 * <p>Follows the MagicHalo architecture: a dedicated std140 uniform block
 * carries the shared theme/motion state at full float precision, while
 * per-target scalars (health, acquire flash, variety seed, material page)
 * ride in one flat integer vertex varying. All targets are drawn in a single
 * RenderPass from one vertex buffer.</p>
 *
 * <p>Two procedural material pages are drawn by
 * {@code core/killaura_target}: page 0 is the world-aligned ground sigil,
 * page 1 the camera-facing aura ring. The palette, dash dialects and
 * distortion mirror MagicHalo so both effects share one visual language.</p>
 */
public final class KillAuraTargetRenderer {

    /** Horizontal summoning circle laid on the ground under the target. */
    public static final int MATERIAL_SIGIL = 0;
    /** Camera-facing energy ring behind the target's body. */
    public static final int MATERIAL_RING = 1;

    private static final int TARGET_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4().putVec4()
            .get();

    private static GpuBuffer targetUniforms;

    private static final VertexFormat TARGET_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .build();

    private static final DepthStencilState TARGET_DEPTH =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 1.0F);

    private static final ColorTargetState TARGET_BLEND = new ColorTargetState(new BlendFunction(
            BlendFactor.SRC_ALPHA, BlendFactor.ONE,
            BlendFactor.ONE, BlendFactor.ZERO));

    public static final RenderPipeline TARGET_PIPELINE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/killaura_target"))
            .withVertexShader(getIdentifier("core/killaura_target"))
            .withFragmentShader(getIdentifier("core/killaura_target"))
            .withBindGroupLayout(GeminiRenderPipelines.uniform("TargetUniforms"))
            .withVertexBinding(0, TARGET_FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(TARGET_DEPTH)
            .withColorTargetState(TARGET_BLEND)
            .withCull(false)
            .build();

    private KillAuraTargetRenderer() {}

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(TARGET_PIPELINE);
    }

    /**
     * One indicator quad on one target.
     *
     * @param argb         primary tint; MC 26.2 {@code setColor} takes ARGB
     * @param health       0..1, drives the aura ring health arc and Health theme
     * @param acquireFlash 0..1 burst when the target was newly locked
     * @param seed         0..255 per-target variety (rotation offsets)
     */
    public record TargetQuad(
            double x, double y, double z,
            float halfSize,
            int material,
            int argb,
            float health,
            float acquireFlash,
            int seed
    ) {}

    /** Shared per-frame theme and motion state, mirrored onto TargetUniforms. */
    public record Uniforms(
            float time,
            int styleId,
            int colorMode,
            float attackPulse,
            int primaryArgb,
            int secondaryArgb,
            int accentArgb,
            float opacity,
            float rainbowSpeed,
            float glow,
            float rotationSpeed,
            float pulse,
            float distortion,
            float sharpness
    ) {}

    /**
     * Draws every supplied quad in one batched RenderPass.
     */
    public static void draw(PoseStack poseStack, List<TargetQuad> quads, Uniforms uniforms) {
        if (quads.isEmpty() || uniforms.opacity() < 0.001f) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        float camX = (float) camera.position().x;
        float camY = (float) camera.position().y;
        float camZ = (float) camera.position().z;

        Quaternionf camRot = camera.rotation();
        Vector3f up = camRot.transform(new Vector3f(0, 1, 0));
        Vector3f right = camRot.transform(new Vector3f(1, 0, 0));

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        RenderSystem.getProjectionType().applyLayeringTransform(modelViewStack, 1.0F);

        poseStack.pushPose();
        try {
            Matrix4f poseMatrix = poseStack.last().pose();

            BufferBuilder buffer = GeminiTesselator.getInstance()
                    .begin(PrimitiveTopology.QUADS, TARGET_FORMAT);
            for (TargetQuad quad : quads) {
                appendQuad(buffer, poseMatrix, quad, camX, camY, camZ, right, up);
            }
            MeshData mesh = buffer.buildOrThrow();

            try {
                GpuBuffer vertices = GeminiTesselator.uploadVertexBuffer(
                        TARGET_PIPELINE.getVertexFormatBinding(0), mesh.vertexBuffer());

                GpuBuffer indices;
                IndexType indexType;
                if (mesh.indexBuffer() == null) {
                    RenderSystem.AutoStorageIndexBuffer autoIndices =
                            RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
                    indices = autoIndices.getBuffer(mesh.drawState().indexCount());
                    indexType = autoIndices.type();
                } else {
                    indices = GeminiTesselator.uploadIndexBuffer(
                            TARGET_PIPELINE.getVertexFormatBinding(0), mesh.indexBuffer());
                    indexType = mesh.drawState().indexType();
                }

                GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                        .writeTransform(
                                new Matrix4f(),
                                new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
                                new Vector3f(),
                                new Matrix4f());

                RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
                GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
                        ? RenderSystem.outputColorTextureOverride
                        : mainTarget.getColorTextureView();
                GpuTextureView depthTexture = mainTarget.useDepth
                        ? (RenderSystem.outputDepthTextureOverride != null
                            ? RenderSystem.outputDepthTextureOverride
                            : mainTarget.getDepthTextureView())
                        : null;

                CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                writeUniforms(encoder, uniforms);

                try (RenderPass pass = encoder.createRenderPass(
                        () -> "KillAuraTargets",
                        colorTexture,
                        Optional.empty(),
                        depthTexture,
                        OptionalDouble.empty())) {

                    pass.setPipeline(TARGET_PIPELINE);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", dynamicTransforms);
                    pass.setUniform("TargetUniforms", targetUniforms);
                    pass.setVertexBuffer(0, vertices.slice());
                    pass.setIndexBuffer(indices, indexType);
                    pass.drawIndexed(mesh.drawState().indexCount(), 1, 0, 0, 0);
                }
            } finally {
                mesh.close();
            }
        } finally {
            poseStack.popPose();
            modelViewStack.popMatrix();
        }
    }

    private static void appendQuad(BufferBuilder buffer, Matrix4f matrix, TargetQuad quad,
                                   float camX, float camY, float camZ,
                                   Vector3f right, Vector3f up) {
        float cx = (float) quad.x() - camX;
        float cy = (float) quad.y() - camY;
        float cz = (float) quad.z() - camZ;
        float half = Math.max(0.001f, quad.halfSize());

        int packedA = (Math.round(clamp01(quad.health()) * 255f) & 0xFF)
                | (Math.round(clamp01(quad.acquireFlash()) * 255f) << 8);
        int packedB = (quad.seed() & 0xFF)
                | ((quad.material() & 0xF) << 8);

        float x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3;
        if (quad.material() == MATERIAL_SIGIL) {
            // World-aligned horizontal quad so the sigil lies flat on the ground.
            x0 = cx - half; y0 = cy; z0 = cz - half;
            x1 = cx - half; y1 = cy; z1 = cz + half;
            x2 = cx + half; y2 = cy; z2 = cz + half;
            x3 = cx + half; y3 = cy; z3 = cz - half;
        } else {
            // Camera-facing billboard behind the body.
            float rx = right.x() * half;
            float ry = right.y() * half;
            float rz = right.z() * half;
            float ux = up.x() * half;
            float uy = up.y() * half;
            float uz = up.z() * half;
            x0 = cx - rx - ux; y0 = cy - ry - uy; z0 = cz - rz - uz;
            x1 = cx - rx + ux; y1 = cy - ry + uy; z1 = cz - rz + uz;
            x2 = cx + rx + ux; y2 = cy + ry + uy; z2 = cz + rz + uz;
            x3 = cx + rx - ux; y3 = cy + ry - uy; z3 = cz + rz - uz;
        }

        buffer.addVertex(matrix, x0, y0, z0).setUv(0f, 0f).setColor(quad.argb())
                .setUv1(packedA & 0xFFFF, packedB & 0xFFFF);
        buffer.addVertex(matrix, x1, y1, z1).setUv(0f, 1f).setColor(quad.argb())
                .setUv1(packedA & 0xFFFF, packedB & 0xFFFF);
        buffer.addVertex(matrix, x2, y2, z2).setUv(1f, 1f).setColor(quad.argb())
                .setUv1(packedA & 0xFFFF, packedB & 0xFFFF);
        buffer.addVertex(matrix, x3, y3, z3).setUv(1f, 0f).setColor(quad.argb())
                .setUv1(packedA & 0xFFFF, packedB & 0xFFFF);
    }

    private static void writeUniforms(CommandEncoder encoder, Uniforms uniforms) {
        ensureUniformBuffer();

        try (GpuBufferSlice.MappedView view = targetUniforms.map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(uniforms.time(), uniforms.styleId(), uniforms.colorMode(),
                            uniforms.attackPulse())
                    .putVec4(red(uniforms.primaryArgb()), green(uniforms.primaryArgb()),
                            blue(uniforms.primaryArgb()), uniforms.opacity())
                    .putVec4(red(uniforms.secondaryArgb()), green(uniforms.secondaryArgb()),
                            blue(uniforms.secondaryArgb()), uniforms.rainbowSpeed())
                    .putVec4(red(uniforms.accentArgb()), green(uniforms.accentArgb()),
                            blue(uniforms.accentArgb()), uniforms.glow())
                    .putVec4(uniforms.rotationSpeed(), uniforms.pulse(),
                            uniforms.distortion(), uniforms.sharpness());
        }
    }

    private static void ensureUniformBuffer() {
        if (targetUniforms != null) return;
        targetUniforms = RenderSystem.getDevice().createBuffer(
                () -> "Gemini KillAura Target Uniforms",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                TARGET_UNIFORM_SIZE);
    }

    private static float red(int argb) {
        return ((argb >> 16) & 0xFF) / 255f;
    }

    private static float green(int argb) {
        return ((argb >> 8) & 0xFF) / 255f;
    }

    private static float blue(int argb) {
        return (argb & 0xFF) / 255f;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
