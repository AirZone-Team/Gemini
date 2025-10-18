//package geminiclient.gemini.utils;
//
//import com.mojang.blaze3d.systems.RenderSystem;
//import com.mojang.blaze3d.vertex.PoseStack;
//import com.mojang.blaze3d.vertex.VertexConsumer;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.renderer.MultiBufferSource;
//import net.minecraft.client.renderer.RenderType;
//import net.minecraft.util.Mth;
//import net.minecraft.world.entity.Entity;
//import org.joml.Matrix4f;
//
//public class RenderUtils {
//    public static void drawCircle(Entity entity, float partialTicks, double rad, int color, float alpha) {
//        Minecraft mc = Minecraft.getInstance();
//        if (mc.level == null || mc.getCameraEntity() == null) {
//            return;
//        }
//
//        // --- 动画/时间更新逻辑（保持原代码结构，但现代MC不推荐在渲染代码中做此操作）
//    /*
//    ticks += 0.004 * (System.currentTimeMillis() - lastFrame);
//    lastFrame = System.currentTimeMillis();
//    */
//
//        // --- 颜色处理
//        float r = ((color >> 16) & 0xFF) / 255.0F;
//        float g = ((color >> 8) & 0xFF) / 255.0F;
//        float b = (color & 0xFF) / 255.0F;
//        float baseAlpha = alpha;
//
//        // --- 坐标计算（插值和平移）
//        // 获取实体在当前tick的渲染位置（相对于世界的坐标）
//        double entityX = Mth.lerp(partialTicks, entity.xOld, entity.getX());
//        double entityY = Mth.lerp(partialTicks, entity.yOld, entity.getY());
//        double entityZ = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
//
//        // 获取渲染偏移（相机的世界坐标）
//        double cameraX = Mth.lerp(partialTicks, mc.gameRenderer.getMainCamera().getEntity().xOld, mc.gameRenderer.getMainCamera().getEntity().getX());
//        double cameraY = Mth.lerp(partialTicks, mc.gameRenderer.getMainCamera().getEntity().yOld, mc.gameRenderer.getMainCamera().getEntity().getY());
//        double cameraZ = Mth.lerp(partialTicks, mc.gameRenderer.getMainCamera().getEntity().zOld, mc.gameRenderer.getMainCamera().getEntity().getZ());
//
//        // 绘制圆心的相对位置（相对于相机）
//        final double x = entityX - cameraX;
//        final double y = entityY - cameraY + Math.sin(ticks) + 1; // 加上原始代码中的动画偏移
//        final double z = entityZ - cameraZ;
//
//        // --- 现代渲染设置
//        // 1. 设置 PoseStack：平移到圆心位置
//        PoseStack poseStack = new PoseStack();
//        poseStack.pushPose();
//        // 平移到实体的相对坐标
//        poseStack.translate(x, y, z);
//
//        // 2. 获取 MultiBufferSource
//        // 在 EntityRenderersEvent.RENDER_SPECIFIC_ENTITY_EVENT 之后调用，通常使用 Minecraft.getInstance().renderBuffers().bufferSource()
//        // 但在普通渲染中，我们需要一个合适的 BufferSource。这里我们使用一个临时的 MultiBufferSource，或者假设在一个已有的渲染调用中。
//        // 在一个完整的环境中，你可能需要传入一个 MultiBufferSource 实例。这里我们用一个简单的方法获取。
//        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
//
//        // 3. 绘制实心圆 (GL_TRIANGLE_STRIP)
//        // 使用内置的 RenderType.create() 或 RenderType.entityCutout() 等自定义类型
//        // 为了实现平滑着色，我们需要一个支持 GL_SMOOTH 和混合的自定义 RenderType。
//        // 在简单的示例中，我们使用一个通用的 TRIANGLES 类型并手动设置状态。
//        // 在 1.21.9 中，通常使用自定义的 RenderType。这里为了简洁，使用一个通用的 RenderType.lines() 并手动设置状态。
//        // 实际实现中，绘制实心圆需要一个支持混合和深度禁用的自定义 RenderType。
//        // 由于 RenderType 的复杂性，我们先用一个简单的 RenderType.lines() 来演示线框部分，并假设实心部分已被适当处理。
//
//        // --- 绘制线框圆 (GL_LINE_STRIP)
//        // RenderType.LINES 提供了一种绘制线段的方式，但通常用于调试或简单的线框。
//        RenderSystem.;
//        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // 设置白色底色，Alpha会被VertexConsumer覆盖
//        RenderSystem.disableDepthTest();
//        RenderSystem.lineWidth(1.5f); // 设置线宽 (注意：现代 OpenGL 中线宽支持有限)
//
//        // 获取用于绘制线条的 VertexConsumer
//        // 注意：这里的 RenderType.lines() 默认不启用 GL_LINE_SMOOTH，需要自定义。
//        // 为了尽可能还原效果，我们选择一个支持透明度的渲染类型，并手动处理状态。
//        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
//
//        Matrix4f matrix = poseStack.last().pose();
//
//        // 绘制外圆线
//        float lineAlpha = 0.5f * baseAlpha;
//
//        // 循环绘制线段
//        for (int i = 0; i <= 180; i++) {
//            double angle = i * Mth.TWO_PI / 180.0;
//            double lineX = rad * Math.cos(angle);
//            double lineZ = rad * Math.sin(angle);
//
//            // y坐标保持在平移后的 (y-y) 即 0 处，因为我们已经平移到了圆心。
//            consumer.vertex(matrix, (float) lineX, 0.0f, (float) lineZ)
//                    .color(r, g, b, lineAlpha)
//                    .normal(poseStack.last().normal(), 0, 1, 0) // 添加法线，虽然线条不需要
//                    .endVertex();
//        }
//
//        // 提交绘制并清理状态
//        bufferSource.endBatch(RenderType.lines()); // 提交本次绘制
//
//        // --- 清理状态
//        RenderSystem.enableDepthTest();
//        RenderSystem.disableBlend();
//        poseStack.popPose();
//
//        // 最后调用 bufferSource.endBatch() 来确保所有绘制都被提交到 GPU。
//        // 如果这个方法是在一个更大的渲染循环中调用的，可能不需要在这里调用。
//        // bufferSource.endBatch();
//    }
//
//    // 辅助方法（用于插值，原代码使用）
//    private static double interpolate(double oldPos, double newPos, float partialTicks) {
//        return oldPos + (newPos - oldPos) * partialTicks;
//    }
//}
