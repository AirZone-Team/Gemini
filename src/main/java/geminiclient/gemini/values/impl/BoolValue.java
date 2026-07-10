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

    /** 通过 setter 修改值，会触发 onChange 回调。推荐替代直接字段赋值。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        notifyChange();
    }
}
