package geminiclient.gemini.values.impl;

import geminiclient.gemini.values.ValueParent;

import java.util.function.Supplier;

public class FloatValue extends ValueParent {
    protected float value;
    private final float min; // 新增
    private final float max; // 新增

    // 新的构造函数
    public FloatValue(String name, float initialValue, float min, float max) {
        super(name);
        this.value = initialValue;
        this.min = min;
        this.max = max;
    }

    public FloatValue(String name, float initialValue, float min, float max, Supplier<Boolean> booleanSupplier) {
        super(name);
        this.value = initialValue;
        this.min = min;
        this.max = max;
        this.visibility = booleanSupplier;
    }

    // Getter/Setter 方便访问和控制
    public float getValue() { return value; }
    public float getMin() { return min; }
    public float getMax() { return max; }

    public void setValue(float value) {
        // 确保值在范围内
        this.value = Math.max(min, Math.min(max, value));
    }
}