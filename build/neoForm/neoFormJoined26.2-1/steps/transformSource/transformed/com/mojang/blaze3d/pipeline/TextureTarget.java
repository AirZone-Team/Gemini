package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class TextureTarget extends RenderTarget {
    public TextureTarget(@Nullable String label, int width, int height, boolean useDepth, GpuFormat format) {
        this(label, width, height, useDepth, false, format);
    }

    public TextureTarget(@Nullable String label, int width, int height, boolean useDepth, boolean useStencil, GpuFormat format) {
        super(label, useDepth, useStencil, format);
        RenderSystem.assertOnRenderThread();
        this.resize(width, height);
    }
}
