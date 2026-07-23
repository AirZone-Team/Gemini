package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import java.nio.ByteBuffer;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface TransientMemory {
    default ByteBuffer allocateCpu(long size, long alignment) {
        return this.allocateCpu(size, alignment, size, 1L);
    }

    ByteBuffer allocateCpu(final long size, final long alignment, final long minimumAllocation, final long elementSize);

    default GpuBufferSlice.MappedView allocateStaging(long size, long alignment, @GpuBuffer.Usage int usage) {
        return this.allocateStaging(size, alignment, usage, size, 1L);
    }

    GpuBufferSlice.MappedView allocateStaging(
        final long size, final long alignment, final @GpuBuffer.Usage int usage, final long minimumAllocation, final long elementSize
    );

    default GpuBufferSlice allocateGpu(long size, long alignment, @GpuBuffer.Usage int usage) {
        return this.allocateGpu(size, alignment, usage, size, 1L);
    }

    GpuBufferSlice allocateGpu(final long size, final long alignment, final @GpuBuffer.Usage int usage, final long minimumAllocation, final long elementSize);

    default GpuBufferSlice.MappedView allocateGpuMapped(long size, long alignment, @GpuBuffer.Usage int usage) {
        return this.allocateGpuMapped(size, alignment, usage, size, 1L);
    }

    GpuBufferSlice.MappedView allocateGpuMapped(
        final long size, final long alignment, final @GpuBuffer.Usage int usage, final long minimumAllocation, final long elementSize
    );

    default GpuBufferSlice uploadStaging(ByteBuffer data, long alignment, @GpuBuffer.Usage int usage) {
        return this.uploadStaging(data, alignment, usage, data.remaining(), 1L);
    }

    default GpuBufferSlice uploadStaging(ByteBuffer data, long alignment, @GpuBuffer.Usage int usage, long minimumAllocation, long elementSize) {
        return this.uploadStaging(List.of(data), alignment, usage, minimumAllocation, elementSize);
    }

    default GpuBufferSlice uploadStaging(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage) {
        long totalSize = 0L;

        for (ByteBuffer buffer : data) {
            totalSize += buffer.remaining();
        }

        return this.uploadStaging(data, alignment, usage, totalSize, 1L);
    }

    GpuBufferSlice uploadStaging(
        final List<ByteBuffer> data, final long alignment, final @GpuBuffer.Usage int usage, final long minimumAllocation, final long elementSize
    );

    default GpuBufferSlice uploadGpu(ByteBuffer data, long alignment, @GpuBuffer.Usage int usage) {
        return this.uploadGpu(data, alignment, usage, data.remaining(), 1L);
    }

    default GpuBufferSlice uploadGpu(ByteBuffer data, long alignment, @GpuBuffer.Usage int usage, long minimumAllocation, long elementSize) {
        return this.uploadGpu(List.of(data), alignment, usage, minimumAllocation, elementSize);
    }

    default GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage) {
        long totalSize = 0L;

        for (ByteBuffer buffer : data) {
            totalSize += buffer.remaining();
        }

        return this.uploadGpu(data, alignment, usage, totalSize, 1L);
    }

    GpuBufferSlice uploadGpu(
        final List<ByteBuffer> data, final long alignment, final @GpuBuffer.Usage int usage, final long minimumAllocation, final long elementSize
    );

    List<GpuBufferSlice> multiUploadStaging(final List<ByteBuffer> data, final long alignment, final @GpuBuffer.Usage int usage);

    List<GpuBufferSlice> multiUploadGpu(final List<ByteBuffer> data, final long alignment, final @GpuBuffer.Usage int usage);
}
