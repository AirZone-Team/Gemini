package net.minecraft.core;

import com.google.common.collect.Lists;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class NonNullList<E> extends AbstractList<E> {

    /**
     * Neo: utility method to construct a Codec for a NonNullList
     * @param entryCodec the codec to use for the elements
     * @param <E> the element type
     * @return a codec that encodes as a list, and decodes into NonNullList
     */
    public static <E> com.mojang.serialization.Codec<NonNullList<E>> codecOf(com.mojang.serialization.Codec<E> entryCodec) {
        return entryCodec.listOf().xmap(NonNullList::copyOf, java.util.function.Function.identity());
    }

    /**
     * Neo: utility method to construct an immutable NonNullList from a given collection
     * @param entries the collection to make a copy of
     * @param <E> the type of the elements in the list
     * @return a new immutable NonNullList
     * @throws NullPointerException if entries is null, or if it contains any nulls
     */
    public static <E> NonNullList<E> copyOf(java.util.Collection<? extends E> entries) {
        return new NonNullList<>(List.copyOf(entries), null);
    }

    private final List<E> list;
    private final @Nullable E defaultValue;

    public static <E> NonNullList<E> create() {
        return new NonNullList<>(Lists.newArrayList(), null);
    }

    public static <E> NonNullList<E> createWithCapacity(int capacity) {
        return new NonNullList<>(Lists.newArrayListWithCapacity(capacity), null);
    }

    public static <E> NonNullList<E> withSize(int size, E defaultValue) {
        Objects.requireNonNull(defaultValue);
        Object[] objects = new Object[size];
        Arrays.fill(objects, defaultValue);
        return new NonNullList<>(Arrays.asList((E[])objects), defaultValue);
    }

    @SafeVarargs
    public static <E> NonNullList<E> of(E defaultValue, E... values) {
        return new NonNullList<>(Arrays.asList(values), defaultValue);
    }

    protected NonNullList(List<E> list, @Nullable E defaultValue) {
        this.list = list;
        this.defaultValue = defaultValue;
    }

    @Override
    public E get(int index) {
        return this.list.get(index);
    }

    @Override
    public E set(int index, E element) {
        Objects.requireNonNull(element);
        return this.list.set(index, element);
    }

    @Override
    public void add(int index, E element) {
        Objects.requireNonNull(element);
        this.list.add(index, element);
    }

    @Override
    public E remove(int index) {
        return this.list.remove(index);
    }

    @Override
    public int size() {
        return this.list.size();
    }

    @Override
    public void clear() {
        if (this.defaultValue == null) {
            super.clear();
        } else {
            for (int i = 0; i < this.size(); i++) {
                this.set(i, this.defaultValue);
            }
        }
    }
}
