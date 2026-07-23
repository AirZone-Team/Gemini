package com.mojang.blaze3d.vertex;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class UberGpuBuffer<T> implements AutoCloseable {
    private final @GpuBuffer.Usage int bufferUsage;
    private final int heapSize;
    private final int alignSize;
    private final String name;
    private final List<Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap>> nodes = new ArrayList<>();
    private final StagingBuffer stagingBuffer;
    private final Object2ObjectOpenHashMap<T, UberGpuBuffer.StagedAllocationEntry<? extends T>> stagedAllocations = new Object2ObjectOpenHashMap<>(32);
    private final ObjectOpenHashSet<T> skippedStagedAllocations = new ObjectOpenHashSet<>(32);
    private final Map<T, TlsfAllocator.Allocation> allocationMap = new HashMap<>(256);

    public UberGpuBuffer(String name, @GpuBuffer.Usage int bufferUsage, int heapSize, int alignSize, StagingBuffer stagingBuffer) {
        this.name = "UberBuffer " + name;
        this.bufferUsage = bufferUsage;
        this.heapSize = heapSize;
        this.alignSize = alignSize;
        this.stagingBuffer = stagingBuffer;
    }

    public <U extends T> boolean addAllocation(U allocationKey, UberGpuBuffer.UploadCallback<U> callback, ByteBuffer buffer) {
        StagingBuffer.BufferHandle handle = this.stagingBuffer.tryAppend(buffer);
        if (handle == null) {
            return false;
        }

        UberGpuBuffer.StagedAllocationEntry<U> entry = new UberGpuBuffer.StagedAllocationEntry<>(handle, callback);
        UberGpuBuffer.StagedAllocationEntry<? extends T> oldEntry = this.stagedAllocations.put((T)allocationKey, entry);
        if (oldEntry != null) {
            oldEntry.close();
        }

        return true;
    }

    public boolean uploadStagedAllocations(GpuDevice gpuDevice, StagingBuffer.Uploader uploader) {
        uploader.checkValidFor(this.stagingBuffer);

        for (T key : this.stagedAllocations.keySet()) {
            this.freeAllocation(key);
        }

        boolean newHeapCreatedOrDestroyed = false;

        try (Zone var22 = Profiler.get().zone("uploadStagedAllocations")) {
            for (Entry<T, UberGpuBuffer.StagedAllocationEntry<? extends T>> entry : this.stagedAllocations.entrySet()) {
                try (UberGpuBuffer.StagedAllocationEntry<? extends T> staged = entry.getValue()) {
                    long allocationSize = staged.buffer.size();
                    if (!this.skippedStagedAllocations.contains(entry.getKey())) {
                        TlsfAllocator.Allocation allocation = null;

                        for (Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap> node : this.nodes) {
                            allocation = node.getFirst().allocate(allocationSize, this.alignSize);
                            if (allocation != null) {
                                break;
                            }
                        }

                        if (allocation == null) {
                            try (Zone var25 = Profiler.get().zone("createNewHeap")) {
                                assert allocationSize <= this.heapSize;
                                String heapName = String.format(Locale.ROOT, "%s %d", this.name, this.nodes.size());
                                UberGpuBuffer.UberGpuBufferHeap newHeap = new UberGpuBuffer.UberGpuBufferHeap(
                                    this.heapSize, gpuDevice, this.bufferUsage, heapName
                                );
                                TlsfAllocator newTlsfAllocator = new TlsfAllocator(newHeap);
                                this.nodes.add(new Pair<>(newTlsfAllocator, newHeap));
                                allocation = newTlsfAllocator.allocate(allocationSize, this.alignSize);
                                newHeapCreatedOrDestroyed = true;
                            }
                        }

                        if (allocation != null) {
                            TlsfAllocator.Heap allocationHeap = allocation.getHeap();
                            GpuBuffer allocationDestBuffer = ((UberGpuBuffer.UberGpuBufferHeap)allocationHeap).gpuBuffer;
                            uploader.copyTo(staged.buffer, allocationDestBuffer, allocation.getOffsetFromHeap());
                            this.allocationMap.put(entry.getKey(), allocation);
                            runCallbackUnchecked(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }

            this.stagedAllocations.clear();
            this.skippedStagedAllocations.clear();
        }

        Iterator<Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap>> iterator = this.nodes.iterator();

        while (iterator.hasNext()) {
            Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap> node = iterator.next();
            if (node.getFirst().isCompletelyFree()) {
                node.getSecond().gpuBuffer.close();
                iterator.remove();
                newHeapCreatedOrDestroyed = true;
                break;
            }
        }

        return newHeapCreatedOrDestroyed;
    }

    private static <T, U extends T> void runCallbackUnchecked(T key, UberGpuBuffer.StagedAllocationEntry<U> value) {
        if (value.callback != null) {
            value.callback.bufferHasBeenUploaded((U)key);
        }
    }

    public TlsfAllocator.@Nullable Allocation getAllocation(T allocationKey) {
        return this.allocationMap.get(allocationKey);
    }

    public void removeAllocation(T allocationKey) {
        this.skippedStagedAllocations.add(allocationKey);
        this.freeAllocation(allocationKey);
    }

    private void freeAllocation(T allocationKey) {
        TlsfAllocator.Allocation allocation = this.allocationMap.remove(allocationKey);
        if (allocation != null) {
            for (Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap> node : this.nodes) {
                if (node.getSecond() == allocation.getHeap()) {
                    node.getFirst().free(allocation);
                    break;
                }
            }
        }
    }

    public GpuBuffer getGpuBuffer(TlsfAllocator.Allocation allocation) {
        return ((UberGpuBuffer.UberGpuBufferHeap)allocation.getHeap()).gpuBuffer;
    }

    @VisibleForDebug
    public void printStatistics() {
        for (int i = 0; i < this.nodes.size(); i++) {
            Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap> node = this.nodes.get(i);
            String heapName = String.format(Locale.ROOT, "%s %d", this.name, i);
            node.getFirst().printAllocatorStatistics(heapName);
        }
    }

    @Override
    public void close() {
        this.stagedAllocations.values().forEach(UberGpuBuffer.StagedAllocationEntry::close);
        this.stagedAllocations.clear();
        this.allocationMap.clear();

        for (Pair<TlsfAllocator, UberGpuBuffer.UberGpuBufferHeap> node : this.nodes) {
            node.getSecond().gpuBuffer.close();
        }

        this.nodes.clear();
    }

    private record StagedAllocationEntry<T>(StagingBuffer.BufferHandle buffer, UberGpuBuffer.@Nullable UploadCallback<T> callback) implements AutoCloseable {
        @Override
        public void close() {
            this.buffer.close();
        }
    }

    public static class UberGpuBufferHeap extends TlsfAllocator.Heap {
        private final GpuBuffer gpuBuffer;

        public UberGpuBufferHeap(long size, GpuDevice gpuDevice, @GpuBuffer.Usage int usage, String name) {
            super(size);
            this.gpuBuffer = gpuDevice.createBuffer(() -> name, usage | 8 | 16, size);
        }
    }

    public interface UploadCallback<T> {
        void bufferHasBeenUploaded(T key);
    }
}
