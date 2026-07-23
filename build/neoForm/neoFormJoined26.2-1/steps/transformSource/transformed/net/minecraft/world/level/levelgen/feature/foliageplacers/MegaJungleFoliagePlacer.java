package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class MegaJungleFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<MegaJungleFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        i -> foliagePlacerParts(i).and(Codec.intRange(0, 16).fieldOf("height").forGetter(p -> p.height)).apply(i, MegaJungleFoliagePlacer::new)
    );
    protected final int height;

    public MegaJungleFoliagePlacer(IntProvider radius, IntProvider offset, int height) {
        super(radius, offset);
        this.height = height;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.MEGA_JUNGLE_FOLIAGE_PLACER;
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
        int leafHeight = foliageAttachment.doubleTrunk() ? foliageHeight : 1 + random.nextInt(2);

        for (int yo = offset; yo >= offset - leafHeight; yo--) {
            int currentRadius = leafRadius + foliageAttachment.radiusOffset() + 1 - yo;
            this.placeLeavesRow(level, foliageSetter, random, config, foliageAttachment.pos(), currentRadius, yo, foliageAttachment.doubleTrunk());
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.height;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return dx + dz >= 7 ? true : dx * dx + dz * dz > currentRadius * currentRadius;
    }
}
