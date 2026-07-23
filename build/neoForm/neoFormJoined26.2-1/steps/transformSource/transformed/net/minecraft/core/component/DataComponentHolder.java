package net.minecraft.core.component;

import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.common.extensions.IDataComponentHolderExtension;

public interface DataComponentHolder extends DataComponentGetter, IDataComponentHolderExtension {
    DataComponentMap getComponents();

    @Override
    default <T> @Nullable T get(DataComponentType<? extends T> type) {
        return this.getComponents().get(type);
    }

    default <T> Stream<T> getAllOfType(Class<? extends T> valueClass) {
        return this.getComponents()
            .stream()
            .map(TypedDataComponent::value)
            .filter(value -> valueClass.isAssignableFrom(value.getClass()))
            .map(value -> (T)value);
    }

    @Override
    default <T> T getOrDefault(DataComponentType<? extends T> type, T defaultValue) {
        return this.getComponents().getOrDefault(type, defaultValue);
    }

    default boolean has(DataComponentType<?> type) {
        return this.getComponents().has(type);
    }
}
