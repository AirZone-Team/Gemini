package net.minecraft.client.renderer.block.dispatch;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import net.neoforged.neoforge.client.extensions.BlockStateModelExtension;

@OnlyIn(Dist.CLIENT)
public interface BlockStateModel extends BlockStateModelExtension {
    /// @deprecated Neo: Use [#collectParts(net.minecraft.client.renderer.block.BlockAndTintGetter, net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, RandomSource, List)].
    @Deprecated
    void collectParts(RandomSource random, List<BlockStateModelPart> output);

    /// @deprecated Neo: Use [#particleMaterial(net.minecraft.client.renderer.block.BlockAndTintGetter, net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState)].
    @Deprecated
    Material.Baked particleMaterial();

    /// @deprecated Neo: Use [#materialFlags(net.minecraft.client.renderer.block.BlockAndTintGetter, net.minecraft.core.BlockPos, BlockState)] instead
    @Deprecated
    @BakedQuad.MaterialFlags int materialFlags();

    /// @deprecated Neo: Use [#hasMaterialFlag(net.minecraft.client.renderer.block.BlockAndTintGetter, net.minecraft.core.BlockPos, BlockState, int)] instead
    @Deprecated
    default boolean hasMaterialFlag(@BakedQuad.MaterialFlags int flag) {
        return (this.materialFlags() & flag) != 0;
    }

    class SimpleCachedUnbakedRoot implements BlockStateModel.UnbakedRoot {
        private final BlockStateModel.Unbaked contents;
        private final ModelBaker.SharedOperationKey<BlockStateModel> bakingKey = new ModelBaker.SharedOperationKey<BlockStateModel>() {
            public BlockStateModel compute(ModelBaker modelBakery) {
                return SimpleCachedUnbakedRoot.this.contents.bake(modelBakery);
            }
        };

        public SimpleCachedUnbakedRoot(BlockStateModel.Unbaked contents) {
            this.contents = contents;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            this.contents.resolveDependencies(resolver);
        }

        @Override
        public BlockStateModel bake(BlockState blockState, ModelBaker modelBakery) {
            return modelBakery.compute(this.bakingKey);
        }

        @Override
        public Object visualEqualityGroup(BlockState blockState) {
            return this;
        }
    }

    interface Unbaked extends ResolvableModel {
        Codec<Weighted<Variant>> ELEMENT_CODEC = RecordCodecBuilder.create(
            i -> i.group(Variant.MAP_CODEC.forGetter(Weighted::value), ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(Weighted::weight))
                .apply(i, Weighted::new)
        );
        @org.jetbrains.annotations.ApiStatus.Internal
        Codec<Either<net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel, SingleVariant.Unbaked>> SINGLE_MODEL_CODEC = net.neoforged.neoforge.client.model.block.BlockStateModelHooks.makeSingleModelCodec().codec();
        @org.jetbrains.annotations.ApiStatus.Internal
        Codec<Weighted<Either<net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel, SingleVariant.Unbaked>>> WEIGHTED_MODEL_CODEC = net.neoforged.neoforge.client.model.block.BlockStateModelHooks.makeElementCodec();
        Codec<WeightedVariants.Unbaked> HARDCODED_WEIGHTED_CODEC = ExtraCodecs.nonEmptyList(WEIGHTED_MODEL_CODEC.listOf())
            .flatComapMap(
                w -> new WeightedVariants.Unbaked(WeightedList.of(Lists.transform(w, e -> e.map(either -> either.map(m -> m, m -> m))))),
                unbaked -> {
                    List<Weighted<BlockStateModel.Unbaked>> entries = unbaked.entries().unwrap();
                    List<Weighted<Either<net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel, SingleVariant.Unbaked>>> result = new ArrayList<>(entries.size());

                    for (Weighted<BlockStateModel.Unbaked> entry : entries) {
                        switch (entry.value()) {
                            case net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel customModel -> {
                                result.add(new Weighted<>(Either.left(customModel), entry.weight()));
                            }
                            case SingleVariant.Unbaked singlevariant$unbaked -> {
                                result.add(new Weighted<>(Either.right(new SingleVariant.Unbaked(singlevariant$unbaked.variant())), entry.weight()));
                            }
                            default -> {
                                return DataResult.error(() -> "Only custom models or single variants are supported");
                            }
                        }
                    }

                    return DataResult.success(result);
                }
            );
        Codec<BlockStateModel.Unbaked> CODEC = Codec.either(HARDCODED_WEIGHTED_CODEC, SINGLE_MODEL_CODEC)
            .flatComapMap(v -> v.map(l -> l, r -> r.map(m -> m, m -> m)), o -> {
                return switch (o) {
                    case net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel customModel -> DataResult.success(Either.right(Either.left(customModel)));
                    case SingleVariant.Unbaked single -> DataResult.success(Either.right(Either.right(single)));
                    case WeightedVariants.Unbaked multiple -> DataResult.success(Either.left(multiple));
                    default -> DataResult.error(() -> "Only a custom model or a single variant or a list of variants are supported");
                };
            });

        BlockStateModel bake(ModelBaker modelBakery);

        default BlockStateModel.UnbakedRoot asRoot() {
            return new BlockStateModel.SimpleCachedUnbakedRoot(this);
        }
    }

    interface UnbakedRoot extends ResolvableModel {
        BlockStateModel bake(BlockState blockState, ModelBaker modelBakery);

        Object visualEqualityGroup(BlockState blockState);
    }
}
