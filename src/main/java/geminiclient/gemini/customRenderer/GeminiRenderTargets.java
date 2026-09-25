package geminiclient.gemini.customRenderer;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.textures.GpuTexture;
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
        // 26.3 selects the depth attachment by format instead of a useDepth flag.
        GpuTexture mainDepth = mainTarget.getDepthTexture();
        GpuFormat depthFormat = useDepth
                ? mainDepth != null ? mainDepth.getFormat() : GpuFormat.D32_FLOAT
                : null;
        return new TextureTarget(label, width, height, mainColor.getFormat(), depthFormat);
    }
}
