package net.minecraft.nbt;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractStringBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class NbtOps implements DynamicOps<Tag> {
    public static final NbtOps INSTANCE = new NbtOps();

    private NbtOps() {
    }

    public Tag empty() {
        return EndTag.INSTANCE;
    }

    public Tag emptyList() {
        return new ListTag();
    }

    public Tag emptyMap() {
        return new CompoundTag();
    }

    public <U> U convertTo(DynamicOps<U> outOps, Tag input) {
        return (U)(switch (input) {
            case EndTag ignored -> outOps.empty();
            case ByteTag(byte value) -> outOps.createByte(value);
            case ShortTag(short value) -> outOps.createShort(value);
            case IntTag(int value) -> outOps.createInt(value);
            case LongTag(long value) -> outOps.createLong(value);
            case FloatTag(float value) -> outOps.createFloat(value);
            case DoubleTag(double value) -> outOps.createDouble(value);
            case ByteArrayTag byteArrayTag -> outOps.createByteList(ByteBuffer.wrap(byteArrayTag.getAsByteArray()));
            case StringTag(String value) -> outOps.createString(value);
            case ListTag listTag -> this.convertList(outOps, listTag);
            case CompoundTag compoundTag -> this.convertMap(outOps, compoundTag);
            case IntArrayTag intArrayTag -> outOps.createIntList(Arrays.stream(intArrayTag.getAsIntArray()));
            case LongArrayTag longArrayTag -> outOps.createLongList(Arrays.stream(longArrayTag.getAsLongArray()));
            default -> throw new MatchException(null, null);
        });
    }

    public DataResult<Number> getNumberValue(Tag input) {
        return input.asNumber().map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Not a number"));
    }

    public Tag createNumeric(Number i) {
        return DoubleTag.valueOf(i.doubleValue());
    }

    public Tag createByte(byte value) {
        return ByteTag.valueOf(value);
    }

    public Tag createShort(short value) {
        return ShortTag.valueOf(value);
    }

    public Tag createInt(int value) {
        return IntTag.valueOf(value);
    }

    public Tag createLong(long value) {
        return LongTag.valueOf(value);
    }

    public Tag createFloat(float value) {
        return FloatTag.valueOf(value);
    }

    public Tag createDouble(double value) {
        return DoubleTag.valueOf(value);
    }

    public DataResult<Boolean> getBooleanValue(Tag input) {
        return this.getNumberValue(input).map(value -> value.doubleValue() != 0.0);
    }

    public Tag createBoolean(boolean value) {
        return ByteTag.valueOf(value);
    }

    public DataResult<String> getStringValue(Tag input) {
        return input instanceof StringTag(String value) ? DataResult.success(value) : DataResult.error(() -> "Not a string");
    }

    public Tag createString(String value) {
        return StringTag.valueOf(value);
    }

    public DataResult<Tag> mergeToList(Tag list, Tag value) {
        return createCollector(list)
            .map(collector -> DataResult.success(collector.accept(value).result()))
            .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
    }

    public DataResult<Tag> mergeToList(Tag list, List<Tag> values) {
        return createCollector(list)
            .map(collector -> DataResult.success(collector.acceptAll(values).result()))
            .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
    }

    public DataResult<Tag> mergeToMap(Tag map, Tag key, Tag value) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        } else if (key instanceof StringTag(String stringKey)) {
            CompoundTag output = map instanceof CompoundTag tag ? tag.shallowCopy() : new CompoundTag();
            output.put(stringKey, value);
            return DataResult.success(output);
        } else {
            return DataResult.error(() -> "key is not a string: " + key, map);
        }
    }

    public DataResult<Tag> mergeToMap(Tag map, MapLike<Tag> values) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        }

        Iterator<Pair<Tag, Tag>> valuesIterator = values.entries().iterator();
        if (!valuesIterator.hasNext()) {
            return map == this.empty() ? DataResult.success(this.emptyMap()) : DataResult.success(map);
        }

        CompoundTag output = map instanceof CompoundTag tag ? tag.shallowCopy() : new CompoundTag();
        List<Tag> missed = new ArrayList<>();
        valuesIterator.forEachRemaining(entry -> {
            Tag key = entry.getFirst();
            if (key instanceof StringTag(String stringKey)) {
                output.put(stringKey, entry.getSecond());
            } else {
                missed.add(key);
            }
        });
        return !missed.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + missed, output) : DataResult.success(output);
    }

    public DataResult<Tag> mergeToMap(Tag map, Map<Tag, Tag> values) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        }

        if (values.isEmpty()) {
            return map == this.empty() ? DataResult.success(this.emptyMap()) : DataResult.success(map);
        }

        CompoundTag output = map instanceof CompoundTag tag ? tag.shallowCopy() : new CompoundTag();
        List<Tag> missed = new ArrayList<>();

        for (Entry<Tag, Tag> entry : values.entrySet()) {
            Tag key = entry.getKey();
            if (key instanceof StringTag(String stringKey)) {
                output.put(stringKey, entry.getValue());
            } else {
                missed.add(key);
            }
        }

        return !missed.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + missed, output) : DataResult.success(output);
    }

    public DataResult<Stream<Pair<Tag, Tag>>> getMapValues(Tag input) {
        return input instanceof CompoundTag tag
            ? DataResult.success(tag.entrySet().stream().map(entry -> Pair.of(this.createString(entry.getKey()), entry.getValue())))
            : DataResult.error(() -> "Not a map: " + input);
    }

    public DataResult<Consumer<BiConsumer<Tag, Tag>>> getMapEntries(Tag input) {
        return input instanceof CompoundTag tag ? DataResult.success(c -> {
            for (Entry<String, Tag> entry : tag.entrySet()) {
                c.accept(this.createString(entry.getKey()), entry.getValue());
            }
        }) : DataResult.error(() -> "Not a map: " + input);
    }

    public DataResult<MapLike<Tag>> getMap(Tag input) {
        return input instanceof CompoundTag tag ? DataResult.success(new MapLike<Tag>() {
            public @Nullable Tag get(Tag key) {
                if (key instanceof StringTag(String stringKey)) {
                    return tag.get(stringKey);
                } else {
                    throw new UnsupportedOperationException("Cannot get map entry with non-string key: " + key);
                }
            }

            public @Nullable Tag get(String key) {
                return tag.get(key);
            }

            @Override
            public Stream<Pair<Tag, Tag>> entries() {
                return tag.entrySet().stream().map(entry -> Pair.of(NbtOps.this.createString(entry.getKey()), entry.getValue()));
            }

            @Override
            public String toString() {
                return "MapLike[" + tag + "]";
            }
        }) : DataResult.error(() -> "Not a map: " + input);
    }

    public Tag createMap(Stream<Pair<Tag, Tag>> map) {
        CompoundTag tag = new CompoundTag();
        map.forEach(entry -> {
            Tag key = entry.getFirst();
            Tag value = entry.getSecond();
            if (key instanceof StringTag(String stringKey)) {
                tag.put(stringKey, value);
            } else {
                throw new UnsupportedOperationException("Cannot create map with non-string key: " + key);
            }
        });
        return tag;
    }

    public DataResult<Stream<Tag>> getStream(Tag input) {
        return input instanceof CollectionTag collection ? DataResult.success(collection.stream()) : DataResult.error(() -> "Not a list");
    }

    public DataResult<Consumer<Consumer<Tag>>> getList(Tag input) {
        return input instanceof CollectionTag collection ? DataResult.success(collection::forEach) : DataResult.error(() -> "Not a list: " + input);
    }

    public DataResult<ByteBuffer> getByteBuffer(Tag input) {
        return input instanceof ByteArrayTag array ? DataResult.success(ByteBuffer.wrap(array.getAsByteArray())) : DynamicOps.super.getByteBuffer(input);
    }

    public Tag createByteList(ByteBuffer input) {
        ByteBuffer wholeBuffer = input.duplicate().clear();
        byte[] bytes = new byte[input.capacity()];
        wholeBuffer.get(0, bytes, 0, bytes.length);
        return new ByteArrayTag(bytes);
    }

    public DataResult<IntStream> getIntStream(Tag input) {
        return input instanceof IntArrayTag array ? DataResult.success(Arrays.stream(array.getAsIntArray())) : DynamicOps.super.getIntStream(input);
    }

    public Tag createIntList(IntStream input) {
        return new IntArrayTag(input.toArray());
    }

    public DataResult<LongStream> getLongStream(Tag input) {
        return input instanceof LongArrayTag array ? DataResult.success(Arrays.stream(array.getAsLongArray())) : DynamicOps.super.getLongStream(input);
    }

    public Tag createLongList(LongStream input) {
        return new LongArrayTag(input.toArray());
    }

    public Tag createList(Stream<Tag> input) {
        return new ListTag(input.collect(Util.toMutableList()));
    }

    public Tag remove(Tag input, String key) {
        if (input instanceof CompoundTag tag) {
            CompoundTag result = tag.shallowCopy();
            result.remove(key);
            return result;
        } else {
            return input;
        }
    }

    @Override
    public String toString() {
        return "NBT";
    }

    @Override
    public RecordBuilder<Tag> mapBuilder() {
        return new NbtOps.NbtRecordBuilder();
    }

    private static Optional<NbtOps.ListCollector> createCollector(Tag tag) {
        if (tag instanceof EndTag) {
            return Optional.of(new NbtOps.GenericListCollector());
        }

        if (tag instanceof CollectionTag collection) {
            if (collection.isEmpty()) {
                return Optional.of(new NbtOps.GenericListCollector());
            }

            return switch (collection) {
                case ListTag list -> Optional.of(new NbtOps.GenericListCollector(list));
                case ByteArrayTag array -> Optional.of(new NbtOps.ByteListCollector(array.getAsByteArray()));
                case IntArrayTag array -> Optional.of(new NbtOps.IntListCollector(array.getAsIntArray()));
                case LongArrayTag array -> Optional.of(new NbtOps.LongListCollector(array.getAsLongArray()));
                default -> throw new MatchException(null, null);
            };
        } else {
            return Optional.empty();
        }
    }

    private static class ByteListCollector implements NbtOps.ListCollector {
        private final ByteArrayList values = new ByteArrayList();

        public ByteListCollector(byte[] initialValues) {
            this.values.addElements(0, initialValues);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof ByteTag byteTag) {
                this.values.add(byteTag.byteValue());
                return this;
            } else {
                return new NbtOps.GenericListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new ByteArrayTag(this.values.toByteArray());
        }
    }

    private static class GenericListCollector implements NbtOps.ListCollector {
        private final ListTag result = new ListTag();

        private GenericListCollector() {
        }

        private GenericListCollector(ListTag initial) {
            this.result.addAll(initial);
        }

        public GenericListCollector(IntArrayList initials) {
            initials.forEach(v -> this.result.add(IntTag.valueOf(v)));
        }

        public GenericListCollector(ByteArrayList initials) {
            initials.forEach(v -> this.result.add(ByteTag.valueOf(v)));
        }

        public GenericListCollector(LongArrayList initials) {
            initials.forEach(v -> this.result.add(LongTag.valueOf(v)));
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            this.result.add(tag);
            return this;
        }

        @Override
        public Tag result() {
            return this.result;
        }
    }

    private static class IntListCollector implements NbtOps.ListCollector {
        private final IntArrayList values = new IntArrayList();

        public IntListCollector(int[] initialValues) {
            this.values.addElements(0, initialValues);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof IntTag intTag) {
                this.values.add(intTag.intValue());
                return this;
            } else {
                return new NbtOps.GenericListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new IntArrayTag(this.values.toIntArray());
        }
    }

    private interface ListCollector {
        NbtOps.ListCollector accept(Tag t);

        default NbtOps.ListCollector acceptAll(Iterable<Tag> tags) {
            NbtOps.ListCollector collector = this;

            for (Tag tag : tags) {
                collector = collector.accept(tag);
            }

            return collector;
        }

        Tag result();
    }

    private static class LongListCollector implements NbtOps.ListCollector {
        private final LongArrayList values = new LongArrayList();

        public LongListCollector(long[] initialValues) {
            this.values.addElements(0, initialValues);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof LongTag longTag) {
                this.values.add(longTag.longValue());
                return this;
            } else {
                return new NbtOps.GenericListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new LongArrayTag(this.values.toLongArray());
        }
    }

    private class NbtRecordBuilder extends AbstractStringBuilder<Tag, CompoundTag> {
        protected NbtRecordBuilder() {
            super(NbtOps.this);
        }

        protected CompoundTag initBuilder() {
            return new CompoundTag();
        }

        protected CompoundTag append(String key, Tag value, CompoundTag builder) {
            builder.put(key, value);
            return builder;
        }

        protected DataResult<Tag> build(CompoundTag builder, Tag prefix) {
            if (prefix == null || prefix == EndTag.INSTANCE) {
                return DataResult.success(builder);
            } else if (!(prefix instanceof CompoundTag compound)) {
                return DataResult.error(() -> "mergeToMap called with not a map: " + prefix, prefix);
            } else {
                CompoundTag result = compound.shallowCopy();

                for (Entry<String, Tag> entry : builder.entrySet()) {
                    result.put(entry.getKey(), entry.getValue());
                }

                return DataResult.success(result);
            }
        }
    }
}
