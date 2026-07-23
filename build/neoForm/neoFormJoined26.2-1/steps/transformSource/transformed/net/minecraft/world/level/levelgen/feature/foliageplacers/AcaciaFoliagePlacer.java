package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class AcaciaFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<AcaciaFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(i -> foliagePlacerParts(i).apply(i, AcaciaFoliagePlacer::new));

    public AcaciaFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.ACACIA_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(
        WorldGenLevel level,
        FoliagePlacer.FoliageSetter foliageSetter,
        RandomSource random,
        TreeConfiguration config,
        int treeHeight,
        FoliagePlacer.FoliageAttachment foliageAttachment,
        int foliageHeight,
        int leafRadius,
        int offset
    ) {
        boolean doubleTrunk = foliageAttachment.doubleTrunk();
        BlockPos foliagePos = foliageAttachment.pos().above(offset);
        this.placeLeavesRow(level, foliageSetter, random, config, foliagePos, leafRadius + foliageAttachment.radiusOffset(), -1 - foliageHeight, doubleTrunk);
        this.placeLeavesRow(level, foliageSetter, random, config, foliagePos, leafRadius - 1, -foliageHeight, doubleTrunk);
        this.placeLeavesRow(level, foliageSetter, random, config, foliagePos, leafRadius + foliageAttachment.radiusOffset() - 1, 0, doubleTrunk);
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return 0;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return y == 0 ? (dx > 1 || dz > 1) && dx != 0 && dz != 0 : dx == currentRadius && dz == currentRadius && currentRadius > 0;
    }
}
