package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.GpuFormat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public sealed interface Uniform extends AutoCloseable permits Uniform.Sampler, Uniform.Ubo, Uniform.Utb {
    @Override
    default void close() {
    }

    record Sampler(int location, int samplerIndex) implements Uniform {
    }

    record Ubo(int blockBinding) implements Uniform {
    }

    record Utb(int location, int samplerIndex, GpuFormat format, int texture) implements Uniform {
        public Utb(int location, int samplerIndex, GpuFormat format) {
            this(location, samplerIndex, format, GlStateManager._genTexture());
        }

        @Override
        public void close() {
            GlStateManager._deleteTexture(this.texture);
        }
    }
}
