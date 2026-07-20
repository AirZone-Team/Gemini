package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * Procedural renderer shared by held-item prediction and launched-projectile FX.
 * Geometry is camera-relative and rendered as actual ribbons rather than one-pixel
 * debug lines, making width, glow and animated styles consistent at every resolution.
 */
public final class TrajectoriesRenderer {

    private static final DepthStencilState DEPTH_TESTED =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0f, -1.0f);
    private static final DepthStencilState XRAY =
            new DepthStencilState(CompareOp.ALWAYS_PASS, false, -1.0f, -1.0f);
    private static final ColorTargetState ADDITIVE = new ColorTargetState(new BlendFunction(
            SourceFactor.SRC_ALPHA, DestFactor.ONE,
            SourceFactor.ONE, DestFactor.ZERO));

    public static final RenderPipeline RIBBON_PIPELINE = ribbonPipeline(
            "pipeline/trajectory_ribbon", DEPTH_TESTED);
    public static final RenderPipeline RIBBON_XRAY_PIPELINE = ribbonPipeline(
            "pipeline/trajectory_ribbon_xray", XRAY);
    public static final RenderPipeline MARKER_PIPELINE = effectPipeline(
            "pipeline/trajectory_marker", "core/trajectory_landing", DEPTH_TESTED);
    public static final RenderPipeline MARKER_XRAY_PIPELINE = effectPipeline(
            "pipeline/trajectory_marker_xray", "core/trajectory_landing", XRAY);
    public static final RenderPipeline FLARE_PIPELINE = effectPipeline(
            "pipeline/projectile_flare", "core/projectile_flare", DEPTH_TESTED);
    public static final RenderPipeline FLARE_XRAY_PIPELINE = effectPipeline(
            "pipeline/projectile_flare_xray", "core/projectile_flare", XRAY);

    private static final RenderType RIBBON_TYPE = renderType(
            "gemini_trajectory_ribbon", RIBBON_PIPELINE);
    private static final RenderType RIBBON_XRAY_TYPE = renderType(
            "gemini_trajectory_ribbon_xray", RIBBON_XRAY_PIPELINE);
    private static final RenderType MARKER_TYPE = renderType(
            "gemini_trajectory_marker", MARKER_PIPELINE);
    private static final RenderType MARKER_XRAY_TYPE = renderType(
            "gemini_trajectory_marker_xray", MARKER_XRAY_PIPELINE);
    private static final RenderType FLARE_TYPE = renderType(
            "gemini_projectile_flare", FLARE_PIPELINE);
    private static final RenderType FLARE_XRAY_TYPE = renderType(
            "gemini_projectile_flare_xray", FLARE_XRAY_PIPELINE);

    private TrajectoriesRenderer() {}

    private static RenderPipeline ribbonPipeline(String location, DepthStencilState depth) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(getIdentifier(location))
                .withVertexShader(getIdentifier("core/trajectory"))
                .withFragmentShader(getIdentifier("core/trajectory_ribbon"))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthStencilState(depth)
                .withColorTargetState(ADDITIVE)
                .withCull(false)
                .build();
    }

    private static RenderPipeline effectPipeline(String location, String fragment,
                                                 DepthStencilState depth) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(getIdentifier(location))
                .withVertexShader(getIdentifier("core/trajectory"))
                .withFragmentShader(getIdentifier(fragment))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthStencilState(depth)
                .withColorTargetState(ADDITIVE)
                .withCull(false)
                .build();
    }

    private static RenderType renderType(String name, RenderPipeline pipeline) {
        return RenderType.create(name, RenderSetup.builder(pipeline)
                .sortOnUpload()
                .setLayeringTransform(LayeringTransform.NO_LAYERING)
                .createRenderSetup());
    }

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(RIBBON_PIPELINE);
        registry.accept(RIBBON_XRAY_PIPELINE);
        registry.accept(MARKER_PIPELINE);
        registry.accept(MARKER_XRAY_PIPELINE);
        registry.accept(FLARE_PIPELINE);
        registry.accept(FLARE_XRAY_PIPELINE);
    }

    public static void drawRibbon(PoseStack poseStack, float[] positions, int count,
                                  int startColor, int endColor, float width, int style,
                                  float dashDensity, float time, boolean throughWalls) {
        if (count < 2 || width <= 0.0f) return;
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            float progress = i / (float) (count - 1);
            colors[i] = lerpColor(startColor, endColor, progress);
        }
        drawRibbonInternal(poseStack, positions, colors, count, width, style,
                dashDensity, time, throughWalls);
    }

    public static void drawColoredRibbon(PoseStack poseStack, float[] positions, int[] colors,
                                         int count, float width, boolean throughWalls) {
        if (count < 2 || colors.length < count || width <= 0.0f) return;
        drawRibbonInternal(poseStack, positions, colors, count, width,
                2, 1.0f, (float) (System.currentTimeMillis() * 0.001), throughWalls);
    }

    private static void drawRibbonInternal(PoseStack poseStack, float[] positions, int[] colors,
                                           int count, float width, int style, float dashDensity,
                                           float time, boolean throughWalls) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        Vector3f cameraLook = new Vector3f(0.0f, 0.0f, -1.0f);
        Vector3f cameraRight = new Vector3f(1.0f, 0.0f, 0.0f);
        camera.rotation().transform(cameraLook);
        camera.rotation().transform(cameraRight);

        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < count - 1; i++) {
            float progress0 = i / (float) (count - 1);
            float progress1 = (i + 1) / (float) (count - 1);
            float mid = (progress0 + progress1) * 0.5f;
            if (style == 1 && (((int) Math.floor((mid + time * 0.08f) * dashDensity)) & 1) != 0) {
                continue;
            }

            float ax = positions[i * 3];
            float ay = positions[i * 3 + 1];
            float az = positions[i * 3 + 2];
            float bx = positions[(i + 1) * 3];
            float by = positions[(i + 1) * 3 + 1];
            float bz = positions[(i + 1) * 3 + 2];

            Vector3d direction = new Vector3d(bx - ax, by - ay, bz - az);
            if (direction.lengthSquared() < 1.0e-10) continue;
            direction.normalize();
            Vector3d side = direction.cross(
                    new Vector3d(cameraLook.x, cameraLook.y, cameraLook.z), new Vector3d());
            if (side.lengthSquared() < 1.0e-10) {
                side.set(cameraRight.x, cameraRight.y, cameraRight.z);
            } else {
                side.normalize();
            }

            float taper0 = style == 2 ? 0.22f + 0.78f * progress0 : 1.0f;
            float taper1 = style == 2 ? 0.22f + 0.78f * progress1 : 1.0f;
            float r0x = (float) side.x * width * taper0;
            float r0y = (float) side.y * width * taper0;
            float r0z = (float) side.z * width * taper0;
            float r1x = (float) side.x * width * taper1;
            float r1y = (float) side.y * width * taper1;
            float r1z = (float) side.z * width * taper1;

            ax -= cameraX;
            ay -= cameraY;
            az -= cameraZ;
            bx -= cameraX;
            by -= cameraY;
            bz -= cameraZ;

            float animatedU0 = progress0 * 5.0f - time * 0.55f;
            float animatedU1 = progress1 * 5.0f - time * 0.55f;
            buffer.addVertex(matrix, ax - r0x, ay - r0y, az - r0z)
                    .setUv(animatedU0, -1.0f).setColor(colors[i]);
            buffer.addVertex(matrix, ax + r0x, ay + r0y, az + r0z)
                    .setUv(animatedU0, 1.0f).setColor(colors[i]);
            buffer.addVertex(matrix, bx + r1x, by + r1y, bz + r1z)
                    .setUv(animatedU1, 1.0f).setColor(colors[i + 1]);
            buffer.addVertex(matrix, bx - r1x, by - r1y, bz - r1z)
                    .setUv(animatedU1, -1.0f).setColor(colors[i + 1]);
        }

        var mesh = buffer.build();
        if (mesh != null) {
            (throughWalls ? RIBBON_XRAY_TYPE : RIBBON_TYPE).draw(mesh);
        }
    }

    public static void drawMarker(PoseStack poseStack, Vec3 position, float size, int color,
                                  int style, float time, boolean throughWalls) {
        if (size <= 0.0f || ((color >>> 24) & 0xFF) == 0) return;
        drawBillboard(poseStack, position, size, color, style, time,
                throughWalls ? MARKER_XRAY_TYPE : MARKER_TYPE);
    }

    public static void drawFlare(PoseStack poseStack, Vec3 position, float size, int color,
                                 float time, boolean throughWalls) {
        if (size <= 0.0f || ((color >>> 24) & 0xFF) == 0) return;
        drawBillboard(poseStack, position, size, color, 0, time,
                throughWalls ? FLARE_XRAY_TYPE : FLARE_TYPE);
    }

    private static void drawBillboard(PoseStack poseStack, Vec3 position, float size, int color,
                                      int style, float time, RenderType renderType) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        poseStack.pushPose();
        poseStack.translate(position.x - camera.position().x,
                position.y - camera.position().y,
                position.z - camera.position().z);
        poseStack.mulPose(camera.rotation());
        poseStack.mulPose(new Quaternionf().rotationZ(time * 0.28f));

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float u0 = style;
        float u1 = style + 0.999f;
        buffer.addVertex(matrix, -size, -size, 0.0f).setUv(u0, 0.0f).setColor(color);
        buffer.addVertex(matrix, -size, size, 0.0f).setUv(u0, 1.0f).setColor(color);
        buffer.addVertex(matrix, size, size, 0.0f).setUv(u1, 1.0f).setColor(color);
        buffer.addVertex(matrix, size, -size, 0.0f).setUv(u1, 0.0f).setColor(color);
        renderType.draw(buffer.buildOrThrow());
        poseStack.popPose();
    }

    private static int lerpColor(int start, int end, float amount) {
        float inverse = 1.0f - amount;
        int a = Math.round(((start >>> 24) & 0xFF) * inverse + ((end >>> 24) & 0xFF) * amount);
        int r = Math.round(((start >>> 16) & 0xFF) * inverse + ((end >>> 16) & 0xFF) * amount);
        int g = Math.round(((start >>> 8) & 0xFF) * inverse + ((end >>> 8) & 0xFF) * amount);
        int b = Math.round((start & 0xFF) * inverse + (end & 0xFF) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
