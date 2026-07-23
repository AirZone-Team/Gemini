package net.minecraft.client.resources.model;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.util.thread.ParallelMapTransform;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ModelBakery {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final SpriteId FIRE_0 = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("fire_0");
    public static final SpriteId FIRE_1 = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("fire_1");
    public static final int DESTROY_STAGE_COUNT = 10;
    public static final List<Identifier> DESTROY_STAGES = IntStream.range(0, 10)
        .mapToObj(i -> Identifier.withDefaultNamespace("block/destroy_stage_" + i))
        .collect(Collectors.toList());
    public static final List<Identifier> BREAKING_LOCATIONS = DESTROY_STAGES.stream()
        .map(location -> location.withPath(path -> "textures/" + path + ".png"))
        .collect(Collectors.toList());
    public static final List<RenderType> DESTROY_TYPES = BREAKING_LOCATIONS.stream().map(RenderTypes::crumbling).collect(Collectors.toList());
    private static final Matrix4fc IDENTITY = new Matrix4f();
    private final EntityModelSet entityModelSet;
    private final SpriteGetter sprites;
    private final PlayerSkinRenderCache playerSkinRenderCache;
    private final Map<BlockState, BlockStateModel.UnbakedRoot> unbakedBlockStateModels;
    private final Map<Identifier, ClientItem> clientInfos;
    private final Map<Identifier, ResolvedModel> resolvedModels;
    private final ResolvedModel missingModel;
    private final net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader.LoadedModels standaloneModels;
    private final net.neoforged.neoforge.client.entity.animation.json.AnimationLoader.PendingAnimations pendingAnimations;

    /// @deprecated Neo: use [#ModelBakery(EntityModelSet, SpriteGetter, PlayerSkinRenderCache, Map, Map, Map, ResolvedModel, net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader.LoadedModels, net.neoforged.neoforge.client.entity.animation.json.AnimationLoader.PendingAnimations)] instead
    @Deprecated
    public ModelBakery(
        EntityModelSet entityModelSet,
        SpriteGetter sprites,
        PlayerSkinRenderCache playerSkinRenderCache,
        Map<BlockState, BlockStateModel.UnbakedRoot> unbakedBlockStateModels,
        Map<Identifier, ClientItem> clientInfos,
        Map<Identifier, ResolvedModel> resolvedModels,
        ResolvedModel missingModel
    ) {
        this(entityModelSet, sprites, playerSkinRenderCache, unbakedBlockStateModels, clientInfos, resolvedModels, missingModel, net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader.LoadedModels.EMPTY, net.neoforged.neoforge.client.entity.animation.json.AnimationLoader.PendingAnimations.EMPTY);
    }

    public ModelBakery(
        EntityModelSet entityModelSet,
        SpriteGetter sprites,
        PlayerSkinRenderCache playerSkinRenderCache,
        Map<BlockState, BlockStateModel.UnbakedRoot> unbakedBlockStateModels,
        Map<Identifier, ClientItem> clientInfos,
        Map<Identifier, ResolvedModel> resolvedModels,
        ResolvedModel missingModel,
        net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader.LoadedModels standaloneModels,
        net.neoforged.neoforge.client.entity.animation.json.AnimationLoader.PendingAnimations pendingAnimations
    ) {
        this.entityModelSet = entityModelSet;
        this.sprites = sprites;
        this.playerSkinRenderCache = playerSkinRenderCache;
        this.unbakedBlockStateModels = unbakedBlockStateModels;
        this.clientInfos = clientInfos;
        this.resolvedModels = resolvedModels;
        this.missingModel = missingModel;
        this.standaloneModels = standaloneModels;
        this.pendingAnimations = pendingAnimations;
    }

    public CompletableFuture<ModelBakery.BakingResult> bakeModels(MaterialBaker materials, Executor taskExecutor) {
        ModelBakery.InternerImpl interner = new ModelBakery.InternerImpl();
        ModelBakery.MissingModels missingModels = ModelBakery.MissingModels.bake(this.missingModel, materials, interner);
        ModelBakery.ModelBakerImpl baker = new ModelBakery.ModelBakerImpl(materials, interner, missingModels);
        CompletableFuture<Map<BlockState, BlockStateModel>> bakedBlockStateModelFuture = ParallelMapTransform.schedule(
            this.unbakedBlockStateModels, (blockState, model) -> {
                try {
                    return model.bake(blockState, baker);
                } catch (Exception e) {
                    LOGGER.warn("Unable to bake model: '{}': {}", blockState, e);
                    return null;
                }
            }, taskExecutor
        );
        CompletableFuture<Map<Identifier, ItemModel>> bakedItemStackModelFuture = ParallelMapTransform.schedule(
            this.clientInfos,
            (location, clientInfo) -> {
                try {
                    return clientInfo.model()
                        .bake(
                            new ItemModel.BakingContext(
                                baker, this.entityModelSet, this.sprites, this.playerSkinRenderCache, missingModels.item, clientInfo.registrySwapper(), this.pendingAnimations
                            ),
                            IDENTITY
                        );
                } catch (Exception e) {
                    LOGGER.warn("Unable to bake item model: '{}'", location, e);
                    return null;
                }
            },
            taskExecutor
        );
        CompletableFuture<net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader.BakedModels> standaloneModelsFuture =
                net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader.bake(this.standaloneModels, baker, taskExecutor);
        Map<Identifier, ClientItem.Properties> itemStackModelProperties = new HashMap<>(this.clientInfos.size());
        this.clientInfos.forEach((id, clientInfo) -> {
            ClientItem.Properties properties = clientInfo.properties();
            if (!properties.equals(ClientItem.Properties.DEFAULT)) {
                itemStackModelProperties.put(id, properties);
            }
        });
        bakedBlockStateModelFuture = bakedBlockStateModelFuture.thenCombine(standaloneModelsFuture, (stateModels, standaloneModels) -> stateModels);
        return bakedBlockStateModelFuture.thenCombine(
            bakedItemStackModelFuture,
            (bakedBlockStateModels, bakedItemStateModels) -> new ModelBakery.BakingResult(
                missingModels,
                (Map<BlockState, BlockStateModel>)bakedBlockStateModels,
                (Map<Identifier, ItemModel>)bakedItemStateModels,
                itemStackModelProperties,
                standaloneModelsFuture.join()
            )
        );
    }

    public record BakingResult(
        ModelBakery.MissingModels missingModels,
        Map<BlockState, BlockStateModel> blockStateModels,
        Map<Identifier, ItemModel> itemStackModels,
        Map<Identifier, ClientItem.Properties> itemProperties
        , net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader.BakedModels standaloneModels
    ) {
        /// @deprecated Neo: use [#BakingResult(ModelBakery.MissingModels, Map, Map, Map, net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader.BakedModels)] instead
        @Deprecated
        public BakingResult(
                ModelBakery.MissingModels missingModels,
                Map<BlockState, BlockStateModel> blockStateModels,
                Map<Identifier, ItemModel> itemStackModels,
                Map<Identifier, ClientItem.Properties> itemProperties
        ) {
            this(missingModels, blockStateModels, itemStackModels, itemProperties, net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader.BakedModels.EMPTY);
        }

        public BlockStateModel getBlockStateModel(BlockState blockState) {
            return this.blockStateModels.getOrDefault(blockState, this.missingModels.block);
        }
    }

    private static class InternerImpl implements ModelBaker.Interner {
        private final Interner<Vector3fc> vectors = Interners.newStrongInterner();
        private final Interner<BakedQuad.MaterialInfo> materialInfos = Interners.newStrongInterner();
        private final Interner<net.neoforged.neoforge.client.model.quad.BakedNormals> normals = Interners.newStrongInterner();
        private final Interner<net.neoforged.neoforge.client.model.quad.BakedColors> colors = Interners.newStrongInterner();

        @Override
        public Vector3fc vector(Vector3fc v) {
            return this.vectors.intern(v);
        }

        @Override
        public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
            return this.materialInfos.intern(material);
        }

        @Override
        public net.neoforged.neoforge.client.model.quad.BakedNormals normals(net.neoforged.neoforge.client.model.quad.BakedNormals normals) {
            return this.normals.intern(normals);
        }

        @Override
        public net.neoforged.neoforge.client.model.quad.BakedColors colors(net.neoforged.neoforge.client.model.quad.BakedColors colors) {
            return this.colors.intern(colors);
        }
    }

    public record MissingModels(BlockStateModelPart blockPart, BlockStateModel block, MissingItemModel item, FluidModel fluid) {
        public static ModelBakery.MissingModels bake(ResolvedModel unbaked, MaterialBaker materials, ModelBaker.Interner interner) {
            ModelBaker missingModelBakery = new ModelBaker() {
                @Override
                public ResolvedModel getModel(Identifier location) {
                    throw new IllegalStateException("Missing model can't have dependencies, but asked for " + location);
                }

                @Override
                public BlockStateModelPart missingBlockModelPart() {
                    throw new IllegalStateException();
                }

                @Override
                public <T> T compute(ModelBaker.SharedOperationKey<T> key) {
                    return key.compute(this);
                }

                @Override
                public MaterialBaker materials() {
                    return materials;
                }

                @Override
                public ModelBaker.Interner interner() {
                    return interner;
                }
            };
            TextureSlots textureSlots = unbaked.getTopTextureSlots();
            boolean hasAmbientOcclusion = unbaked.getTopAmbientOcclusion();
            boolean usesBlockLight = unbaked.getTopGuiLight().lightLikeBlock();
            ItemTransforms transforms = unbaked.getTopTransforms();
            QuadCollection geometry = unbaked.bakeTopGeometry(textureSlots, missingModelBakery, BlockModelRotation.IDENTITY);
            Multimap<Identifier, Identifier> forbiddenSprites = SimpleModelWrapper.findNonBlockSprites(geometry);
            if (forbiddenSprites != null) {
                throw new IllegalStateException("Missing block contains sprites from outside of block atlas: " + forbiddenSprites);
            }

            Material.Baked particleMaterial = unbaked.resolveParticleMaterial(textureSlots, missingModelBakery);
            SimpleModelWrapper missingModelPart = new SimpleModelWrapper(geometry, hasAmbientOcclusion, particleMaterial);
            BlockStateModel bakedBlockModel = new SingleVariant(missingModelPart);
            MissingItemModel bakedItemModel = new MissingItemModel(geometry.getAll(), new ModelRenderProperties(usesBlockLight, particleMaterial, transforms));
            FluidModel bakedFluidModel = new FluidModel(ChunkSectionLayer.SOLID, particleMaterial, particleMaterial, null, null);
            return new ModelBakery.MissingModels(missingModelPart, bakedBlockModel, bakedItemModel, bakedFluidModel);
        }
    }

    private class ModelBakerImpl implements ModelBaker {
        private final MaterialBaker materials;
        private final ModelBaker.Interner interner;
        private final ModelBakery.MissingModels missingModels;
        private final Map<ModelBaker.SharedOperationKey<Object>, Object> operationCache = new ConcurrentHashMap<>();
        private final Function<ModelBaker.SharedOperationKey<Object>, Object> cacheComputeFunction = k -> k.compute(this);

        private ModelBakerImpl(MaterialBaker materials, ModelBaker.Interner interner, ModelBakery.MissingModels missingModels) {
            this.materials = materials;
            this.interner = interner;
            this.missingModels = missingModels;
        }

        @Override
        public BlockStateModelPart missingBlockModelPart() {
            return this.missingModels.blockPart;
        }

        @Override
        public MaterialBaker materials() {
            return this.materials;
        }

        @Override
        public ModelBaker.Interner interner() {
            return this.interner;
        }

        @Override
        public ResolvedModel getModel(Identifier location) {
            ResolvedModel result = ModelBakery.this.resolvedModels.get(location);
            if (result == null) {
                ModelBakery.LOGGER.warn("Requested a model that was not discovered previously: {}", location);
                return ModelBakery.this.missingModel;
            } else {
                return result;
            }
        }

        @Override
        public <T> T compute(ModelBaker.SharedOperationKey<T> key) {
            return (T)this.operationCache.computeIfAbsent((ModelBaker.SharedOperationKey<Object>)key, this.cacheComputeFunction);
        }
    }
}
