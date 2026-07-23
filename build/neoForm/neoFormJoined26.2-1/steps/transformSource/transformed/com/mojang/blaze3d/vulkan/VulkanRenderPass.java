package com.mojang.blaze3d.vulkan;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.checkpoints.CheckpointExtension;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.EXTMultiDraw;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferViewCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDrawIndexedIndirectCommand;
import org.lwjgl.vulkan.VkDrawIndirectCommand;
import org.lwjgl.vulkan.VkMultiDrawIndexedInfoEXT;
import org.lwjgl.vulkan.VkMultiDrawInfoEXT;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkViewport.Buffer;

@OnlyIn(Dist.CLIENT)
public class VulkanRenderPass implements RenderPassBackend {
    public static final boolean VALIDATION = SharedConstants.IS_RUNNING_IN_IDE;
    private final VulkanDevice device;
    private final VulkanCommandEncoder encoder;
    private final CheckpointExtension.CheckpointStorage checkpointStorage;
    private final RenderPass.@Nullable RenderArea renderArea;
    private final int outputWidth;
    private final int outputHeight;
    private final boolean hasDepth;
    private final boolean hasStencil;
    private final Supplier<String> label;
    protected int pushedDebugGroups = 0;
    private final VkCommandBuffer commandBuffer;
    protected @Nullable VulkanRenderPipeline pipeline;
    private boolean anyDescriptorDirty = false;
    protected final HashMap<String, GpuBufferSlice> uniforms = new HashMap<>();
    protected final HashMap<String, VulkanRenderPass.TextureViewAndSampler> textures = new HashMap<>();

    public VulkanRenderPass(
        VulkanDevice device,
        VulkanCommandEncoder encoder,
        VkCommandBuffer commandBuffer,
        CheckpointExtension.CheckpointStorage checkpointStorage,
        RenderPass.RenderArea renderArea,
        int outputWidth,
        int outputHeight,
        boolean hasDepth,
        boolean hasStencil,
        Supplier<String> label
    ) {
        this.device = device;
        this.encoder = encoder;
        this.commandBuffer = commandBuffer;
        this.checkpointStorage = checkpointStorage;
        this.renderArea = renderArea;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.hasDepth = hasDepth;
        this.hasStencil = hasStencil;
        this.label = label;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Buffer viewport = VkViewport.calloc(1, stack);
            viewport.x(0.0F);
            viewport.y(0.0F);
            viewport.width(outputWidth);
            viewport.height(outputHeight);
            viewport.minDepth(0.0F);
            viewport.maxDepth(1.0F);
            VK12.vkCmdSetViewport(this.commandBuffer(), 0, viewport);
            setScissor(stack, this.commandBuffer(), renderArea.x(), renderArea.y(), renderArea.width(), renderArea.height());
        }
    }

    private VkCommandBuffer commandBuffer() {
        return this.commandBuffer;
    }

    @Override
    public void pushDebugGroup(Supplier<String> label) {
        this.pushedDebugGroups++;
        this.device.instance().debug().beginDebugGroup(this.commandBuffer(), label);
    }

    @Override
    public void popDebugGroup() {
        if (this.pushedDebugGroups == 0) {
            throw new IllegalStateException("Can't pop more debug groups than was pushed!");
        }

        this.pushedDebugGroups--;
        this.device.instance().debug().endDebugGroup(this.commandBuffer());
    }

    @Override
    public void setPipeline(RenderPipeline pipeline) {
        this.pipeline = this.device.getOrCompilePipeline(pipeline);
        if (!this.pipeline.isValid()) {
            throw new IllegalStateException("Pipeline is not valid (may contain invalid shaders?)");
        }

        this.anyDescriptorDirty = true;
        if (this.hasStencil) {
            VK12.vkCmdBindPipeline(this.commandBuffer(), VK12.VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline.withDepthStencilPipeline());
            return;
        }
        VK12.vkCmdBindPipeline(this.commandBuffer(), 0, this.hasDepth ? this.pipeline.withDepthPipeline() : this.pipeline.withoutDepthPipeline());
    }

    @Override
    public void bindTexture(String name, @Nullable GpuTextureView textureView, @Nullable GpuSampler sampler) {
        if (textureView != null && sampler != null) {
            this.textures.put(name, new VulkanRenderPass.TextureViewAndSampler((VulkanGpuTextureView)textureView, (VulkanGpuSampler)sampler));
            this.anyDescriptorDirty = true;
        } else if (textureView == null && sampler == null) {
            this.textures.remove(name);
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void setUniform(String name, GpuBuffer value) {
        this.uniforms.put(name, value.slice());
        this.anyDescriptorDirty = true;
    }

    @Override
    public void setUniform(String name, GpuBufferSlice value) {
        this.uniforms.put(name, value);
        this.anyDescriptorDirty = true;
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.x(x);
            viewport.y(y);
            viewport.width(width);
            viewport.height(height);
            VK12.vkCmdSetViewport(this.commandBuffer(), 0, viewport);
        }
    }

    @Override
    public void enableScissor(int x, int y, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            setScissor(stack, this.commandBuffer(), x, y, width, height);
        }
    }

    private static void setScissor(MemoryStack stack, VkCommandBuffer commandBuffer, int x, int y, int width, int height) {
        org.lwjgl.vulkan.VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
        scissor.offset().set(x, y);
        scissor.extent().set(width, height);
        VK12.vkCmdSetScissor(commandBuffer, 0, scissor);
    }

    @Override
    public void disableScissor() {
        if (this.renderArea != null) {
            this.enableScissor(this.renderArea.x(), this.renderArea.y(), this.renderArea.width(), this.renderArea.height());
        } else {
            this.enableScissor(0, 0, this.outputWidth, this.outputHeight);
        }
    }

    @Override
    public void setVertexBuffer(int slot, @Nullable GpuBufferSlice vertexBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = vertexBuffer != null ? ((VulkanGpuBuffer)vertexBuffer.buffer()).vkBuffer() : 0L;
            long offset = vertexBuffer != null ? vertexBuffer.offset() : 0L;
            VK12.vkCmdBindVertexBuffers(this.commandBuffer(), slot, stack.longs(buffer), stack.longs(offset));
        }
    }

    @Override
    public void setIndexBuffer(GpuBuffer indexBuffer, IndexType indexType) {
        int type = switch (indexType) {
            case SHORT -> 0;
            case INT -> 1;
        };
        VK12.vkCmdBindIndexBuffer(this.commandBuffer(), ((VulkanGpuBuffer)indexBuffer).vkBuffer(), 0L, type);
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        if (this.pipeline != null && this.pipeline.isValid()) {
            this.pushDescriptors();
            VK12.vkCmdDrawIndexed(this.commandBuffer(), indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
        } else {
            throw new IllegalStateException("Pipeline is missing or not valid");
        }
    }

    @Override
    public void multiDrawIndexed(IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
        if (this.pipeline != null && this.pipeline.isValid()) {
            this.pushDescriptors();
            EXTMultiDraw.nvkCmdDrawMultiIndexedEXT(
                this.commandBuffer(), drawCount, MemoryUtil.memAddress(drawParameters), instanceCount, firstInstance, VkMultiDrawIndexedInfoEXT.SIZEOF, 0L
            );
        } else {
            throw new IllegalStateException("Pipeline is missing or not valid");
        }
    }

    @Override
    public void multiDrawIndexed(PointerBuffer firstIndexOffsets, IntBuffer indexCounts, IntBuffer vertexOffsets, int drawCount) {
        throw new UnsupportedOperationException("Vulkan does not support the multiDrawDirectSeparate device feature");
    }

    @Override
    public void drawIndexedIndirect(GpuBufferSlice commands, int drawCount) {
        if (this.pipeline != null && this.pipeline.isValid()) {
            this.pushDescriptors();
            VK12.vkCmdDrawIndexedIndirect(
                this.commandBuffer(), ((VulkanGpuBuffer)commands.buffer()).vkBuffer(), commands.offset(), drawCount, VkDrawIndexedIndirectCommand.SIZEOF
            );
        } else {
            throw new IllegalStateException("Pipeline is missing or not valid");
        }
    }

    @Override
    public <T> void drawMultipleIndexed(
        Collection<RenderPass.Draw<T>> draws,
        @Nullable GpuBuffer defaultIndexBuffer,
        @Nullable IndexType defaultIndexType,
        Collection<String> dynamicUniforms,
        T uniformArgument
    ) {
        for (RenderPass.Draw<T> draw : draws) {
            BiConsumer<T, RenderPass.UniformUploader> uniformUploaderConsumer = draw.uniformUploaderConsumer();
            if (uniformUploaderConsumer != null) {
                uniformUploaderConsumer.accept(uniformArgument, this::setUniform);
            }

            assert draw.indexBuffer() != null || defaultIndexBuffer != null;
            assert draw.indexType() != null || defaultIndexType != null;
            this.setIndexBuffer(
                draw.indexBuffer() == null ? defaultIndexBuffer : draw.indexBuffer(), draw.indexType() == null ? defaultIndexType : draw.indexType()
            );
            this.setVertexBuffer(draw.slot(), draw.vertexBuffer().slice());
            this.drawIndexed(draw.indexCount(), 1, draw.firstIndex(), draw.baseVertex(), 0);
        }
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        if (this.pipeline != null && this.pipeline.isValid()) {
            this.pushDescriptors();
            VK12.vkCmdDraw(this.commandBuffer(), vertexCount, instanceCount, firstVertex, firstInstance);
        }
    }

    @Override
    public void multiDraw(IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
        if (this.pipeline != null && this.pipeline.isValid()) {
            this.pushDescriptors();
            EXTMultiDraw.nvkCmdDrawMultiEXT(
                this.commandBuffer(), drawCount, MemoryUtil.memAddress(drawParameters), instanceCount, firstInstance, VkMultiDrawInfoEXT.SIZEOF
            );
        } else {
            throw new IllegalStateException("Pipeline is missing or not valid");
        }
    }

    @Override
    public void multiDraw(IntBuffer firstVertices, IntBuffer vertexCounts, int drawCount) {
        throw new UnsupportedOperationException("Vulkan does not support the multiDrawDirectSeparate device feature");
    }

    @Override
    public void drawIndirect(GpuBufferSlice commands, int drawCount) {
        if (this.pipeline != null && this.pipeline.isValid()) {
            this.pushDescriptors();
            VK12.vkCmdDrawIndirect(
                this.commandBuffer(), ((VulkanGpuBuffer)commands.buffer()).vkBuffer(), commands.offset(), drawCount, VkDrawIndirectCommand.SIZEOF
            );
        } else {
            throw new IllegalStateException("Pipeline is missing or not valid");
        }
    }

    private void pushDescriptors() {
        if (this.anyDescriptorDirty) {
            if (VALIDATION) {
                for (BindGroupLayout.UniformDescription uniform : BindGroupLayout.flattenUniforms(this.pipeline.info().getBindGroupLayouts())) {
                    GpuBufferSlice value = this.uniforms.get(uniform.name());
                    if (value == null) {
                        throw new IllegalStateException("Missing uniform " + uniform.name() + " (should be " + uniform.type() + ")");
                    }

                    if (uniform.type() == UniformType.UNIFORM_BUFFER) {
                        if (value.buffer().isClosed()) {
                            throw new IllegalStateException("Uniform buffer " + uniform.name() + " is already closed");
                        }

                        if ((value.buffer().usage() & 128) == 0) {
                            throw new IllegalStateException("Uniform buffer " + uniform.name() + " must have GpuBuffer.USAGE_UNIFORM");
                        }
                    }

                    if (uniform.type() == UniformType.TEXEL_BUFFER) {
                        if (value.offset() != 0L || value.length() != value.buffer().size()) {
                            throw new IllegalStateException("Uniform texel buffers do not support a slice of a buffer, must be entire buffer");
                        }

                        if ((value.buffer().usage() & 256) == 0) {
                            throw new IllegalStateException("Uniform texel buffer " + uniform.name() + " must have GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER");
                        }

                        if (uniform.gpuFormat() == null) {
                            throw new IllegalStateException("Invalid uniform texel buffer " + uniform.name() + " (missing a texture format)");
                        }
                    }
                }
            }

            assert this.pipeline != null;
            VulkanBindGroupLayout layout = this.pipeline.layout();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(layout.entries().size(), stack);

                for (int i = 0; i < layout.entries().size(); i++) {
                    VulkanBindGroupLayout.Entry entry = layout.entries().get(i);
                    VkWriteDescriptorSet set = writes.get().sType$Default();
                    set.dstBinding(i);
                    set.dstArrayElement(0);
                    set.descriptorCount(1);
                    if (entry.type() == VulkanBindGroupLayout.VulkanBindGroupEntryType.UNIFORM_BUFFER) {
                        GpuBufferSlice buffer = this.uniforms.get(entry.name());
                        if (buffer == null) {
                            throw new IllegalStateException("Missing uniform " + entry.name() + " (should be " + entry.type() + ")");
                        }

                        org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
                        bufferInfo.buffer(((VulkanGpuBuffer)buffer.buffer()).vkBuffer());
                        bufferInfo.offset(buffer.offset());
                        bufferInfo.range(buffer.length());
                        set.descriptorType(6);
                        set.pBufferInfo(bufferInfo);
                    } else if (entry.type() == VulkanBindGroupLayout.VulkanBindGroupEntryType.SAMPLED_IMAGE) {
                        VulkanRenderPass.TextureViewAndSampler value = this.textures.get(entry.name());
                        if (value == null) {
                            throw new IllegalStateException("Missing sampler " + entry.name());
                        }

                        org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
                        imageInfo.sampler(value.sampler.vkSampler());
                        imageInfo.imageView(value.view.vkImageView());
                        imageInfo.imageLayout(1);
                        set.descriptorType(1);
                        set.pImageInfo(imageInfo);
                    } else if (entry.type() == VulkanBindGroupLayout.VulkanBindGroupEntryType.TEXEL_BUFFER) {
                        GpuBufferSlice value = this.uniforms.get(entry.name());
                        if (value == null) {
                            throw new IllegalStateException("Missing uniform " + entry.name() + " (should be " + entry.type() + ")");
                        }

                        LongBuffer bufferViewPtr = stack.callocLong(1);

                        try (MemoryStack var9 = stack.push()) {
                            assert entry.texelBufferFormat() != null;
                            VkBufferViewCreateInfo viewCreateInfo = VkBufferViewCreateInfo.calloc(stack).sType$Default();
                            viewCreateInfo.buffer(((VulkanGpuBuffer)value.buffer()).vkBuffer());
                            viewCreateInfo.offset(value.offset());
                            viewCreateInfo.range(value.length());
                            viewCreateInfo.format(VulkanConst.toVk(entry.texelBufferFormat()));
                            VulkanUtils.crashIfFailure(
                                this.device,
                                VK12.vkCreateBufferView(this.device.vkDevice(), viewCreateInfo, null, bufferViewPtr),
                                "Couldn't create buffer view for texel buffer"
                            );
                            long bufferViewHandle = bufferViewPtr.get(0);
                            this.encoder.queueForDestroy(() -> VK12.vkDestroyBufferView(this.device.vkDevice(), bufferViewHandle, null));
                        }

                        set.descriptorType(4);
                        set.pTexelBufferView(bufferViewPtr);
                    }
                }

                KHRPushDescriptor.vkCmdPushDescriptorSetKHR(this.commandBuffer(), 0, this.pipeline.pipelineLayout(), 0, writes.flip());
            }

            this.anyDescriptorDirty = false;
        }
    }

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        long queryPool = ((VulkanQueryPool)pool).vkQueryPool();
        VK12.vkResetQueryPool(this.device.vkDevice(), queryPool, index, 1);
        KHRSynchronization2.vkCmdWriteTimestamp2KHR(this.commandBuffer(), 65536L, queryPool, index);
    }

    public Supplier<String> getLabel() {
        return this.label;
    }

    protected record TextureViewAndSampler(VulkanGpuTextureView view, VulkanGpuSampler sampler) {
    }
}
