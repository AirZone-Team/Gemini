package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.shaders.UniformType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class BindGroupLayout {
    private final List<String> samplers;
    private final List<BindGroupLayout.UniformDescription> uniforms;

    private BindGroupLayout(List<String> samplers, List<BindGroupLayout.UniformDescription> uniforms) {
        this.samplers = samplers;
        this.uniforms = uniforms;
    }

    public static BindGroupLayout.Builder builder() {
        return new BindGroupLayout.Builder();
    }

    public List<String> getSamplers() {
        return this.samplers;
    }

    public List<BindGroupLayout.UniformDescription> getUniforms() {
        return this.uniforms;
    }

    public static List<String> flattenSamplers(List<BindGroupLayout> bindGroupLayouts) {
        List<String> flattened = new ArrayList<>();

        for (BindGroupLayout bindGroupLayout : bindGroupLayouts) {
            flattened.addAll(bindGroupLayout.getSamplers());
        }

        return flattened;
    }

    public static List<BindGroupLayout.UniformDescription> flattenUniforms(List<BindGroupLayout> bindGroupLayouts) {
        List<BindGroupLayout.UniformDescription> flattened = new ArrayList<>();

        for (BindGroupLayout bindGroupLayout : bindGroupLayouts) {
            flattened.addAll(bindGroupLayout.getUniforms());
        }

        return flattened;
    }

    public static void ensureCompatible(List<BindGroupLayout> bindGroupLayouts) {
        Set<String> names = new HashSet<>();

        for (int layoutIndex = 0; layoutIndex < bindGroupLayouts.size(); layoutIndex++) {
            BindGroupLayout bindGroupLayout = bindGroupLayouts.get(layoutIndex);

            for (BindGroupLayout.UniformDescription uniform : bindGroupLayout.getUniforms()) {
                if (!names.add(uniform.name())) {
                    throw new IllegalArgumentException("Duplicate bind name '" + uniform.name() + "' in bind group layout " + layoutIndex);
                }
            }

            for (String sampler : bindGroupLayout.getSamplers()) {
                if (!names.add(sampler)) {
                    throw new IllegalArgumentException("Duplicate bind name '" + sampler + "' in bind group layout " + layoutIndex);
                }
            }
        }
    }

    public static class Builder {
        private final List<String> samplers = new ArrayList<>();
        private final List<BindGroupLayout.UniformDescription> uniforms = new ArrayList<>();

        private Builder() {
        }

        public BindGroupLayout.Builder withSampler(String sampler) {
            this.samplers.add(sampler);
            return this;
        }

        public BindGroupLayout.Builder withUniform(String name, UniformType type) {
            if (type == UniformType.TEXEL_BUFFER) {
                throw new IllegalArgumentException("Cannot use texel buffer without specifying texture format");
            }

            this.uniforms.add(new BindGroupLayout.UniformDescription(name, type));
            return this;
        }

        public BindGroupLayout.Builder withUniform(String name, UniformType type, GpuFormat format) {
            if (type != UniformType.TEXEL_BUFFER) {
                throw new IllegalArgumentException("Only texel buffer can specify texture format");
            }

            this.uniforms.add(new BindGroupLayout.UniformDescription(name, format));
            return this;
        }

        public BindGroupLayout build() {
            return new BindGroupLayout(List.copyOf(this.samplers), List.copyOf(this.uniforms));
        }
    }

    public record UniformDescription(String name, UniformType type, @Nullable GpuFormat gpuFormat) {
        public UniformDescription(String name, UniformType type) {
            this(name, type, null);
            if (type == UniformType.TEXEL_BUFFER) {
                throw new IllegalArgumentException("Texel buffer needs a texture format");
            }
        }

        public UniformDescription(String name, GpuFormat gpuFormat) {
            this(name, UniformType.TEXEL_BUFFER, gpuFormat);
        }
    }
}
