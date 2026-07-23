package com.mojang.blaze3d.opengl;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface FrameBufferAttachment {
    int glId();

    int fboMipLevel();

    void addAssociatedFbo(FrameBufferCache.CacheKey fboKey);

    void removeAssociatedFbo(FrameBufferCache.CacheKey fboKey);

    default boolean hasStencilAspect() {
        return false;
    }
}
