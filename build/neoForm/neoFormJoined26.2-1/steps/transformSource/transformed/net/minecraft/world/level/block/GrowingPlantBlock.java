package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class GrowingPlantBlock extends Block {
    protected final Direction growthDirection;
    protected final boolean scheduleFluidTicks;
    protected final VoxelShape shape;

    public GrowingPlantBlock(BlockBehaviour.Properties properties, Direction growthDirection, VoxelShape shape, boolean scheduleFluidTicks) {
        super(properties);
        this.growthDirection = growthDirection;
        this.shape = shape;
        this.scheduleFluidTicks = scheduleFluidTicks;
    }

    @Override
    protected abstract MapCodec<? extends GrowingPlantBlock> codec();

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState growthDirectionState = context.getLevel().getBlockState(context.getClickedPos().relative(this.growthDirection));
        return !growthDirectionState.is(this.getHeadBlock()) && !growthDirectionState.is(this.getBodyBlock())
            ? this.getStateForPlacement(context.getLevel().getRandom())
            : this.getBodyBlock().defaultBlockState();
    }

    public BlockState getStateForPlacement(RandomSource random) {
        return this.defaultBlockState();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos attachedToPos = pos.relative(this.growthDirection.getOpposite());
        BlockState attachedToState = level.getBlockState(attachedToPos);
        return !this.canAttachTo(attachedToState)
            ? false
            : attachedToState.is(this.getHeadBlock())
                || attachedToState.is(this.getBodyBlock())
                || attachedToState.isFaceSturdy(level, attachedToPos, this.growthDirection);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    protected boolean canAttachTo(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shape;
    }

    protected abstract GrowingPlantHeadBlock getHeadBlock();

    protected abstract Block getBodyBlock();
}
