package geminiclient.gemini.utils;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class RenderUtils {
    private static final RenderPipeline FILLED_BOX_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/filled_box"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static final RenderType FILLED_BOX = RenderType.create("sakura_filled_box",
            RenderSetup.builder(FILLED_BOX_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup());

    private static final RenderPipeline OUTLINE_BOX_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/outline_box"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static final RenderType OUTLINE_BOX = RenderType.create("sakura_outline_box",
            RenderSetup.builder(OUTLINE_BOX_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup());

    public static void drawFilledBox(AABB box, int c) {
        drawFilledFadeBox(box, c, c);
    }

    public static void drawFilledFadeBox(AABB box, int c, int c1) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        Matrix4f matrix = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;

        vertex(buffer, matrix, minX, minY, minZ, c);
        vertex(buffer, matrix, minX, minY, maxZ, c);
        vertex(buffer, matrix, maxX, minY, maxZ, c);
        vertex(buffer, matrix, maxX, minY, minZ, c);

        vertex(buffer, matrix, minX, maxY, minZ, c1);
        vertex(buffer, matrix, maxX, maxY, minZ, c1);
        vertex(buffer, matrix, maxX, maxY, maxZ, c1);
        vertex(buffer, matrix, minX, maxY, maxZ, c);

        vertex(buffer, matrix, minX, minY, minZ, c);
        vertex(buffer, matrix, minX, maxY, minZ, c1);
        vertex(buffer, matrix, maxX, maxY, minZ, c1);
        vertex(buffer, matrix, maxX, minY, minZ, c);

        vertex(buffer, matrix, maxX, minY, minZ, c);
        vertex(buffer, matrix, maxX, maxY, minZ, c1);
        vertex(buffer, matrix, maxX, maxY, maxZ, c1);
        vertex(buffer, matrix, maxX, minY, maxZ, c);

        vertex(buffer, matrix, minX, minY, maxZ, c);
        vertex(buffer, matrix, maxX, minY, maxZ, c);
        vertex(buffer, matrix, maxX, maxY, maxZ, c1);
        vertex(buffer, matrix, minX, maxY, maxZ, c1);

        vertex(buffer, matrix, minX, minY, minZ, c);
        vertex(buffer, matrix, minX, minY, maxZ, c);
        vertex(buffer, matrix, minX, maxY, maxZ, c1);
        vertex(buffer, matrix, minX, maxY, minZ, c1);

        FILLED_BOX.draw(buffer.buildOrThrow());
    }

    public static void drawOutlineBox(AABB box, int c) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);

        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        Matrix4f matrix = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;

        // Bottom face edges
        line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, c);
        line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, c);
        line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, c);
        line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, c);

        // Top face edges
        line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, c);
        line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, c);
        line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, c);
        line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, c);

        // Vertical edges
        line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, c);
        line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, c);
        line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, c);
        line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, c);

        OUTLINE_BOX.draw(buffer.buildOrThrow());
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(color).setNormal(0, 1, 0).setLineWidth(2.0f);
        buffer.addVertex(matrix, x2, y2, z2).setColor(color).setNormal(0, 1, 0).setLineWidth(2.0f);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, int color) {
        buffer.addVertex(matrix, x, y, z).setColor(color);
    }
}
