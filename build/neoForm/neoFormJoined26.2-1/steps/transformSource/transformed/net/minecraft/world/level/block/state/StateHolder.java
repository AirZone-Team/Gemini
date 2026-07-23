package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public abstract class StateHolder<O, S> {
    private static final int VALUE_NOT_FOUND = -1;
    public static final String NAME_TAG = "Name";
    public static final String PROPERTIES_TAG = "Properties";
    protected final O owner;
    private final Property<?>[] propertyKeys;
    private final Comparable<?>[] propertyValues;
    private S[][] neighbors;

    protected StateHolder(O owner, Property<?>[] propertyKeys, Comparable<?>[] propertyValues) {
        assert propertyKeys.length == propertyValues.length;
        this.owner = owner;
        this.propertyKeys = propertyKeys;
        this.propertyValues = propertyValues;
    }

    public <T extends Comparable<T>> S cycle(Property<T> property) {
        return this.setValue(property, findNextInCollection(property.getPossibleValues(), this.getValue(property)));
    }

    protected static <T> T findNextInCollection(List<T> values, T current) {
        int nextIndex = values.indexOf(current) + 1;
        return nextIndex == values.size() ? values.getFirst() : values.get(nextIndex);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.owner);
        if (!this.isSingletonState()) {
            builder.append('[');
            builder.append(this.getValues().map(Property.Value::toString).collect(Collectors.joining(",")));
            builder.append(']');
        }

        return builder.toString();
    }

    @Override
    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public Collection<Property<?>> getProperties() {
        return List.of(this.propertyKeys);
    }

    private int valueIndex(Property<?> property) {
        for (int i = 0; i < this.propertyKeys.length; i++) {
            if (this.propertyKeys[i] == property) {
                return i;
            }
        }

        return -1;
    }

    public boolean hasProperty(Property<?> property) {
        return this.valueIndex(property) != -1;
    }

    private <T extends Comparable<T>> @Nullable T getNullableValue(Property<T> property) {
        int index = this.valueIndex(property);
        return index == -1 ? null : property.getValueClass().cast(this.propertyValues[index]);
    }

    public <T extends Comparable<T>> T getValue(Property<T> property) {
        T value = this.getNullableValue(property);
        if (value == null) {
            throw new IllegalArgumentException("Cannot get property " + property + " as it does not exist in " + this.owner);
        } else {
            return value;
        }
    }

    public <T extends Comparable<T>> Optional<T> getOptionalValue(Property<T> property) {
        return Optional.ofNullable(this.getNullableValue(property));
    }

    public <T extends Comparable<T>> T getValueOrElse(Property<T> property, T defaultValue) {
        return Objects.requireNonNullElse(this.getNullableValue(property), defaultValue);
    }

    public <T extends Comparable<T>, V extends T> S setValue(Property<T> property, V value) {
        int index = this.valueIndex(property);
        if (index == -1) {
            throw new IllegalArgumentException("Cannot set property " + property + " as it does not exist in " + this.owner);
        } else {
            return this.setValueInternal(property, index, value);
        }
    }

    public <T extends Comparable<T>, V extends T> S trySetValue(Property<T> property, V value) {
        int index = this.valueIndex(property);
        return (S)(index == -1 ? this : this.setValueInternal(property, index, value));
    }

    private <T extends Comparable<T>, V extends T> S setValueInternal(Property<T> property, int propertyIndex, V value) {
        int valueIndex = property.getInternalIndex((T)value);
        if (valueIndex < 0) {
            throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner + ", it is not an allowed value");
        } else {
            return this.neighbors[propertyIndex][valueIndex];
        }
    }

    void initializeNeighbors(S[][] neighbors) {
        if (this.neighbors != null) {
            throw new IllegalStateException();
        }

        this.neighbors = neighbors;
    }

    public boolean isSingletonState() {
        return this.propertyKeys.length == 0;
    }

    public Stream<Property.Value<?>> getValues() {
        int length = this.propertyKeys.length;
        return length == 0 ? Stream.empty() : IntStream.range(0, length).mapToObj(i -> createValue(this.propertyKeys[i], this.propertyValues[i]));
    }

    private static <T extends Comparable<T>> Property.Value<T> createValue(Property<T> propertyKey, Comparable<?> propertyValue) {
        return new Property.Value<>(propertyKey, (T)propertyValue);
    }

    protected static <O, S extends StateHolder<O, S>> Codec<S> codec(
        Codec<O> ownerCodec, Function<O, S> defaultState, Function<O, StateDefinition<O, S>> stateDefinition
    ) {
        return ownerCodec.dispatch(
            "Name",
            s -> s.owner,
            o -> {
                StateDefinition<O, S> definition = stateDefinition.apply((O)o);
                S defaultValue = defaultState.apply((O)o);
                return definition.isSingletonState()
                    ? MapCodec.unit(defaultValue)
                    : definition.propertiesCodec().codec().lenientOptionalFieldOf("Properties").xmap(oo -> oo.orElse(defaultValue), Optional::of);
            }
        );
    }
}
