package geminiclient.gemini.values.impl;

import geminiclient.gemini.values.ValueParent;

import java.util.function.Supplier;

public class CheckboxValue extends ValueParent {
    public BoolValue[] boolValues;
    public CheckboxValue(String name,BoolValue[] boolValues) {
        super(name);
        this.boolValues = boolValues;
    }

    public CheckboxValue(String name, BoolValue[] boolValues, Supplier<Boolean> booleanSupplier) {
        super(name,booleanSupplier);
        this.boolValues = boolValues;
    }
}
