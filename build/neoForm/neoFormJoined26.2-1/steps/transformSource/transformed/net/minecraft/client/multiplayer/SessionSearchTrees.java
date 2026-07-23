package net.minecraft.client.multiplayer;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.searchtree.FullTextSearchTree;
import net.minecraft.client.searchtree.IdSearchTree;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SessionSearchTrees {
    private static final SessionSearchTrees.Key RECIPE_COLLECTIONS = new SessionSearchTrees.Key();
    public static final SessionSearchTrees.Key CREATIVE_NAMES = new SessionSearchTrees.Key();
    public static final SessionSearchTrees.Key CREATIVE_TAGS = new SessionSearchTrees.Key();
    private CompletableFuture<SearchTree<ItemStack>> creativeByNameSearch = CompletableFuture.completedFuture(SearchTree.empty());
    private CompletableFuture<SearchTree<ItemStack>> creativeByTagSearch = CompletableFuture.completedFuture(SearchTree.empty());
    private CompletableFuture<SearchTree<RecipeCollection>> recipeSearch = CompletableFuture.completedFuture(SearchTree.empty());
    private final Map<SessionSearchTrees.Key, Runnable> reloaders = new IdentityHashMap<>();

    private void register(SessionSearchTrees.Key location, Runnable updater) {
        updater.run();
        this.reloaders.put(location, updater);
    }

    public void rebuildAfterLanguageChange() {
        for (Runnable value : this.reloaders.values()) {
            value.run();
        }
    }

    private static Stream<String> getTooltipLines(Stream<ItemStack> items, Item.TooltipContext context, TooltipFlag flag) {
        return items.<Component>flatMap(item -> item.getTooltipLines(context, null, flag).stream())
            .map(l -> ChatFormatting.stripFormatting(l.getString()).trim())
            .filter(s -> !s.isEmpty());
    }

    public void updateRecipes(ClientRecipeBook recipeBook, Level level) {
        this.register(
            RECIPE_COLLECTIONS,
            () -> {
                List<RecipeCollection> recipes = recipeBook.getCollections();
                RegistryAccess registryAccess = level.registryAccess();
                Registry<Item> itemRegistries = registryAccess.lookupOrThrow(Registries.ITEM);
                Item.TooltipContext tooltipContext = Item.TooltipContext.of(registryAccess);
                ContextMap recipeContext = SlotDisplayContext.fromLevel(level);
                TooltipFlag tooltipFlag = net.neoforged.neoforge.client.ClientTooltipFlag.of(TooltipFlag.Default.NORMAL);
                CompletableFuture<?> previous = this.recipeSearch;
                this.recipeSearch = CompletableFuture.supplyAsync(
                    () -> new FullTextSearchTree<>(
                        collection -> getTooltipLines(
                            collection.getRecipes().stream().flatMap(e -> e.resultItems(recipeContext).stream()), tooltipContext, tooltipFlag
                        ),
                        collection -> collection.getRecipes()
                            .stream()
                            .flatMap(e -> e.resultItems(recipeContext).stream())
                            .map(stack -> itemRegistries.getKey(stack.getItem())),
                        recipes
                    ),
                    Util.backgroundExecutor()
                );
                previous.cancel(true);
            }
        );
    }

    public SearchTree<RecipeCollection> recipes() {
        return this.recipeSearch.join();
    }

    public void updateCreativeTags(List<ItemStack> items) {
        this.updateCreativeTags(items, CREATIVE_TAGS);
    }

    public void updateCreativeTags(List<ItemStack> items, SessionSearchTrees.Key key) {
        this.register(
            key,
            () -> {
                CompletableFuture<?> previous = net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getTagSearchTree(key);
                net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.putTagSearchTree(key, CompletableFuture.supplyAsync(
                    () -> new IdSearchTree<>(itemStack -> itemStack.tags().map(TagKey::location), items), Util.backgroundExecutor()
                ));
                previous.cancel(true);
            }
        );
    }

    public SearchTree<ItemStack> creativeTagSearch() {
        return this.creativeTagSearch(CREATIVE_TAGS);
    }

    public SearchTree<ItemStack> creativeTagSearch(SessionSearchTrees.Key key) {
        return net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getTagSearchTree(key).join();
    }

    public void updateCreativeTooltips(HolderLookup.Provider registries, List<ItemStack> itemStacks) {
        this.updateCreativeTooltips(registries, itemStacks, CREATIVE_NAMES);
    }

    public void updateCreativeTooltips(HolderLookup.Provider registries, List<ItemStack> itemStacks, SessionSearchTrees.Key key) {
        this.register(
            key,
            () -> {
                Item.TooltipContext tooltipContext = Item.TooltipContext.of(registries);
                TooltipFlag tooltipFlag = net.neoforged.neoforge.client.ClientTooltipFlag.of(TooltipFlag.Default.NORMAL.asCreative());
                CompletableFuture<?> previous = net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getNameSearchTree(key);
                net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.putNameSearchTree(key, CompletableFuture.supplyAsync(
                    () -> new FullTextSearchTree<>(
                        itemStack -> getTooltipLines(Stream.of(itemStack), tooltipContext, tooltipFlag),
                        itemStack -> itemStack.typeHolder().unwrapKey().map(ResourceKey::identifier).stream(),
                        itemStacks
                    ),
                    Util.backgroundExecutor()
                ));
                previous.cancel(true);
            }
        );
    }

    public SearchTree<ItemStack> creativeNameSearch() {
        return this.creativeNameSearch(CREATIVE_NAMES);
    }

    public SearchTree<ItemStack> creativeNameSearch(SessionSearchTrees.Key key) {
        return net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getNameSearchTree(key).join();
    }

    public static class Key {
    }
}
