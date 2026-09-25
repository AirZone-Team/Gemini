package geminiclient.gemini.customRenderer;

import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Compatibility bridge for the immediate mesh path removed in Minecraft 26.2.
 *
 * <p>The old {@code Tesselator}/{@code RenderType.draw} path cached one upload
 * buffer per vertex format. This bridge preserves that behavior while using
 * the backend-neutral 26.3 RenderPearl GPU API, so the same call sites work on
 * both the OpenGL and Vulkan devices.</p>
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

            GpuBuffer indices = null;
            IndexType indexType;
            ByteBuffer indexData = mesh.indexBuffer();
            if (indexData == null) {
                RenderSystem.AutoStorageIndexBuffer sequential =
                        RenderSystem.getSequentialBuffer(state.primitiveTopology());
                // getBuffer(int) both records the request and grows the shared buffer, so
                // the null custom index in ExecuteInfo resolves to it later.
                sequential.getBuffer(state.indexCount());
                indexType = sequential.type();
            } else {
                indices = uploadIndexBuffer(format, indexData);
                indexType = state.indexType();
            }

            StagedVertexBuffer.ExecuteInfo info = new StagedVertexBuffer.ExecuteInfo(
                    vertices, indices, indexType, 0, 0, state.indexCount(), state.primitiveTopology());

            // 26.3 removed the ambient render pass the old draw path relied on, so the
            // bridge opens its own against the main target and preserves its contents.
            RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            GpuTextureView color = target.getColorTextureView();
            GpuTextureView depth = target.hasDepth() ? target.getDepthTextureView() : null;
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "Gemini immediate", color, Optional.empty(), depth, OptionalDouble.empty())) {
                renderType.prepare().drawFromBuffer(info, pass);
            }
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
