package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class MossyCarpetBlock extends Block implements BonemealableBlock {
    public static final MapCodec<MossyCarpetBlock> CODEC = simpleCodec(MossyCarpetBlock::new);
    public static final BooleanProperty BASE = BlockStateProperties.BOTTOM;
    public static final EnumProperty<WallSide> NORTH = BlockStateProperties.NORTH_WALL;
    public static final EnumProperty<WallSide> EAST = BlockStateProperties.EAST_WALL;
    public static final EnumProperty<WallSide> SOUTH = BlockStateProperties.SOUTH_WALL;
    public static final EnumProperty<WallSide> WEST = BlockStateProperties.WEST_WALL;
    public static final Map<Direction, EnumProperty<WallSide>> PROPERTY_BY_DIRECTION = ImmutableMap.copyOf(
        Maps.newEnumMap(Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST))
    );
    private final Function<BlockState, VoxelShape> shapes;

    @Override
    public MapCodec<MossyCarpetBlock> codec() {
        return CODEC;
    }

    public MossyCarpetBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(BASE, true)
                .setValue(NORTH, WallSide.NONE)
                .setValue(EAST, WallSide.NONE)
                .setValue(SOUTH, WallSide.NONE)
                .setValue(WEST, WallSide.NONE)
        );
        this.shapes = this.makeShapes();
    }

    public Function<BlockState, VoxelShape> makeShapes() {
        Map<Direction, VoxelShape> low = Shapes.rotateHorizontal(Block.boxZ(16.0, 0.0, 10.0, 0.0, 1.0));
        Map<Direction, VoxelShape> tall = Shapes.rotateAll(Block.boxZ(16.0, 0.0, 1.0));
        return this.getShapeForEachState(state -> {
            VoxelShape shape = state.getValue(BASE) ? tall.get(Direction.DOWN) : Shapes.empty();

            for (Entry<Direction, EnumProperty<WallSide>> entry : PROPERTY_BY_DIRECTION.entrySet()) {
                switch ((WallSide)state.getValue(entry.getValue())) {
                    case NONE:
                    default:
                        break;
                    case LOW:
                        shape = Shapes.or(shape, low.get(entry.getKey()));
                        break;
                    case TALL:
                        shape = Shapes.or(shape, tall.get(entry.getKey()));
                }
            }

            return shape.isEmpty() ? Shapes.block() : shape;
        });
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BASE) ? this.shapes.apply(this.defaultBlockState()) : Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState belowState = level.getBlockState(pos.below());
        return state.getValue(BASE) ? !belowState.isAir() : belowState.is(this) && belowState.getValue(BASE);
    }

    private static boolean hasFaces(BlockState blockState) {
        if (blockState.getValue(BASE)) {
            return true;
        }

        for (EnumProperty<WallSide> property : PROPERTY_BY_DIRECTION.values()) {
            if (blockState.getValue(property) != WallSide.NONE) {
                return true;
            }
        }

        return false;
    }

    private static boolean canSupportAtFace(BlockGetter level, BlockPos pos, Direction direction) {
        return direction == Direction.UP ? false : MultifaceBlock.canAttachTo(level, pos, direction);
    }

    private static BlockState getUpdatedState(BlockState state, BlockGetter level, BlockPos pos, boolean createSides) {
        BlockState aboveState = null;
        BlockState belowState = null;
        createSides |= state.getValue(BASE);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            EnumProperty<WallSide> property = getPropertyForFace(direction);
            WallSide side = canSupportAtFace(level, pos, direction) ? (createSides ? WallSide.LOW : state.getValue(property)) : WallSide.NONE;
            if (side == WallSide.LOW) {
                if (aboveState == null) {
                    aboveState = level.getBlockState(pos.above());
                }

                if (aboveState.is(Blocks.PALE_MOSS_CARPET) && aboveState.getValue(property) != WallSide.NONE && !aboveState.getValue(BASE)) {
                    side = WallSide.TALL;
                }

                if (!state.getValue(BASE)) {
                    if (belowState == null) {
                        belowState = level.getBlockState(pos.below());
                    }

                    if (belowState.is(Blocks.PALE_MOSS_CARPET) && belowState.getValue(property) == WallSide.NONE) {
                        side = WallSide.NONE;
                    }
                }
            }

            state = state.setValue(property, side);
        }

        return state;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return getUpdatedState(this.defaultBlockState(), context.getLevel(), context.getClickedPos(), true);
    }

    public static void placeAt(LevelAccessor level, BlockPos pos, RandomSource random, @Block.UpdateFlags int updateType) {
        BlockState simpleCarpetLayer = Blocks.PALE_MOSS_CARPET.defaultBlockState();
        BlockState adjustedCarpetLayer = getUpdatedState(simpleCarpetLayer, level, pos, true);
        level.setBlock(pos, adjustedCarpetLayer, updateType);
        BlockState state = createTopperWithSideChance(level, pos, random::nextBoolean);
        if (!state.isAir()) {
            level.setBlock(pos.above(), state, updateType);
            BlockState updateBottomCarpet = getUpdatedState(adjustedCarpetLayer, level, pos, true);
            level.setBlock(pos, updateBottomCarpet, updateType);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        if (!level.isClientSide()) {
            RandomSource random = level.getRandom();
            BlockState topper = createTopperWithSideChance(level, pos, random::nextBoolean);
            if (!topper.isAir()) {
                level.setBlock(pos.above(), topper, 3);
            }
        }
    }

    private static BlockState createTopperWithSideChance(BlockGetter level, BlockPos pos, BooleanSupplier sideSurvivalTest) {
        BlockPos above = pos.above();
        BlockState abovePreviousState = level.getBlockState(above);
        boolean isMossyCarpetAbove = abovePreviousState.is(Blocks.PALE_MOSS_CARPET);
        if ((!isMossyCarpetAbove || !abovePreviousState.getValue(BASE)) && (isMossyCarpetAbove || abovePreviousState.canBeReplaced())) {
            BlockState noCarpetBaseState = Blocks.PALE_MOSS_CARPET.defaultBlockState().setValue(BASE, false);
            BlockState aboveState = getUpdatedState(noCarpetBaseState, level, pos.above(), true);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                EnumProperty<WallSide> property = getPropertyForFace(direction);
                if (aboveState.getValue(property) != WallSide.NONE && !sideSurvivalTest.getAsBoolean()) {
                    aboveState = aboveState.setValue(property, WallSide.NONE);
                }
            }

            return hasFaces(aboveState) && aboveState != abovePreviousState ? aboveState : Blocks.AIR.defaultBlockState();
        } else {
            return Blocks.AIR.defaultBlockState();
        }
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
            return Blocks.AIR.defaultBlockState();
        }

        BlockState blockState = getUpdatedState(state, level, pos, false);
        return !hasFaces(blockState) ? Blocks.AIR.defaultBlockState() : blockState;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BASE, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 -> (BlockState)state.setValue(NORTH, state.getValue(SOUTH))
                .setValue(EAST, state.getValue(WEST))
                .setValue(SOUTH, state.getValue(NORTH))
                .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> (BlockState)state.setValue(NORTH, state.getValue(EAST))
                .setValue(EAST, state.getValue(SOUTH))
                .setValue(SOUTH, state.getValue(WEST))
                .setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90 -> (BlockState)state.setValue(NORTH, state.getValue(WEST))
                .setValue(EAST, state.getValue(NORTH))
                .setValue(SOUTH, state.getValue(EAST))
                .setValue(WEST, state.getValue(SOUTH));
            default -> state;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> (BlockState)state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> (BlockState)state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default -> super.mirror(state, mirror);
        };
    }

    public static @Nullable EnumProperty<WallSide> getPropertyForFace(Direction direction) {
        return PROPERTY_BY_DIRECTION.get(direction);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(BASE) && !createTopperWithSideChance(level, pos, () -> true).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockState topper = createTopperWithSideChance(level, pos, () -> true);
        if (!topper.isAir()) {
            level.setBlock(pos.above(), topper, 3);
        }
    }
}
