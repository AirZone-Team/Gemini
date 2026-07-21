package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.IndexType;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;

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
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import geminiclient.gemini.customRenderer.GeminiTesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.Optional;
import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GPU renderer for the configurable procedural MagicHalo.
 *
 * <p>A dedicated std140 block carries all style data at full float precision.
 * This replaces the old four-channel vertex-color encoding and leaves enough
 * room for live palette, geometry, ornament and motion controls.</p>
 */
public final class MagicHaloRenderer {

    public static final int FLAG_CROWN = 1;
    public static final int FLAG_RUNES = 1 << 1;
    public static final int FLAG_ORBITALS = 1 << 2;
    public static final int FLAG_STARFIELD = 1 << 3;

    private static final int HALO_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4()
            .get();

    private static GpuBuffer haloUniforms;

    private static final DepthStencilState HALO_DEPTH =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 1.0F);

    private static final ColorTargetState HALO_BLEND = new ColorTargetState(new BlendFunction(
            BlendFactor.SRC_ALPHA, BlendFactor.ONE,
            BlendFactor.ONE, BlendFactor.ZERO));

    public static final RenderPipeline HALO_PIPE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/magic_halo"))
            .withVertexShader(getIdentifier("core/magic_halo"))
            .withFragmentShader(getIdentifier("core/magic_halo"))
            .withBindGroupLayout(GeminiRenderPipelines.uniform("HaloUniforms"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(HALO_DEPTH)
            .withColorTargetState(HALO_BLEND)
            .withCull(false)
            .build();

    private MagicHaloRenderer() {}

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(HALO_PIPE);
    }

    /**
     * Complete per-frame visual configuration. Style and colorMode use the
     * list indices declared by the MagicHalo module.
     */
    public record Settings(
            float time,
            int style,
            int colorMode,
            int flags,
            int primaryColor,
            int secondaryColor,
            int accentColor,
            float alpha,
            float intensity,
            float glow,
            float ringRadius,
            float ringThickness,
            float spikeLength,
            int spikeCount,
            int layers,
            int runeDetail,
            int particleDensity,
            float sharpness,
            float rotation,
            float pulse,
            float distortion,
            float rainbowSpeed,
            float tiltDegrees
    ) {}

    public static void draw(PoseStack poseStack,
                            double x, double y, double z,
                            float radius,
                            Settings settings) {
        if (radius <= 0f || settings.alpha() < 0.001f || settings.intensity() < 0.001f) return;

        ensureUniformBuffer();

        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        float cx = (float) camera.position().x;
        float cy = (float) camera.position().y;
        float cz = (float) camera.position().z;

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        RenderSystem.getProjectionType().applyLayeringTransform(modelViewStack, 1.0F);

        poseStack.pushPose();
        poseStack.translate(x - cx, y - cy, z - cz);
        if (Math.abs(settings.tiltDegrees()) > 0.001f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(settings.tiltDegrees()));
        }
        Matrix4f poseMatrix = poseStack.last().pose();

        BufferBuilder buffer = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        buffer.addVertex(poseMatrix, -radius, 0f, -radius).setUv(0f, 0f).setColor(0xFFFFFFFF);
        buffer.addVertex(poseMatrix, -radius, 0f, radius).setUv(0f, 1f).setColor(0xFFFFFFFF);
        buffer.addVertex(poseMatrix, radius, 0f, radius).setUv(1f, 1f).setColor(0xFFFFFFFF);
        buffer.addVertex(poseMatrix, radius, 0f, -radius).setUv(1f, 0f).setColor(0xFFFFFFFF);

        MeshData mesh = buffer.buildOrThrow();

        try {
            GpuBuffer vertices = GeminiTesselator.uploadVertexBuffer(HALO_PIPE.getVertexFormatBinding(0), mesh.vertexBuffer());

            GpuBuffer indices;
            IndexType indexType;
            if (mesh.indexBuffer() == null) {
                RenderSystem.AutoStorageIndexBuffer autoIndices =
                        RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
                indices = autoIndices.getBuffer(mesh.drawState().indexCount());
                indexType = autoIndices.type();
            } else {
                indices = GeminiTesselator.uploadIndexBuffer(HALO_PIPE.getVertexFormatBinding(0), mesh.indexBuffer());
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
            writeUniforms(encoder, settings);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "MagicHalo",
                    colorTexture,
                    Optional.empty(),
                    depthTexture,
                    OptionalDouble.empty())) {

                pass.setPipeline(HALO_PIPE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicTransforms);
                pass.setUniform("HaloUniforms", haloUniforms);
                pass.setVertexBuffer(0, vertices.slice());
                pass.setIndexBuffer(indices, indexType);
                pass.drawIndexed(mesh.drawState().indexCount(), 1, 0, 0, 0);
            }
        } finally {
            mesh.close();
            poseStack.popPose();
            modelViewStack.popMatrix();
        }
    }

    private static void ensureUniformBuffer() {
        if (haloUniforms != null) return;
        haloUniforms = RenderSystem.getDevice().createBuffer(
                () -> "Gemini MagicHalo Uniforms",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                HALO_UNIFORM_SIZE);
    }

    private static void writeUniforms(CommandEncoder encoder, Settings settings) {
        float[] primary = rgb(settings.primaryColor());
        float[] secondary = rgb(settings.secondaryColor());
        float[] accent = rgb(settings.accentColor());

        try (GpuBufferSlice.MappedView view = haloUniforms.map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(settings.time(), settings.style(), settings.colorMode(), settings.flags())
                    .putVec4(primary[0], primary[1], primary[2], settings.alpha())
                    .putVec4(secondary[0], secondary[1], secondary[2], settings.intensity())
                    .putVec4(accent[0], accent[1], accent[2], settings.glow())
                    .putVec4(settings.ringRadius(), settings.ringThickness(),
                            settings.spikeLength(), settings.spikeCount())
                    .putVec4(settings.layers(), settings.runeDetail(),
                            settings.particleDensity(), settings.sharpness())
                    .putVec4(settings.rotation(), settings.pulse(),
                            settings.distortion(), settings.rainbowSpeed())
                    .putVec4(0f, 0f, 0f, 0f);
        }
    }

    private static float[] rgb(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f
        };
    }
}
