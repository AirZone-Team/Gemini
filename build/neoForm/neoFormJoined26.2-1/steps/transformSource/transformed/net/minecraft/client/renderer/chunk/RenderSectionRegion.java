package net.minecraft.client.renderer.chunk;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class RenderSectionRegion implements BlockAndTintGetter {
    public static final int RADIUS = 1;
    public static final int SIZE = 3;
    private final int minSectionX;
    private final int minSectionY;
    private final int minSectionZ;
    private final SectionCopy[] sections;
    private final ClientLevel level;
    private final CardinalLighting cardinalLighting;
    private final LevelLightEngine lightEngine;
    private final it.unimi.dsi.fastutil.longs.Long2ObjectFunction<net.neoforged.neoforge.model.data.ModelData> modelDataSnapshot;

    /// @deprecated Neo: use [#RenderSectionRegion(ClientLevel, int, int, int, SectionCopy\[\], it.unimi.dsi.fastutil.longs.Long2ObjectFunction)] instead
    @Deprecated
    public RenderSectionRegion(ClientLevel level, int minSectionX, int minSectionY, int minSectionZ, SectionCopy[] sections) {
        this(level, minSectionX, minSectionY, minSectionZ, sections, net.neoforged.neoforge.model.data.ModelDataManager.EMPTY_SNAPSHOT);
    }

    RenderSectionRegion(ClientLevel level, int minSectionX, int minSectionY, int minSectionZ, SectionCopy[] sections, it.unimi.dsi.fastutil.longs.Long2ObjectFunction<net.neoforged.neoforge.model.data.ModelData> modelDataSnapshot) {
        this.level = level;
        this.minSectionX = minSectionX;
        this.minSectionY = minSectionY;
        this.minSectionZ = minSectionZ;
        this.sections = sections;
        this.cardinalLighting = level.cardinalLighting();
        this.lightEngine = level.getLightEngine();
        this.modelDataSnapshot = modelDataSnapshot;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.getSection(
                SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ())
            )
            .getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getSection(
                SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ())
            )
            .getBlockState(pos)
            .getFluidState();
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return this.cardinalLighting;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return this.getSection(
                SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ())
            )
            .getBlockEntity(pos);
    }

    private SectionCopy getSection(int sectionX, int sectionY, int sectionZ) {
        return this.sections[index(this.minSectionX, this.minSectionY, this.minSectionZ, sectionX, sectionY, sectionZ)];
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return this.level.getBlockTint(pos, resolver);
    }

    @Override
    public int getMinY() {
        return this.level.getMinY();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public net.neoforged.neoforge.model.data.ModelData getModelData(BlockPos pos) {
        return modelDataSnapshot.get(pos.asLong());
    }

    @Override
    public net.neoforged.neoforge.common.world.AuxiliaryLightManager getAuxLightManager(net.minecraft.world.level.ChunkPos pos) {
        return this.getSection(pos.x(), this.minSectionY, pos.z()).wrapped.getAuxLightManager(pos);
    }

    public static int index(int minSectionX, int minSectionY, int minSectionZ, int sectionX, int sectionY, int sectionZ) {
        return sectionX - minSectionX + (sectionY - minSectionY) * 3 + (sectionZ - minSectionZ) * 3 * 3;
    }
}
