package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class MappedRegistry<T> extends net.neoforged.neoforge.registries.BaseMappedRegistry<T> implements WritableRegistry<T> {
    private final ResourceKey<? extends Registry<T>> key;
    private final ObjectList<Holder.Reference<T>> byId = new ObjectArrayList<>(256);
    private final Reference2IntMap<T> toId = Util.make(new Reference2IntOpenHashMap<>(), t -> t.defaultReturnValue(-1));
    private final Map<Identifier, Holder.Reference<T>> byLocation = new HashMap<>();
    private final Map<ResourceKey<T>, Holder.Reference<T>> byKey = new HashMap<>();
    private final Map<T, Holder.Reference<T>> byValue = new IdentityHashMap<>();
    private final Map<ResourceKey<T>, RegistrationInfo> registrationInfos = new IdentityHashMap<>();
    private Lifecycle registryLifecycle;
    private final Map<TagKey<T>, HolderSet.Named<T>> frozenTags = new IdentityHashMap<>();
    private MappedRegistry.TagSet<T> allTags = MappedRegistry.TagSet.unbound();
    private @Nullable DataComponentLookup<T> componentLookup;
    private boolean frozen;
    private @Nullable Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;

    @Override
    public Stream<HolderSet.Named<T>> listTags() {
        return this.getTags();
    }

    public MappedRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle) {
        this(key, lifecycle, false);
    }

    public MappedRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle initialLifecycle, boolean intrusiveHolders) {
        this.key = key;
        this.registryLifecycle = initialLifecycle;
        if (intrusiveHolders) {
            this.unregisteredIntrusiveHolders = new IdentityHashMap<>();
        }
    }

    @Override
    public ResourceKey<? extends Registry<T>> key() {
        return this.key;
    }

    @Override
    public String toString() {
        return "Registry[" + this.key + " (" + this.registryLifecycle + ")]";
    }

    private void validateWrite() {
        if (this.frozen) {
            throw new IllegalStateException("Registry is already frozen");
        }
    }

    private void validateWrite(ResourceKey<T> key) {
        if (this.frozen) {
            throw new IllegalStateException("Registry is already frozen (trying to add key " + key + ")");
        }
    }

    @Override
    public Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo registrationInfo) {
        return register(this.byId.size(), key, value, registrationInfo);
    }

    public Holder.Reference<T> register(int id, ResourceKey<T> key, T value, RegistrationInfo registrationInfo) {
        this.validateWrite(key);
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        int newId = id;
        if (newId > this.getMaxId())
            throw new IllegalStateException(String.format(java.util.Locale.ENGLISH, "Invalid id %d - maximum id range of %d exceeded.", newId, this.getMaxId()));

        if (this.byLocation.containsKey(key.identifier())) {
            throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Adding duplicate key '" + key + "' to registry"));
        }

        if (this.byValue.containsKey(value)) {
            throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Adding duplicate value '" + value + "' to registry"));
        }

        Holder.Reference<T> holder;
        if (this.unregisteredIntrusiveHolders != null) {
            holder = this.unregisteredIntrusiveHolders.remove(value);
            if (holder == null) {
                throw new AssertionError("Missing intrusive holder for " + key + ":" + value);
            }

            holder.bindKey(key);
        } else {
            holder = this.byKey.computeIfAbsent(key, k -> Holder.Reference.createStandAlone(this, (ResourceKey<T>)k));
            // Neo: Bind the value immediately so it can be queried while the registry is not frozen
            holder.bindValue(value);
        }

        this.byKey.put(key, holder);
        this.byLocation.put(key.identifier(), holder);
        this.byValue.put(value, holder);
        this.byId.add(holder);
        this.toId.put(value, newId);
        this.registrationInfos.put(key, registrationInfo);
        this.registryLifecycle = this.registryLifecycle.add(registrationInfo.lifecycle());
        this.addCallbacks.forEach(addCallback -> addCallback.onAdd(this, newId, key, value));
        return holder;
    }

    @Override
    public @Nullable Identifier getKey(T thing) {
        Holder.Reference<T> holder = this.byValue.get(thing);
        return holder != null ? holder.key().identifier() : null;
    }

    @Override
    public Optional<ResourceKey<T>> getResourceKey(T thing) {
        return Optional.ofNullable(this.byValue.get(thing)).map(Holder.Reference::key);
    }

    @Override
    public int getId(@Nullable T thing) {
        return this.toId.getInt(thing);
    }

    @Override
    public @Nullable T getValue(@Nullable ResourceKey<T> key) {
        return getValueFromNullable(this.byKey.get(resolve(key)));
    }

    @Override
    public @Nullable T byId(int id) {
        return id >= 0 && id < this.byId.size() ? this.byId.get(id).value() : null;
    }

    @Override
    public Optional<Holder.Reference<T>> get(int id) {
        return id >= 0 && id < this.byId.size() ? Optional.ofNullable(this.byId.get(id)) : Optional.empty();
    }

    @Override
    public Optional<Holder.Reference<T>> get(Identifier id) {
        return Optional.ofNullable(this.byLocation.get(resolve(id)));
    }

    @Override
    public Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
        return Optional.ofNullable(this.byKey.get(resolve(id)));
    }

    @Override
    public Optional<Holder.Reference<T>> getAny() {
        return this.byId.isEmpty() ? Optional.empty() : Optional.of(this.byId.getFirst());
    }

    @Override
    public Holder<T> wrapAsHolder(T value) {
        Holder.Reference<T> existingHolder = this.byValue.get(value);
        return existingHolder != null ? existingHolder : Holder.direct(value);
    }

    private Holder.Reference<T> getOrCreateHolderOrThrow(ResourceKey<T> key) {
        return this.byKey.computeIfAbsent(resolve(key), id -> {
            if (this.unregisteredIntrusiveHolders != null) {
                throw new IllegalStateException("This registry can't create new holders without value");
            }

            this.validateWrite((ResourceKey<T>)id);
            return Holder.Reference.createStandAlone(this, (ResourceKey<T>)id);
        });
    }

    @Override
    public int size() {
        return this.byKey.size();
    }

    @Override
    public Optional<RegistrationInfo> registrationInfo(ResourceKey<T> element) {
        return Optional.ofNullable(this.registrationInfos.get(element));
    }

    @Override
    public Lifecycle registryLifecycle() {
        return this.registryLifecycle;
    }

    @Override
    public Iterator<T> iterator() {
        return Iterators.transform(this.byId.iterator(), Holder::value);
    }

    @Override
    public @Nullable T getValue(@Nullable Identifier key) {
        Holder.Reference<T> result = this.byLocation.get(key != null ? resolve(key) : null);
        return getValueFromNullable(result);
    }

    private static <T> @Nullable T getValueFromNullable(Holder.@Nullable Reference<T> result) {
        return result != null ? result.value() : null;
    }

    @Override
    public Set<Identifier> keySet() {
        return Collections.unmodifiableSet(this.byLocation.keySet());
    }

    @Override
    public Set<ResourceKey<T>> registryKeySet() {
        return Collections.unmodifiableSet(this.byKey.keySet());
    }

    @Override
    public Set<Entry<ResourceKey<T>, T>> entrySet() {
        return Collections.unmodifiableSet(Util.mapValuesLazy(this.byKey, Holder::value).entrySet());
    }

    @Override
    public Stream<Holder.Reference<T>> listElements() {
        return this.byId.stream();
    }

    @Override
    public Stream<HolderSet.Named<T>> getTags() {
        return this.allTags.getTags();
    }

    private HolderSet.Named<T> getOrCreateTagForRegistration(TagKey<T> tag) {
        return this.frozenTags.computeIfAbsent(tag, this::createTag);
    }

    private HolderSet.Named<T> createTag(TagKey<T> tag) {
        return new HolderSet.Named<>(this, tag);
    }

    @Override
    public boolean isEmpty() {
        return this.byKey.isEmpty();
    }

    @Override
    public Optional<Holder.Reference<T>> getRandom(RandomSource random) {
        return Util.getRandomSafe(this.byId, random);
    }

    @Override
    public boolean containsKey(Identifier key) {
        return this.byLocation.containsKey(key);
    }

    @Override
    public boolean containsKey(ResourceKey<T> key) {
        return this.byKey.containsKey(key);
    }

    @Override
    public DataComponentLookup<T> componentLookup() {
        return Objects.requireNonNull(this.componentLookup, "Registry not frozen yet");
    }

    /** @deprecated Neo: For internal use only. Use the Register events when registering values. */
    @Deprecated
    @Override
    public void unfreeze(boolean clearTags) {
        if (clearTags) {
            // unbind tags which were bound by the previous freeze
            this.allTags = MappedRegistry.TagSet.unbound();
        }
        this.frozen = false;
    }

    @Override
    public Registry<T> freeze() {
        if (this.frozen) {
            return this;
        }

        this.frozen = true;
        List<Identifier> unboundEntries = this.byKey
            .entrySet()
            .stream()
            .filter(e -> !e.getValue().isBound())
            .map(e -> e.getKey().identifier())
            .sorted()
            .toList();
        if (!unboundEntries.isEmpty()) {
            throw new IllegalStateException("Unbound values in registry " + this.key() + ": " + unboundEntries);
        }

        if (this.unregisteredIntrusiveHolders != null) {
            if (!this.unregisteredIntrusiveHolders.isEmpty()) {
                throw new IllegalStateException("Some intrusive holders were not registered: " + this.unregisteredIntrusiveHolders.values());
            }

            // Neo: We freeze/unfreeze vanilla registries more than once, so we need to keep the unregistered intrusive holders map around.
            // this.unregisteredIntrusiveHolders = null;
        }
        this.bakeCallbacks.forEach(bakeCallback -> bakeCallback.onBake(this));

        if (this.allTags.isBound()) {
            // Neo: we freeze/unfreeze the registry to apply snapshots and tags need to be preserved in those cases
            // throw new IllegalStateException("Tags already present before freezing");
        }

        List<Identifier> unboundTags = this.frozenTags
            .entrySet()
            .stream()
            .filter(e -> !e.getValue().isBound())
            .map(e -> e.getKey().location())
            .sorted()
            .toList();
        if (!unboundTags.isEmpty()) {
            throw new IllegalStateException("Unbound tags in registry " + this.key() + ": " + unboundTags);
        }

        this.componentLookup = new DataComponentLookup<>(this.byId);
        this.allTags = MappedRegistry.TagSet.fromMap(this.frozenTags);
        this.refreshTagsInHolders();
        return this;
    }

    @Override
    public Holder.Reference<T> createIntrusiveHolder(T value) {
        if (this.unregisteredIntrusiveHolders == null) {
            throw new IllegalStateException("This registry can't create intrusive holders");
        }

        this.validateWrite();
        return this.unregisteredIntrusiveHolders.computeIfAbsent(value, v -> Holder.Reference.createIntrusive(this, (T)v));
    }

    @Override
    public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
        return this.allTags.get(id);
    }

    private Holder.Reference<T> validateAndUnwrapTagElement(TagKey<T> id, Holder<T> value) {
        if (!value.canSerializeIn(this)) {
            throw new IllegalStateException("Can't create named set " + id + " containing value " + value + " from outside registry " + this);
        } else if (value instanceof Holder.Reference<T> reference) {
            return reference;
        } else {
            throw new IllegalStateException("Found direct holder " + value + " value in tag " + id);
        }
    }

    @Override
    public void bindTags(Map<TagKey<T>, List<Holder<T>>> pendingTags) {
        this.validateWrite();
        pendingTags.forEach((id, values) -> this.getOrCreateTagForRegistration((TagKey<T>)id).bind((List<Holder<T>>)values));
    }

    private void refreshTagsInHolders() {
        Map<Holder.Reference<T>, List<TagKey<T>>> tagsForElement = new IdentityHashMap<>();
        this.byKey.values().forEach(h -> tagsForElement.put((Holder.Reference<T>)h, new ArrayList<>()));
        this.allTags.forEach((id, values) -> {
            for (Holder<T> value : values) {
                Holder.Reference<T> reference = this.validateAndUnwrapTagElement((TagKey<T>)id, value);
                tagsForElement.get(reference).add((TagKey<T>)id);
            }
        });
        tagsForElement.forEach(Holder.Reference::bindTags);
    }

    public void bindAllTagsToEmpty() {
        this.validateWrite();
        this.frozenTags.values().forEach(e -> e.bind(List.of()));
    }

    @Override
    public HolderGetter<T> createRegistrationLookup() {
        this.validateWrite();
        return new HolderGetter<T>() {
            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
                return Optional.of(this.getOrThrow(id));
            }

            @Override
            public Holder.Reference<T> getOrThrow(ResourceKey<T> id) {
                return MappedRegistry.this.getOrCreateHolderOrThrow(id);
            }

            @Override
            public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
                return Optional.of(this.getOrThrow(id));
            }

            @Override
            public HolderSet.Named<T> getOrThrow(TagKey<T> id) {
                return MappedRegistry.this.getOrCreateTagForRegistration(id);
            }
        };
    }

    @Override
    public Registry.PendingTags<T> prepareTagReload(TagLoader.LoadResult<T> tags) {
        if (!this.frozen) {
            throw new IllegalStateException("Invalid method used for tag loading");
        }

        Builder<TagKey<T>, HolderSet.Named<T>> pendingTagsBuilder = ImmutableMap.builder();
        final Map<TagKey<T>, List<Holder<T>>> pendingContents = new HashMap<>();
        tags.tags().forEach((id, contents) -> {
            HolderSet.Named<T> tagToAdd = this.frozenTags.get(id);
            if (tagToAdd == null) {
                tagToAdd = this.createTag((TagKey<T>)id);
            }

            pendingTagsBuilder.put((TagKey<T>)id, tagToAdd);
            pendingContents.put((TagKey<T>)id, List.copyOf(contents));
        });
        final ImmutableMap<TagKey<T>, HolderSet.Named<T>> pendingTags = pendingTagsBuilder.build();
        final HolderLookup.RegistryLookup<T> patchedHolder = new HolderLookup.RegistryLookup.Delegate<T>() {
            @Override
            public HolderLookup.RegistryLookup<T> parent() {
                return MappedRegistry.this;
            }

            @Override
            public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
                return Optional.ofNullable(pendingTags.get(id));
            }

            @Override
            public Stream<HolderSet.Named<T>> listTags() {
                return pendingTags.values().stream();
            }
        };
        return new Registry.PendingTags<T>() {
            @Override
            public ResourceKey<? extends Registry<? extends T>> key() {
                return MappedRegistry.this.key();
            }

            @Override
            public int size() {
                return pendingContents.size();
            }

            @Override
            public HolderLookup.RegistryLookup<T> lookup() {
                return patchedHolder;
            }

            // Neo: Make the underlying staged tag contents accessible for loading condition evaluation.
            @Override
            public Map<TagKey<T>, List<Holder<T>>> contents() {
                return Collections.unmodifiableMap(pendingContents);
            }

            @Override
            public void apply() {
                pendingTags.forEach((id, tag) -> {
                    List<Holder<T>> values = pendingContents.getOrDefault(id, List.of());
                    tag.bind(values);
                });
                MappedRegistry.this.allTags = MappedRegistry.TagSet.fromMap(pendingTags);
                MappedRegistry.this.refreshTagsInHolders();
            }
        };
    }

    @Override
    protected void clear(boolean full) {
        this.validateWrite();
        this.clearCallbacks.forEach(clearCallback -> clearCallback.onClear(this, full));
        super.clear(full);
        this.byId.clear();
        this.toId.clear();
        if (full) {
            this.byLocation.clear();
            this.byKey.clear();
            this.byValue.clear();
            this.allTags = MappedRegistry.TagSet.unbound();
            this.frozenTags.entrySet().removeIf(entry -> !entry.getValue().isBound());
            if (unregisteredIntrusiveHolders != null) {
                unregisteredIntrusiveHolders.clear();
                unregisteredIntrusiveHolders = null;
            }
        }
    }

    @Override
    protected void registerIdMapping(ResourceKey<T> key, int id) {
        this.validateWrite(key);
        if (id > this.getMaxId())
            throw new IllegalStateException(String.format(java.util.Locale.ENGLISH, "Invalid id %d - maximum id range of %d exceeded.", id, this.getMaxId()));
        if (0 <= id && id < this.byId.size() && this.byId.get(id) != null) { // Don't use byId() method, it will return the default value if the entry is absent
            throw new IllegalStateException("Duplicate id " + id + " for " + key + " and " + this.getKey(this.byId.get(id).value()));
        }
        var holder = byKey.get(key);
        while (this.byId.size() < (id + 1)) this.byId.add(null);
        this.byId.set(id, holder);
        this.toId.put(holder.value(), id);
    }

    @Override
    public int getId(Identifier name) {
        return getId(getValue(name));
    }

    @Override
    public int getId(ResourceKey<T> key) {
        return getId(getValue(key));
    }

    @Override
    public boolean containsValue(T value) {
        return byValue.containsKey(value);
    }

    private interface TagSet<T> {
        static <T> MappedRegistry.TagSet<T> unbound() {
            return new MappedRegistry.TagSet<T>() {
                @Override
                public boolean isBound() {
                    return false;
                }

                @Override
                public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
                    throw new IllegalStateException("Tags not bound, trying to access " + id);
                }

                @Override
                public void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action) {
                    throw new IllegalStateException("Tags not bound");
                }

                @Override
                public Stream<HolderSet.Named<T>> getTags() {
                    throw new IllegalStateException("Tags not bound");
                }
            };
        }

        static <T> MappedRegistry.TagSet<T> fromMap(Map<TagKey<T>, HolderSet.Named<T>> tags) {
            return new MappedRegistry.TagSet<T>() {
                @Override
                public boolean isBound() {
                    return true;
                }

                @Override
                public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
                    return Optional.ofNullable(tags.get(id));
                }

                @Override
                public void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action) {
                    tags.forEach(action);
                }

                @Override
                public Stream<HolderSet.Named<T>> getTags() {
                    return tags.values().stream();
                }
            };
        }

        boolean isBound();

        Optional<HolderSet.Named<T>> get(TagKey<T> id);

        void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> action);

        Stream<HolderSet.Named<T>> getTags();
    }
}
