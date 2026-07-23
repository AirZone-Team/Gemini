package net.minecraft.core;

import com.mojang.datafixers.util.Either;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.common.extensions.IHolderExtension;

// TODO 26.2 - decide how to deal with Holder being seleaed
public /* sealed */ interface Holder<T> extends IHolderExtension<T> /* permits Holder.Direct, Holder.Reference */ {
    T value();

    boolean isBound();

    boolean areComponentsBound();

    boolean is(Identifier key);

    boolean is(ResourceKey<T> key);

    boolean is(Predicate<ResourceKey<T>> predicate);

    boolean is(TagKey<T> tag);

    @Deprecated
    boolean is(Holder<T> holder);

    Stream<TagKey<T>> tags();

    DataComponentMap components();

    Either<ResourceKey<T>, T> unwrap();

    Optional<ResourceKey<T>> unwrapKey();

    Holder.Kind kind();

    boolean canSerializeIn(HolderOwner<T> registry);

    default String getRegisteredName() {
        return this.unwrapKey().map(key -> key.identifier().toString()).orElse("[unregistered]");
    }

    static <T> Holder<T> direct(T value) {
        return new Holder.Direct<>(value, DataComponentMap.EMPTY);
    }

    static <T> Holder<T> direct(T value, DataComponentMap components) {
        return new Holder.Direct<>(value, components);
    }

    record Direct<T>(T value, DataComponentMap components) implements Holder<T> {
        @Override
        public boolean isBound() {
            return true;
        }

        @Override
        public boolean areComponentsBound() {
            return true;
        }

        @Override
        public boolean is(Identifier key) {
            return false;
        }

        @Override
        public boolean is(ResourceKey<T> key) {
            return false;
        }

        @Override
        public boolean is(TagKey<T> tag) {
            return false;
        }

        @Override
        public boolean is(Holder<T> holder) {
            return this.value.equals(holder.value());
        }

        @Override
        public boolean is(Predicate<ResourceKey<T>> predicate) {
            return false;
        }

        @Override
        public Either<ResourceKey<T>, T> unwrap() {
            return Either.right(this.value);
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.empty();
        }

        @Override
        public Holder.Kind kind() {
            return Holder.Kind.DIRECT;
        }

        @Override
        public String toString() {
            return "Direct{" + this.value + "}";
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> registry) {
            return true;
        }

        @Override
        public Stream<TagKey<T>> tags() {
            return Stream.of();
        }
    }

    enum Kind {
        REFERENCE,
        DIRECT;
    }

    /* non-sealed */ class Reference<T> implements Holder<T> {
        private final HolderOwner<T> owner;
        private @Nullable Set<TagKey<T>> tags;
        private @Nullable DataComponentMap components;
        private final Holder.Reference.Type type;
        private @Nullable ResourceKey<T> key;
        private @Nullable T value;

        protected Reference(Holder.Reference.Type type, HolderOwner<T> owner, @Nullable ResourceKey<T> key, @Nullable T value) {
            this.owner = owner;
            this.type = type;
            this.key = key;
            this.value = value;
        }

        public static <T> Holder.Reference<T> createStandAlone(HolderOwner<T> owner, ResourceKey<T> key) {
            return new Holder.Reference<>(Holder.Reference.Type.STAND_ALONE, owner, key, null);
        }

        @Deprecated
        public static <T> Holder.Reference<T> createIntrusive(HolderOwner<T> owner, @Nullable T value) {
            return new Holder.Reference<>(Holder.Reference.Type.INTRUSIVE, owner, null, value);
        }

        public ResourceKey<T> key() {
            if (this.key == null) {
                throw new IllegalStateException("Trying to access unbound value '" + this.value + "' from registry " + this.owner);
            } else {
                return this.key;
            }
        }

        @Override
        public T value() {
            if (this.value == null) {
                throw new IllegalStateException("Trying to access unbound value '" + this.key + "' from registry " + this.owner);
            } else {
                return this.value;
            }
        }

        @Override
        public boolean is(Identifier key) {
            return this.key().identifier().equals(key);
        }

        @Override
        public boolean is(ResourceKey<T> key) {
            return this.key() == key;
        }

        private Set<TagKey<T>> boundTags() {
            if (this.tags == null) {
                throw new IllegalStateException("Tags not bound");
            } else {
                return this.tags;
            }
        }

        @Override
        public boolean is(TagKey<T> tag) {
            return this.boundTags().contains(tag);
        }

        @Override
        public boolean is(Holder<T> holder) {
            return holder.is(this.key());
        }

        @Override
        public boolean is(Predicate<ResourceKey<T>> predicate) {
            return predicate.test(this.key());
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> context) {
            return this.owner.canSerializeIn(context);
        }

        @Override
        public Either<ResourceKey<T>, T> unwrap() {
            return Either.left(this.key());
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.of(this.key());
        }

        @Override
        public Holder.Kind kind() {
            return Holder.Kind.REFERENCE;
        }

        @Override
        public boolean isBound() {
            return this.key != null && this.value != null;
        }

        @Override
        public boolean areComponentsBound() {
            return this.components != null;
        }

        void bindKey(ResourceKey<T> key) {
            if (this.key != null && key != this.key) {
                throw new IllegalStateException("Can't change holder key: existing=" + this.key + ", new=" + key);
            }

            this.key = key;
        }

        protected void bindValue(T value) {
            if (this.type == Holder.Reference.Type.INTRUSIVE && this.value != value) {
                throw new IllegalStateException("Can't change holder " + this.key + " value: existing=" + this.value + ", new=" + value);
            }

            this.value = value;
        }


        @org.jspecify.annotations.Nullable
        public <A> A getData(net.neoforged.neoforge.registries.datamaps.DataMapType<T, A> type) {
            if (owner instanceof HolderLookup.RegistryLookup<T> lookup) {
                return lookup.getData(type, key());
            }
            return null;
        }

        void bindTags(Collection<TagKey<T>> tags) {
            this.tags = Set.copyOf(tags);
        }

        public void bindComponents(DataComponentMap components) {
            this.components = components;
        }

        @Override
        public Stream<TagKey<T>> tags() {
            return this.boundTags().stream();
        }

        @Override
        public DataComponentMap components() {
            return Objects.requireNonNull(this.components, "Components not bound yet");
        }

        @Override
        public String toString() {
            return "Reference{" + this.key + "=" + this.value + "}";
        }

        // Neo: Add key getter that doesn't allocate
        @Override
        @org.jspecify.annotations.Nullable
        public ResourceKey<T> getKey() {
            return this.key;
        }

        // Neo: Add DeferredHolder-compatible hashCode() overrides
        @Override
        public int hashCode() {
            return key().hashCode();
        }

        // Neo: Add DeferredHolder-compatible equals() overrides
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            return obj instanceof Holder<?> h && h.kind() == Kind.REFERENCE && h.getKey() == this.key();
        }

        // Neo: Helper method to get the registry lookup from a holder
        @Override
        public HolderLookup.@Nullable RegistryLookup<T> unwrapLookup() {
            return this.owner instanceof HolderLookup.RegistryLookup<T> rl ? rl : null;
        }

        protected enum Type {
            STAND_ALONE,
            INTRUSIVE;
        }
    }
}
