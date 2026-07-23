package net.minecraft.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractUniversalBuilder;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public class NullOps implements DynamicOps<Unit> {
    public static final NullOps INSTANCE = new NullOps();
    private static final MapLike<Unit> EMPTY_MAP = new MapLike<Unit>() {
        public @Nullable Unit get(Unit key) {
            return null;
        }

        public @Nullable Unit get(String key) {
            return null;
        }

        @Override
        public Stream<Pair<Unit, Unit>> entries() {
            return Stream.empty();
        }
    };

    private NullOps() {
    }

    public <U> U convertTo(DynamicOps<U> outOps, Unit input) {
        return outOps.empty();
    }

    public Unit empty() {
        return Unit.INSTANCE;
    }

    public Unit emptyMap() {
        return Unit.INSTANCE;
    }

    public Unit emptyList() {
        return Unit.INSTANCE;
    }

    public Unit createNumeric(Number value) {
        return Unit.INSTANCE;
    }

    public Unit createByte(byte value) {
        return Unit.INSTANCE;
    }

    public Unit createShort(short value) {
        return Unit.INSTANCE;
    }

    public Unit createInt(int value) {
        return Unit.INSTANCE;
    }

    public Unit createLong(long value) {
        return Unit.INSTANCE;
    }

    public Unit createFloat(float value) {
        return Unit.INSTANCE;
    }

    public Unit createDouble(double value) {
        return Unit.INSTANCE;
    }

    public Unit createBoolean(boolean value) {
        return Unit.INSTANCE;
    }

    public Unit createString(String value) {
        return Unit.INSTANCE;
    }

    public DataResult<Number> getNumberValue(Unit input) {
        return DataResult.success(0);
    }

    public DataResult<Boolean> getBooleanValue(Unit input) {
        return DataResult.success(false);
    }

    public DataResult<String> getStringValue(Unit input) {
        return DataResult.success("");
    }

    public DataResult<Unit> mergeToList(Unit input, Unit value) {
        return DataResult.success(Unit.INSTANCE);
    }

    public DataResult<Unit> mergeToList(Unit input, List<Unit> values) {
        return DataResult.success(Unit.INSTANCE);
    }

    public DataResult<Unit> mergeToMap(Unit input, Unit key, Unit value) {
        return DataResult.success(Unit.INSTANCE);
    }

    public DataResult<Unit> mergeToMap(Unit input, Map<Unit, Unit> values) {
        return DataResult.success(Unit.INSTANCE);
    }

    public DataResult<Unit> mergeToMap(Unit input, MapLike<Unit> values) {
        return DataResult.success(Unit.INSTANCE);
    }

    public DataResult<Stream<Pair<Unit, Unit>>> getMapValues(Unit input) {
        return DataResult.success(Stream.empty());
    }

    public DataResult<Consumer<BiConsumer<Unit, Unit>>> getMapEntries(Unit input) {
        return DataResult.success(consumer -> {});
    }

    public DataResult<MapLike<Unit>> getMap(Unit input) {
        return DataResult.success(EMPTY_MAP);
    }

    public DataResult<Stream<Unit>> getStream(Unit input) {
        return DataResult.success(Stream.empty());
    }

    public DataResult<Consumer<Consumer<Unit>>> getList(Unit input) {
        return DataResult.success(consumer -> {});
    }

    public DataResult<ByteBuffer> getByteBuffer(Unit input) {
        return DataResult.success(ByteBuffer.wrap(new byte[0]));
    }

    public DataResult<IntStream> getIntStream(Unit input) {
        return DataResult.success(IntStream.empty());
    }

    public DataResult<LongStream> getLongStream(Unit input) {
        return DataResult.success(LongStream.empty());
    }

    public Unit createMap(Stream<Pair<Unit, Unit>> map) {
        return Unit.INSTANCE;
    }

    public Unit createMap(Map<Unit, Unit> map) {
        return Unit.INSTANCE;
    }

    public Unit createList(Stream<Unit> input) {
        return Unit.INSTANCE;
    }

    public Unit createByteList(ByteBuffer input) {
        return Unit.INSTANCE;
    }

    public Unit createIntList(IntStream input) {
        return Unit.INSTANCE;
    }

    public Unit createLongList(LongStream input) {
        return Unit.INSTANCE;
    }

    public Unit remove(Unit input, String key) {
        return input;
    }

    @Override
    public RecordBuilder<Unit> mapBuilder() {
        return new NullOps.NullMapBuilder(this);
    }

    @Override
    public ListBuilder<Unit> listBuilder() {
        return new NullOps.NullListBuilder(this);
    }

    @Override
    public String toString() {
        return "Null";
    }

    private static final class NullListBuilder extends AbstractListBuilder<Unit, Unit> {
        public NullListBuilder(DynamicOps<Unit> ops) {
            super(ops);
        }

        protected Unit initBuilder() {
            return Unit.INSTANCE;
        }

        protected Unit append(Unit builder, Unit value) {
            return builder;
        }

        protected DataResult<Unit> build(Unit builder, Unit prefix) {
            return DataResult.success(builder);
        }
    }

    private static final class NullMapBuilder extends AbstractUniversalBuilder<Unit, Unit> {
        public NullMapBuilder(DynamicOps<Unit> ops) {
            super(ops);
        }

        protected Unit initBuilder() {
            return Unit.INSTANCE;
        }

        protected Unit append(Unit key, Unit value, Unit builder) {
            return builder;
        }

        protected DataResult<Unit> build(Unit builder, Unit prefix) {
            return DataResult.success(prefix);
        }
    }
}
