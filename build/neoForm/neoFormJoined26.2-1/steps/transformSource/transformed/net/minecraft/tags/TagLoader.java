package net.minecraft.tags;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.DependencySorter;
import net.minecraft.util.StrictJsonParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class TagLoader<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final TagLoader.ElementLookup<T> elementLookup;
    private final String directory;

    public TagLoader(TagLoader.ElementLookup<T> elementLookup, String directory) {
        this.elementLookup = elementLookup;
        this.directory = directory;
    }

    public Map<Identifier, List<TagLoader.EntryWithSource>> load(ResourceManager resourceManager) {
        Map<Identifier, List<TagLoader.EntryWithSource>> builders = new HashMap<>();
        FileToIdConverter lister = FileToIdConverter.json(this.directory);

        for (Entry<Identifier, List<Resource>> entry : lister.listMatchingResourceStacks(resourceManager).entrySet()) {
            Identifier location = entry.getKey();
            Identifier id = lister.fileToId(location);

            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonElement element = StrictJsonParser.parse(reader);
                    List<TagLoader.EntryWithSource> tagContents = builders.computeIfAbsent(id, key -> new ArrayList<>());
                    TagFile parsedContents = TagFile.CODEC.parse(new Dynamic<>(JsonOps.INSTANCE, element)).getOrThrow();
                    if (parsedContents.replace()) {
                        tagContents.clear();
                    }

                    String sourceId = resource.sourcePackId();
                    parsedContents.entries().forEach(ex -> tagContents.add(new TagLoader.EntryWithSource(ex, sourceId)));
                    // Make all removal entries optional at runtime to avoid them creating intrusive holders - see NeoForge#2319
                    parsedContents.remove().forEach(e -> tagContents.add(new TagLoader.EntryWithSource(e.withRequired(false), sourceId, true)));
                } catch (Exception e) {
                    LOGGER.error("Couldn't read tag list {} from {} in data pack {}", id, location, resource.sourcePackId(), e);
                }
            }
        }

        return builders;
    }

    private Either<List<TagLoader.EntryWithSource>, List<T>> tryBuildTag(TagEntry.Lookup<T> lookup, List<TagLoader.EntryWithSource> entries) {
        SequencedSet<T> values = new LinkedHashSet<>();
        List<TagLoader.EntryWithSource> missingElements = new ArrayList<>();

        for (TagLoader.EntryWithSource entry : entries) {
            if (!entry.entry().build(lookup, entry.remove() ? values::remove : values::add)) {
                missingElements.add(entry);
            }
        }

        return missingElements.isEmpty() ? Either.right(List.copyOf(values)) : Either.left(missingElements);
    }

    public Map<Identifier, List<T>> build(Map<Identifier, List<TagLoader.EntryWithSource>> builders) {
        final Map<Identifier, List<T>> newTags = new HashMap<>();
        TagEntry.Lookup<T> lookup = new TagEntry.Lookup<T>() {
            @Override
            public @Nullable T element(Identifier key, boolean required) {
                return (T)TagLoader.this.elementLookup.get(key, required).orElse(null);
            }

            @Override
            public @Nullable Collection<T> tag(Identifier key) {
                return newTags.get(key);
            }
        };
        DependencySorter<Identifier, TagLoader.SortingEntry> sorter = new DependencySorter<>();
        builders.forEach((id, entry) -> sorter.addEntry(id, new TagLoader.SortingEntry((List<TagLoader.EntryWithSource>)entry)));
        sorter.orderByDependencies(
            (id, contents) -> this.tryBuildTag(lookup, contents.entries)
                .ifLeft(
                    missing -> LOGGER.error(
                        "Couldn't load tag {} as it is missing following references: {}",
                        id,
                        missing.stream().map(Objects::toString).collect(Collectors.joining("\n\t", "\n\t", ""))
                    )
                )
                .ifRight(tag -> newTags.put(id, (List<T>)tag))
        );
        return newTags;
    }

    public static <T> Map<TagKey<T>, List<Holder<T>>> loadTagsFromNetwork(TagNetworkSerialization.NetworkPayload tags, Registry<T> registry) {
        return tags.resolve(registry).tags;
    }

    public static List<Registry.PendingTags<?>> loadTagsForExistingRegistries(ResourceManager manager, RegistryAccess layer) {
        return layer.registries().map(entry -> loadPendingTags(manager, entry.value())).flatMap(Optional::stream).collect(Collectors.toUnmodifiableList());
    }

    public static <T> void loadTagsForRegistry(ResourceManager manager, WritableRegistry<T> registry) {
        loadTagsForRegistry(manager, registry.key(), TagLoader.ElementLookup.fromWritableRegistry(registry));
    }

    public static <T> Map<TagKey<T>, List<Holder<T>>> loadTagsForRegistry(
        ResourceManager manager, ResourceKey<? extends Registry<T>> registryKey, TagLoader.ElementLookup<Holder<T>> lookup
    ) {
        TagLoader<Holder<T>> loader = new TagLoader<>(lookup, Registries.tagsDirPath(registryKey));
        return wrapTags(registryKey, loader.build(loader.load(manager)));
    }

    private static <T> Map<TagKey<T>, List<Holder<T>>> wrapTags(ResourceKey<? extends Registry<T>> registryKey, Map<Identifier, List<Holder<T>>> tags) {
        return tags.entrySet().stream().collect(Collectors.toUnmodifiableMap(e -> TagKey.create(registryKey, e.getKey()), Entry::getValue));
    }

    private static <T> Optional<Registry.PendingTags<T>> loadPendingTags(ResourceManager manager, Registry<T> registry) {
        ResourceKey<? extends Registry<T>> key = registry.key();
        TagLoader<Holder<T>> loader = new TagLoader<>(
            (TagLoader.ElementLookup<Holder<T>>)TagLoader.ElementLookup.fromFrozenRegistry(registry), Registries.tagsDirPath(key)
        );
        TagLoader.LoadResult<T> tags = new TagLoader.LoadResult<>(key, wrapTags(registry.key(), loader.build(loader.load(manager))));
        return tags.tags().isEmpty() ? Optional.empty() : Optional.of(registry.prepareTagReload(tags));
    }

    public static List<HolderLookup.RegistryLookup<?>> buildUpdatedLookups(RegistryAccess.Frozen registries, List<Registry.PendingTags<?>> tags) {
        List<HolderLookup.RegistryLookup<?>> result = new ArrayList<>();
        registries.registries().forEach(lookup -> {
            Registry.PendingTags<?> foundTags = findTagsForRegistry(tags, lookup.key());
            result.add(foundTags != null ? foundTags.lookup() : lookup.value());
        });
        return result;
    }

    private static Registry.@Nullable PendingTags<?> findTagsForRegistry(List<Registry.PendingTags<?>> tags, ResourceKey<? extends Registry<?>> registryKey) {
        for (Registry.PendingTags<?> tag : tags) {
            if (tag.key() == registryKey) {
                return tag;
            }
        }

        return null;
    }

    public interface ElementLookup<T> {
        Optional<? extends T> get(Identifier id, boolean required);

        static <T> TagLoader.ElementLookup<? extends Holder<T>> fromFrozenRegistry(Registry<T> registry) {
            return (id, required) -> registry.get(id);
        }

        static <T> TagLoader.ElementLookup<Holder<T>> fromWritableRegistry(WritableRegistry<T> registry) {
            return fromGetters(registry.key(), registry.createRegistrationLookup(), registry);
        }

        static <T> TagLoader.ElementLookup<Holder<T>> fromGetters(
            ResourceKey<? extends Registry<T>> registryKey, HolderGetter<T> writable, HolderGetter<T> immutable
        ) {
            return (id, required) -> (required ? writable : immutable).get(ResourceKey.create(registryKey, id));
        }
    }

    public static record EntryWithSource(TagEntry entry, String source, boolean remove) {
        public EntryWithSource(TagEntry entry, String source) {
            this(entry, source, false);
        }

        @Override
        public String toString() {
            return this.entry + " (from " + this.source + ")";
        }
    }

    public record LoadResult<T>(ResourceKey<? extends Registry<T>> key, Map<TagKey<T>, List<Holder<T>>> tags) {
    }

    private record SortingEntry(List<TagLoader.EntryWithSource> entries) implements DependencySorter.Entry<Identifier> {
        @Override
        public void visitRequiredDependencies(Consumer<Identifier> output) {
            this.entries.forEach(e -> e.entry.visitRequiredDependencies(output));
        }

        @Override
        public void visitOptionalDependencies(Consumer<Identifier> output) {
            this.entries.forEach(e -> e.entry.visitOptionalDependencies(output));
        }
    }
}
