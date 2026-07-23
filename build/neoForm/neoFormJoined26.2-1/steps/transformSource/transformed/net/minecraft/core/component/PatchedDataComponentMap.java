package net.minecraft.core.component;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class PatchedDataComponentMap implements DataComponentMap {
    private final DataComponentMap prototype;
    private Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch;
    private boolean copyOnWrite;

    public PatchedDataComponentMap(DataComponentMap prototype) {
        this(prototype, Reference2ObjectMaps.emptyMap(), true);
    }

    private PatchedDataComponentMap(DataComponentMap prototype, Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch, boolean copyOnWrite) {
        this.prototype = prototype;
        this.patch = patch;
        this.copyOnWrite = copyOnWrite;
    }

    public static PatchedDataComponentMap fromPatch(DataComponentMap prototype, DataComponentPatch patch) {
        if (isPatchSanitized(prototype, patch.map)) {
            return new PatchedDataComponentMap(prototype, patch.map, true);
        }

        PatchedDataComponentMap map = new PatchedDataComponentMap(prototype);
        map.applyPatch(patch);
        return map;
    }

    private static boolean isPatchSanitized(DataComponentMap prototype, Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch) {
        for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(patch)) {
            Object defaultValue = prototype.get(entry.getKey());
            Optional<?> value = entry.getValue();
            if (value.isPresent() && value.get().equals(defaultValue)) {
                return false;
            }

            if (value.isEmpty() && defaultValue == null) {
                return false;
            }
        }

        return true;
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return DataComponentPatch.getFromPatchAndPrototype(this.patch, this.prototype, type);
    }

    public boolean hasNonDefault(DataComponentType<?> type) {
        return this.patch.containsKey(type);
    }

    public <T> @Nullable T set(DataComponentType<T> type, @Nullable T value) {
        net.neoforged.neoforge.common.CommonHooks.validateComponent(value);
        this.ensureMapOwnership();
        T defaultValue = this.prototype.get(type);
        Optional<T> lastValue;
        if (Objects.equals(value, defaultValue)) {
            lastValue = (Optional<T>)this.patch.remove(type);
        } else {
            lastValue = (Optional<T>)this.patch.put(type, Optional.ofNullable(value));
        }

        return lastValue != null ? lastValue.orElse(defaultValue) : defaultValue;
    }

    public <T> @Nullable T set(TypedDataComponent<T> value) {
        return this.set(value.type(), value.value());
    }

    public <T> @Nullable T remove(DataComponentType<? extends T> type) {
        this.ensureMapOwnership();
        T defaultValue = this.prototype.get(type);
        Optional<? extends T> lastValue;
        if (defaultValue != null) {
            lastValue = (Optional<? extends T>)this.patch.put(type, Optional.empty());
        } else {
            lastValue = (Optional<? extends T>)this.patch.remove(type);
        }

        return (T)(lastValue != null ? lastValue.orElse(null) : defaultValue);
    }

    public void applyPatch(DataComponentPatch patch) {
        this.ensureMapOwnership();

        for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(patch.map)) {
            this.applyPatch(entry.getKey(), entry.getValue());
        }
    }

    private void applyPatch(DataComponentType<?> type, Optional<?> value) {
        Object defaultValue = this.prototype.get(type);
        if (value.isPresent()) {
            if (value.get().equals(defaultValue)) {
                this.patch.remove(type);
            } else {
                this.patch.put(type, value);
            }
        } else if (defaultValue != null) {
            this.patch.put(type, Optional.empty());
        } else {
            this.patch.remove(type);
        }
    }

    public void restorePatch(DataComponentPatch patch) {
        this.ensureMapOwnership();
        this.patch.clear();
        this.patch.putAll(patch.map);
    }

    public void clearPatch() {
        this.ensureMapOwnership();
        this.patch.clear();
    }

    public void setAll(DataComponentMap components) {
        for (TypedDataComponent<?> entry : components) {
            entry.applyTo(this);
        }
    }

    private void ensureMapOwnership() {
        if (this.copyOnWrite) {
            this.patch = new Reference2ObjectArrayMap<>(this.patch);
            this.copyOnWrite = false;
        }
    }

    @Override
    public Set<DataComponentType<?>> keySet() {
        if (this.patch.isEmpty()) {
            return this.prototype.keySet();
        }

        Set<DataComponentType<?>> components = new ReferenceArraySet<>(this.prototype.keySet());

        for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(this.patch)) {
            Optional<?> value = entry.getValue();
            if (value.isPresent()) {
                components.add(entry.getKey());
            } else {
                components.remove(entry.getKey());
            }
        }

        return components;
    }

    @Override
    public Iterator<TypedDataComponent<?>> iterator() {
        if (this.patch.isEmpty()) {
            return this.prototype.iterator();
        }

        List<TypedDataComponent<?>> components = new ArrayList<>(this.patch.size() + this.prototype.size());

        for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(this.patch)) {
            if (entry.getValue().isPresent()) {
                components.add(TypedDataComponent.createUnchecked(entry.getKey(), entry.getValue().get()));
            }
        }

        for (TypedDataComponent<?> component : this.prototype) {
            if (!this.patch.containsKey(component.type())) {
                components.add(component);
            }
        }

        return components.iterator();
    }

    @Override
    public int size() {
        int size = this.prototype.size();

        for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(this.patch)) {
            boolean inPatch = entry.getValue().isPresent();
            boolean inPrototype = this.prototype.has(entry.getKey());
            if (inPatch != inPrototype) {
                size += inPatch ? 1 : -1;
            }
        }

        return size;
    }

    public boolean isPatchEmpty() {
        return this.patch.isEmpty();
    }

    public DataComponentPatch asPatch() {
        if (this.patch.isEmpty()) {
            return DataComponentPatch.EMPTY;
        }

        this.copyOnWrite = true;
        return new DataComponentPatch(this.patch);
    }

    public PatchedDataComponentMap copy() {
        this.copyOnWrite = true;
        return new PatchedDataComponentMap(this.prototype, this.patch, true);
    }

    public DataComponentMap toImmutableMap() {
        return this.patch.isEmpty() ? this.prototype : this.copy();
    }

    /**
     * {@return true if the contained patch equals the given patch.}
     */
    public boolean patchEquals(DataComponentPatch patch) {
        return this.patch.equals(patch.map);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj
            ? true
            : obj instanceof PatchedDataComponentMap otherMap && this.prototype.equals(otherMap.prototype) && this.patch.equals(otherMap.patch);
    }

    @Override
    public int hashCode() {
        return this.prototype.hashCode() + hashPatch(this.patch) * 31;
    }

    // Neo: Change implementation of hashCode to reduce collisions.
    // For a map, hashCode is specified as the sum of the hash codes of its entries.
    // We do that, but change the entry hash code to 8191^<key hash> * <value hash>,
    // where <key hash> is the lower bits of the identity hash code of the key.
    private static int hashPatch(Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch) {
        int h = 0, n = patch.size();
        var iterator = it.unimi.dsi.fastutil.objects.Reference2ObjectMaps.fastIterator(patch);
        while (n-- != 0) {
            var entry = iterator.next();
            int exponent = System.identityHashCode(entry.getKey()) & 0xff;
            // Use 8191 instead of the usual 31, as 31 can produce many collisions with typical integer component ranges (0-255) if the exponent difference is only 1
            int entryHash = com.google.common.math.IntMath.pow(8191, exponent) * entry.getValue().hashCode();
            h += entryHash;
        }
        return h;
    }

    @Override
    public String toString() {
        return "{" + this.stream().map(TypedDataComponent::toString).collect(Collectors.joining(", ")) + "}";
    }
}
