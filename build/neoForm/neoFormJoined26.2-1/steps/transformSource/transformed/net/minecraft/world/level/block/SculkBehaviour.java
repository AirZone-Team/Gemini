package net.minecraft.world.level.block;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

public interface SculkBehaviour {
    SculkBehaviour DEFAULT = new SculkBehaviour() {
        @Override
        public boolean attemptSpreadVein(LevelAccessor level, BlockPos pos, BlockState state, @Nullable Collection<Direction> facings, boolean postProcess) {
            if (facings == null) {
                return ((SculkVeinBlock)Blocks.SCULK_VEIN).getSameSpaceSpreader().spreadAll(level.getBlockState(pos), level, pos, postProcess) > 0L;
            } else if (!facings.isEmpty()) {
                return !state.isAir() && !state.getFluidState().is(Fluids.WATER) ? false : SculkVeinBlock.regrow(level, pos, state, facings);
            } else {
                return SculkBehaviour.super.attemptSpreadVein(level, pos, state, facings, postProcess);
            }
        }

        @Override
        public int attemptUseCharge(
            SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos originPos, RandomSource random, SculkSpreader spreader, boolean spreadVeins
        ) {
            return cursor.getDecayDelay() > 0 ? cursor.getCharge() : 0;
        }

        @Override
        public int updateDecayDelay(int age) {
            return Math.max(age - 1, 0);
        }
    };

    default byte getSculkSpreadDelay() {
        return 1;
    }

    default void onDischarged(LevelAccessor level, BlockState state, BlockPos pos, RandomSource random) {
    }

    default boolean depositCharge(LevelAccessor level, BlockPos pos, RandomSource random) {
        return false;
    }

    default boolean attemptSpreadVein(LevelAccessor level, BlockPos pos, BlockState state, @Nullable Collection<Direction> facings, boolean postProcess) {
        return ((MultifaceSpreadeableBlock)Blocks.SCULK_VEIN).getSpreader().spreadAll(state, level, pos, postProcess) > 0L;
    }

    default boolean canChangeBlockStateOnSpread() {
        return true;
    }

    default int updateDecayDelay(int age) {
        return 1;
    }

    int attemptUseCharge(
        SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos originPos, RandomSource random, SculkSpreader spreader, boolean spreadVeins
    );
}
