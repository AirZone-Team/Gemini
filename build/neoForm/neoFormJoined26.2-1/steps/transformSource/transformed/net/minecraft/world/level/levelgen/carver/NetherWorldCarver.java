package net.minecraft.world.level.levelgen.carver;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.material.Fluids;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class NetherWorldCarver extends CaveWorldCarver {
    public NetherWorldCarver(Codec<CaveCarverConfiguration> configurationFactory) {
        super(configurationFactory);
        this.liquids = ImmutableSet.of(Fluids.LAVA, Fluids.WATER);
    }

    @Override
    protected int getCaveBound() {
        return 10;
    }

    @Override
    protected float getThickness(RandomSource random) {
        return (random.nextFloat() * 2.0F + random.nextFloat()) * 2.0F;
    }

    @Override
    protected double getYScale() {
        return 5.0;
    }

    protected boolean carveBlock(
        CarvingContext context,
        CaveCarverConfiguration configuration,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeGetter,
        CarvingMask mask,
        BlockPos.MutableBlockPos blockPos,
        BlockPos.MutableBlockPos helperPos,
        Aquifer aquifer,
        MutableBoolean hasGrass
    ) {
        if (this.canReplaceBlock(configuration, chunk.getBlockState(blockPos))) {
            BlockState state;
            if (blockPos.getY() <= context.getMinGenY() + 31) {
                state = LAVA.createLegacyBlock();
            } else {
                state = CAVE_AIR;
            }

            chunk.setBlockState(blockPos, state);
            return true;
        } else {
            return false;
        }
    }
}
