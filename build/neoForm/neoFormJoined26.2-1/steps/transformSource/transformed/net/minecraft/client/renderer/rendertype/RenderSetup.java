package net.minecraft.client.renderer.rendertype;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class RenderSetup {
    final RenderPipeline pipeline;
    final Map<String, RenderSetup.TextureBinding> textures;
    final TextureTransform textureTransform;
    final OutputTarget outputTarget;
    final RenderSetup.OutlineProperty outlineProperty;
    final boolean useLightmap;
    final boolean useOverlay;
    final boolean affectsCrumbling;
    final boolean sortOnUpload;
    final LayeringTransform layeringTransform;

    private RenderSetup(
        RenderPipeline pipeline,
        Map<String, RenderSetup.TextureBinding> textures,
        boolean useLightmap,
        boolean useOverlay,
        LayeringTransform layeringTransform,
        OutputTarget outputTarget,
        TextureTransform textureTransform,
        RenderSetup.OutlineProperty outlineProperty,
        boolean affectsCrumbling,
        boolean sortOnUpload
    ) {
        this.pipeline = pipeline;
        this.textures = textures;
        this.outputTarget = outputTarget;
        this.textureTransform = textureTransform;
        this.useLightmap = useLightmap;
        this.useOverlay = useOverlay;
        this.outlineProperty = outlineProperty;
        this.layeringTransform = layeringTransform;
        this.affectsCrumbling = affectsCrumbling;
        this.sortOnUpload = sortOnUpload;
    }

    @Override
    public String toString() {
        return "RenderSetup[layeringTransform="
            + this.layeringTransform
            + ", textureTransform="
            + this.textureTransform
            + ", textures="
            + this.textures
            + ", outlineProperty="
            + this.outlineProperty
            + ", useLightmap="
            + this.useLightmap
            + ", useOverlay="
            + this.useOverlay
            + "]";
    }

    public static RenderSetup.RenderSetupBuilder builder(RenderPipeline pipeline) {
        return new RenderSetup.RenderSetupBuilder(pipeline);
    }

    public List<PreparedRenderType.Texture> prepareTextures(
        TextureManager textureManager, SamplerCache samplerCache, GpuTextureView overlayTexture, GpuTextureView lightmapTexture
    ) {
        if (this.textures.isEmpty() && !this.useOverlay && !this.useLightmap) {
            return List.of();
        }

        Builder<PreparedRenderType.Texture> textures = ImmutableList.builderWithExpectedSize(this.textures.size() + 2);
        if (this.useOverlay) {
            textures.add(new PreparedRenderType.Texture("Sampler1", overlayTexture, samplerCache.getClampToEdge(FilterMode.LINEAR)));
        }

        if (this.useLightmap) {
            textures.add(new PreparedRenderType.Texture("Sampler2", lightmapTexture, samplerCache.getClampToEdge(FilterMode.LINEAR)));
        }

        for (Entry<String, RenderSetup.TextureBinding> entry : this.textures.entrySet()) {
            AbstractTexture texture = textureManager.getTexture(entry.getValue().location);
            GpuSampler samplerOverride = entry.getValue().sampler().get();
            textures.add(
                new PreparedRenderType.Texture(entry.getKey(), texture.getTextureView(), samplerOverride != null ? samplerOverride : texture.getSampler())
            );
        }

        return textures.build();
    }

    public enum OutlineProperty {
        NONE("none"),
        IS_OUTLINE("is_outline"),
        AFFECTS_OUTLINE("affects_outline");

        private final String name;

        OutlineProperty(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public static class RenderSetupBuilder {
        private final RenderPipeline pipeline;
        private boolean useLightmap = false;
        private boolean useOverlay = false;
        private LayeringTransform layeringTransform = LayeringTransform.NO_LAYERING;
        private OutputTarget outputTarget = OutputTarget.MAIN_TARGET;
        private TextureTransform textureTransform = TextureTransform.DEFAULT_TEXTURING;
        private boolean affectsCrumbling = false;
        private boolean sortOnUpload = false;
        private RenderSetup.OutlineProperty outlineProperty = RenderSetup.OutlineProperty.NONE;
        private final Map<String, RenderSetup.TextureBinding> textures = new HashMap<>();

        private RenderSetupBuilder(RenderPipeline pipeline) {
            this.pipeline = pipeline;
        }

        public RenderSetup.RenderSetupBuilder withTexture(String name, Identifier texture) {
            this.textures.put(name, new RenderSetup.TextureBinding(texture, () -> null));
            return this;
        }

        public RenderSetup.RenderSetupBuilder withTexture(String name, Identifier texture, @Nullable Supplier<GpuSampler> sampler) {
            this.textures.put(name, new RenderSetup.TextureBinding(texture, Suppliers.memoize(() -> sampler == null ? null : sampler.get())));
            return this;
        }

        public RenderSetup.RenderSetupBuilder useLightmap() {
            this.useLightmap = true;
            return this;
        }

        public RenderSetup.RenderSetupBuilder useOverlay() {
            this.useOverlay = true;
            return this;
        }

        public RenderSetup.RenderSetupBuilder affectsCrumbling() {
            this.affectsCrumbling = true;
            return this;
        }

        public RenderSetup.RenderSetupBuilder sortOnUpload() {
            this.sortOnUpload = true;
            return this;
        }

        public RenderSetup.RenderSetupBuilder setLayeringTransform(LayeringTransform layeringTransform) {
            this.layeringTransform = layeringTransform;
            return this;
        }

        public RenderSetup.RenderSetupBuilder setOutputTarget(OutputTarget outputTarget) {
            this.outputTarget = outputTarget;
            return this;
        }

        public RenderSetup.RenderSetupBuilder setTextureTransform(TextureTransform textureTransform) {
            this.textureTransform = textureTransform;
            return this;
        }

        public RenderSetup.RenderSetupBuilder setOutline(RenderSetup.OutlineProperty outlineProperty) {
            this.outlineProperty = outlineProperty;
            return this;
        }

        public RenderSetup createRenderSetup() {
            return new RenderSetup(
                this.pipeline,
                this.textures,
                this.useLightmap,
                this.useOverlay,
                this.layeringTransform,
                this.outputTarget,
                this.textureTransform,
                this.outlineProperty,
                this.affectsCrumbling,
                this.sortOnUpload
            );
        }
    }

    record TextureBinding(Identifier location, Supplier<@Nullable GpuSampler> sampler) {
    }
}
