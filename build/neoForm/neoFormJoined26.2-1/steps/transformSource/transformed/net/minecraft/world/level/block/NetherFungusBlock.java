package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NetherFungusBlock extends VegetationBlock implements BonemealableBlock {
    public static final MapCodec<NetherFungusBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                ResourceKey.codec(Registries.CONFIGURED_FEATURE).fieldOf("feature").forGetter(b -> b.feature),
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("grows_on").forGetter(b -> b.requiredBlock),
                TagKey.codec(Registries.BLOCK).fieldOf("support_blocks").forGetter(b -> b.supportBlocks),
                propertiesCodec()
            )
            .apply(i, NetherFungusBlock::new)
    );
    private static final double BONEMEAL_SUCCESS_PROBABILITY = 0.4;
    private static final VoxelShape SHAPE = Block.column(8.0, 0.0, 9.0);
    private final Block requiredBlock;
    private final ResourceKey<ConfiguredFeature<?, ?>> feature;
    private final TagKey<Block> supportBlocks;

    @Override
    public MapCodec<NetherFungusBlock> codec() {
        return CODEC;
    }

    public NetherFungusBlock(
        ResourceKey<ConfiguredFeature<?, ?>> feature, Block requiredBlock, TagKey<Block> supportBlocks, BlockBehaviour.Properties properties
    ) {
        super(properties);
        this.feature = feature;
        this.requiredBlock = requiredBlock;
        this.supportBlocks = supportBlocks;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(this.supportBlocks);
    }

    private Optional<? extends Holder<ConfiguredFeature<?, ?>>> getFeature(LevelReader level) {
        return level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(this.feature);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockState belowState = level.getBlockState(pos.below());
        return belowState.is(this.requiredBlock) && level.isInsideBuildHeight(pos.above());
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return random.nextFloat() < 0.4;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        // Neo: Fire the BlockGrowFeatureEvent and change the ifPresent call to use the event's result.
        var event = net.neoforged.neoforge.event.EventHooks.fireBlockGrowFeature(level, random, pos, this.getFeature(level).orElse(null));
        if (event.isCanceled()) return;
        Optional.ofNullable(event.getFeature()).ifPresent(feature -> feature.value().place(level, level.getChunkSource().getGenerator(), random, pos));
    }
}
