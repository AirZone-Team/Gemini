package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class ChorusPlantBlock extends PipeBlock {
    public static final MapCodec<ChorusPlantBlock> CODEC = simpleCodec(ChorusPlantBlock::new);

    @Override
    public MapCodec<ChorusPlantBlock> codec() {
        return CODEC;
    }

    public ChorusPlantBlock(BlockBehaviour.Properties properties) {
        super(10.0F, properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getStateWithConnections(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    public static BlockState getStateWithConnections(BlockGetter level, BlockPos pos, BlockState defaultState) {
        BlockState down = level.getBlockState(pos.below());
        BlockState up = level.getBlockState(pos.above());
        BlockState north = level.getBlockState(pos.north());
        BlockState east = level.getBlockState(pos.east());
        BlockState south = level.getBlockState(pos.south());
        BlockState west = level.getBlockState(pos.west());
        Block block = defaultState.getBlock();
        var soilDecision = down.canSustainPlant(level, pos.below(), Direction.UP, defaultState);
        return defaultState.trySetValue(DOWN, down.is(block) || down.is(Blocks.CHORUS_FLOWER) || down.is(Blocks.END_STONE) || soilDecision.isTrue())
            .trySetValue(UP, up.is(block) || up.is(Blocks.CHORUS_FLOWER))
            .trySetValue(NORTH, north.is(block) || north.is(Blocks.CHORUS_FLOWER))
            .trySetValue(EAST, east.is(block) || east.is(Blocks.CHORUS_FLOWER))
            .trySetValue(SOUTH, south.is(block) || south.is(Blocks.CHORUS_FLOWER))
            .trySetValue(WEST, west.is(block) || west.is(Blocks.CHORUS_FLOWER));
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
        if (!state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, this, 1);
            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        } else {
            boolean connect = neighbourState.is(this)
                || neighbourState.is(Blocks.CHORUS_FLOWER)
                || directionToNeighbour == Direction.DOWN && neighbourState.is(BlockTags.SUPPORTS_CHORUS_PLANT);
            if (directionToNeighbour == Direction.DOWN) {
                var soilDecision = neighbourState.canSustainPlant(level, neighbourPos.relative(directionToNeighbour), directionToNeighbour.getOpposite(), state);
                if (!soilDecision.isDefault()) {
                    connect = soilDecision.isTrue();
                }
            }
            return state.setValue(PROPERTY_BY_DIRECTION.get(directionToNeighbour), connect);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState belowState = level.getBlockState(pos.below());
        boolean blockAboveOrBelow = !level.getBlockState(pos.above()).isAir() && !belowState.isAir();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.is(this)) {
                if (blockAboveOrBelow) {
                    return false;
                }

                BlockState below = level.getBlockState(neighborPos.below());
                if (below.is(this) || below.is(BlockTags.SUPPORTS_CHORUS_PLANT)) {
                    return true;
                }
            }
        }

        var soilDecision = belowState.canSustainPlant(level, pos.below(), Direction.UP, state);
        if (!soilDecision.isDefault()) return soilDecision.isTrue();
        return belowState.is(this) || belowState.is(BlockTags.SUPPORTS_CHORUS_PLANT);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
