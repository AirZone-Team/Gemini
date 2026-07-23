package net.minecraft.client.renderer.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WindowRenderState {
    public int width;
    public int height;
    public int guiScale;
    public float appropriateLineWidth;
    public boolean isMinimized;
}
