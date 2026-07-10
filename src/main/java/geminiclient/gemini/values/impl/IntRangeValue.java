package geminiclient.gemini.values.impl;

import geminiclient.gemini.values.ValueParent;

import java.util.function.Supplier;

public class IntRangeValue extends ValueParent {
    private int minValue; // 移除初始值，在构造函数中设置
    private int maxValue; // 移除初始值
    private final int min; // 绝对最小
    private final int max; // 绝对最大

    // 新的构造函数
    public IntRangeValue(String name, int minValue, int maxValue, int min, int max) {
        super(name);
        this.min = min;
        this.max = max;
        // 确保初始值在合法范围内
        this.minValue = Math.max(min, Math.min(max, minValue));
        this.maxValue = Math.max(min, Math.min(max, maxValue));
    }

    public IntRangeValue(String name, int minValue, int maxValue, int min, int max, Supplier<Boolean> booleanSupplier) {
        super(name, booleanSupplier);
        this.min = min;
        this.max = max;
        this.minValue = Math.max(min, Math.min(max, minValue));
        this.maxValue = Math.max(min, Math.min(max, maxValue));
    }

    // Getter
    public int getMin() { return min; }
    public int getMax() { return max; }

    public int getMinValue() { return minValue; }
    public int getMaxValue() { return maxValue; }

    public void setMinValue(int value) {
        // 1. 确保在绝对 min/max 范围内
        int boundedValue = Math.max(min, Math.min(max, value));
        // 2. 确保不大于当前的最大值
        this.minValue = Math.min(boundedValue, maxValue);
        notifyChange();
    }

    public void setMaxValue(int value) {
        // 1. 确保在绝对 min/max 范围内
        int boundedValue = Math.max(min, Math.min(max, value));
        // 2. 确保不小于当前的最小值
        this.maxValue = Math.max(boundedValue, minValue);
        notifyChange();
    }
}