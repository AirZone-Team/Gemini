package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

public class EnvironmentScanPlacement extends PlacementModifier {
    private final Direction directionOfSearch;
    private final BlockPredicate targetCondition;
    private final BlockPredicate allowedSearchCondition;
    private final int maxSteps;
    public static final MapCodec<EnvironmentScanPlacement> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                // Neo: Allow any direction, not just vertical. The code already handles it fine.
                Direction.CODEC.fieldOf("direction_of_search").forGetter(c -> c.directionOfSearch),
                BlockPredicate.CODEC.fieldOf("target_condition").forGetter(c -> c.targetCondition),
                BlockPredicate.CODEC.optionalFieldOf("allowed_search_condition", BlockPredicate.alwaysTrue()).forGetter(c -> c.allowedSearchCondition),
                Codec.intRange(1, 32).fieldOf("max_steps").forGetter(c -> c.maxSteps)
            )
            .apply(i, EnvironmentScanPlacement::new)
    );

    private EnvironmentScanPlacement(Direction directionOfSearch, BlockPredicate targetCondition, BlockPredicate allowedSearchCondition, int maxSteps) {
        this.directionOfSearch = directionOfSearch;
        this.targetCondition = targetCondition;
        this.allowedSearchCondition = allowedSearchCondition;
        this.maxSteps = maxSteps;
    }

    public static EnvironmentScanPlacement scanningFor(
        Direction directionOfSearch, BlockPredicate targetCondition, BlockPredicate allowedSearchCondition, int maxSteps
    ) {
        return new EnvironmentScanPlacement(directionOfSearch, targetCondition, allowedSearchCondition, maxSteps);
    }

    public static EnvironmentScanPlacement scanningFor(Direction directionOfSearch, BlockPredicate targetCondition, int maxSteps) {
        return scanningFor(directionOfSearch, targetCondition, BlockPredicate.alwaysTrue(), maxSteps);
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos origin) {
        BlockPos.MutableBlockPos pos = origin.mutable();
        WorldGenLevel level = context.getLevel();
        if (!this.allowedSearchCondition.test(level, pos)) {
            return Stream.of();
        }

        for (int i = 0; i < this.maxSteps; i++) {
            if (this.targetCondition.test(level, pos)) {
                return Stream.of(pos);
            }

            pos.move(this.directionOfSearch);
            if (level.isOutsideBuildHeight(pos.getY())) {
                return Stream.of();
            }

            if (!this.allowedSearchCondition.test(level, pos)) {
                break;
            }
        }

        return this.targetCondition.test(level, pos) ? Stream.of(pos) : Stream.of();
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.ENVIRONMENT_SCAN;
    }
}
