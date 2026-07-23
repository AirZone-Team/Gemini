package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.AbstractDoubleList;

public class CubePointRange extends AbstractDoubleList {
    private final int parts;

    public CubePointRange(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("Need at least 1 part");
        }

        this.parts = parts;
    }

    @Override
    public double getDouble(int index) {
        return (double)index / this.parts;
    }

    @Override
    public int size() {
        return this.parts + 1;
    }
}
