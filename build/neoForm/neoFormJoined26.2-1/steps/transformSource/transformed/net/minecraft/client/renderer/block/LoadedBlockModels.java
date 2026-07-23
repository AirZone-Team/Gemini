package net.minecraft.client.renderer.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

@OnlyIn(Dist.CLIENT)
public class LoadedBlockModels {
    private static final Matrix4fc IDENTITY = new Matrix4f();
    private final Map<BlockState, BlockModel.Unbaked> unbakedModels;
    private final EntityModelSet entityModelSet;
    private final SpriteGetter sprites;
    private final PlayerSkinRenderCache playerSkinRenderCache;

    public LoadedBlockModels(
        Map<BlockState, BlockModel.Unbaked> unbakedModels, EntityModelSet entityModelSet, SpriteGetter sprites, PlayerSkinRenderCache playerSkinRenderCache
    ) {
        this.unbakedModels = unbakedModels;
        this.entityModelSet = entityModelSet;
        this.sprites = sprites;
        this.playerSkinRenderCache = playerSkinRenderCache;
    }

    /// @deprecated Neo: use [#bake(Function, BlockStateModel, Executor, net.neoforged.neoforge.client.entity.animation.json.AnimationLoader.PendingAnimations)] instead
    @Deprecated
    public CompletableFuture<Map<BlockState, BlockModel>> bake(
        Function<BlockState, BlockStateModel> bakedBlockStateModels, BlockStateModel missingModel, Executor executor
    ) {
        return bake(bakedBlockStateModels, missingModel, executor, net.neoforged.neoforge.client.entity.animation.json.AnimationLoader.PendingAnimations.EMPTY);
    }

    public CompletableFuture<Map<BlockState, BlockModel>> bake(
        Function<BlockState, BlockStateModel> bakedBlockStateModels, BlockStateModel missingModel, Executor executor, net.neoforged.neoforge.client.entity.animation.json.AnimationLoader.PendingAnimations pendingAnimations
    ) {
        return CompletableFuture.supplyAsync(
            () -> {
                BlockModel wrappedMissingModel = new BlockStateModelWrapper(missingModel, List.of(), IDENTITY);
                BlockModel.BakingContext context = new BlockModel.BakingContext(
                    this.entityModelSet, this.sprites, this.playerSkinRenderCache, bakedBlockStateModels, wrappedMissingModel, pendingAnimations
                );
                Builder<BlockState, BlockModel> result = ImmutableMap.builder();
                this.unbakedModels.forEach((blockState, unbakedModel) -> result.put(blockState, unbakedModel.bake(context, IDENTITY)));
                return result.build();
            },
            executor
        );
    }
}
