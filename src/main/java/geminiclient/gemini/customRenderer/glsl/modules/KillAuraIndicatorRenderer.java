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
 * GPU foot-ring indicator for KillAura targets.
 *
 * <p>Each dot is a camera-facing billboard quad with a sphere-impostor fragment
 * shader that produces diffuse + specular lighting for a convincing 3D
 * appearance.  All dots are batched into a single draw call regardless of
 * target count.</p>
 *
 * <h3>Data format</h3>
 * Each dot consumes 7 floats: {@code [worldX, worldY, worldZ, halfSize,
 * dotIndexNorm, hp, alpha]}.  The caller is responsible for computing the
 * ring positions on the CPU.
 *
 * <h3>Vertex colour encoding</h3>
 * <ul>
 *   <li>{@code .r} — normalised HP (current/max, 0→1)</li>
 *   <li>{@code .g} — dot index / {@code DOT_COUNT} (0→1)</li>
 *   <li>{@code .b} — reserved</li>
 *   <li>{@code .a} — master alpha</li>
 * </ul>
 */
public final class KillAuraIndicatorRenderer {

    private KillAuraIndicatorRenderer() {}

    /** Must match {@code DOT_COUNT} in the fragment shader. */
    public static final int DOT_COUNT = 16;

    // ── Pipeline ──────────────────────────────────────────────────

    public static final RenderPipeline KILLAURA_DOTS_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/killaura_dots"))
            .withVertexShader(getIdentifier("core/killaura_dots"))
            .withFragmentShader(getIdentifier("core/killaura_dots"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(
                    CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F))
            .withColorTargetState(new ColorTargetState(new BlendFunction(
                    SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
                    SourceFactor.ONE, DestFactor.ZERO)))
            .withCull(false)
            .build();

    // ── Render type ───────────────────────────────────────────────

    private static final RenderType KILLAURA_DOTS_TYPE = RenderType.create(
            "gemini_killaura_dots",
            RenderSetup.builder(KILLAURA_DOTS_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup());

    // ── Registration ──────────────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(KILLAURA_DOTS_PIPELINE);
    }

    // ── Drawing ───────────────────────────────────────────────────

    /**
     * Draw all dot indicators in a single batched call.
     *
     * @param poseStack the current pose stack (from {@code Render3DEvent})
     * @param dotData   flat array: {@code [x, y, z, halfSize, dotIdxNorm, hp, alpha] * dotCount}
     * @param dotCount  total number of dots (entities × DOT_COUNT)
     */
    public static void drawIndicators(PoseStack poseStack, float[] dotData, int dotCount) {
        if (dotCount == 0) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        float camX = (float) camera.position().x;
        float camY = (float) camera.position().y;
        float camZ = (float) camera.position().z;

        // Derive camera basis vectors from the rotation quaternion
        Quaternionf camRot = camera.rotation();
        Vector3f up    = camRot.transform(new Vector3f(0, 1, 0));
        Vector3f right = camRot.transform(new Vector3f(1, 0, 0));

        Matrix4f vm = poseStack.last().pose();

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < dotCount; i++) {
            int off = i * 7;
            float dx = dotData[off]     - camX;
            float dy = dotData[off + 1] - camY;
            float dz = dotData[off + 2] - camZ;
            float h  = dotData[off + 3];                    // half-size of the billboard
            float dotIdxNorm = dotData[off + 4];            // dot index / DOT_COUNT
            float hp  = dotData[off + 5];
            float a   = dotData[off + 6];

            int ir = (int)(hp * 255f);
            int ig = (int)(dotIdxNorm * 255f);
            int ia = (int)(a * 255f);
            int rgba = (ia << 24) | (ir << 16) | (ig << 8);

            // Billboard quad corners: centre ± right·h ± up·h
            // v0  (-right, -up)    v1  (-right, +up)
            // v2  (+right, +up)    v3  (+right, -up)
            float rpx = right.x * h, rpy = right.y * h, rpz = right.z * h;
            float upx = up.x    * h, upy = up.y    * h, upz = up.z    * h;

            buffer.addVertex(vm, dx - rpx - upx, dy - rpy - upy, dz - rpz - upz)
                    .setUv(0f, 0f).setColor(rgba);
            buffer.addVertex(vm, dx - rpx + upx, dy - rpy + upy, dz - rpz + upz)
                    .setUv(0f, 1f).setColor(rgba);
            buffer.addVertex(vm, dx + rpx + upx, dy + rpy + upy, dz + rpz + upz)
                    .setUv(1f, 1f).setColor(rgba);
            buffer.addVertex(vm, dx + rpx - upx, dy + rpy - upy, dz + rpz - upz)
                    .setUv(1f, 0f).setColor(rgba);
        }

        KILLAURA_DOTS_TYPE.draw(buffer.buildOrThrow());
    }
}
