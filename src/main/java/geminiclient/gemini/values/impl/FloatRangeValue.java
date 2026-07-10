package geminiclient.gemini.values.impl;

import geminiclient.gemini.values.ValueParent;

import java.util.function.Supplier;

public class FloatRangeValue extends ValueParent {
    private float minValue; // 移除初始值，在构造函数中设置
    private float maxValue; // 移除初始值
    private final float min; // 绝对最小
    private final float max; // 绝对最大

    // 新的构造函数
    public FloatRangeValue(String name, float minValue, float maxValue, float min, float max) {
        super(name);
        this.min = min;
        this.max = max;
        // 确保初始值在合法范围内
        this.minValue = Math.max(min, Math.min(max, minValue));
        this.maxValue = Math.max(min, Math.min(max, maxValue));
    }

    public FloatRangeValue(String name, float minValue, float maxValue, float min, float max, Supplier<Boolean> booleanSupplier) {
        super(name, booleanSupplier);
        this.min = min;
        this.max = max;
        this.minValue = Math.max(min, Math.min(max, minValue));
        this.maxValue = Math.max(min, Math.min(max, maxValue));
    }

    // Getter
    public float getMin() { return min; }
    public float getMax() { return max; }

    public float getMinValue() { return minValue; }
    public float getMaxValue() { return maxValue; }

    public void setMinValue(float value) {
        // 1. 确保在绝对 min/max 范围内
        float boundedValue = Math.max(min, Math.min(max, value));
        // 2. 确保不大于当前的最大值
        this.minValue = Math.min(boundedValue, maxValue);
        notifyChange();
    }

    public void setMaxValue(float value) {
        // 1. 确保在绝对 min/max 范围内
        float boundedValue = Math.max(min, Math.min(max, value));
        // 2. 确保不小于当前的最小值
        this.maxValue = Math.max(boundedValue, minValue);
        notifyChange();
    }
}