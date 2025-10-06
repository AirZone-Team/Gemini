package geminiclient.gemini.values.impl;

import geminiclient.gemini.values.ValueParent;

import java.util.function.Supplier;

@SuppressWarnings("unused")
public class BoolValue extends ValueParent {
    public boolean enabled = false;

    public BoolValue(String name, Supplier<Boolean> vi) {
        super(name,vi);
    }

    public BoolValue(String name) {
        super(name);
    }

    public BoolValue(String name,boolean bool) {
        super(name);
        this.enabled = bool;
    }

    public BoolValue(String name,boolean b,Supplier<Boolean> booleanSupplier) {
        super(name,booleanSupplier);
        this.enabled = b;
    }
}
