package net.minecraft.client.renderer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class MovingBlockRenderState implements BlockAndTintGetter {
    public BlockPos randomSeedPos = BlockPos.ZERO;
    public BlockPos blockPos = BlockPos.ZERO;
    public BlockState blockState = Blocks.AIR.defaultBlockState();
    public @Nullable Holder<Biome> biome;
    public CardinalLighting cardinalLighting = CardinalLighting.DEFAULT;
    public LevelLightEngine lightEngine = LevelLightEngine.EMPTY;
    public net.neoforged.neoforge.model.data.ModelData modelData = net.neoforged.neoforge.model.data.ModelData.EMPTY;

    @Override
    public CardinalLighting cardinalLighting() {
        return this.cardinalLighting;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver color) {
        return this.biome == null ? -1 : color.getColor(this.biome.value(), pos.getX(), pos.getZ());
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return pos.equals(this.blockPos) ? this.blockState : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return 1;
    }

    @Override
    public int getMinY() {
        return this.blockPos.getY();
    }

    @Override
    public net.neoforged.neoforge.model.data.ModelData getModelData(BlockPos pos) {
        return pos.equals(this.blockPos) ? this.modelData : net.neoforged.neoforge.model.data.ModelData.EMPTY;
    }
}
