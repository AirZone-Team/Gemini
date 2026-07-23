package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class SectionCompiler {
    private final boolean ambientOcclusion;
    private final boolean cutoutLeaves;
    private final BlockStateModelSet blockModelSet;
    private final FluidStateModelSet fluidModelSet;
    private final BlockColors blockColors;

    public SectionCompiler(
        boolean ambientOcclusion, boolean cutoutLeaves, BlockStateModelSet blockModelSet, FluidStateModelSet fluidModelSet, BlockColors blockColors
    ) {
        this.ambientOcclusion = ambientOcclusion;
        this.cutoutLeaves = cutoutLeaves;
        this.blockModelSet = blockModelSet;
        this.fluidModelSet = fluidModelSet;
        this.blockColors = blockColors;
    }

    /// @deprecated Neo: use [#compile(SectionPos, RenderSectionRegion, VertexSorting, SectionBufferBuilderPack, java.util.List)] instead
    @Deprecated
    public SectionCompiler.Results compile(SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting, SectionBufferBuilderPack builders) {
        return compile(sectionPos, region, vertexSorting, builders, List.of());
    }

    public SectionCompiler.Results compile(SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting, SectionBufferBuilderPack builders, List<net.neoforged.neoforge.client.event.AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers) {
        SectionCompiler.Results results = new SectionCompiler.Results();
        BlockPos minPos = sectionPos.origin();
        BlockPos maxPos = minPos.offset(15, 15, 15);
        VisGraph visGraph = new VisGraph();
        BlockModelLighter.enableCaching();
        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(this.ambientOcclusion, true, this.blockColors);
        FluidRenderer fluidRenderer = new FluidRenderer(this.fluidModelSet);
        Map<ChunkSectionLayer, BufferBuilder> startedLayers = new EnumMap<>(ChunkSectionLayer.class);
        BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, quad.materialInfo().layer());
            builder.putBlockBakedQuad(x, y, z, quad, instance);
        };
        BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, ChunkSectionLayer.SOLID);
            builder.putBlockBakedQuad(x, y, z, quad, instance);
        };
        FluidRenderer.Output fluidOutput = layerx -> this.getOrBeginLayer(startedLayers, builders, layerx);

        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            BlockState blockState = region.getBlockState(pos);
            if (!blockState.isAir()) {
                try {
                    if (blockState.isSolidRender()) {
                        visGraph.setOpaque(pos);
                    }

                    if (blockState.hasBlockEntity()) {
                        BlockEntity blockEntity = region.getBlockEntity(pos);
                        if (blockEntity != null) {
                            this.handleBlockEntity(results, blockEntity);
                        }
                    }

                    FluidState fluidState = blockState.getFluidState();
                    if (!fluidState.isEmpty()) {
                        var customRenderer = this.fluidModelSet.get(fluidState).customRenderer();
                        if (customRenderer == null || !customRenderer.renderFluid(fluidRenderer, fluidState, region, pos, fluidOutput, blockState))
                        fluidRenderer.tesselate(region, pos, fluidOutput, blockState, fluidState);
                    }

                    if (blockState.getRenderShape() == RenderShape.MODEL) {
                        blockRenderer.tesselateBlock(
                            ModelBlockRenderer.forceOpaque(this.cutoutLeaves, blockState) ? opaqueQuadOutput : quadOutput,
                            SectionPos.sectionRelative(pos.getX()),
                            SectionPos.sectionRelative(pos.getY()),
                            SectionPos.sectionRelative(pos.getZ()),
                            region,
                            pos,
                            blockState,
                            this.blockModelSet.get(blockState),
                            blockState.getSeed(pos)
                        );
                    }
                } catch (Throwable t) {
                    CrashReport report = CrashReport.forThrowable(t, "Tesselating block in world");
                    CrashReportCategory category = report.addCategory("Block being tesselated");
                    CrashReportCategory.populateBlockDetails(category, region, pos, blockState);
                    throw new ReportedException(report);
                }
            }
        }

        net.neoforged.neoforge.client.ClientHooks.addAdditionalGeometry(additionalRenderers, layer -> this.getOrBeginLayer(startedLayers, builders, layer), region, blockRenderer);
        for (Entry<ChunkSectionLayer, BufferBuilder> entry : startedLayers.entrySet()) {
            ChunkSectionLayer layer = entry.getKey();
            MeshData mesh = entry.getValue().build();
            if (mesh != null) {
                if (layer == ChunkSectionLayer.TRANSLUCENT) {
                    results.transparencyState = mesh.sortQuads(builders.buffer(layer), vertexSorting);
                }

                results.renderedLayers.put(layer, mesh);
            }
        }

        BlockModelLighter.clearCache();
        results.visibilitySet = visGraph.resolve();
        return results;
    }

    private BufferBuilder getOrBeginLayer(Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack buffers, ChunkSectionLayer layer) {
        BufferBuilder builder = startedLayers.get(layer);
        if (builder == null) {
            ByteBufferBuilder buffer = buffers.buffer(layer);
            builder = new BufferBuilder(buffer, PrimitiveTopology.QUADS, layer.vertexFormat());
            startedLayers.put(layer, builder);
        }

        return builder;
    }

    private <E extends BlockEntity> void handleBlockEntity(SectionCompiler.Results results, E blockEntity) {
        results.blockEntities.add(blockEntity);
    }

    public static final class Results {
        public final List<BlockEntity> blockEntities = new ArrayList<>();
        public final Map<ChunkSectionLayer, MeshData> renderedLayers = new EnumMap<>(ChunkSectionLayer.class);
        public VisibilitySet visibilitySet = new VisibilitySet();
        public MeshData.@Nullable SortState transparencyState;

        public void release() {
            this.renderedLayers.values().forEach(MeshData::close);
        }
    }
}
