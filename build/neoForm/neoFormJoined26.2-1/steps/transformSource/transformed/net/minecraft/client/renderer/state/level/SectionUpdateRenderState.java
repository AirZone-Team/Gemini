package net.minecraft.client.renderer.state.level;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record SectionUpdateRenderState(long sectionNode, boolean playerChanged, RenderSectionRegion region, java.util.List<net.neoforged.neoforge.client.event.AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers) {
    /// @deprecated Neo: use [#SectionUpdateRenderState(long, boolean, RenderSectionRegion, java.util.List)] instead
    @Deprecated
    public SectionUpdateRenderState(long sectionNode, boolean playerChanged, RenderSectionRegion region) {
        this(sectionNode, playerChanged, region, java.util.List.of());
    }
}
