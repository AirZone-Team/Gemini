package geminiclient.gemini.customRenderer;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Compatibility bridge for the immediate mesh path removed in Minecraft 26.2.
 *
 * <p>The old {@code Tesselator}/{@code RenderType.draw} path cached one upload
 * buffer per vertex format. This bridge preserves that behavior while using
 * the backend-neutral 26.2 GPU API, so the same call sites work on both the
 * OpenGL and Vulkan devices.</p>
 */
public final class GeminiTesselator {
    private static final int MAX_BYTES = 786_432;
    private static final GeminiTesselator INSTANCE = new GeminiTesselator();

    private final ByteBufferBuilder stagingBuffer = new ByteBufferBuilder(MAX_BYTES);
    private final Map<VertexFormat, GpuBuffer> vertexBuffers = new IdentityHashMap<>();
    private final Map<VertexFormat, GpuBuffer> indexBuffers = new IdentityHashMap<>();

    private GeminiTesselator() {
    }

    public static GeminiTesselator getInstance() {
        return INSTANCE;
    }

    public BufferBuilder begin(PrimitiveTopology topology, VertexFormat format) {
        return new BufferBuilder(this.stagingBuffer, topology, format);
    }

    public static void draw(RenderType renderType, MeshData mesh) {
        INSTANCE.drawImmediate(renderType, mesh);
    }

    public static GpuBuffer uploadVertexBuffer(VertexFormat format, ByteBuffer data) {
        GpuBuffer buffer = upload(
                INSTANCE.vertexBuffers.get(format), data,
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_VERTEX,
                () -> "Gemini immediate vertices / " + format);
        INSTANCE.vertexBuffers.put(format, buffer);
        return buffer;
    }

    public static GpuBuffer uploadIndexBuffer(VertexFormat format, ByteBuffer data) {
        GpuBuffer buffer = upload(
                INSTANCE.indexBuffers.get(format), data,
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_INDEX,
                () -> "Gemini immediate indices / " + format);
        INSTANCE.indexBuffers.put(format, buffer);
        return buffer;
    }

    private void drawImmediate(RenderType renderType, MeshData mesh) {
        try (mesh) {
            MeshData.DrawState state = mesh.drawState();
            VertexFormat format = state.format();
            GpuBuffer vertices = uploadVertexBuffer(format, mesh.vertexBuffer());

            GpuBuffer indices;
            IndexType indexType;
            ByteBuffer indexData = mesh.indexBuffer();
            if (indexData == null) {
                RenderSystem.AutoStorageIndexBuffer sequential =
                        RenderSystem.getSequentialBuffer(state.primitiveTopology());
                indices = sequential.getBuffer(state.indexCount());
                indexType = sequential.type();
            } else {
                indices = uploadIndexBuffer(format, indexData);
                indexType = state.indexType();
            }

            PreparedRenderType prepared = renderType.prepare();
            prepared.drawFromBuffer(vertices, indices, indexType, 0, 0, state.indexCount());
        }
    }

    private static GpuBuffer upload(
            @Nullable GpuBuffer target,
            ByteBuffer source,
            @GpuBuffer.Usage int usage,
            Supplier<String> label) {
        GpuDevice device = RenderSystem.getDevice();
        ByteBuffer data = source.duplicate();
        if (target == null || target.isClosed() || target.size() < data.remaining()) {
            if (target != null && !target.isClosed()) {
                target.close();
            }
            return device.createBuffer(label, usage, data);
        }

        CommandEncoder encoder = device.createCommandEncoder();
        encoder.writeToBuffer(target.slice(0L, data.remaining()), data);
        return target;
    }
}
