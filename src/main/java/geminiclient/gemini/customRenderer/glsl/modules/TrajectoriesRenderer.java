package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
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
 * GPU trajectory prediction line + landing-point ring renderer.
 *
 * <p>Two pipelines:
 * <ul>
 *   <li>{@link #LINE_PIPELINE} — DEBUD_LINE_STRIP for the predicted parabolic arc.</li>
 *   <li>{@link #LANDING_PIPELINE} — camera-facing billboard quad that draws a
 *       procedural ring + glow in the fragment shader.</li>
 * </ul>
 */
public final class TrajectoriesRenderer {

    private TrajectoriesRenderer() {}

    // ── Trajectory prediction line pipeline ────────────────

    public static final RenderPipeline LINE_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/trajectory_line"))
            .withVertexShader(getIdentifier("core/trajectory"))
            .withFragmentShader(getIdentifier("core/trajectory_line"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.DEBUG_LINE_STRIP)
            .withDepthStencilState(new DepthStencilState(
                    CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    // ── Landing-point ring pipeline ────────────────────────

    public static final RenderPipeline LANDING_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/trajectory_landing"))
            .withVertexShader(getIdentifier("core/trajectory"))
            .withFragmentShader(getIdentifier("core/trajectory_landing"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(
                    CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    // ── Render types ───────────────────────────────────────

    private static final RenderType LINE_TYPE = RenderType.create(
            "gemini_trajectory_line",
            RenderSetup.builder(LINE_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.NO_LAYERING)
                    .createRenderSetup());

    private static final RenderType LANDING_TYPE = RenderType.create(
            "gemini_trajectory_landing",
            RenderSetup.builder(LANDING_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.NO_LAYERING)
                    .createRenderSetup());

    // ── Registration ───────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(LINE_PIPELINE);
        registry.accept(LANDING_PIPELINE);
    }

    // ── Drawing ────────────────────────────────────────────

    /**
     * Draw the predicted trajectory as a line strip.
     *
     * @param poseStack  current PoseStack from Render3DEvent
     * @param points     flat array [x0,y0,z0, x1,y1,z1, …] in world space
     * @param pointCount number of valid points
     * @param startColor ARGB colour at the start of the arc
     * @param endColor   ARGB colour at the end (fading to transparent is nice)
     */
    public static void drawTrajectoryLine(PoseStack poseStack, float[] points,
                                          int pointCount, int startColor, int endColor) {
        if (pointCount < 2) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        float camX = (float) camera.position().x;
        float camY = (float) camera.position().y;
        float camZ = (float) camera.position().z;

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_TEX_COLOR);

        float startA = ((startColor >> 24) & 0xFF) / 255f;
        float startR = ((startColor >> 16) & 0xFF) / 255f;
        float startG = ((startColor >> 8) & 0xFF) / 255f;
        float startB = (startColor & 0xFF) / 255f;

        float endA = ((endColor >> 24) & 0xFF) / 255f;
        float endR = ((endColor >> 16) & 0xFF) / 255f;
        float endG = ((endColor >> 8) & 0xFF) / 255f;
        float endB = (endColor & 0xFF) / 255f;

        for (int i = 0; i < pointCount; i++) {
            float t = pointCount > 1 ? i / (float) (pointCount - 1) : 0f;

            float px = points[i * 3] - camX;
            float py = points[i * 3 + 1] - camY;
            float pz = points[i * 3 + 2] - camZ;

            int r = (int) ((startR + (endR - startR) * t) * 255f);
            int g = (int) ((startG + (endG - startG) * t) * 255f);
            int b = (int) ((startB + (endB - startB) * t) * 255f);
            int a = (int) ((startA + (endA - startA) * t) * 255f);
            int rgba = (a << 24) | (r << 16) | (g << 8) | b;

            buffer.addVertex(matrix, px, py, pz)
                    .setUv(0f, 0f)
                    .setColor(rgba);
        }

        LINE_TYPE.draw(buffer.buildOrThrow());
    }

    /**
     * Draw a camera-facing ring at the predicted landing point.
     *
     * @param poseStack current PoseStack from Render3DEvent
     * @param x         world X
     * @param y         world Y
     * @param z         world Z
     * @param rgba      colour with alpha (ARGB format)
     */
    public static void drawLandingRing(PoseStack poseStack, double x, double y, double z, int rgba) {
        Camera camera = mc.getEntityRenderDispatcher().camera;

        poseStack.pushPose();
        poseStack.translate(x - camera.position().x,
                y - camera.position().y,
                z - camera.position().z);
        poseStack.mulPose(camera.rotation());

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float r = 0.3f;
        buffer.addVertex(matrix, -r, -r, 0f).setUv(0f, 0f).setColor(rgba);
        buffer.addVertex(matrix, -r,  r, 0f).setUv(0f, 1f).setColor(rgba);
        buffer.addVertex(matrix,  r,  r, 0f).setUv(1f, 1f).setColor(rgba);
        buffer.addVertex(matrix,  r, -r, 0f).setUv(1f, 0f).setColor(rgba);

        LANDING_TYPE.draw(buffer.buildOrThrow());
        poseStack.popPose();
    }

    /**
     * Draw a line strip with per-vertex colours (used for projectile trails).
     *
     * @param poseStack current PoseStack from Render3DEvent
     * @param positions flat array [x0,y0,z0, x1,y1,z1, …] in world space
     * @param colors    ARGB colour per vertex
     * @param count     number of vertices (must match lengths of both arrays)
     */
    public static void drawColoredLineStrip(PoseStack poseStack,
                                            float[] positions, int[] colors, int count) {
        if (count < 2) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        float camX = (float) camera.position().x;
        float camY = (float) camera.position().y;
        float camZ = (float) camera.position().z;

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < count; i++) {
            float px = positions[i * 3]     - camX;
            float py = positions[i * 3 + 1] - camY;
            float pz = positions[i * 3 + 2] - camZ;

            buffer.addVertex(matrix, px, py, pz)
                    .setUv(0f, 0f)
                    .setColor(colors[i]);
        }

        LINE_TYPE.draw(buffer.buildOrThrow());
    }
}
