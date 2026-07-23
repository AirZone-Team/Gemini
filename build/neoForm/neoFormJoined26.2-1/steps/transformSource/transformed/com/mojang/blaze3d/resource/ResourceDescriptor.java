package com.mojang.blaze3d.resource;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ResourceDescriptor<T> {
    T allocate();

    default void prepare(T resource) {
    }

    void free(T resource);

    default boolean canUsePhysicalResource(ResourceDescriptor<?> other) {
        return this.equals(other);
    }
}
