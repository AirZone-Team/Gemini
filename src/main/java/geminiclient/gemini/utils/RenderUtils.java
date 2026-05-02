package geminiclient.gemini.utils;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import geminiclient.gemini.base.MinecraftInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class RenderUtils implements MinecraftInstance {
    /**
     * 在 GuiGraphics 上绘制圆角矩形
     *
     * @param graphics GuiGraphics 实例
     * @param x      左上角 X
     * @param y       左上角 Y
     * @param width       右下角 X
     * @param height       右下角 Y
     * @param radius   圆角半径（像素）
     * @param color    ARGB 颜色
     */
    public static void fillRoundedRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        // 边界保护：半径不能超过矩形尺寸的一半
        graphics.pose().pushMatrix();
        radius = Math.min(radius, Math.min(width / 2, height / 2));
        int x2 = x + width;
        int y2 = y + height;

        // 获取当前变换矩阵（关键：graphics.pose().last().pose() 返回 Matrix4f）
        Matrix4fc matrix = RenderSystem.getModelViewStack();

        // 设置渲染状态
        GlStateManager._enableBlend();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR); // 1.21.11 中 getBuilder() 无参数


        // 解析颜色分量（0-1 范围）
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        // 三角形扇的中心点
        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;
        buffer.addVertex(matrix, centerX, centerY, 0).setColor(r, g, b, a);

        // 圆弧分段数（越大越圆滑）
        int segments = 16;
        float angleStep = (float) (Math.PI / 2 / segments);

        // 顺时针生成所有边界点
        for (int i = 0; i <= 4 * segments; i++) {
            double angle = i * angleStep; // 从 0 到 2π
            int quadrant = i / segments;  // 0:右上, 1:左上, 2:左下, 3:右下

            float cx, cy; // 圆弧圆心坐标
            cy = switch (quadrant) {
                case 0 -> {
                    cx = x2 - radius;
                    yield y + radius;
                }
                case 1 -> {
                    cx = x + radius;
                    yield y + radius;
                }
                case 2 -> {
                    cx = x + radius;
                    yield y2 - radius;
                }
                default -> {
                    cx = x2 - radius;
                    yield y2 - radius;
                }
            };

            // 计算当前点在圆上的位置（Y 轴向下为正，因此 sin 取负）
            float px = (float) (cx + radius * Math.cos(angle));
            float py = (float) (cy - radius * Math.sin(angle));

            buffer.addVertex(matrix, px, py, 0).setColor(r, g, b, a);
        }
        tesselator.clear();
        GlStateManager._disableBlend();
        graphics.pose().popMatrix();
    }
}