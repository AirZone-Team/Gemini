package net.minecraft.client.data.models.blockstates;

import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class PropertyDispatch<V> {
    private final Map<PropertyValueList, V> values = new HashMap<>();

    protected void putValue(PropertyValueList key, V variant) {
        V previous = this.values.put(key, variant);
        if (previous != null) {
            throw new IllegalStateException("Value " + key + " is already defined");
        }
    }

    Map<PropertyValueList, V> getEntries() {
        this.verifyComplete();
        return Map.copyOf(this.values);
    }

    private void verifyComplete() {
        List<Property<?>> properties = this.getDefinedProperties();
        Stream<PropertyValueList> valuesToCover = Stream.of(PropertyValueList.EMPTY);

        for (Property<?> property : properties) {
            valuesToCover = valuesToCover.flatMap(current -> property.getAllValues().map(current::extend));
        }

        List<PropertyValueList> undefinedCombinations = valuesToCover.filter(f -> !this.values.containsKey(f)).toList();
        if (!undefinedCombinations.isEmpty()) {
            throw new IllegalStateException("Missing definition for properties: " + undefinedCombinations);
        }
    }

    abstract List<Property<?>> getDefinedProperties();

    public static <T1 extends Comparable<T1>> PropertyDispatch.C1<MultiVariant, T1> initial(Property<T1> property1) {
        return new PropertyDispatch.C1<>(property1);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>> PropertyDispatch.C2<MultiVariant, T1, T2> initial(
        Property<T1> property1, Property<T2> property2
    ) {
        return new PropertyDispatch.C2<>(property1, property2);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>> PropertyDispatch.C3<MultiVariant, T1, T2, T3> initial(
        Property<T1> property1, Property<T2> property2, Property<T3> property3
    ) {
        return new PropertyDispatch.C3<>(property1, property2, property3);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>> PropertyDispatch.C4<MultiVariant, T1, T2, T3, T4> initial(
        Property<T1> property1, Property<T2> property2, Property<T3> property3, Property<T4> property4
    ) {
        return new PropertyDispatch.C4<>(property1, property2, property3, property4);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>, T5 extends Comparable<T5>> PropertyDispatch.C5<MultiVariant, T1, T2, T3, T4, T5> initial(
        Property<T1> property1, Property<T2> property2, Property<T3> property3, Property<T4> property4, Property<T5> property5
    ) {
        return new PropertyDispatch.C5<>(property1, property2, property3, property4, property5);
    }

    public static <T1 extends Comparable<T1>> PropertyDispatch.C1<VariantMutator, T1> modify(Property<T1> property1) {
        return new PropertyDispatch.C1<>(property1);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>> PropertyDispatch.C2<VariantMutator, T1, T2> modify(
        Property<T1> property1, Property<T2> property2
    ) {
        return new PropertyDispatch.C2<>(property1, property2);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>> PropertyDispatch.C3<VariantMutator, T1, T2, T3> modify(
        Property<T1> property1, Property<T2> property2, Property<T3> property3
    ) {
        return new PropertyDispatch.C3<>(property1, property2, property3);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>> PropertyDispatch.C4<VariantMutator, T1, T2, T3, T4> modify(
        Property<T1> property1, Property<T2> property2, Property<T3> property3, Property<T4> property4
    ) {
        return new PropertyDispatch.C4<>(property1, property2, property3, property4);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>, T5 extends Comparable<T5>> PropertyDispatch.C5<VariantMutator, T1, T2, T3, T4, T5> modify(
        Property<T1> property1, Property<T2> property2, Property<T3> property3, Property<T4> property4, Property<T5> property5
    ) {
        return new PropertyDispatch.C5<>(property1, property2, property3, property4, property5);
    }

    /// Creates a property dispatch on a single blockstate property that can be used to apply a [net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator].
    public static <T1 extends Comparable<T1>> PropertyDispatch.C1<net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator, T1> modifyUnbaked(Property<T1> p1) {
        return new PropertyDispatch.C1<>(p1);
    }

    /// Creates a property dispatch on two blockstate properties that can be used to apply a [net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator].
    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>> PropertyDispatch.C2<net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator, T1, T2> modifyUnbaked(
            Property<T1> p1, Property<T2> p2
    ) {
        return new PropertyDispatch.C2<>(p1, p2);
    }

    /// Creates a property dispatch on three blockstate properties that can be used to apply a [net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator].
    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>> PropertyDispatch.C3<net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator, T1, T2, T3> modifyUnbaked(
            Property<T1> p1, Property<T2> p2, Property<T3> p3
    ) {
        return new PropertyDispatch.C3<>(p1, p2, p3);
    }

    /// Creates a property dispatch on four blockstate properties that can be used to apply a [net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator].
    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>> PropertyDispatch.C4<net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator, T1, T2, T3, T4> modifyUnbaked(
            Property<T1> p1, Property<T2> p2, Property<T3> p3, Property<T4> p4
    ) {
        return new PropertyDispatch.C4<>(p1, p2, p3, p4);
    }

    /// Creates a property dispatch on five blockstate properties that can be used to apply a [net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator].
    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>, T5 extends Comparable<T5>> PropertyDispatch.C5<net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator, T1, T2, T3, T4, T5> modifyUnbaked(
            Property<T1> p1, Property<T2> p2, Property<T3> p3, Property<T4> p4, Property<T5> p5
    ) {
        return new PropertyDispatch.C5<>(p1, p2, p3, p4, p5);
    }

    public static class C1<V, T1 extends Comparable<T1>> extends PropertyDispatch<V> {
        private final Property<T1> property1;

        private C1(Property<T1> property1) {
            this.property1 = property1;
        }

        @Override
        public List<Property<?>> getDefinedProperties() {
            return List.of(this.property1);
        }

        public PropertyDispatch.C1<V, T1> select(T1 value1, V variants) {
            PropertyValueList key = PropertyValueList.of(this.property1.value(value1));
            this.putValue(key, variants);
            return this;
        }

        public PropertyDispatch<V> generate(Function<T1, V> generator) {
            this.property1.getPossibleValues().forEach(value1 -> this.select((T1)value1, generator.apply((T1)value1)));
            return this;
        }
    }

    public static class C2<V, T1 extends Comparable<T1>, T2 extends Comparable<T2>> extends PropertyDispatch<V> {
        private final Property<T1> property1;
        private final Property<T2> property2;

        private C2(Property<T1> property1, Property<T2> property2) {
            this.property1 = property1;
            this.property2 = property2;
        }

        @Override
        public List<Property<?>> getDefinedProperties() {
            return List.of(this.property1, this.property2);
        }

        public PropertyDispatch.C2<V, T1, T2> select(T1 value1, T2 value2, V variants) {
            PropertyValueList key = PropertyValueList.of(this.property1.value(value1), this.property2.value(value2));
            this.putValue(key, variants);
            return this;
        }

        public PropertyDispatch<V> generate(BiFunction<T1, T2, V> generator) {
            this.property1
                .getPossibleValues()
                .forEach(
                    value1 -> this.property2
                        .getPossibleValues()
                        .forEach(value2 -> this.select((T1)value1, (T2)value2, generator.apply((T1)value1, (T2)value2)))
                );
            return this;
        }
    }

    public static class C3<V, T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>> extends PropertyDispatch<V> {
        private final Property<T1> property1;
        private final Property<T2> property2;
        private final Property<T3> property3;

        private C3(Property<T1> property1, Property<T2> property2, Property<T3> property3) {
            this.property1 = property1;
            this.property2 = property2;
            this.property3 = property3;
        }

        @Override
        public List<Property<?>> getDefinedProperties() {
            return List.of(this.property1, this.property2, this.property3);
        }

        public PropertyDispatch.C3<V, T1, T2, T3> select(T1 value1, T2 value2, T3 value3, V variants) {
            PropertyValueList key = PropertyValueList.of(this.property1.value(value1), this.property2.value(value2), this.property3.value(value3));
            this.putValue(key, variants);
            return this;
        }

        public PropertyDispatch<V> generate(Function3<T1, T2, T3, V> generator) {
            this.property1
                .getPossibleValues()
                .forEach(
                    value1 -> this.property2
                        .getPossibleValues()
                        .forEach(
                            value2 -> this.property3
                                .getPossibleValues()
                                .forEach(value3 -> this.select((T1)value1, (T2)value2, (T3)value3, generator.apply((T1)value1, (T2)value2, (T3)value3)))
                        )
                );
            return this;
        }
    }

    public static class C4<V, T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>>
        extends PropertyDispatch<V> {
        private final Property<T1> property1;
        private final Property<T2> property2;
        private final Property<T3> property3;
        private final Property<T4> property4;

        private C4(Property<T1> property1, Property<T2> property2, Property<T3> property3, Property<T4> property4) {
            this.property1 = property1;
            this.property2 = property2;
            this.property3 = property3;
            this.property4 = property4;
        }

        @Override
        public List<Property<?>> getDefinedProperties() {
            return List.of(this.property1, this.property2, this.property3, this.property4);
        }

        public PropertyDispatch.C4<V, T1, T2, T3, T4> select(T1 value1, T2 value2, T3 value3, T4 value4, V variants) {
            PropertyValueList key = PropertyValueList.of(
                this.property1.value(value1), this.property2.value(value2), this.property3.value(value3), this.property4.value(value4)
            );
            this.putValue(key, variants);
            return this;
        }

        public PropertyDispatch<V> generate(Function4<T1, T2, T3, T4, V> generator) {
            this.property1
                .getPossibleValues()
                .forEach(
                    value1 -> this.property2
                        .getPossibleValues()
                        .forEach(
                            value2 -> this.property3
                                .getPossibleValues()
                                .forEach(
                                    value3 -> this.property4
                                        .getPossibleValues()
                                        .forEach(
                                            value4 -> this.select(
                                                (T1)value1, (T2)value2, (T3)value3, (T4)value4, generator.apply((T1)value1, (T2)value2, (T3)value3, (T4)value4)
                                            )
                                        )
                                )
                        )
                );
            return this;
        }
    }

    public static class C5<V, T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>, T5 extends Comparable<T5>>
        extends PropertyDispatch<V> {
        private final Property<T1> property1;
        private final Property<T2> property2;
        private final Property<T3> property3;
        private final Property<T4> property4;
        private final Property<T5> property5;

        private C5(Property<T1> property1, Property<T2> property2, Property<T3> property3, Property<T4> property4, Property<T5> property5) {
            this.property1 = property1;
            this.property2 = property2;
            this.property3 = property3;
            this.property4 = property4;
            this.property5 = property5;
        }

        @Override
        public List<Property<?>> getDefinedProperties() {
            return List.of(this.property1, this.property2, this.property3, this.property4, this.property5);
        }

        public PropertyDispatch.C5<V, T1, T2, T3, T4, T5> select(T1 value1, T2 value2, T3 value3, T4 value4, T5 value5, V variants) {
            PropertyValueList key = PropertyValueList.of(
                this.property1.value(value1),
                this.property2.value(value2),
                this.property3.value(value3),
                this.property4.value(value4),
                this.property5.value(value5)
            );
            this.putValue(key, variants);
            return this;
        }

        public PropertyDispatch<V> generate(Function5<T1, T2, T3, T4, T5, V> generator) {
            this.property1
                .getPossibleValues()
                .forEach(
                    value1 -> this.property2
                        .getPossibleValues()
                        .forEach(
                            value2 -> this.property3
                                .getPossibleValues()
                                .forEach(
                                    value3 -> this.property4
                                        .getPossibleValues()
                                        .forEach(
                                            value4 -> this.property5
                                                .getPossibleValues()
                                                .forEach(
                                                    value5 -> this.select(
                                                        (T1)value1,
                                                        (T2)value2,
                                                        (T3)value3,
                                                        (T4)value4,
                                                        (T5)value5,
                                                        generator.apply((T1)value1, (T2)value2, (T3)value3, (T4)value4, (T5)value5)
                                                    )
                                                )
                                        )
                                )
                        )
                );
            return this;
        }
    }
}
