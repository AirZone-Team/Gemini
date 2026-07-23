package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class MultifaceSpreader {
    public static final MultifaceSpreader.SpreadType[] DEFAULT_SPREAD_ORDER = new MultifaceSpreader.SpreadType[]{
        MultifaceSpreader.SpreadType.SAME_POSITION, MultifaceSpreader.SpreadType.SAME_PLANE, MultifaceSpreader.SpreadType.WRAP_AROUND
    };
    private final MultifaceSpreader.SpreadConfig config;

    public MultifaceSpreader(MultifaceBlock multifaceBlock) {
        this(new MultifaceSpreader.DefaultSpreaderConfig(multifaceBlock));
    }

    public MultifaceSpreader(MultifaceSpreader.SpreadConfig config) {
        this.config = config;
    }

    public boolean canSpreadInAnyDirection(BlockState state, BlockGetter level, BlockPos pos, Direction startingFace) {
        return Direction.stream()
            .anyMatch(
                spreadDirection -> this.getSpreadFromFaceTowardDirection(state, level, pos, startingFace, spreadDirection, this.config::canSpreadInto)
                    .isPresent()
            );
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromRandomFaceTowardRandomDirection(
        BlockState state, LevelAccessor level, BlockPos pos, RandomSource random
    ) {
        return Direction.allShuffled(random)
            .stream()
            .filter(faceDirection -> this.config.canSpreadFrom(state, faceDirection))
            .map(faceDirection -> this.spreadFromFaceTowardRandomDirection(state, level, pos, faceDirection, random, false))
            .filter(Optional::isPresent)
            .findFirst()
            .orElse(Optional.empty());
    }

    public long spreadAll(BlockState state, LevelAccessor level, BlockPos pos, boolean postProcess) {
        return Direction.stream()
            .filter(faceDirection -> this.config.canSpreadFrom(state, faceDirection))
            .map(faceDirection -> this.spreadFromFaceTowardAllDirections(state, level, pos, faceDirection, postProcess))
            .reduce(0L, Long::sum);
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardRandomDirection(
        BlockState state, LevelAccessor level, BlockPos pos, Direction startingFace, RandomSource random, boolean postProcess
    ) {
        return Direction.allShuffled(random)
            .stream()
            .map(spreadDirection -> this.spreadFromFaceTowardDirection(state, level, pos, startingFace, spreadDirection, postProcess))
            .filter(Optional::isPresent)
            .findFirst()
            .orElse(Optional.empty());
    }

    private long spreadFromFaceTowardAllDirections(BlockState state, LevelAccessor level, BlockPos pos, Direction startingFace, boolean postProcess) {
        return Direction.stream()
            .map(spreadDirection -> this.spreadFromFaceTowardDirection(state, level, pos, startingFace, spreadDirection, postProcess))
            .filter(Optional::isPresent)
            .count();
    }

    @VisibleForTesting
    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardDirection(
        BlockState state, LevelAccessor level, BlockPos pos, Direction fromFace, Direction spreadDirection, boolean postProcess
    ) {
        return this.getSpreadFromFaceTowardDirection(state, level, pos, fromFace, spreadDirection, this.config::canSpreadInto)
            .flatMap(spreadPos -> this.spreadToFace(level, spreadPos, postProcess));
    }

    public Optional<MultifaceSpreader.SpreadPos> getSpreadFromFaceTowardDirection(
        BlockState state, BlockGetter level, BlockPos pos, Direction startingFace, Direction spreadDirection, MultifaceSpreader.SpreadPredicate canSpreadInto
    ) {
        if (spreadDirection.getAxis() == startingFace.getAxis()) {
            return Optional.empty();
        }

        if (this.config.isOtherBlockValidAsSource(state) || this.config.hasFace(state, startingFace) && !this.config.hasFace(state, spreadDirection)) {
            for (MultifaceSpreader.SpreadType type : this.config.getSpreadTypes()) {
                MultifaceSpreader.SpreadPos spreadPos = type.getSpreadPos(pos, spreadDirection, startingFace);
                if (canSpreadInto.test(level, pos, spreadPos)) {
                    return Optional.of(spreadPos);
                }
            }

            return Optional.empty();
        } else {
            return Optional.empty();
        }
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadToFace(LevelAccessor level, MultifaceSpreader.SpreadPos spreadPos, boolean postProcess) {
        BlockState oldState = level.getBlockState(spreadPos.pos());
        return this.config.placeBlock(level, spreadPos, oldState, postProcess) ? Optional.of(spreadPos) : Optional.empty();
    }

    public static class DefaultSpreaderConfig implements MultifaceSpreader.SpreadConfig {
        protected final MultifaceBlock block;

        public DefaultSpreaderConfig(MultifaceBlock block) {
            this.block = block;
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockState oldState, BlockGetter level, BlockPos placementPos, Direction placementDirection) {
            return this.block.getStateForPlacement(oldState, level, placementPos, placementDirection);
        }

        protected boolean stateCanBeReplaced(
            BlockGetter level, BlockPos sourcePos, BlockPos placementPos, Direction placementDirection, BlockState existingState
        ) {
            return existingState.isAir() || existingState.is(this.block) || existingState.is(Blocks.WATER) && existingState.getFluidState().isSource();
        }

        @Override
        public boolean canSpreadInto(BlockGetter level, BlockPos sourcePos, MultifaceSpreader.SpreadPos spreadPos) {
            BlockState existingState = level.getBlockState(spreadPos.pos());
            return this.stateCanBeReplaced(level, sourcePos, spreadPos.pos(), spreadPos.face(), existingState)
                && this.block.isValidStateForPlacement(level, existingState, spreadPos.pos(), spreadPos.face());
        }
    }

    public interface SpreadConfig {
        @Nullable BlockState getStateForPlacement(
            final BlockState oldState, final BlockGetter level, final BlockPos placementPos, final Direction placementDirection
        );

        boolean canSpreadInto(final BlockGetter level, final BlockPos sourcePos, final MultifaceSpreader.SpreadPos spreadPos);

        default MultifaceSpreader.SpreadType[] getSpreadTypes() {
            return MultifaceSpreader.DEFAULT_SPREAD_ORDER;
        }

        default boolean hasFace(BlockState state, Direction face) {
            return MultifaceBlock.hasFace(state, face);
        }

        default boolean isOtherBlockValidAsSource(BlockState state) {
            return false;
        }

        default boolean canSpreadFrom(BlockState state, Direction face) {
            return this.isOtherBlockValidAsSource(state) || this.hasFace(state, face);
        }

        default boolean placeBlock(LevelAccessor level, MultifaceSpreader.SpreadPos spreadPos, BlockState oldState, boolean postProcess) {
            BlockState spreadState = this.getStateForPlacement(oldState, level, spreadPos.pos(), spreadPos.face());
            if (spreadState != null) {
                if (postProcess) {
                    level.getChunk(spreadPos.pos()).markPosForPostProcessing(spreadPos.pos());
                }

                return level.setBlock(spreadPos.pos(), spreadState, 2);
            } else {
                return false;
            }
        }
    }

    public record SpreadPos(BlockPos pos, Direction face) {
    }

    @FunctionalInterface
    public interface SpreadPredicate {
        boolean test(final BlockGetter level, final BlockPos sourcePos, final MultifaceSpreader.SpreadPos spreadPos);
    }

    public enum SpreadType {
        SAME_POSITION {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction spreadDirection, Direction fromFace) {
                return new MultifaceSpreader.SpreadPos(pos, spreadDirection);
            }
        },
        SAME_PLANE {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction spreadDirection, Direction fromFace) {
                return new MultifaceSpreader.SpreadPos(pos.relative(spreadDirection), fromFace);
            }
        },
        WRAP_AROUND {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction spreadDirection, Direction fromFace) {
                return new MultifaceSpreader.SpreadPos(pos.relative(spreadDirection).relative(fromFace), spreadDirection.getOpposite());
            }
        };

        public abstract MultifaceSpreader.SpreadPos getSpreadPos(final BlockPos pos, final Direction spreadDirection, final Direction fromFace);
    }
}
