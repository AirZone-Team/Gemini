package geminiclient.gemini.customRenderer;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;

/** Creates off-screen targets with the active Minecraft backend's color format. */
public final class GeminiRenderTargets {
    private GeminiRenderTargets() {
    }

    public static TextureTarget colorTarget(String label, int width, int height, boolean useDepth) {
        RenderTarget mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTexture mainColor = mainTarget.getColorTexture();
        if (mainColor == null) {
            throw new IllegalStateException("The main color target is unavailable while creating " + label);
        }
        GpuFormat format = mainColor.getFormat();
        return new TextureTarget(label, width, height, useDepth, format);
    }
}
