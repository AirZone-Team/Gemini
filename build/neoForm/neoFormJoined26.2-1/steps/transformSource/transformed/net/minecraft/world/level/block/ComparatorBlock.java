package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;
import org.jspecify.annotations.Nullable;

public class ComparatorBlock extends DiodeBlock implements EntityBlock {
    public static final MapCodec<ComparatorBlock> CODEC = simpleCodec(ComparatorBlock::new);
    public static final EnumProperty<ComparatorMode> MODE = BlockStateProperties.MODE_COMPARATOR;

    @Override
    public MapCodec<ComparatorBlock> codec() {
        return CODEC;
    }

    public ComparatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(MODE, ComparatorMode.COMPARE));
    }

    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    public BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess ticks,
        BlockPos pos,
        Direction directionToNeighbour,
        BlockPos neighbourPos,
        BlockState neighbourState,
        RandomSource random
    ) {
        return directionToNeighbour == Direction.DOWN && !this.canSurviveOn(level, neighbourPos, neighbourState)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof ComparatorBlockEntity comparatorBlockEntity ? comparatorBlockEntity.getOutputSignal() : 0;
    }

    private int calculateOutputSignal(Level level, BlockPos pos, BlockState state) {
        int inputSignal = this.getInputSignal(level, pos, state);
        if (inputSignal == 0) {
            return 0;
        } else {
            int alternateSignal = this.getAlternateSignal(level, pos, state);
            if (alternateSignal > inputSignal) {
                return 0;
            } else {
                return state.getValue(MODE) == ComparatorMode.SUBTRACT ? inputSignal - alternateSignal : inputSignal;
            }
        }
    }

    @Override
    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        int input = this.getInputSignal(level, pos, state);
        if (input == 0) {
            return false;
        }

        int sideInput = this.getAlternateSignal(level, pos, state);
        return input > sideInput ? true : input == sideInput && state.getValue(MODE) == ComparatorMode.COMPARE;
    }

    @Override
    protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
        int resultSignal = super.getInputSignal(level, pos, state);
        Direction direction = state.getValue(FACING);
        BlockPos targetPos = pos.relative(direction);
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.hasAnalogOutputSignal()) {
            resultSignal = targetState.getAnalogOutputSignal(level, targetPos, direction.getOpposite());
        } else if (resultSignal < 15 && targetState.isRedstoneConductor(level, targetPos)) {
            targetPos = targetPos.relative(direction);
            targetState = level.getBlockState(targetPos);
            ItemFrame itemFrame = this.getItemFrame(level, direction, targetPos);
            int itemFrameOrBlockSignal = Math.max(
                itemFrame == null ? Integer.MIN_VALUE : itemFrame.getAnalogOutput(),
                targetState.hasAnalogOutputSignal() ? targetState.getAnalogOutputSignal(level, targetPos, direction.getOpposite()) : Integer.MIN_VALUE
            );
            if (itemFrameOrBlockSignal != Integer.MIN_VALUE) {
                resultSignal = itemFrameOrBlockSignal;
            }
        }

        return resultSignal;
    }

    private @Nullable ItemFrame getItemFrame(Level level, Direction direction, BlockPos tPos) {
        List<ItemFrame> itemFrames = level.getEntitiesOfClass(
            ItemFrame.class,
            new AABB(tPos.getX(), tPos.getY(), tPos.getZ(), tPos.getX() + 1, tPos.getY() + 1, tPos.getZ() + 1),
            entity -> entity.getDirection() == direction
        );
        return itemFrames.size() == 1 ? itemFrames.get(0) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        }

        state = state.cycle(MODE);
        float pitch = state.getValue(MODE) == ComparatorMode.SUBTRACT ? 0.55F : 0.5F;
        level.playSound(player, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, pitch);
        level.setBlock(pos, state, 2);
        if (level.getBlockState(pos).is(this)) {
            this.refreshOutputState(level, pos, state);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void checkTickOnNeighbor(Level level, BlockPos pos, BlockState state) {
        if (!level.getBlockTicks().willTickThisTick(pos, this)) {
            int outputValue = this.calculateOutputSignal(level, pos, state);
            int oldValue = level.getBlockEntity(pos) instanceof ComparatorBlockEntity comparatorBlockEntity ? comparatorBlockEntity.getOutputSignal() : 0;
            if (outputValue != oldValue || state.getValue(POWERED) != this.shouldTurnOn(level, pos, state)) {
                TickPriority priority = this.shouldPrioritize(level, pos, state) ? TickPriority.HIGH : TickPriority.NORMAL;
                level.scheduleTick(pos, this, 2, priority);
            }
        }
    }

    private void refreshOutputState(Level level, BlockPos pos, BlockState state) {
        int outputValue = this.calculateOutputSignal(level, pos, state);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        int oldValue = 0;
        if (blockEntity instanceof ComparatorBlockEntity comparatorBlockEntity) {
            oldValue = comparatorBlockEntity.getOutputSignal();
            comparatorBlockEntity.setOutputSignal(outputValue);
        }

        if (oldValue != outputValue || state.getValue(MODE) == ComparatorMode.COMPARE) {
            boolean sourceOn = this.shouldTurnOn(level, pos, state);
            boolean isOn = state.getValue(POWERED);
            if (isOn && !sourceOn) {
                level.setBlock(pos, state.setValue(POWERED, false), 2);
            } else if (!isOn && sourceOn) {
                level.setBlock(pos, state.setValue(POWERED, true), 2);
            }

            this.updateNeighborsInFront(level, pos, state);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.refreshOutputState(level, pos, state);
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
        super.triggerEvent(state, level, pos, b0, b1);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(b0, b1);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new ComparatorBlockEntity(worldPosition, blockState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, POWERED);
    }

    @Override
    public boolean getWeakChanges(BlockState state, net.minecraft.world.level.LevelReader world, BlockPos pos) {
        return state.is(Blocks.COMPARATOR);
    }

    @Override
    public void onNeighborChange(BlockState state, net.minecraft.world.level.LevelReader levelReader, BlockPos pos, BlockPos neighbor) {
        if (pos.getY() == neighbor.getY() && levelReader instanceof Level level && !levelReader.isClientSide()) {
            // TODO porting: check this still works as expected
            state.handleNeighborChanged(level, pos, levelReader.getBlockState(neighbor).getBlock(), null, false);
        }
    }
}
