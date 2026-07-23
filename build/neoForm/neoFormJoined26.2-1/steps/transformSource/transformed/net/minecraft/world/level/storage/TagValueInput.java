package net.minecraft.world.level.storage;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Streams;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.DataResult.Success;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.util.ProblemReporter;
import org.jspecify.annotations.Nullable;

public class TagValueInput implements ValueInput {
    private final ProblemReporter problemReporter;
    private final ValueInputContextHelper context;
    private final CompoundTag input;

    private TagValueInput(ProblemReporter problemReporter, ValueInputContextHelper context, CompoundTag input) {
        this.problemReporter = problemReporter;
        this.context = context;
        this.input = input;
    }

    public static ValueInput create(ProblemReporter problemReporter, HolderLookup.Provider holders, CompoundTag tag) {
        return new TagValueInput(problemReporter, new ValueInputContextHelper(holders, NbtOps.INSTANCE), tag);
    }

    public static ValueInput.ValueInputList create(ProblemReporter problemReporter, HolderLookup.Provider holders, List<CompoundTag> tags) {
        return new TagValueInput.CompoundListWrapper(problemReporter, new ValueInputContextHelper(holders, NbtOps.INSTANCE), tags);
    }

    @Override
    public <T> Optional<T> read(String name, Codec<T> codec) {
        Tag tag = this.input.get(name);
        if (tag == null) {
            return Optional.empty();
        }

        return switch (codec.parse(this.context.ops(), tag)) {
            case Success<T> success -> Optional.of(success.value());
            case Error<T> error -> {
                this.problemReporter.report(new TagValueInput.DecodeFromFieldFailedProblem(name, tag, error));
                yield error.partialValue();
            }
            default -> throw new MatchException(null, null);
        };
    }

    @Override
    public <T> Optional<T> read(MapCodec<T> codec) {
        DynamicOps<Tag> ops = this.context.ops();

        return switch (ops.getMap(this.input).flatMap(map -> codec.decode(ops, (MapLike<Tag>)map))) {
            case Success<T> success -> Optional.of(success.value());
            case Error<T> error -> {
                this.problemReporter.report(new TagValueInput.DecodeFromMapFailedProblem(error));
                yield error.partialValue();
            }
            default -> throw new MatchException(null, null);
        };
    }

    private <T extends Tag> @Nullable T getOptionalTypedTag(String name, TagType<T> expectedType) {
        Tag tag = this.input.get(name);
        if (tag == null) {
            return null;
        } else {
            TagType<?> actualType = tag.getType();
            if (actualType != expectedType) {
                this.problemReporter.report(new TagValueInput.UnexpectedTypeProblem(name, expectedType, actualType));
                return null;
            } else {
                return (T)tag;
            }
        }
    }

    private @Nullable NumericTag getNumericTag(String name) {
        Tag tag = this.input.get(name);
        if (tag == null) {
            return null;
        } else if (tag instanceof NumericTag numericTag) {
            return numericTag;
        } else {
            this.problemReporter.report(new TagValueInput.UnexpectedNonNumberProblem(name, tag.getType()));
            return null;
        }
    }

    @Override
    public Optional<ValueInput> child(String name) {
        CompoundTag compound = this.getOptionalTypedTag(name, CompoundTag.TYPE);
        return compound != null ? Optional.of(this.wrapChild(name, compound)) : Optional.empty();
    }

    @Override
    public ValueInput childOrEmpty(String name) {
        CompoundTag compound = this.getOptionalTypedTag(name, CompoundTag.TYPE);
        return compound != null ? this.wrapChild(name, compound) : this.context.empty();
    }

    @Override
    public ValueInput rawChildOrEmpty(String key) {
        CompoundTag compoundtag = this.getOptionalTypedTag(key, CompoundTag.TYPE);
        return compoundtag != null ? new TagValueInput(this.problemReporter.forChild(new ProblemReporter.FieldPathElement(key)), this.context, compoundtag) : this.context.empty();
    }

    @Override
    public Optional<ValueInput.ValueInputList> childrenList(String name) {
        ListTag list = this.getOptionalTypedTag(name, ListTag.TYPE);
        return list != null ? Optional.of(this.wrapList(name, this.context, list)) : Optional.empty();
    }

    @Override
    public ValueInput.ValueInputList childrenListOrEmpty(String name) {
        ListTag list = this.getOptionalTypedTag(name, ListTag.TYPE);
        return list != null ? this.wrapList(name, this.context, list) : this.context.emptyList();
    }

    @Override
    public <T> Optional<ValueInput.TypedInputList<T>> list(String name, Codec<T> codec) {
        ListTag list = this.getOptionalTypedTag(name, ListTag.TYPE);
        return list != null ? Optional.of(this.wrapTypedList(name, list, codec)) : Optional.empty();
    }

    @Override
    public <T> ValueInput.TypedInputList<T> listOrEmpty(String name, Codec<T> codec) {
        ListTag list = this.getOptionalTypedTag(name, ListTag.TYPE);
        return list != null ? this.wrapTypedList(name, list, codec) : this.context.emptyTypedList();
    }

    @Override
    public boolean getBooleanOr(String name, boolean defaultValue) {
        NumericTag numericTag = this.getNumericTag(name);
        return numericTag != null ? numericTag.byteValue() != 0 : defaultValue;
    }

    @Override
    public byte getByteOr(String name, byte defaultValue) {
        NumericTag numericTag = this.getNumericTag(name);
        return numericTag != null ? numericTag.byteValue() : defaultValue;
    }

    @Override
    public int getShortOr(String name, short defaultValue) {
        NumericTag numericTag = this.getNumericTag(name);
        return numericTag != null ? numericTag.shortValue() : defaultValue;
    }

    @Override
    public Optional<Integer> getInt(String name) {
        NumericTag numericTag = this.getNumericTag(name);
        return numericTag != null ? Optional.of(numericTag.intValue()) : Optional.empty();
    }

    @Override
    public int getIntOr(String name, int defaultValue) {
        NumericTag numericTag = this.getNumericTag(name);
        return numericTag != null ? numericTag.intValue() : defaultValue;
    }

    @Override
    public long getLongOr(String name, long defaultValue) {
        NumericTag numericTag = this.getNumericTag(name);
        return numericTag != null ? numericTag.longValue() : defaultValue;
    }

    @Override
    public Optional<Long> getLong(String name) {
        NumericTag numericTag = this.getNumericTag(name);
        return numericTag != null ? Optional.of(numericTag.longValue()) : Optional.empty();
    }

    @Override
    public float getFloatOr(String name, float defaultValue) {
        NumericTag numericTag = this.getNumericTag(name);
        return numericTag != null ? numericTag.floatValue() : defaultValue;
    }

    @Override
    public double getDoubleOr(String name, double defaultValue) {
        NumericTag numericTag = this.getNumericTag(name);
        return numericTag != null ? numericTag.doubleValue() : defaultValue;
    }

    @Override
    public Optional<String> getString(String name) {
        StringTag tag = this.getOptionalTypedTag(name, StringTag.TYPE);
        return tag != null ? Optional.of(tag.value()) : Optional.empty();
    }

    @Override
    public String getStringOr(String name, String defaultValue) {
        StringTag tag = this.getOptionalTypedTag(name, StringTag.TYPE);
        return tag != null ? tag.value() : defaultValue;
    }

    @Override
    public Optional<int[]> getIntArray(String name) {
        IntArrayTag tag = this.getOptionalTypedTag(name, IntArrayTag.TYPE);
        return tag != null ? Optional.of(tag.getAsIntArray()) : Optional.empty();
    }

    @Override
    public HolderLookup.Provider lookup() {
        return this.context.lookup();
    }

    @Override
    public java.util.Set<String> keySet() {
        return java.util.Collections.unmodifiableSet(input.keySet());
    }

    private ValueInput wrapChild(String name, CompoundTag compoundTag) {
        return compoundTag.isEmpty()
            ? this.context.empty()
            : new TagValueInput(this.problemReporter.forChild(new ProblemReporter.FieldPathElement(name)), this.context, compoundTag);
    }

    private static ValueInput wrapChild(ProblemReporter problemReporter, ValueInputContextHelper context, CompoundTag compoundTag) {
        return compoundTag.isEmpty() ? context.empty() : new TagValueInput(problemReporter, context, compoundTag);
    }

    private ValueInput.ValueInputList wrapList(String name, ValueInputContextHelper context, ListTag list) {
        return list.isEmpty() ? context.emptyList() : new TagValueInput.ListWrapper(this.problemReporter, name, context, list);
    }

    private <T> ValueInput.TypedInputList<T> wrapTypedList(String name, ListTag list, Codec<T> codec) {
        return list.isEmpty() ? this.context.emptyTypedList() : new TagValueInput.TypedListWrapper<>(this.problemReporter, name, this.context, codec, list);
    }

    private static class CompoundListWrapper implements ValueInput.ValueInputList {
        private final ProblemReporter problemReporter;
        private final ValueInputContextHelper context;
        private final List<CompoundTag> list;

        public CompoundListWrapper(ProblemReporter problemReporter, ValueInputContextHelper context, List<CompoundTag> list) {
            this.problemReporter = problemReporter;
            this.context = context;
            this.list = list;
        }

        private ValueInput wrapChild(int index, CompoundTag compoundTag) {
            return TagValueInput.wrapChild(this.problemReporter.forChild(new ProblemReporter.IndexedPathElement(index)), this.context, compoundTag);
        }

        @Override
        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        @Override
        public Stream<ValueInput> stream() {
            return Streams.mapWithIndex(this.list.stream(), (value, index) -> this.wrapChild((int)index, value));
        }

        @Override
        public Iterator<ValueInput> iterator() {
            final ListIterator<CompoundTag> iterator = this.list.listIterator();
            return new AbstractIterator<ValueInput>() {
                protected @Nullable ValueInput computeNext() {
                    if (iterator.hasNext()) {
                        int index = iterator.nextIndex();
                        CompoundTag value = iterator.next();
                        return CompoundListWrapper.this.wrapChild(index, value);
                    } else {
                        return this.endOfData();
                    }
                }
            };
        }
    }

    public record DecodeFromFieldFailedProblem(String name, Tag tag, Error<?> error) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Failed to decode value '" + this.tag + "' from field '" + this.name + "': " + this.error.message();
        }
    }

    public record DecodeFromListFailedProblem(String name, int index, Tag tag, Error<?> error) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Failed to decode value '" + this.tag + "' from field '" + this.name + "' at index " + this.index + "': " + this.error.message();
        }
    }

    public record DecodeFromMapFailedProblem(Error<?> error) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Failed to decode from map: " + this.error.message();
        }
    }

    private static class ListWrapper implements ValueInput.ValueInputList {
        private final ProblemReporter problemReporter;
        private final String name;
        private final ValueInputContextHelper context;
        private final ListTag list;

        private ListWrapper(ProblemReporter problemReporter, String name, ValueInputContextHelper context, ListTag list) {
            this.problemReporter = problemReporter;
            this.name = name;
            this.context = context;
            this.list = list;
        }

        @Override
        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        private ProblemReporter reporterForChild(int index) {
            return this.problemReporter.forChild(new ProblemReporter.IndexedFieldPathElement(this.name, index));
        }

        private void reportIndexUnwrapProblem(int index, Tag value) {
            this.problemReporter.report(new TagValueInput.UnexpectedListElementTypeProblem(this.name, index, CompoundTag.TYPE, value.getType()));
        }

        @Override
        public Stream<ValueInput> stream() {
            return Streams.<Tag, ValueInput>mapWithIndex(this.list.stream(), (value, index) -> {
                if (value instanceof CompoundTag compoundTag) {
                    return TagValueInput.wrapChild(this.reporterForChild((int)index), this.context, compoundTag);
                } else {
                    this.reportIndexUnwrapProblem((int)index, value);
                    return null;
                }
            }).filter(Objects::nonNull);
        }

        @Override
        public Iterator<ValueInput> iterator() {
            final Iterator<Tag> iterator = this.list.iterator();
            return new AbstractIterator<ValueInput>() {
                private int index;

                protected @Nullable ValueInput computeNext() {
                    while (iterator.hasNext()) {
                        Tag value = iterator.next();
                        int currentIndex = this.index++;
                        if (value instanceof CompoundTag compoundTag) {
                            return TagValueInput.wrapChild(ListWrapper.this.reporterForChild(currentIndex), ListWrapper.this.context, compoundTag);
                        }

                        ListWrapper.this.reportIndexUnwrapProblem(currentIndex, value);
                    }

                    return this.endOfData();
                }
            };
        }
    }

    private static class TypedListWrapper<T> implements ValueInput.TypedInputList<T> {
        private final ProblemReporter problemReporter;
        private final String name;
        private final ValueInputContextHelper context;
        private final Codec<T> codec;
        private final ListTag list;

        private TypedListWrapper(ProblemReporter problemReporter, String name, ValueInputContextHelper context, Codec<T> codec, ListTag list) {
            this.problemReporter = problemReporter;
            this.name = name;
            this.context = context;
            this.codec = codec;
            this.list = list;
        }

        @Override
        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        private void reportIndexUnwrapProblem(int index, Tag value, Error<?> error) {
            this.problemReporter.report(new TagValueInput.DecodeFromListFailedProblem(this.name, index, value, error));
        }

        @Override
        public Stream<T> stream() {
            return Streams.<Tag, T>mapWithIndex(this.list.stream(), (value, index) -> {
                return (T)(switch (this.codec.parse(this.context.ops(), value)) {
                    case Success<T> success -> success.value();
                    case Error<T> error -> {
                        this.reportIndexUnwrapProblem((int)index, value, error);
                        yield error.partialValue().orElse(null);
                    }
                    default -> throw new MatchException(null, null);
                });
            }).filter(Objects::nonNull);
        }

        @Override
        public Iterator<T> iterator() {
            final ListIterator<Tag> iterator = this.list.listIterator();
            return new AbstractIterator<T>() {
                @Override
                protected @Nullable T computeNext() {
                    while (iterator.hasNext()) {
                        int index = iterator.nextIndex();
                        Tag value = iterator.next();
                        switch (TypedListWrapper.this.codec.parse((DynamicOps<T>)TypedListWrapper.this.context.ops(), (T)value)) {
                            case Success<T> success:
                                return success.value();
                            case Error<T> error:
                                TypedListWrapper.this.reportIndexUnwrapProblem(index, value, error);
                                if (!error.partialValue().isPresent()) {
                                    break;
                                }

                                return error.partialValue().get();
                            default:
                                throw new MatchException(null, null);
                        }
                    }

                    return (T)this.endOfData();
                }
            };
        }
    }

    public record UnexpectedListElementTypeProblem(String name, int index, TagType<?> expected, TagType<?> actual) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Expected list '"
                + this.name
                + "' to contain at index "
                + this.index
                + " value of type "
                + this.expected.getName()
                + ", but got "
                + this.actual.getName();
        }
    }

    public record UnexpectedNonNumberProblem(String name, TagType<?> actual) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Expected field '" + this.name + "' to contain number, but got " + this.actual.getName();
        }
    }

    public record UnexpectedTypeProblem(String name, TagType<?> expected, TagType<?> actual) implements ProblemReporter.Problem {
        @Override
        public String description() {
            return "Expected field '" + this.name + "' to contain value of type " + this.expected.getName() + ", but got " + this.actual.getName();
        }
    }
}
