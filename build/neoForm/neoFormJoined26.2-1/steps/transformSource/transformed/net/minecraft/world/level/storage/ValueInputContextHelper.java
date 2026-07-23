package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

public class ValueInputContextHelper {
    private final HolderLookup.Provider lookup;
    private final DynamicOps<Tag> ops;
    private final ValueInput.ValueInputList emptyChildList = new ValueInput.ValueInputList() {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Stream<ValueInput> stream() {
            return Stream.empty();
        }

        @Override
        public Iterator<ValueInput> iterator() {
            return Collections.emptyIterator();
        }
    };
    private final ValueInput.TypedInputList<Object> emptyTypedList = new ValueInput.TypedInputList<Object>() {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Stream<Object> stream() {
            return Stream.empty();
        }

        @Override
        public Iterator<Object> iterator() {
            return Collections.emptyIterator();
        }
    };
    private final ValueInput empty = new ValueInput() {
        @Override
        public <T> Optional<T> read(String name, Codec<T> codec) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> read(MapCodec<T> codec) {
            return Optional.empty();
        }

        @Override
        public Optional<ValueInput> child(String name) {
            return Optional.empty();
        }

        @Override
        public ValueInput childOrEmpty(String name) {
            return this;
        }

        @Override
        public Optional<ValueInput.ValueInputList> childrenList(String name) {
            return Optional.empty();
        }

        @Override
        public ValueInput.ValueInputList childrenListOrEmpty(String name) {
            return ValueInputContextHelper.this.emptyChildList;
        }

        @Override
        public <T> Optional<ValueInput.TypedInputList<T>> list(String name, Codec<T> codec) {
            return Optional.empty();
        }

        @Override
        public <T> ValueInput.TypedInputList<T> listOrEmpty(String name, Codec<T> codec) {
            return ValueInputContextHelper.this.emptyTypedList();
        }

        @Override
        public boolean getBooleanOr(String name, boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public byte getByteOr(String name, byte defaultValue) {
            return defaultValue;
        }

        @Override
        public int getShortOr(String name, short defaultValue) {
            return defaultValue;
        }

        @Override
        public Optional<Integer> getInt(String name) {
            return Optional.empty();
        }

        @Override
        public int getIntOr(String name, int defaultValue) {
            return defaultValue;
        }

        @Override
        public long getLongOr(String name, long defaultValue) {
            return defaultValue;
        }

        @Override
        public Optional<Long> getLong(String name) {
            return Optional.empty();
        }

        @Override
        public float getFloatOr(String name, float defaultValue) {
            return defaultValue;
        }

        @Override
        public double getDoubleOr(String name, double defaultValue) {
            return defaultValue;
        }

        @Override
        public Optional<String> getString(String name) {
            return Optional.empty();
        }

        @Override
        public String getStringOr(String name, String defaultValue) {
            return defaultValue;
        }

        @Override
        public HolderLookup.Provider lookup() {
            return ValueInputContextHelper.this.lookup;
        }

        @Override
        public Optional<int[]> getIntArray(String name) {
            return Optional.empty();
        }

        @Override
        public java.util.Set<String> keySet() {
            return java.util.Set.of();
        }
    };

    public ValueInputContextHelper(HolderLookup.Provider lookup, DynamicOps<Tag> ops) {
        this.lookup = lookup;
        this.ops = lookup.createSerializationContext(ops);
    }

    public DynamicOps<Tag> ops() {
        return this.ops;
    }

    public HolderLookup.Provider lookup() {
        return this.lookup;
    }

    public ValueInput empty() {
        return this.empty;
    }

    public ValueInput.ValueInputList emptyList() {
        return this.emptyChildList;
    }

    public <T> ValueInput.TypedInputList<T> emptyTypedList() {
        return (ValueInput.TypedInputList<T>)this.emptyTypedList;
    }
}
