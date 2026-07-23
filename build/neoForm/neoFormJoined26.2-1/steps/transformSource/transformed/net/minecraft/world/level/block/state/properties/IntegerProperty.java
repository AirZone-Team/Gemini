package net.minecraft.world.level.block.state.properties;

import it.unimi.dsi.fastutil.ints.IntImmutableList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public final class IntegerProperty extends Property<Integer> {
    private final IntImmutableList values;
    private final int min;
    private final int max;

    private IntegerProperty(String name, int min, int max) {
        super(name, Integer.class);
        if (min < 0) {
            throw new IllegalArgumentException("Min value of " + name + " must be 0 or greater");
        }

        if (max <= min) {
            throw new IllegalArgumentException("Max value of " + name + " must be greater than min (" + min + ")");
        }

        this.min = min;
        this.max = max;
        this.values = IntImmutableList.toList(IntStream.range(min, max + 1));
    }

    @Override
    public List<Integer> getPossibleValues() {
        return this.values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else {
            return o instanceof IntegerProperty that && super.equals(o) ? this.values.equals(that.values) : false;
        }
    }

    @Override
    public int generateHashCode() {
        return 31 * super.generateHashCode() + this.values.hashCode();
    }

    public static IntegerProperty create(String name, int min, int max) {
        return new IntegerProperty(name, min, max);
    }

    @Override
    public Optional<Integer> getValue(String name) {
        try {
            int value = Integer.parseInt(name);
            return value >= this.min && value <= this.max ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public String getName(Integer value) {
        return value.toString();
    }

    public int getInternalIndex(Integer value) {
        return value <= this.max ? value - this.min : -1;
    }
}
