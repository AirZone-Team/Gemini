package net.minecraft.client.renderer.state.gui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record PanoramaRenderState(float spin) {
}
