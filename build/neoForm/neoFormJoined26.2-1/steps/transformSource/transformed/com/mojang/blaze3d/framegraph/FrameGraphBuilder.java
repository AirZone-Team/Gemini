package com.mojang.blaze3d.framegraph;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class FrameGraphBuilder {
    private final List<FrameGraphBuilder.InternalVirtualResource<?>> internalResources = new ArrayList<>();
    private final List<FrameGraphBuilder.ExternalResource<?>> externalResources = new ArrayList<>();
    private final List<FrameGraphBuilder.Pass> passes = new ArrayList<>();

    public FramePass addPass(String name) {
        FrameGraphBuilder.Pass pass = new FrameGraphBuilder.Pass(this.passes.size(), name);
        this.passes.add(pass);
        return pass;
    }

    public <T> ResourceHandle<T> importExternal(String name, T resource) {
        FrameGraphBuilder.ExternalResource<T> holder = new FrameGraphBuilder.ExternalResource<>(name, null, resource);
        this.externalResources.add(holder);
        return holder.handle;
    }

    public <T> ResourceHandle<T> createInternal(String name, ResourceDescriptor<T> descriptor) {
        return this.createInternalResource(name, descriptor, null).handle;
    }

    private <T> FrameGraphBuilder.InternalVirtualResource<T> createInternalResource(
        String name, ResourceDescriptor<T> descriptor, FrameGraphBuilder.@Nullable Pass createdBy
    ) {
        int id = this.internalResources.size();
        FrameGraphBuilder.InternalVirtualResource<T> resource = new FrameGraphBuilder.InternalVirtualResource<>(id, name, createdBy, descriptor);
        this.internalResources.add(resource);
        return resource;
    }

    public void execute(GraphicsResourceAllocator resourceAllocator) {
        this.execute(resourceAllocator, FrameGraphBuilder.Inspector.NONE);
    }

    public void execute(GraphicsResourceAllocator resourceAllocator, FrameGraphBuilder.Inspector inspector) {
        BitSet passesToKeep = this.identifyPassesToKeep();
        List<FrameGraphBuilder.Pass> passesInOrder = new ArrayList<>(passesToKeep.cardinality());
        BitSet visiting = new BitSet(this.passes.size());

        for (FrameGraphBuilder.Pass pass : this.passes) {
            this.resolvePassOrder(pass, passesToKeep, visiting, passesInOrder);
        }

        this.assignResourceLifetimes(passesInOrder);

        for (FrameGraphBuilder.Pass pass : passesInOrder) {
            for (FrameGraphBuilder.InternalVirtualResource<?> resource : pass.resourcesToAcquire) {
                inspector.acquireResource(resource.name);
                resource.acquire(resourceAllocator);
            }

            inspector.beforeExecutePass(pass.name);
            pass.task.run();
            inspector.afterExecutePass(pass.name);

            for (int id = pass.resourcesToRelease.nextSetBit(0); id >= 0; id = pass.resourcesToRelease.nextSetBit(id + 1)) {
                FrameGraphBuilder.InternalVirtualResource<?> resource = this.internalResources.get(id);
                inspector.releaseResource(resource.name);
                resource.release(resourceAllocator);
            }
        }
    }

    private BitSet identifyPassesToKeep() {
        Deque<FrameGraphBuilder.Pass> scratchQueue = new ArrayDeque<>(this.passes.size());
        BitSet passesToKeep = new BitSet(this.passes.size());

        for (FrameGraphBuilder.VirtualResource<?> resource : this.externalResources) {
            FrameGraphBuilder.Pass pass = resource.handle.createdBy;
            if (pass != null) {
                this.discoverAllRequiredPasses(pass, passesToKeep, scratchQueue);
            }
        }

        for (FrameGraphBuilder.Pass pass : this.passes) {
            if (pass.disableCulling) {
                this.discoverAllRequiredPasses(pass, passesToKeep, scratchQueue);
            }
        }

        return passesToKeep;
    }

    private void discoverAllRequiredPasses(FrameGraphBuilder.Pass sourcePass, BitSet visited, Deque<FrameGraphBuilder.Pass> passesToTrace) {
        passesToTrace.add(sourcePass);

        while (!passesToTrace.isEmpty()) {
            FrameGraphBuilder.Pass pass = passesToTrace.poll();
            if (!visited.get(pass.id)) {
                visited.set(pass.id);

                for (int id = pass.requiredPassIds.nextSetBit(0); id >= 0; id = pass.requiredPassIds.nextSetBit(id + 1)) {
                    passesToTrace.add(this.passes.get(id));
                }
            }
        }
    }

    private void resolvePassOrder(FrameGraphBuilder.Pass pass, BitSet passesToFind, BitSet visiting, List<FrameGraphBuilder.Pass> output) {
        if (visiting.get(pass.id)) {
            String involvedPasses = visiting.stream().mapToObj(idx -> this.passes.get(idx).name).collect(Collectors.joining(", "));
            throw new IllegalStateException("Frame graph cycle detected between " + involvedPasses);
        }

        if (passesToFind.get(pass.id)) {
            visiting.set(pass.id);
            passesToFind.clear(pass.id);

            for (int id = pass.requiredPassIds.nextSetBit(0); id >= 0; id = pass.requiredPassIds.nextSetBit(id + 1)) {
                this.resolvePassOrder(this.passes.get(id), passesToFind, visiting, output);
            }

            for (FrameGraphBuilder.Handle<?> handle : pass.writesFrom) {
                for (int id = handle.readBy.nextSetBit(0); id >= 0; id = handle.readBy.nextSetBit(id + 1)) {
                    if (id != pass.id) {
                        this.resolvePassOrder(this.passes.get(id), passesToFind, visiting, output);
                    }
                }
            }

            output.add(pass);
            visiting.clear(pass.id);
        }
    }

    private void assignResourceLifetimes(Collection<FrameGraphBuilder.Pass> passesInOrder) {
        FrameGraphBuilder.Pass[] lastPassByResource = new FrameGraphBuilder.Pass[this.internalResources.size()];

        for (FrameGraphBuilder.Pass pass : passesInOrder) {
            for (int id = pass.requiredResourceIds.nextSetBit(0); id >= 0; id = pass.requiredResourceIds.nextSetBit(id + 1)) {
                FrameGraphBuilder.InternalVirtualResource<?> resource = this.internalResources.get(id);
                FrameGraphBuilder.Pass lastPass = lastPassByResource[id];
                lastPassByResource[id] = pass;
                if (lastPass == null) {
                    pass.resourcesToAcquire.add(resource);
                } else {
                    lastPass.resourcesToRelease.clear(id);
                }

                pass.resourcesToRelease.set(id);
            }
        }
    }

    private static class ExternalResource<T> extends FrameGraphBuilder.VirtualResource<T> {
        private final T resource;

        public ExternalResource(String name, FrameGraphBuilder.@Nullable Pass createdBy, T resource) {
            super(name, createdBy);
            this.resource = resource;
        }

        @Override
        public T get() {
            return this.resource;
        }
    }

    private static class Handle<T> implements ResourceHandle<T> {
        private final FrameGraphBuilder.VirtualResource<T> holder;
        private final int version;
        private final FrameGraphBuilder.@Nullable Pass createdBy;
        private final BitSet readBy = new BitSet();
        private FrameGraphBuilder.@Nullable Handle<T> aliasedBy;

        private Handle(FrameGraphBuilder.VirtualResource<T> holder, int version, FrameGraphBuilder.@Nullable Pass createdBy) {
            this.holder = holder;
            this.version = version;
            this.createdBy = createdBy;
        }

        @Override
        public T get() {
            return this.holder.get();
        }

        private FrameGraphBuilder.Handle<T> writeAndAlias(FrameGraphBuilder.Pass pass) {
            if (this.holder.handle != this) {
                throw new IllegalStateException("Handle " + this + " is no longer valid, as its contents were moved into " + this.aliasedBy);
            }

            FrameGraphBuilder.Handle<T> newHandle = new FrameGraphBuilder.Handle<>(this.holder, this.version + 1, pass);
            this.holder.handle = newHandle;
            this.aliasedBy = newHandle;
            return newHandle;
        }

        @Override
        public String toString() {
            return this.createdBy != null ? this.holder + "#" + this.version + " (from " + this.createdBy + ")" : this.holder + "#" + this.version;
        }
    }

    public interface Inspector {
        FrameGraphBuilder.Inspector NONE = new FrameGraphBuilder.Inspector() {};

        default void acquireResource(String name) {
        }

        default void releaseResource(String name) {
        }

        default void beforeExecutePass(String name) {
        }

        default void afterExecutePass(String name) {
        }
    }

    private static class InternalVirtualResource<T> extends FrameGraphBuilder.VirtualResource<T> {
        private final int id;
        private final ResourceDescriptor<T> descriptor;
        private @Nullable T physicalResource;

        public InternalVirtualResource(int id, String name, FrameGraphBuilder.@Nullable Pass createdBy, ResourceDescriptor<T> descriptor) {
            super(name, createdBy);
            this.id = id;
            this.descriptor = descriptor;
        }

        @Override
        public T get() {
            return Objects.requireNonNull(this.physicalResource, "Resource is not currently available");
        }

        public void acquire(GraphicsResourceAllocator allocator) {
            if (this.physicalResource != null) {
                throw new IllegalStateException("Tried to acquire physical resource, but it was already assigned");
            }

            this.physicalResource = allocator.acquire(this.descriptor);
        }

        public void release(GraphicsResourceAllocator allocator) {
            if (this.physicalResource == null) {
                throw new IllegalStateException("Tried to release physical resource that was not allocated");
            }

            allocator.release(this.descriptor, this.physicalResource);
            this.physicalResource = null;
        }
    }

    private class Pass implements FramePass {
        private final int id;
        private final String name;
        private final List<FrameGraphBuilder.Handle<?>> writesFrom = new ArrayList<>();
        private final BitSet requiredResourceIds = new BitSet();
        private final BitSet requiredPassIds = new BitSet();
        private Runnable task = () -> {};
        private final List<FrameGraphBuilder.InternalVirtualResource<?>> resourcesToAcquire = new ArrayList<>();
        private final BitSet resourcesToRelease = new BitSet();
        private boolean disableCulling;

        public Pass(int id, String name) {
            this.id = id;
            this.name = name;
        }

        private <T> void markResourceRequired(FrameGraphBuilder.Handle<T> handle) {
            if (handle.holder instanceof FrameGraphBuilder.InternalVirtualResource<?> resource) {
                this.requiredResourceIds.set(resource.id);
            }
        }

        private void markPassRequired(FrameGraphBuilder.Pass pass) {
            this.requiredPassIds.set(pass.id);
        }

        @Override
        public <T> ResourceHandle<T> createsInternal(String name, ResourceDescriptor<T> descriptor) {
            FrameGraphBuilder.InternalVirtualResource<T> resource = FrameGraphBuilder.this.createInternalResource(name, descriptor, this);
            this.requiredResourceIds.set(resource.id);
            return resource.handle;
        }

        @Override
        public <T> void reads(ResourceHandle<T> handle) {
            this._reads((FrameGraphBuilder.Handle<T>)handle);
        }

        private <T> void _reads(FrameGraphBuilder.Handle<T> handle) {
            this.markResourceRequired(handle);
            if (handle.createdBy != null) {
                this.markPassRequired(handle.createdBy);
            }

            handle.readBy.set(this.id);
        }

        @Override
        public <T> ResourceHandle<T> readsAndWrites(ResourceHandle<T> handle) {
            return this._readsAndWrites((FrameGraphBuilder.Handle<T>)handle);
        }

        @Override
        public void requires(FramePass pass) {
            this.requiredPassIds.set(((FrameGraphBuilder.Pass)pass).id);
        }

        @Override
        public void disableCulling() {
            this.disableCulling = true;
        }

        private <T> FrameGraphBuilder.Handle<T> _readsAndWrites(FrameGraphBuilder.Handle<T> handle) {
            this.writesFrom.add(handle);
            this._reads(handle);
            return handle.writeAndAlias(this);
        }

        @Override
        public void executes(Runnable task) {
            this.task = task;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    private abstract static class VirtualResource<T> {
        public final String name;
        public FrameGraphBuilder.Handle<T> handle;

        public VirtualResource(String name, FrameGraphBuilder.@Nullable Pass createdBy) {
            this.name = name;
            this.handle = new FrameGraphBuilder.Handle<>(this, 0, createdBy);
        }

        public abstract T get();

        @Override
        public String toString() {
            return this.name;
        }
    }
}
