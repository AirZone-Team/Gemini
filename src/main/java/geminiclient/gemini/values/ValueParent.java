package geminiclient.gemini.values;

import java.util.function.Supplier;

public class ValueParent {
    protected final String name;
    public Supplier<Boolean> visibility = () -> true;

    public ValueParent(String name) {
        this.name = name;
    }

    public ValueParent(String name,Supplier<Boolean> visibility) {
        this.name = name;
        this.visibility = visibility;
    }

    public String getName() {
        return this.name;
    }
}
