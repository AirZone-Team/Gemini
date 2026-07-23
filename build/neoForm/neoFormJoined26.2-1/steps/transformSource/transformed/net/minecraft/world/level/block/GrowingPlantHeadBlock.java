package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class GrowingPlantHeadBlock extends GrowingPlantBlock implements BonemealableBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_25;
    public static final int MAX_AGE = 25;
    private final double growPerTickProbability;

    public GrowingPlantHeadBlock(
        BlockBehaviour.Properties properties, Direction growthDirection, VoxelShape shape, boolean scheduleFluidTicks, double growPerTickProbability
    ) {
        super(properties, growthDirection, shape, scheduleFluidTicks);
        this.growPerTickProbability = growPerTickProbability;
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected abstract MapCodec<? extends GrowingPlantHeadBlock> codec();

    @Override
    public BlockState getStateForPlacement(RandomSource random) {
        return this.defaultBlockState().setValue(AGE, random.nextInt(25));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 25;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(AGE) < 25 && net.neoforged.neoforge.common.CommonHooks.canCropGrow(level, pos.relative(this.growthDirection), state, random.nextDouble() < this.growPerTickProbability)) {
            BlockPos growthPos = pos.relative(this.growthDirection);
            if (this.canGrowInto(level.getBlockState(growthPos))) {
                level.setBlockAndUpdate(growthPos, this.getGrowIntoState(state, level.getRandom()));
                net.neoforged.neoforge.common.CommonHooks.fireCropGrowPost(level, growthPos, level.getBlockState(growthPos));
            }
        }
    }

    protected BlockState getGrowIntoState(BlockState growFromState, RandomSource random) {
        return growFromState.cycle(AGE);
    }

    public BlockState getMaxAgeState(BlockState fromState) {
        return fromState.setValue(AGE, 25);
    }

    public boolean isMaxAge(BlockState state) {
        return state.getValue(AGE) == 25;
    }

    protected BlockState updateBodyAfterConvertedFromHead(BlockState headState, BlockState bodyState) {
        return bodyState;
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess ticks,
        BlockPos pos,
        Direction directionToNeighbour,
        BlockPos neighbourPos,
        BlockState neighbourState,
        RandomSource random
    ) {
        if (directionToNeighbour == this.growthDirection.getOpposite()) {
            if (!state.canSurvive(level, pos)) {
                ticks.scheduleTick(pos, this, 1);
            } else {
                BlockState neighborInGrowthDirection = level.getBlockState(pos.relative(this.growthDirection));
                if (neighborInGrowthDirection.is(this) || neighborInGrowthDirection.is(this.getBodyBlock())) {
                    return this.updateBodyAfterConvertedFromHead(state, this.getBodyBlock().defaultBlockState());
                }
            }
        }

        if (directionToNeighbour != this.growthDirection || !neighbourState.is(this) && !neighbourState.is(this.getBodyBlock())) {
            if (this.scheduleFluidTicks) {
                ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }

            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        } else {
            return this.updateBodyAfterConvertedFromHead(state, this.getBodyBlock().defaultBlockState());
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos growthPos = pos.relative(this.growthDirection);
        return this.canGrowInto(level.getBlockState(growthPos)) && level.isInsideBuildHeight(growthPos);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos forwardPos = pos.relative(this.growthDirection);
        int nextAge = Math.min(state.getValue(AGE) + 1, 25);
        int blocksToGrow = this.getBlocksToGrowWhenBonemealed(random);

        for (int i = 0; i < blocksToGrow && this.canGrowInto(level.getBlockState(forwardPos)) && !level.isOutsideBuildHeight(forwardPos); i++) {
            level.setBlockAndUpdate(forwardPos, state.setValue(AGE, nextAge));
            forwardPos = forwardPos.relative(this.growthDirection);
            nextAge = Math.min(nextAge + 1, 25);
        }
    }

    protected abstract int getBlocksToGrowWhenBonemealed(final RandomSource random);

    protected abstract boolean canGrowInto(final BlockState state);

    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return this;
    }
}
