//package geminiclient.gemini.utils;
//
//import com.mojang.blaze3d.systems.RenderSystem;
//import com.mojang.blaze3d.vertex.BufferBuilder;
//import com.mojang.blaze3d.vertex.DefaultVertexFormat;
//import com.mojang.blaze3d.vertex.Tesselator;
//import com.mojang.blaze3d.vertex.VertexFormat;
//import net.minecraft.client.gui.GuiGraphicsExtractor;
//import net.minecraft.client.renderer.GameRenderer;
//import net.minecraft.util.Mth;
//import org.joml.Matrix4f;
//
//    public class RenderUtils {
//        /**
//         * 在 GuiGraphics 上绘制纯色圆角矩形（填充）
//         *
//         * @param guiGraphics 当前渲染上下文
//         * @param x           矩形左上角 X 坐标
//         * @param y           矩形左上角 Y 坐标
//         * @param width       矩形宽度
//         * @param height      矩形高度
//         * @param radius      圆角半径
//         * @param color       ARGB 颜色值，例如 0xFF606060
//         */
//        public static void drawRoundedRect(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int radius, int color) {
//            // 如果半径过大则自动修正
//            radius = Math.min(radius, Math.min(width, height) / 2);
//
//            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
//            Matrix4f matrix = guiGraphics.pose().;
//
//            float a = (color >> 24 & 255) / 255.0F;
//            float r = (color >> 16 & 255) / 255.0F;
//            float g = (color >> 8 & 255) / 255.0F;
//            float b = (color & 255) / 255.0F;
//
//            // 圆心定位（相对于矩形左上角）
//            float centerX = x + width / 2.0F;
//            float centerY = y + height / 2.0F;
//
//            // 每个角的起始角度
//            // 左上角：180° ~ 270°  右下角：0° ~ 90°  右上角：270° ~ 360°  左下角：90° ~ 180°
//            int[][] corners = {
//                    {x + radius, y + radius, 180, 270},          // 左上
//                    {x + width - radius, y + height - radius, 0, 90}, // 右下
//                    {x + width - radius, y + radius, 270, 360},      // 右上
//                    {x + radius, y + height - radius, 90, 180}       // 左下
//            };
//
//            // 首先绘制矩形中心部分（用四边形填满，简化方式：从中心点向四周三角形扇）
//            // 但我们采用triangle fan，以一个内点作为起点
//            // 这里用矩形的中心点作为 fan 的起点
//            builder.addVertex(matrix, centerX, centerY, 0).setColor(r, g, b, a);
//
//            // 再依次添加所有边缘点（圆角+直边）
//            // 我们按顺时针顺序添加：左上圆角 -> 上边 -> 右上圆角 -> 右边 -> 右下圆角 -> 下边 -> 左下圆角 -> 左边
//            int segments = 10; // 每个圆角分割的段数，越大越平滑
//
//            // 左上角 (180° -> 270°)
//            addCornerVertices(builder, matrix, x + radius, y + radius, radius, 180, 270, segments, r, g, b, a);
//            // 上边 (从左上角结束点连接到右上角开始点)
//            builder.addVertex(matrix, x + width - radius, y, 0).setColor(r, g, b, a);
//            // 右上角 (270° -> 360°)
//            addCornerVertices(builder, matrix, x + width - radius, y + radius, radius, 270, 360, segments, r, g, b, a);
//            // 右边
//            builder.addVertex(matrix, x + width, y + height - radius, 0).setColor(r, g, b, a);
//            // 右下角 (0° -> 90°)
//            addCornerVertices(builder, matrix, x + width - radius, y + height - radius, radius, 0, 90, segments, r, g, b, a);
//            // 下边
//            builder.addVertex(matrix, x + radius, y + height, 0).setColor(r, g, b, a);
//            // 左下角 (90° -> 180°)
//            addCornerVertices(builder, matrix, x + radius, y + height - radius, radius, 90, 180, segments, r, g, b, a);
//            // 左边
//            builder.addVertex(matrix, x, y + radius, 0).setColor(r, g, b, a);
//
//            // 渲染设置
//            RenderSystem.enab;
//            RenderSystem.setShader(GameRenderer::getPositionColorShader);
//            BufferUploader.drawWithShader(builder.buildOrThrow());
//            RenderSystem.disableBlend();
//        }
//
//        private static void addCornerVertices(BufferBuilder builder, Matrix4f matrix, float centerX, float centerY, float radius, int startAngle, int endAngle, int segments, float r, float g, float b, float a) {
//            for (int i = 0; i <= segments; i++) {
//                float angle = startAngle + (endAngle - startAngle) * i / (float) segments;
//                float rad = (float) Math.toRadians(angle);
//                float px = centerX + Mth.cos(rad) * radius;
//                float py = centerY - Mth.sin(rad) * radius; // 注意屏幕坐标系Y轴向下
//                builder.addVertex(matrix, px, py, 0).setColor(r, g, b, a);
//            }
//        }
//}
