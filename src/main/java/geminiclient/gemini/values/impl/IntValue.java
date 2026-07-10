package geminiclient.gemini.values.impl;

import geminiclient.gemini.values.ValueParent;

import java.util.function.Supplier;

public class IntValue extends ValueParent {
    protected int value;
    private final int min; // 新增
    private final int max; // 新增

    // 新的构造函数
    public IntValue(String name, int initialValue, int min, int max) {
        super(name);
        this.value = initialValue;
        this.min = min;
        this.max = max;
    }

    public IntValue(String name, int initialValue, int min, int max, Supplier<Boolean> booleanSupplier) {
        super(name);
        this.value = initialValue;
        this.min = min;
        this.max = max;
        this.visibility = booleanSupplier;
    }

    // Getter/Setter 方便访问和控制
    public int getValue() { return value; }
    public int getMin() { return min; }
    public int getMax() { return max; }

    public void setValue(int value) {
        // 确保值在范围内
        this.value = Math.max(min, Math.min(max, value));
        notifyChange();
    }
}
