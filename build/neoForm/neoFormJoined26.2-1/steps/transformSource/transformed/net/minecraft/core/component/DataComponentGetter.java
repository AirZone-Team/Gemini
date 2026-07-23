package net.minecraft.core.component;

import org.jspecify.annotations.Nullable;

public interface DataComponentGetter {
    <T> @Nullable T get(DataComponentType<? extends T> type);

    default <T> T getOrDefault(DataComponentType<? extends T> type, T defaultValue) {
        T value = this.get(type);
        return value != null ? value : defaultValue;
    }

    default <T> @Nullable TypedDataComponent<T> getTyped(DataComponentType<T> type) {
        T value = this.get(type);
        return value != null ? new TypedDataComponent<>(type, value) : null;
    }

    // Neo: Utility for modded component types, to remove the need to invoke '.value()'
    @Nullable
    default <T> T get(java.util.function.Supplier<? extends DataComponentType<? extends T>> componentType) {
        return get(componentType.get());
    }

    default <T> T getOrDefault(java.util.function.Supplier<? extends DataComponentType<? extends T>> componentType, T value) {
        return getOrDefault(componentType.get(), value);
    }

    default <T> boolean has(java.util.function.Supplier<? extends DataComponentType<? extends T>> componentType) {
        return get(componentType) != null;
    }

    default boolean has(DataComponentType<?> componentType) {
        return get(componentType) != null;
    }
}
