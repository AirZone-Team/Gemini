package net.minecraft.core.component;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.common.extensions.IDataComponentMapBuilderExtensions;

public interface DataComponentMap extends Iterable<TypedDataComponent<?>>, DataComponentGetter {
    DataComponentMap EMPTY = new DataComponentMap() {
        @Override
        public <T> @Nullable T get(DataComponentType<? extends T> type) {
            return null;
        }

        @Override
        public Set<DataComponentType<?>> keySet() {
            return Set.of();
        }

        @Override
        public Iterator<TypedDataComponent<?>> iterator() {
            return Collections.emptyIterator();
        }
    };
    Codec<DataComponentMap> CODEC = makeCodecFromMap(DataComponentType.VALUE_MAP_CODEC);

    static Codec<DataComponentMap> makeCodec(Codec<DataComponentType<?>> componentTypeCodec) {
        return makeCodecFromMap(Codec.dispatchedMap(componentTypeCodec, DataComponentType::codecOrThrow));
    }

    static Codec<DataComponentMap> makeCodecFromMap(Codec<Map<DataComponentType<?>, Object>> mapCodec) {
        return mapCodec.flatComapMap(DataComponentMap.Builder::buildFromMapTrusted, components -> {
            int size = components.size();
            if (size == 0) {
                return DataResult.success(Reference2ObjectMaps.emptyMap());
            }

            Reference2ObjectMap<DataComponentType<?>, Object> map = new Reference2ObjectArrayMap<>(size);

            for (TypedDataComponent<?> entry : components) {
                if (!entry.type().isTransient()) {
                    map.put(entry.type(), entry.value());
                }
            }

            return DataResult.success(map);
        });
    }

    static DataComponentMap composite(DataComponentMap prototype, DataComponentMap overrides) {
        return new DataComponentMap() {
            @Override
            public <T> @Nullable T get(DataComponentType<? extends T> type) {
                T value = overrides.get(type);
                return value != null ? value : prototype.get(type);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return Sets.union(prototype.keySet(), overrides.keySet());
            }
        };
    }

    static DataComponentMap.Builder builder() {
        return new DataComponentMap.Builder();
    }

    Set<DataComponentType<?>> keySet();

    default boolean has(DataComponentType<?> type) {
        return this.get(type) != null;
    }

    @Override
    default Iterator<TypedDataComponent<?>> iterator() {
        return Iterators.transform(this.keySet().iterator(), type -> Objects.requireNonNull(this.getTyped((DataComponentType<?>)type)));
    }

    default Stream<TypedDataComponent<?>> stream() {
        return StreamSupport.stream(Spliterators.spliterator(this.iterator(), this.size(), 1345), false);
    }

    default int size() {
        return this.keySet().size();
    }

    default boolean isEmpty() {
        return this.size() == 0;
    }

    default DataComponentMap filter(Predicate<DataComponentType<?>> predicate) {
        return new DataComponentMap() {
            @Override
            public <T> @Nullable T get(DataComponentType<? extends T> type) {
                return predicate.test(type) ? DataComponentMap.this.get(type) : null;
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return Sets.filter(DataComponentMap.this.keySet(), predicate::test);
            }
        };
    }

    class Builder implements IDataComponentMapBuilderExtensions {
        private final Reference2ObjectMap<DataComponentType<?>, Object> map = new Reference2ObjectArrayMap<>();
        private Consumer<DataComponentMap> validator = components -> {};

        private Builder() {
        }

        @SuppressWarnings("unchecked")
        @Override
        public @Nullable <T> T get(DataComponentType<? extends T> type) {
            return (T) map.get(type);
        }

        public <T> DataComponentMap.Builder set(DataComponentType<T> type, @Nullable T value) {
            this.setUnchecked(type, value);
            return this;
        }

        <T> void setUnchecked(DataComponentType<T> type, @Nullable Object value) {
            if (value != null) {
                this.map.put(type, value);
            } else {
                this.map.remove(type);
            }
        }

        public DataComponentMap.Builder addAll(DataComponentMap map) {
            for (TypedDataComponent<?> entry : map) {
                this.map.put(entry.type(), entry.value());
            }

            return this;
        }

        public DataComponentMap.Builder addValidator(Consumer<DataComponentMap> newValidator) {
            this.validator = this.validator.andThen(newValidator);
            return this;
        }

        public DataComponentMap build() {
            DataComponentMap result = buildFromMapTrusted(this.map);
            this.validator.accept(result);
            return result;
        }

        private static DataComponentMap buildFromMapTrusted(Map<DataComponentType<?>, Object> map) {
            if (map.isEmpty()) {
                return DataComponentMap.EMPTY;
            } else {
                return map.size() < 8
                    ? new DataComponentMap.Builder.SimpleMap(new Reference2ObjectArrayMap<>(map))
                    : new DataComponentMap.Builder.SimpleMap(new Reference2ObjectOpenHashMap<>(map));
            }
        }

        private record SimpleMap(Reference2ObjectMap<DataComponentType<?>, Object> map) implements DataComponentMap {
            @Override
            public <T> @Nullable T get(DataComponentType<? extends T> type) {
                return (T)this.map.get(type);
            }

            @Override
            public boolean has(DataComponentType<?> type) {
                return this.map.containsKey(type);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return this.map.keySet();
            }

            @Override
            public Iterator<TypedDataComponent<?>> iterator() {
                return Iterators.transform(Reference2ObjectMaps.fastIterator(this.map), TypedDataComponent::fromEntryUnchecked);
            }

            @Override
            public int size() {
                return this.map.size();
            }

            @Override
            public String toString() {
                return this.map.toString();
            }
        }
    }
}
