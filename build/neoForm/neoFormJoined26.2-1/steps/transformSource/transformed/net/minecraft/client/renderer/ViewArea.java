package net.minecraft.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ViewArea {
    private final SectionOcclusionGraph sectionOcclusionGraph;
    private final RotatingSectionStorage<SectionRenderDispatcher.RenderSection> sections;
    private final int minY;
    private final int maxY;

    public ViewArea(
        SectionRenderDispatcher sectionRenderDispatcher,
        int minY,
        int maxY,
        int minSectionY,
        int maxSectionY,
        int renderDistance,
        SectionOcclusionGraph sectionOcclusionGraph
    ) {
        this.sectionOcclusionGraph = sectionOcclusionGraph;
        this.minY = minY;
        this.maxY = maxY;
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("createSections called from wrong thread: " + Thread.currentThread().getName());
        }

        this.sections = new RotatingSectionStorage<>(
            renderDistance, minSectionY, maxSectionY, (index, sectionNode) -> sectionRenderDispatcher.new RenderSection(index, sectionNode)
        );
    }

    public void releaseAllBuffers() {
        for (SectionRenderDispatcher.RenderSection section : this.sections) {
            section.reset();
        }
    }

    public int size() {
        return this.sections.size();
    }

    public int minY() {
        return this.minY;
    }

    public int maxY() {
        return this.maxY;
    }

    public int minSectionY() {
        return this.sections.minY();
    }

    public int maxSectionY() {
        return this.sections.maxY();
    }

    public int sectionCount() {
        return this.sections.height();
    }

    public int getViewDistance() {
        return this.sections.radius();
    }

    public boolean repositionCamera(SectionPos cameraSectionPos) {
        boolean result = this.sections.repositionCenter(cameraSectionPos);
        if (result) {
            this.sectionOcclusionGraph.invalidate();
        }

        return result;
    }

    public SectionPos getCameraSectionPos() {
        return this.sections.centerSectionPos();
    }

    public SectionRenderDispatcher.@Nullable RenderSection getRenderSectionAt(BlockPos pos) {
        return this.sections.getValueAt(pos);
    }

    protected SectionRenderDispatcher.@Nullable RenderSection getRenderSection(long sectionNode) {
        return this.sections.getValue(sectionNode);
    }
}
