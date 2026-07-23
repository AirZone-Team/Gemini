package net.minecraft.client.data.models;

import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import net.neoforged.neoforge.client.extensions.IModelProviderExtension;

@OnlyIn(Dist.CLIENT)
public class ModelProvider implements DataProvider, IModelProviderExtension {
    private final PackOutput.PathProvider blockStatePathProvider;
    private final PackOutput.PathProvider itemInfoPathProvider;
    private final PackOutput.PathProvider modelPathProvider;
    public final String modId;

    // Neo: Use the constructor which accepts a mod ID.
    @Deprecated
    public ModelProvider(PackOutput output) {
        this(output, Identifier.DEFAULT_NAMESPACE);
    }

    public ModelProvider(PackOutput output, String modId) {
        this.blockStatePathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
        this.itemInfoPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
        this.modelPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
        this.modId = modId;
    }

    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        blockModels.run();
        itemModels.run();
    }

    /**
     * Returns a {@link java.util.stream.Stream stream} containing all {@link Block blocks} which must have their models/block states generated or {@link java.util.stream.Stream#empty() empty} if none are desired.
     * <p>
     * When using providers for specific {@link Block block} usages, it is best to override this method returning the exact {@link Block blocks} which must be generated,
     * or {@link java.util.stream.Stream#empty() empty} if generating only {@link Item item} models.
     * <p>
     * Default implementation generates models for {@link Block blocks} matching the given {@code modId}.
     * @see #getKnownItems()
     */
    protected java.util.stream.Stream<? extends net.minecraft.core.Holder<Block>> getKnownBlocks() {
        return BuiltInRegistries.BLOCK.listElements().filter(holder -> holder.getKey().identifier().getNamespace().equals(modId));
    }

    /**
     * Returns a {@link java.util.stream.Stream stream} containing all {@link Item items} which must have their models/client items generated or {@link java.util.stream.Stream#empty() empty} if none are desired.
     * <p>
     * When using providers for specific {@link Item item} usages, it is best to override this method returning the exact {@link Item items} which must be generated,
     * or {@link java.util.stream.Stream#empty() empty} if generating only {@link Block block} models (which have no respective {@link Item item}).
     * <p>
     * Default implementation generates models for {@link Item items} matching the given {@code modId}.
     * @see #getKnownBlocks()
     */
    protected java.util.stream.Stream<? extends net.minecraft.core.Holder<Item>> getKnownItems() {
        return BuiltInRegistries.ITEM.listElements().filter(holder -> holder.getKey().identifier().getNamespace().equals(modId));
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        ModelProvider.ItemInfoCollector itemModels = new ModelProvider.ItemInfoCollector(this::getKnownItems);
        ModelProvider.BlockStateGeneratorCollector blockStateGenerators = new ModelProvider.BlockStateGeneratorCollector(this::getKnownBlocks);
        ModelProvider.SimpleModelCollector simpleModels = new ModelProvider.SimpleModelCollector();
        registerModels(new BlockModelGenerators(blockStateGenerators, itemModels, simpleModels), new ItemModelGenerators(itemModels, simpleModels));
        blockStateGenerators.validate();
        itemModels.finalizeAndValidate();
        return CompletableFuture.allOf(
            blockStateGenerators.save(cache, this.blockStatePathProvider),
            simpleModels.save(cache, this.modelPathProvider),
            itemModels.save(cache, this.itemInfoPathProvider)
        );
    }

    @Override
    public String getName() {
        return "Model Definitions - " + modId;
    }

    private static class BlockStateGeneratorCollector implements Consumer<BlockModelDefinitionGenerator> {
        private final Map<Block, BlockModelDefinitionGenerator> generators = new HashMap<>();
        private final Supplier<java.util.stream.Stream<? extends net.minecraft.core.Holder<Block>>> knownBlocks;

        public BlockStateGeneratorCollector(Supplier<java.util.stream.Stream<? extends net.minecraft.core.Holder<Block>>> knownBlocks) {
            this.knownBlocks = knownBlocks;
        }

        @Deprecated // Neo: Provided for vanilla/multi-loader compatibility. Use constructor with Supplier parameter.
        public BlockStateGeneratorCollector() {
            this(BuiltInRegistries.BLOCK::listElements);
        }

        public void accept(BlockModelDefinitionGenerator generator) {
            Block block = generator.block();
            BlockModelDefinitionGenerator prev = this.generators.put(block, generator);
            if (prev != null) {
                throw new IllegalStateException("Duplicate blockstate definition for " + block);
            }
        }

        public void validate() {
            var holders = knownBlocks.get();
            List<Identifier> missingDefinitions = holders.filter(e -> !this.generators.containsKey(e.value())).map(e -> e.unwrapKey().orElseThrow().identifier()).toList();
            if (!missingDefinitions.isEmpty()) {
                throw new IllegalStateException("Missing blockstate definitions for: " + missingDefinitions);
            }
        }

        public CompletableFuture<?> save(CachedOutput cache, PackOutput.PathProvider pathProvider) {
            Map<Block, BlockStateModelDispatcher> definitions = Maps.transformValues(this.generators, BlockModelDefinitionGenerator::create);
            Function<Block, Path> pathGetter = block -> pathProvider.json(block.builtInRegistryHolder().key().identifier());
            return DataProvider.saveAll(cache, BlockStateModelDispatcher.CODEC, pathGetter, definitions);
        }
    }

    private static class ItemInfoCollector implements ItemModelOutput {
        private final Map<Item, ClientItem> itemInfos = new HashMap<>();
        private final Map<Item, Item> copies = new HashMap<>();
        private final Supplier<java.util.stream.Stream<? extends net.minecraft.core.Holder<Item>>> knownItems;
        private final Map<Identifier, ClientItem> idItemInfos = new HashMap<>();

        public ItemInfoCollector(Supplier<java.util.stream.Stream<? extends net.minecraft.core.Holder<Item>>> knownItems) {
            this.knownItems = knownItems;
        }

        @Deprecated // Neo: Provided for vanilla/multi-loader compatibility. Use constructor with Supplier parameter.
        public ItemInfoCollector() {
            this(BuiltInRegistries.ITEM::listElements);
        }

        @Override
        public void accept(Item item, ItemModel.Unbaked model, ClientItem.Properties properties) {
            this.register(item, new ClientItem(model, properties));
        }

        public void register(Item item, ClientItem itemInfo) {
            ClientItem prev = this.itemInfos.put(item, itemInfo);
            if (prev != null) {
                throw new IllegalStateException("Duplicate item model definition for " + item);
            }
        }

        @Override
        public void register(Identifier identifier, ClientItem clientItem) {
            ClientItem existing = this.idItemInfos.putIfAbsent(identifier, clientItem);
            if (existing != null) {
                throw new IllegalStateException("Duplicate item model definition for " + identifier);
            }
        }

        @Override
        public void copy(Item donor, Item acceptor) {
            this.copies.put(acceptor, donor);
        }

        public void finalizeAndValidate() {
            knownItems.get().map(net.minecraft.core.Holder::value).forEach(item -> {
                if (!this.copies.containsKey(item)) {
                    if (item instanceof BlockItem blockItem && !this.itemInfos.containsKey(blockItem)) {
                        Identifier targetModel = ModelLocationUtils.getModelLocation(blockItem.getBlock());
                        this.accept(blockItem, ItemModelUtils.plainModel(targetModel));
                    }
                }
            });
            this.copies.forEach((acceptor, donor) -> {
                ClientItem donorInfo = this.itemInfos.get(donor);
                if (donorInfo == null) {
                    throw new IllegalStateException("Missing donor: " + donor + " -> " + acceptor);
                }

                this.register(acceptor, donorInfo);
            });
            List<Identifier> missingDefinitions = knownItems.get()
                .filter(e -> !this.itemInfos.containsKey(e.value()))
                .map(e -> e.unwrapKey().orElseThrow().identifier())
                .toList();
            if (!missingDefinitions.isEmpty()) {
                throw new IllegalStateException("Missing item model definitions for: " + missingDefinitions);
            }
        }

        public CompletableFuture<?> save(CachedOutput cache, PackOutput.PathProvider pathProvider) {
            return CompletableFuture.allOf(
                    DataProvider.saveAll(cache, ClientItem.CODEC, item -> pathProvider.json(item.builtInRegistryHolder().key().identifier()), this.itemInfos),
                    DataProvider.saveAll(cache, ClientItem.CODEC, pathProvider::json, this.idItemInfos));
        }
    }

    private static class SimpleModelCollector implements BiConsumer<Identifier, ModelInstance> {
        private final Map<Identifier, ModelInstance> models = new HashMap<>();

        public void accept(Identifier id, ModelInstance contents) {
            Supplier<JsonElement> prev = this.models.put(id, contents);
            if (prev != null) {
                throw new IllegalStateException("Duplicate model definition for " + id);
            }
        }

        public CompletableFuture<?> save(CachedOutput cache, PackOutput.PathProvider pathProvider) {
            return DataProvider.saveAll(cache, Supplier::get, pathProvider::json, this.models);
        }
    }
}
