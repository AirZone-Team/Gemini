package net.minecraft.world.level.levelgen.feature.rootplacers;

import com.mojang.datafixers.Products.P3;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public abstract class RootPlacer {
    public static final Codec<RootPlacer> CODEC = BuiltInRegistries.ROOT_PLACER_TYPE.byNameCodec().dispatch(RootPlacer::type, RootPlacerType::codec);
    protected final IntProvider trunkOffsetY;
    protected final BlockStateProvider rootProvider;
    protected final Optional<AboveRootPlacement> aboveRootPlacement;

    protected static <P extends RootPlacer> P3<Mu<P>, IntProvider, BlockStateProvider, Optional<AboveRootPlacement>> rootPlacerParts(Instance<P> instance) {
        return instance.group(
            IntProviders.CODEC.fieldOf("trunk_offset_y").forGetter(c -> c.trunkOffsetY),
            BlockStateProvider.CODEC.fieldOf("root_provider").forGetter(c -> c.rootProvider),
            AboveRootPlacement.CODEC.optionalFieldOf("above_root_placement").forGetter(c -> c.aboveRootPlacement)
        );
    }

    public RootPlacer(IntProvider trunkOffsetY, BlockStateProvider rootProvider, Optional<AboveRootPlacement> aboveRootPlacement) {
        this.trunkOffsetY = trunkOffsetY;
        this.rootProvider = rootProvider;
        this.aboveRootPlacement = aboveRootPlacement;
    }

    protected abstract RootPlacerType<?> type();

    public abstract boolean placeRoots(
        final WorldGenLevel level,
        final BiConsumer<BlockPos, BlockState> rootSetter,
        final RandomSource random,
        final BlockPos origin,
        final BlockPos trunkOrigin,
        final TreeConfiguration config
    );

    protected boolean canPlaceRoot(LevelSimulatedReader level, BlockPos pos) {
        return TreeFeature.validTreePos(level, pos);
    }

    protected void placeRoot(WorldGenLevel level, BiConsumer<BlockPos, BlockState> rootSetter, RandomSource random, BlockPos pos, TreeConfiguration config) {
        if (this.canPlaceRoot(level, pos)) {
            rootSetter.accept(pos, this.getPotentiallyWaterloggedState(level, pos, this.rootProvider.getState(level, random, pos)));
            if (this.aboveRootPlacement.isPresent()) {
                AboveRootPlacement abovePlacement = this.aboveRootPlacement.get();
                BlockPos above = pos.above();
                if (random.nextFloat() < abovePlacement.aboveRootPlacementChance() && level.isStateAtPosition(above, BlockBehaviour.BlockStateBase::isAir)) {
                    rootSetter.accept(
                        above, this.getPotentiallyWaterloggedState(level, above, abovePlacement.aboveRootProvider().getState(level, random, above))
                    );
                }
            }
        }
    }

    protected BlockState getPotentiallyWaterloggedState(LevelSimulatedReader level, BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            boolean waterlogged = level.isFluidAtPosition(pos, s -> s.is(FluidTags.WATER));
            return state.setValue(BlockStateProperties.WATERLOGGED, waterlogged);
        } else {
            return state;
        }
    }

    public BlockPos getTrunkOrigin(BlockPos origin, RandomSource random) {
        return origin.above(this.trunkOffsetY.sample(random));
    }
}
