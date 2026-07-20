package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GPU renderer for procedural jump-circle decals.
 *
 * <p>Local coordinates use a normal 0..1 UV. Two compact 32-bit configuration
 * blocks are split across the four 16-bit components of UV1/UV2 and passed as
 * flat integer varyings. Keeping configuration out of UV0 prevents precision
 * loss and interpolation noise on large world-space decals.</p>
 */
public final class JumpCircleRenderer {

    private static final VertexFormat JUMP_CIRCLE_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .build();

    public static final RenderPipeline RING_PIPELINE =
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(getIdentifier("pipeline/jump_circle"))
                    .withVertexShader(getIdentifier("core/jump_circle_screen"))
                    .withFragmentShader(getIdentifier("core/jump_circle"))
                    .withVertexFormat(JUMP_CIRCLE_FORMAT, VertexFormat.Mode.QUADS)
                    .withDepthStencilState(new DepthStencilState(
                            CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .build();

    public static final RenderPipeline SHADOW_DECAL_PIPELINE =
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(getIdentifier("pipeline/jump_circle_shadow"))
                    .withVertexShader(getIdentifier("core/jump_circle_screen"))
                    .withFragmentShader(getIdentifier("core/jump_circle_shadow"))
                    .withVertexFormat(JUMP_CIRCLE_FORMAT, VertexFormat.Mode.QUADS)
                    .withDepthStencilState(new DepthStencilState(
                            CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -10.0F))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .build();

    private static final RenderType RING_TYPE = RenderType.create(
            "gemini_jump_circle",
            RenderSetup.builder(RING_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.NO_LAYERING)
                    .createRenderSetup()
    );

    private static final RenderType SHADOW_DECAL_TYPE = RenderType.create(
            "gemini_jump_circle_shadow",
            RenderSetup.builder(SHADOW_DECAL_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.NO_LAYERING)
                    .createRenderSetup()
    );

    private JumpCircleRenderer() {}

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(RING_PIPELINE);
        registry.accept(SHADOW_DECAL_PIPELINE);
    }

    public static double findGroundY(double x, double y, double z) {
        if (mc.level == null) return y;

        BlockHitResult result = mc.level.clip(new ClipContext(
                new Vec3(x, y + 1.0, z),
                new Vec3(x, y - 5.0, z),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return result.getType() == HitResult.Type.MISS ? y : result.getLocation().y;
    }

    public static void drawJumpCircle(PoseStack poseStack,
                                      double x, double y, double z,
                                      float radius, int abgr,
                                      int styleBits, int materialBits) {
        if (radius <= 0f) return;
        drawQuad3D(poseStack, x, y, z, radius, abgr, RING_TYPE,
                0f, styleBits, materialBits);
    }

    public static void drawShadowDecal(PoseStack poseStack,
                                       double x, double y, double z,
                                       float radius, int abgr, float opacity) {
        if (radius <= 0f) return;
        int packedOpacity = Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f);
        drawQuad3D(poseStack, x, y, z, radius, abgr, SHADOW_DECAL_TYPE,
                -0.001f, packedOpacity, 0);
    }

    private static void drawQuad3D(PoseStack poseStack,
                                   double x, double y, double z,
                                   float radius, int abgr,
                                   RenderType renderType, float yOffset,
                                   int packedX, int packedY) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        Vec3 camPos = camera.position();
        poseStack.pushPose();
        poseStack.translate(x - camPos.x, y + yOffset - camPos.y, z - camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, JUMP_CIRCLE_FORMAT);
        addVertex(buffer, matrix, -radius, -radius, 0f, 0f, abgr, packedX, packedY);
        addVertex(buffer, matrix, -radius, radius, 0f, 1f, abgr, packedX, packedY);
        addVertex(buffer, matrix, radius, radius, 1f, 1f, abgr, packedX, packedY);
        addVertex(buffer, matrix, radius, -radius, 1f, 0f, abgr, packedX, packedY);
        renderType.draw(buffer.buildOrThrow());
        poseStack.popPose();
    }

    private static void addVertex(BufferBuilder buffer, Matrix4f matrix,
                                  float x, float z, float u, float v, int abgr,
                                  int packedX, int packedY) {
        buffer.addVertex(matrix, x, 0f, z)
                .setColor(abgr)
                .setUv(u, v)
                .setUv1(packedX & 0xFFFF, packedX >>> 16 & 0xFFFF)
                .setUv2(packedY & 0xFFFF, packedY >>> 16 & 0xFFFF);
    }
}
