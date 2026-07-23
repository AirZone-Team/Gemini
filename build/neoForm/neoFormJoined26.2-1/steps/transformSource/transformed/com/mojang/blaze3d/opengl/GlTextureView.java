package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GlTextureView extends GpuTextureView implements FrameBufferAttachment {
    private static final int EMPTY = -1;
    private boolean closed;
    private final FrameBufferCache frameBufferCache;
    private final List<FrameBufferCache.CacheKey> fboKeys = new ArrayList<>();

    protected GlTextureView(GlTexture texture, int baseMipLevel, int mipLevels, FrameBufferCache frameBufferCache) {
        super(texture, baseMipLevel, mipLevels);
        texture.addViews();
        this.frameBufferCache = frameBufferCache;
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.texture().removeViews();

            while (!this.fboKeys.isEmpty()) {
                this.frameBufferCache.destroyFbo(this.fboKeys.getLast());
            }
        }
    }

    public GlTexture texture() {
        return (GlTexture)super.texture();
    }

    @Override
    public int glId() {
        return this.texture().id;
    }

    @Override
    public int fboMipLevel() {
        return this.baseMipLevel();
    }

    @Override
    public void addAssociatedFbo(FrameBufferCache.CacheKey fboKey) {
        this.fboKeys.add(fboKey);
    }

    @Override
    public void removeAssociatedFbo(FrameBufferCache.CacheKey fboKey) {
        this.fboKeys.remove(fboKey);
    }

    @Override
    public boolean hasStencilAspect() {
        return this.texture().hasStencilAspect();
    }
}
