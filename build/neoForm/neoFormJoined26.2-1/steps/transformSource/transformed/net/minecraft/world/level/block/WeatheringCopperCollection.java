package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.data.BlockFamily;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.apache.commons.lang3.function.TriFunction;

public record WeatheringCopperCollection<T>(WeatheringCopperCollection.ByState<T> weathering, WeatheringCopperCollection.ByState<T> waxed) {
    public static final WeatheringCopperCollection.ByState<WeatheringCopper.WeatherState> STATES = new WeatheringCopperCollection.ByState<>(
        WeatheringCopper.WeatherState.UNAFFECTED,
        WeatheringCopper.WeatherState.EXPOSED,
        WeatheringCopper.WeatherState.WEATHERED,
        WeatheringCopper.WeatherState.OXIDIZED
    );
    public static final WeatheringCopperCollection<String> PREFIXES = new WeatheringCopperCollection<>(
        new WeatheringCopperCollection.ByState<>("", "exposed_", "weathered_", "oxidized_"),
        new WeatheringCopperCollection.ByState<>("waxed_", "waxed_exposed_", "waxed_weathered_", "waxed_oxidized_")
    );

    public static WeatheringCopperCollection<String> prefixWithState(WeatheringCopperCollection<String> ids) {
        return zipMap(PREFIXES, ids, (state, id) -> state + id);
    }

    public static WeatheringCopperCollection<String> create(String name) {
        return same(WeatheringCopperCollection.ByState.create(name));
    }

    public static WeatheringCopperCollection<String> same(WeatheringCopperCollection.ByState<String> byState) {
        return new WeatheringCopperCollection<>(byState, byState);
    }

    public static <WaxedBlock extends Block, WeatheringBlock extends Block & WeatheringCopper, Id> WeatheringCopperCollection<Block> registerBlocks(
        WeatheringCopperCollection<Id> ids,
        TriFunction<Id, Function<BlockBehaviour.Properties, Block>, BlockBehaviour.Properties, Block> register,
        BiFunction<WeatheringCopper.WeatherState, BlockBehaviour.Properties, WaxedBlock> waxedBlockFactory,
        BiFunction<WeatheringCopper.WeatherState, BlockBehaviour.Properties, WeatheringBlock> weatheringFactory,
        Function<WeatheringCopper.WeatherState, BlockBehaviour.Properties> propertiesSupplier
    ) {
        return ids.apply(
            weatheringIds -> zipMap(
                STATES, weatheringIds, (state, id) -> register.apply(id, p -> weatheringFactory.apply(state, p), propertiesSupplier.apply(state))
            ),
            waxedIds -> zipMap(STATES, waxedIds, (state, id) -> register.apply(id, p -> waxedBlockFactory.apply(state, p), propertiesSupplier.apply(state)))
        );
    }

    public static <Id> WeatheringCopperCollection<Item> registerItems(
        WeatheringCopperCollection<Id> ids, WeatheringCopperCollection<Block> blocks, BiFunction<Id, Block, Item> itemFactory
    ) {
        return zipMap(ids, blocks, itemFactory);
    }

    public static WeatheringCopperCollection<BlockFamily> createFamily(
        BiFunction<String, WeatheringCopper.WeatherState, BlockFamily> waxedProvider,
        BiFunction<String, WeatheringCopper.WeatherState, BlockFamily> weatheringProvider
    ) {
        return PREFIXES.apply(
            weatheringPrefixes -> zipMap(weatheringPrefixes, STATES, weatheringProvider), waxedPrefixes -> zipMap(waxedPrefixes, STATES, waxedProvider)
        );
    }

    public List<T> asList() {
        Builder<T> builder = ImmutableList.builderWithExpectedSize(8);
        this.forEach(builder::add);
        return builder.build();
    }

    public void forEach(Consumer<T> consumer) {
        this.weathering.forEach(consumer);
        this.waxed.forEach(consumer);
    }

    public <U> WeatheringCopperCollection<U> map(Function<T, U> mapper) {
        return new WeatheringCopperCollection<>(this.weathering.map(mapper), this.waxed.map(mapper));
    }

    public <U> WeatheringCopperCollection<U> apply(Function<WeatheringCopperCollection.ByState<T>, WeatheringCopperCollection.ByState<U>> mapper) {
        return this.apply(mapper, mapper);
    }

    public <U> WeatheringCopperCollection<U> apply(
        Function<WeatheringCopperCollection.ByState<T>, WeatheringCopperCollection.ByState<U>> weatheringMapper,
        Function<WeatheringCopperCollection.ByState<T>, WeatheringCopperCollection.ByState<U>> waxedMapper
    ) {
        return new WeatheringCopperCollection<>(weatheringMapper.apply(this.weathering), waxedMapper.apply(this.waxed));
    }

    public static <T, U> void zipApply(WeatheringCopperCollection<T> first, WeatheringCopperCollection<U> second, BiConsumer<T, U> consumer) {
        zipApply(first.weathering, second.weathering, consumer);
        zipApply(first.waxed, second.waxed, consumer);
    }

    public static <T, U, R> WeatheringCopperCollection<R> zipMap(
        WeatheringCopperCollection<T> first, WeatheringCopperCollection<U> second, BiFunction<T, U, R> operation
    ) {
        return new WeatheringCopperCollection<>(zipMap(first.weathering, second.weathering, operation), zipMap(first.waxed, second.waxed, operation));
    }

    public void zipUnwaxedWaxed(BiConsumer<T, T> consumer) {
        zipApply(this.weathering, this.waxed, consumer);
    }

    public static <T, U> void zipApply(WeatheringCopperCollection.ByState<T> first, WeatheringCopperCollection.ByState<U> second, BiConsumer<T, U> consumer) {
        consumer.accept(first.unaffected, second.unaffected);
        consumer.accept(first.exposed, second.exposed);
        consumer.accept(first.weathered, second.weathered);
        consumer.accept(first.oxidized, second.oxidized);
    }

    public static <T, U, R> WeatheringCopperCollection.ByState<R> zipMap(
        WeatheringCopperCollection.ByState<T> first, WeatheringCopperCollection.ByState<U> second, BiFunction<T, U, R> operation
    ) {
        return new WeatheringCopperCollection.ByState<>(
            operation.apply(first.unaffected, second.unaffected),
            operation.apply(first.exposed, second.exposed),
            operation.apply(first.weathered, second.weathered),
            operation.apply(first.oxidized, second.oxidized)
        );
    }

    public record ByState<T>(T unaffected, T exposed, T weathered, T oxidized) {
        public static <T> WeatheringCopperCollection.ByState<T> create(T value) {
            return new WeatheringCopperCollection.ByState<>(value, value, value, value);
        }

        public <U> WeatheringCopperCollection.ByState<U> map(Function<T, U> mapper) {
            return new WeatheringCopperCollection.ByState<>(
                mapper.apply(this.unaffected), mapper.apply(this.exposed), mapper.apply(this.weathered), mapper.apply(this.oxidized)
            );
        }

        public T pick(WeatheringCopper.WeatherState state) {
            return (T)(switch (state) {
                case UNAFFECTED -> this.unaffected;
                case EXPOSED -> this.exposed;
                case WEATHERED -> this.weathered;
                case OXIDIZED -> this.oxidized;
            });
        }

        public void forEach(Consumer<T> consumer) {
            consumer.accept(this.unaffected);
            consumer.accept(this.exposed);
            consumer.accept(this.weathered);
            consumer.accept(this.oxidized);
        }

        public void progressMapping(BiConsumer<T, T> consumer) {
            consumer.accept(this.unaffected, this.exposed);
            consumer.accept(this.exposed, this.weathered);
            consumer.accept(this.weathered, this.oxidized);
        }
    }
}
