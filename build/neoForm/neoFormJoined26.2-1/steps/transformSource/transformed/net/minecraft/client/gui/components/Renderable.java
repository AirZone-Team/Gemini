package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface Renderable {
    void extractRenderState(final GuiGraphicsExtractor graphics, int mouseX, int mouseY, final float a);
}
