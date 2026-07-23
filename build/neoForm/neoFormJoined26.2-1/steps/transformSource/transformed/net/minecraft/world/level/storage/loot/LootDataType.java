package net.minecraft.world.level.storage.loot;

import com.mojang.serialization.Codec;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public record LootDataType<T extends Validatable>(ResourceKey<Registry<T>> registryKey, Codec<T> codec, LootDataType.ContextGetter<T> contextGetter, @org.jspecify.annotations.Nullable T defaultValue, Codec<java.util.Optional<T>> conditionalCodec, java.util.function.BiConsumer<T, net.minecraft.resources.Identifier> idSetter) {
    public static final LootDataType<LootItemCondition> PREDICATE = new LootDataType<>(
        Registries.PREDICATE, LootItemCondition.DIRECT_CODEC, LootDataType.ContextGetter.constant(LootContextParamSets.ALL_PARAMS)
    );
    public static final LootDataType<LootItemFunction> MODIFIER = new LootDataType<>(
        Registries.ITEM_MODIFIER, LootItemFunctions.ROOT_CODEC, LootDataType.ContextGetter.constant(LootContextParamSets.ALL_PARAMS)
    );
    public static final LootDataType<LootTable> TABLE = new LootDataType<>(Registries.LOOT_TABLE, LootTable.DIRECT_CODEC, LootTable::getParamSet, LootTable.EMPTY, LootTable::setLootTableId);

    /**
     * @deprecated Neo: use the constructor {@link #LootDataType(ResourceKey, Codec, LootDataType.ContextGetter, T, java.util.function.BiConsumer) with a default value and id setter} to support conditions
     */
    @Deprecated
    private LootDataType(ResourceKey<Registry<T>> registryKey, Codec<T> codec, LootDataType.ContextGetter<T> contextGetter) {
        this(registryKey, codec, contextGetter, null, (it, id) -> {});
    }

    private LootDataType(ResourceKey<Registry<T>> registryKey, Codec<T> codec, LootDataType.ContextGetter<T> contextGetter, @org.jspecify.annotations.Nullable T defaultValue, java.util.function.BiConsumer<T, net.minecraft.resources.Identifier> idSetter) {
        this(registryKey, codec, contextGetter, defaultValue, net.neoforged.neoforge.common.conditions.ConditionalOps.createConditionalCodec(codec), idSetter);
    }

    public void runValidation(ValidationContextSource contextSource, ResourceKey<T> key, T value) {
        ContextKeySet contextKeys = this.contextGetter.context(value);
        ValidationContext rootContext = contextSource.context(contextKeys).enterElement(new ProblemReporter.RootElementPathElement(key), key);
        value.validate(rootContext);
    }

    public void runValidation(ValidationContextSource contextSource, HolderLookup<T> lookup) {
        lookup.listElements().forEach(holder -> this.runValidation(contextSource, holder.key(), holder.value()));
    }

    public static Stream<LootDataType<?>> values() {
        return Stream.of(PREDICATE, MODIFIER, TABLE);
    }

    @FunctionalInterface
    public interface ContextGetter<T> {
        ContextKeySet context(T value);

        static <T> LootDataType.ContextGetter<T> constant(ContextKeySet v) {
            return value -> v;
        }
    }
}
