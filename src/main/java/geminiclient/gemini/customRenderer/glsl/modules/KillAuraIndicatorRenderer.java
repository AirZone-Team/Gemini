package geminiclient.gemini.customRenderer.glsl.modules;

import geminiclient.gemini.customRenderer.GeminiRenderPipelines;

import com.mojang.blaze3d.PrimitiveTopology;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import geminiclient.gemini.customRenderer.GeminiTesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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
 * Batched GLSL indicator renderer for KillAura targets.
 *
 * <p>The fragment shader draws several procedural SDF materials. Most elements
 * are camera-facing glyphs, while {@link #MATERIAL_SIGIL} is rendered as a
 * world-space horizontal magic circle. Every target still shares one draw
 * call.</p>
 *
 * <p>Each element consumes 10 floats:
 * {@code [worldX, worldY, worldZ, halfSize, red, green, blue, alpha,
 * material, rotation]}.</p>
 */
public final class KillAuraIndicatorRenderer {

    /** Legacy/default particle count used by the classic health ring. */
    public static final int DOT_COUNT = 16;
    public static final int PARTICLE_STRIDE = 10;

    public static final int MATERIAL_ORB = 0;
    public static final int MATERIAL_SPARK = 1;
    public static final int MATERIAL_DIAMOND = 2;
    public static final int MATERIAL_RUNE = 3;
    public static final int MATERIAL_CRESCENT = 4;
    public static final int MATERIAL_SIGIL = 5;
    public static final int MATERIAL_EYE = 6;

    public static final RenderPipeline KILLAURA_DOTS_PIPELINE = RenderPipeline.builder(
                    GeminiRenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/killaura_dots"))
            .withVertexShader(getIdentifier("core/killaura_dots"))
            .withFragmentShader(getIdentifier("core/killaura_dots"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(
                    CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 1.0F))
            .withColorTargetState(new ColorTargetState(new BlendFunction(
                    BlendFactor.SRC_ALPHA, BlendFactor.ONE,
                    BlendFactor.ONE, BlendFactor.ZERO)))
            .withCull(false)
            .build();

    private static final RenderType KILLAURA_DOTS_TYPE = RenderType.create(
            "gemini_killaura_dots",
            RenderSetup.builder(KILLAURA_DOTS_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup());

    private KillAuraIndicatorRenderer() {}

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(KILLAURA_DOTS_PIPELINE);
    }

    /**
     * Draws all supplied particles in one batched call.
     *
     * @param poseStack current world pose stack
     * @param particleData packed particle data using {@link #PARTICLE_STRIDE}
     * @param particleCount number of particles to read
     */
    public static void drawIndicators(PoseStack poseStack, float[] particleData, int particleCount) {
        if (particleCount <= 0) return;
        if (particleData.length < particleCount * PARTICLE_STRIDE) {
            throw new IllegalArgumentException("KillAura particle data is shorter than particleCount");
        }

        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        float camX = (float) camera.position().x;
        float camY = (float) camera.position().y;
        float camZ = (float) camera.position().z;

        Quaternionf camRot = camera.rotation();
        Vector3f up = camRot.transform(new Vector3f(0, 1, 0));
        Vector3f right = camRot.transform(new Vector3f(1, 0, 0));
        Matrix4f viewMatrix = poseStack.last().pose();

        BufferBuilder buffer = GeminiTesselator.getInstance()
                .begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < particleCount; i++) {
            int offset = i * PARTICLE_STRIDE;
            float x = particleData[offset] - camX;
            float y = particleData[offset + 1] - camY;
            float z = particleData[offset + 2] - camZ;
            float halfSize = Math.max(0.001f, particleData[offset + 3]);

            int red = channelToByte(particleData[offset + 4]);
            int green = channelToByte(particleData[offset + 5]);
            int blue = channelToByte(particleData[offset + 6]);
            int alpha = channelToByte(particleData[offset + 7]);
            int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
            int material = Math.round(particleData[offset + 8]);
            float rotation = particleData[offset + 9];

            float cos = (float) Math.cos(rotation);
            float sin = (float) Math.sin(rotation);
            float rightX;
            float rightY;
            float rightZ;
            float upX;
            float upY;
            float upZ;

            if (material == MATERIAL_SIGIL) {
                // A real horizontal quad makes the magic circle sit on the
                // ground instead of following the camera like a billboard.
                rightX = cos * halfSize;
                rightY = 0f;
                rightZ = sin * halfSize;
                upX = -sin * halfSize;
                upY = 0f;
                upZ = cos * halfSize;
            } else {
                float widthScale = material == MATERIAL_SPARK ? 0.68f : 1f;
                float heightScale = material == MATERIAL_SPARK ? 1.35f : 1f;
                rightX = (right.x * cos + up.x * sin) * halfSize * widthScale;
                rightY = (right.y * cos + up.y * sin) * halfSize * widthScale;
                rightZ = (right.z * cos + up.z * sin) * halfSize * widthScale;
                upX = (up.x * cos - right.x * sin) * halfSize * heightScale;
                upY = (up.y * cos - right.y * sin) * halfSize * heightScale;
                upZ = (up.z * cos - right.z * sin) * halfSize * heightScale;
            }

            // UV.x is split into two-unit material pages. The fragment shader
            // recovers both the material id and the local 0..1 coordinate.
            float materialU = material * 2f;

            buffer.addVertex(viewMatrix,
                            x - rightX - upX, y - rightY - upY, z - rightZ - upZ)
                    .setUv(materialU, 0f).setColor(argb);
            buffer.addVertex(viewMatrix,
                            x - rightX + upX, y - rightY + upY, z - rightZ + upZ)
                    .setUv(materialU, 1f).setColor(argb);
            buffer.addVertex(viewMatrix,
                            x + rightX + upX, y + rightY + upY, z + rightZ + upZ)
                    .setUv(materialU + 1f, 1f).setColor(argb);
            buffer.addVertex(viewMatrix,
                            x + rightX - upX, y + rightY - upY, z + rightZ - upZ)
                    .setUv(materialU + 1f, 0f).setColor(argb);
        }

        GeminiTesselator.draw(KILLAURA_DOTS_TYPE, buffer.buildOrThrow());
    }

    private static int channelToByte(float value) {
        return Math.round(Math.max(0f, Math.min(1f, value)) * 255f);
    }
}
