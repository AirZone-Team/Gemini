package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class DarkOakFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<DarkOakFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(i -> foliagePlacerParts(i).apply(i, DarkOakFoliagePlacer::new));

    public DarkOakFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.DARK_OAK_FOLIAGE_PLACER;
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
        BlockPos pos = foliageAttachment.pos().above(offset);
        boolean doubleTrunk = foliageAttachment.doubleTrunk();
        if (doubleTrunk) {
            this.placeLeavesRow(level, foliageSetter, random, config, pos, leafRadius + 2, -1, doubleTrunk);
            this.placeLeavesRow(level, foliageSetter, random, config, pos, leafRadius + 3, 0, doubleTrunk);
            this.placeLeavesRow(level, foliageSetter, random, config, pos, leafRadius + 2, 1, doubleTrunk);
            if (random.nextBoolean()) {
                this.placeLeavesRow(level, foliageSetter, random, config, pos, leafRadius, 2, doubleTrunk);
            }
        } else {
            this.placeLeavesRow(level, foliageSetter, random, config, pos, leafRadius + 2, -1, doubleTrunk);
            this.placeLeavesRow(level, foliageSetter, random, config, pos, leafRadius + 1, 0, doubleTrunk);
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return 4;
    }

    @Override
    protected boolean shouldSkipLocationSigned(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return y != 0 || !doubleTrunk || dx != -currentRadius && dx < currentRadius || dz != -currentRadius && dz < currentRadius
            ? super.shouldSkipLocationSigned(random, dx, y, dz, currentRadius, doubleTrunk)
            : true;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        if (y == -1 && !doubleTrunk) {
            return dx == currentRadius && dz == currentRadius;
        } else {
            return y == 1 ? dx + dz > currentRadius * 2 - 2 : false;
        }
    }
}
