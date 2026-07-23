package com.mojang.blaze3d.vertex;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public abstract class StagingBuffer implements AutoCloseable {
    private int nextWriteOffset;
    private int usedBufferCount;

    public static StagingBuffer create(String name, GpuDevice gpuDevice, int bufferSize) {
        return gpuDevice.getDeviceInfo().hintsAndWorkarounds().writeToBufferIsSlow() && gpuDevice.getDeviceInfo().features().persistentMapping()
            ? new StagingBuffer.PersistentlyMapped(name, bufferSize)
            : new StagingBuffer.Cpu(bufferSize);
    }

    public StagingBuffer.@Nullable BufferHandle tryAppend(ByteBuffer buffer) {
        int writeOffset = this.nextWriteOffset;
        int bufferSize = buffer.remaining();
        ByteBuffer writeBuffer = this.getWriteBuffer();
        if (bufferSize > writeBuffer.capacity()) {
            throw new IllegalArgumentException("Cannot fit allocation of size " + bufferSize + " into staging buffer of size " + writeBuffer.capacity());
        }

        if (bufferSize > writeBuffer.capacity() - writeOffset) {
            return null;
        }

        MemoryUtil.memCopy(buffer, writeBuffer.position(writeOffset));
        this.nextWriteOffset += bufferSize;
        this.usedBufferCount++;
        return new StagingBuffer.BufferHandle(writeOffset, bufferSize);
    }

    protected abstract ByteBuffer getWriteBuffer();

    protected abstract void copyTo(final CommandEncoder encoder, final GpuBuffer dstBuffer, long dstOffset, long stagingBufferOffset, long copySize);

    protected void rotateBuffer() {
    }

    public StagingBuffer.Uploader startUploading(CommandEncoder encoder) {
        return new StagingBuffer.Uploader(encoder);
    }

    private void tryClearAndRotate() {
        if (this.nextWriteOffset > 0 && this.usedBufferCount == 0) {
            this.rotateBuffer();
            this.nextWriteOffset = 0;
        }
    }

    @Override
    public abstract void close();

    public class BufferHandle implements AutoCloseable {
        private final int offset;
        private final int size;
        private boolean closed;

        public BufferHandle(int offset, int size) {
            this.offset = offset;
            this.size = size;
        }

        private void checkValidFor(StagingBuffer stagingBuffer) {
            if (this.closed) {
                throw new IllegalStateException("Buffer has already been closed");
            }

            if (stagingBuffer != StagingBuffer.this) {
                throw new IllegalArgumentException("Buffer is not valid for " + stagingBuffer);
            }
        }

        public long size() {
            return this.size;
        }

        @Override
        public void close() {
            if (!this.closed) {
                this.closed = true;
                StagingBuffer.this.usedBufferCount--;
            }
        }
    }

    private static class Cpu extends StagingBuffer {
        private final ByteBuffer stagingBuffer;

        private Cpu(int bufferSize) {
            this.stagingBuffer = MemoryUtil.memAlloc(bufferSize);
        }

        @Override
        protected ByteBuffer getWriteBuffer() {
            return this.stagingBuffer;
        }

        @Override
        protected void copyTo(CommandEncoder encoder, GpuBuffer dstBuffer, long dstOffset, long stagingBufferOffset, long copySize) {
            encoder.writeToBuffer(dstBuffer.slice(dstOffset, copySize), this.stagingBuffer.slice((int)stagingBufferOffset, (int)copySize));
        }

        @Override
        public void close() {
            MemoryUtil.memFree(this.stagingBuffer);
        }
    }

    private static class PersistentlyMapped extends StagingBuffer {
        private final MappableRingBuffer mappableRingBuffer;
        private GpuBufferSlice.MappedView currentMappedView;
        private GpuBuffer currentGPUBuffer;
        private ByteBuffer currentBuffer;

        private PersistentlyMapped(String name, int bufferSize) {
            this.mappableRingBuffer = new MappableRingBuffer(() -> name + " staging buffer", 18, bufferSize / 2);
            this.currentGPUBuffer = this.mappableRingBuffer.currentBuffer();
            this.currentMappedView = this.currentGPUBuffer.map(false, true);
            this.currentBuffer = this.currentMappedView.data();
        }

        @Override
        protected ByteBuffer getWriteBuffer() {
            return this.currentBuffer;
        }

        @Override
        protected void copyTo(CommandEncoder encoder, GpuBuffer dstBuffer, long dstOffset, long stagingBufferOffset, long copySize) {
            encoder.copyToBuffer(this.currentGPUBuffer.slice(stagingBufferOffset, copySize), dstBuffer.slice(dstOffset, copySize));
        }

        @Override
        protected void rotateBuffer() {
            this.currentMappedView.close();
            this.mappableRingBuffer.rotate();
            this.currentGPUBuffer = this.mappableRingBuffer.currentBuffer();
            this.currentMappedView = this.currentGPUBuffer.map(false, true);
            this.currentBuffer = this.currentMappedView.data();
        }

        @Override
        public void close() {
            this.currentMappedView.close();
            this.mappableRingBuffer.close();
        }
    }

    public class Uploader implements AutoCloseable {
        private final CommandEncoder encoder;

        public Uploader(CommandEncoder encoder) {
            this.encoder = encoder;
        }

        public void copyTo(StagingBuffer.BufferHandle srcBuffer, GpuBuffer dstBuffer, long dstOffset) {
            srcBuffer.checkValidFor(StagingBuffer.this);
            StagingBuffer.this.copyTo(this.encoder, dstBuffer, dstOffset, srcBuffer.offset, srcBuffer.size);
        }

        @Override
        public void close() {
            StagingBuffer.this.tryClearAndRotate();
        }

        public void checkValidFor(StagingBuffer stagingBuffer) {
            if (stagingBuffer != StagingBuffer.this) {
                throw new IllegalArgumentException("Uploader is not valid for " + stagingBuffer);
            }
        }
    }
}
