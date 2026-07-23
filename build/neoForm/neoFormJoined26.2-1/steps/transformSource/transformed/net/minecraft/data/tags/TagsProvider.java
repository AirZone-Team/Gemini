package net.minecraft.data.tags;

import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;

public abstract class TagsProvider<T> implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final CompletableFuture<HolderLookup.Provider> lookupProvider;
    private final CompletableFuture<Void> contentsDone = new CompletableFuture<>();
    private final CompletableFuture<TagsProvider.TagLookup<T>> parentProvider;
    protected final ResourceKey<? extends Registry<T>> registryKey;
    protected final Map<Identifier, TagBuilder> builders = Maps.newLinkedHashMap();
    protected final String modId;

    /**
     * @deprecated Forge: Use the {@linkplain #TagsProvider(PackOutput, ResourceKey, CompletableFuture, String) mod id variant}
     */
    @Deprecated
    protected TagsProvider(PackOutput output, ResourceKey<? extends Registry<T>> registryKey, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        this(output, registryKey, lookupProvider, "vanilla");
    }
    protected TagsProvider(PackOutput output, ResourceKey<? extends Registry<T>> registryKey, CompletableFuture<HolderLookup.Provider> lookupProvider, String modId) {
        this(output, registryKey, lookupProvider, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()), modId);
    }

    /**
     * @deprecated Forge: Use the {@linkplain #TagsProvider(PackOutput, ResourceKey, CompletableFuture, CompletableFuture, String) mod id variant}
     */
    @Deprecated
    protected TagsProvider(
        PackOutput output,
        ResourceKey<? extends Registry<T>> registryKey,
        CompletableFuture<HolderLookup.Provider> lookupProvider,
        CompletableFuture<TagsProvider.TagLookup<T>> parentProvider
    ) {
        this(output, registryKey, lookupProvider, parentProvider, "vanilla");
    }

    protected TagsProvider(PackOutput output, ResourceKey<? extends Registry<T>> registryKey, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagsProvider.TagLookup<T>> parentProvider, String modId) {
        this.pathProvider = output.createRegistryTagsPathProvider(registryKey);
        this.registryKey = registryKey;
        this.parentProvider = parentProvider;
        this.lookupProvider = lookupProvider;
        this.modId = modId;
    }

    // Forge: Allow customizing the path for a given tag or returning null
    @org.jspecify.annotations.Nullable
    protected Path getPath(Identifier id) {
        return this.pathProvider.json(id);
    }

    @Override
    public String getName() {
        return "Tags for " + this.registryKey.identifier() + " mod id " + this.modId;
    }

    protected abstract void addTags(HolderLookup.Provider registries);

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        record CombinedData<T>(HolderLookup.Provider contents, TagsProvider.TagLookup<T> parent) {
        }

        return this.createContentsProvider()
            .thenApply(provider -> {
                this.contentsDone.complete(null);
                return (HolderLookup.Provider)provider;
            })
            .thenCombineAsync(this.parentProvider, (x$0, x$1) -> new CombinedData<>(x$0, (TagsProvider.TagLookup<T>)x$1), Util.backgroundExecutor())
            .thenCompose(
                c -> {
                    HolderLookup.RegistryLookup<T> lookup = c.contents.lookupOrThrow(this.registryKey);
                    Predicate<Identifier> elementCheck = id -> lookup.get(ResourceKey.create(this.registryKey, id)).isPresent();
                    Predicate<Identifier> tagCheck = id -> this.builders.containsKey(id) || c.parent.contains(TagKey.create(this.registryKey, id));
                    return CompletableFuture.allOf(
                        this.builders
                            .entrySet()
                            .stream()
                            .map(
                                entry -> {
                                    Identifier id = entry.getKey();
                                    TagBuilder builder = entry.getValue();
                                    List<TagEntry> entries = builder.build();
                                    List<TagEntry> unresolvedEntries = java.util.stream.Stream.concat(entries.stream(), builder.getRemoveEntries())
                                               // Neo: Assume tags from other namespaces always exists
                                              .filter((e) -> e.getId().getNamespace().equals(modId) && !e.verifyIfPresent(elementCheck, tagCheck))
                                              .toList();
                                    if (!unresolvedEntries.isEmpty()) {
                                        throw new IllegalArgumentException(
                                            String.format(
                                                Locale.ROOT,
                                                "Couldn't define tag %s as it is missing following references: %s",
                                                id,
                                                unresolvedEntries.stream().map(Objects::toString).collect(Collectors.joining(","))
                                            )
                                        );
                                    }

                                    Path path = this.getPath(id);
                                    if (path == null) return CompletableFuture.completedFuture(null); // Neo: Allow running this data provider without writing it. Recipe provider needs valid tags.
                                    var removed = builder.getRemoveEntries().toList();
                                    return DataProvider.saveStable(cache, c.contents, TagFile.CODEC, new TagFile(entries, builder.shouldReplace(), removed), path);
                                }
                            )
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    protected TagBuilder getOrCreateRawBuilder(TagKey<T> tag) {
        return this.builders.computeIfAbsent(tag.location(), k -> TagBuilder.create());
    }

    public CompletableFuture<TagsProvider.TagLookup<T>> contentsGetter() {
        return this.contentsDone.thenApply(ignore -> id -> Optional.ofNullable(this.builders.get(id.location())));
    }

    protected CompletableFuture<HolderLookup.Provider> createContentsProvider() {
        return this.lookupProvider.thenApply(registries -> {
            this.builders.clear();
            this.addTags(registries);
            return (HolderLookup.Provider)registries;
        });
    }

    protected TagAppender<T> tag(TagKey<T> tag) {
        TagBuilder builder = this.getOrCreateRawBuilder(tag);
        return TagAppender.forBuilder(builder);
    }

    protected TagAppender<T> tag(TagKey<T> tag, boolean replace) {
        TagBuilder builder = this.getOrCreateRawBuilder(tag);
        builder.setReplace(replace);
        return TagAppender.forBuilder(builder);
    }

    @FunctionalInterface
    public interface TagLookup<T> extends Function<TagKey<T>, Optional<TagBuilder>> {
        static <T> TagsProvider.TagLookup<T> empty() {
            return id -> Optional.empty();
        }

        default boolean contains(TagKey<T> key) {
            return this.apply(key).isPresent();
        }
    }
}
