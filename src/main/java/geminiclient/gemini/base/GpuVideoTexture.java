package geminiclient.gemini.base;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.function.Supplier;

/**
 * A texture-manager compatible video texture with no {@code NativeImage} backing store.
 */
final class GpuVideoTexture extends AbstractTexture {
    private final int width;
    private final int height;

    GpuVideoTexture(Supplier<String> label, int width, int height) {
        this.width = width;
        this.height = height;

        GpuDevice device = RenderSystem.getDevice();
        texture = device.createTexture(
                label,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM,
                width, height, 1, 1);
        textureView = device.createTextureView(texture);
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }
}
