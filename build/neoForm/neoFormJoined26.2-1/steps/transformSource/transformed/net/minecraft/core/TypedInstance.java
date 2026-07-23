package net.minecraft.core;

import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import net.neoforged.neoforge.common.extensions.TypedInstanceExtension;

public interface TypedInstance<T> extends TypedInstanceExtension<T> {
    Holder<T> typeHolder();

    default Stream<TagKey<T>> tags() {
        return this.typeHolder().tags();
    }

    default boolean is(TagKey<T> tag) {
        return this.typeHolder().is(tag);
    }

    default boolean is(HolderSet<T> set) {
        return set.contains(this.typeHolder());
    }

    default boolean is(T rawType) {
        return this.typeHolder().value() == rawType;
    }

    default boolean is(Holder<T> type) {
        // Neo: Fix comparing for custom holders such as DeferredHolders
        return is(type.value());
    }

    default boolean is(ResourceKey<T> type) {
        return this.typeHolder().is(type);
    }
}
