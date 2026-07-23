package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

public class PoweredRailBlock extends BaseRailBlock {
    public static final MapCodec<PoweredRailBlock> CODEC = simpleCodec(PoweredRailBlock::new);
    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    @Override
    public MapCodec<PoweredRailBlock> codec() {
        return CODEC;
    }

    /**
     * Neo: {@code true} for an activator rail, {@code false} for powered rail
     */
    private final boolean isActivator;

    public PoweredRailBlock(BlockBehaviour.Properties properties) {
        this(properties, false);
    }

    public PoweredRailBlock(BlockBehaviour.Properties properties, boolean isPoweredRail) {
        super(true, properties);
        this.isActivator = !isPoweredRail;
        this.registerDefaultState();
    }

    protected void registerDefaultState() {
        this.registerDefaultState(this.stateDefinition.any().setValue(SHAPE, RailShape.NORTH_SOUTH).setValue(POWERED, false).setValue(WATERLOGGED, false));
    }

    protected boolean findPoweredRailSignal(Level level, BlockPos pos, BlockState state, boolean forward, int searchDepth) {
        if (searchDepth >= 8) {
            return false;
        }

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        boolean checkBelow = true;
        RailShape shape = ((BaseRailBlock)state.getBlock()).getRailDirection(state, level, pos, null);
        switch (shape) {
            case NORTH_SOUTH:
                if (forward) {
                    z++;
                } else {
                    z--;
                }
                break;
            case EAST_WEST:
                if (forward) {
                    x--;
                } else {
                    x++;
                }
                break;
            case ASCENDING_EAST:
                if (forward) {
                    x--;
                } else {
                    x++;
                    y++;
                    checkBelow = false;
                }

                shape = RailShape.EAST_WEST;
                break;
            case ASCENDING_WEST:
                if (forward) {
                    x--;
                    y++;
                    checkBelow = false;
                } else {
                    x++;
                }

                shape = RailShape.EAST_WEST;
                break;
            case ASCENDING_NORTH:
                if (forward) {
                    z++;
                } else {
                    z--;
                    y++;
                    checkBelow = false;
                }

                shape = RailShape.NORTH_SOUTH;
                break;
            case ASCENDING_SOUTH:
                if (forward) {
                    z++;
                    y++;
                    checkBelow = false;
                } else {
                    z--;
                }

                shape = RailShape.NORTH_SOUTH;
        }

        return this.isSameRailWithPower(level, new BlockPos(x, y, z), forward, searchDepth, shape)
            ? true
            : checkBelow && this.isSameRailWithPower(level, new BlockPos(x, y - 1, z), forward, searchDepth, shape);
    }

    protected boolean isSameRailWithPower(Level level, BlockPos pos, boolean forward, int searchDepth, RailShape dir) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PoweredRailBlock other)) {
            return false;
        }

        RailShape myShape = other.getRailDirection(state, level, pos, null);
        if (dir != RailShape.EAST_WEST || myShape != RailShape.NORTH_SOUTH && myShape != RailShape.ASCENDING_NORTH && myShape != RailShape.ASCENDING_SOUTH) {
            if (dir != RailShape.NORTH_SOUTH || myShape != RailShape.EAST_WEST && myShape != RailShape.ASCENDING_EAST && myShape != RailShape.ASCENDING_WEST) {
                if (isActivatorRail() != other.isActivatorRail()) {
                    return false;
                } else {
                    return level.hasNeighborSignal(pos) ? true : other.findPoweredRailSignal(level, pos, state, forward, searchDepth + 1);
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    protected void updateState(BlockState state, Level level, BlockPos pos, Block block) {
        boolean isPowered = state.getValue(POWERED);
        boolean shouldPower = level.hasNeighborSignal(pos)
            || this.findPoweredRailSignal(level, pos, state, true, 0)
            || this.findPoweredRailSignal(level, pos, state, false, 0);
        if (shouldPower != isPowered) {
            level.setBlock(pos, state.setValue(POWERED, shouldPower), 3);
            level.updateNeighborsAt(pos.below(), this);
            if (((BaseRailBlock)state.getBlock()).getRailDirection(state, level, pos, null).isSlope()) {
                level.updateNeighborsAt(pos.above(), this);
            }
        }
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        RailShape currentShape = state.getValue(SHAPE);
        RailShape newShape = this.rotate(currentShape, rotation);
        return state.setValue(SHAPE, newShape);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        RailShape currentShape = state.getValue(SHAPE);
        RailShape newShape = this.mirror(currentShape, mirror);
        return state.setValue(SHAPE, newShape);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(getShapeProperty(), POWERED, WATERLOGGED);
    }

    public boolean isActivatorRail() {
        return isActivator;
    }
}
