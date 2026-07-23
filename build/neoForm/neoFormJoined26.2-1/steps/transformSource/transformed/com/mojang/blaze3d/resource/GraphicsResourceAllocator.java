package com.mojang.blaze3d.resource;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface GraphicsResourceAllocator {
    GraphicsResourceAllocator UNPOOLED = new GraphicsResourceAllocator() {
        @Override
        public <T> T acquire(ResourceDescriptor<T> descriptor) {
            T resource = descriptor.allocate();
            descriptor.prepare(resource);
            return resource;
        }

        @Override
        public <T> void release(ResourceDescriptor<T> descriptor, T resource) {
            descriptor.free(resource);
        }
    };

    <T> T acquire(ResourceDescriptor<T> descriptor);

    <T> void release(ResourceDescriptor<T> descriptor, T resource);
}
