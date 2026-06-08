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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Matrix4f;

import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * Jump / landing ring renderer.
 * <p>
 * World positions are projected to screen space on the CPU using the camera's
 * yaw, pitch and FOV (视锥体缩放), then the ring quad is drawn in NDC via a
 * passthrough vertex shader.  Because the full projection is resolved on the
 * CPU per draw-call, the ring position is immune to FOV variation from
 * sprinting, speed effects, flying, or other dynamic sources.
 */
public class JumpCircleRenderer {

    public enum RingType {
        JUMP,
        LANDING_NORMAL,
        LANDING_HEAVY
    }

    // ── Pipelines (passthrough vertex shader, no GPU matrix multiply) ─

    public static final RenderPipeline RING_JUMP_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(getIdentifier("pipeline/jump_circle_jump"))
            .withVertexShader(getIdentifier("core/jump_circle_screen"))
            .withFragmentShader(getIdentifier("core/jump_circle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final RenderPipeline RING_NORMAL_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(getIdentifier("pipeline/jump_circle_normal"))
            .withVertexShader(getIdentifier("core/jump_circle_screen"))
            .withFragmentShader(getIdentifier("core/jump_circle"))
            .withShaderDefine("NORMAL")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final RenderPipeline RING_HEAVY_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(getIdentifier("pipeline/jump_circle_heavy"))
            .withVertexShader(getIdentifier("core/jump_circle_screen"))
            .withFragmentShader(getIdentifier("core/jump_circle"))
            .withShaderDefine("HEAVY")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final RenderPipeline SHADOW_DECAL_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(getIdentifier("pipeline/jump_circle_shadow"))
            .withVertexShader(getIdentifier("core/jump_circle_screen"))
            .withFragmentShader(getIdentifier("core/jump_circle_shadow"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -10.0F))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    // ── Render types ────────────────────────────────────────────

    private static final RenderType RING_JUMP_TYPE = RenderType.create("gemini_jump_circle_jump",
            RenderSetup.builder(RING_JUMP_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.NO_LAYERING)
                    .createRenderSetup());

    private static final RenderType RING_NORMAL_TYPE = RenderType.create("gemini_jump_circle_normal",
            RenderSetup.builder(RING_NORMAL_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.NO_LAYERING)
                    .createRenderSetup());

    private static final RenderType RING_HEAVY_TYPE = RenderType.create("gemini_jump_circle_heavy",
            RenderSetup.builder(RING_HEAVY_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.NO_LAYERING)
                    .createRenderSetup());

    private static final RenderType SHADOW_DECAL_TYPE = RenderType.create("gemini_jump_circle_shadow",
            RenderSetup.builder(SHADOW_DECAL_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.NO_LAYERING)
                    .createRenderSetup());

    // ── Registration ────────────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(RING_JUMP_PIPELINE);
        registry.accept(RING_NORMAL_PIPELINE);
        registry.accept(RING_HEAVY_PIPELINE);
        registry.accept(SHADOW_DECAL_PIPELINE);
    }

    // ── Raycast ─────────────────────────────────────────────────

    public static double findGroundY(double x, double y, double z) {
        if (mc.level == null) return y;

        Vec3 start = new Vec3(x, y + 1.0, z);
        Vec3 end   = new Vec3(x, y - 5.0, z);

        BlockHitResult result = mc.level.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player));

        if (result.getType() == HitResult.Type.MISS) {
            return y;
        }
        return result.getLocation().y;
    }

    // ── Drawing ─────────────────────────────────────────────────

    public static void drawJumpCircle(PoseStack poseStack, double x, double y, double z, float radius, int rgba, RingType type) {
        if (radius <= 0f) return;

        RenderType renderType = switch (type) {
            case JUMP           -> RING_JUMP_TYPE;
            case LANDING_NORMAL -> RING_NORMAL_TYPE;
            case LANDING_HEAVY  -> RING_HEAVY_TYPE;
        };

        drawQuad3D(poseStack, x, y, z, radius, rgba, renderType, 0.002f);
    }

    public static void drawShadowDecal(PoseStack poseStack, double x, double y, double z, float radius, int rgba) {
        if (radius <= 0f) return;
        drawQuad3D(poseStack, x, y, z, radius, rgba, SHADOW_DECAL_TYPE, 0.001f);
    }

    // ── Internal ────────────────────────────────────────────────

    private static void drawQuad3D(PoseStack poseStack, double x, double y, double z, float radius, int rgba,
                                   RenderType renderType, float yOffset) {

        Camera camera = mc.getEntityRenderDispatcher().camera;
        Vec3 camPos = camera.position();

        // 1. 压入矩阵栈，避免影响后续渲染
        poseStack.pushPose();

        // 2. 将坐标系原点平移到目标 3D 坐标处（减去相机坐标以获得相对坐标）
        poseStack.translate(x - camPos.x, y + yOffset - camPos.y, z - camPos.z);

        /* * [可选]: 如果你想让光环像之前的代码一样始终“面朝相机”（Billboard 广告牌效果）
         * 而不是平铺在地上，请取消注释下面这行代码：
         * poseStack.mulPose(camera.rotation());
         */

        // 获取当前矩阵
        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        // 3. 构建 3D 顶点 (平铺在 XZ 平面上，Y 轴为 0)
        // 注意：因为 Pipeline 中设置了 cull(false)，所以顶点顺时针/逆时针皆可显示
        buffer.addVertex(matrix, -radius, 0f, -radius).setUv(0f, 0f).setColor(rgba);
        buffer.addVertex(matrix, -radius, 0f,  radius).setUv(0f, 1f).setColor(rgba);
        buffer.addVertex(matrix,  radius, 0f,  radius).setUv(1f, 1f).setColor(rgba);
        buffer.addVertex(matrix,  radius, 0f, -radius).setUv(1f, 0f).setColor(rgba);

        renderType.draw(buffer.buildOrThrow());

        // 4. 弹出矩阵栈
        poseStack.popPose();
    }
}
