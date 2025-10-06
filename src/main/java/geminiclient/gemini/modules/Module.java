package geminiclient.gemini.modules;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.values.ValueParent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Module implements MinecraftInstance {
    protected String name;
    private final ModuleEnum moduleEnum;
    public boolean enabled = false;
    public int key = 0;
    private final List<ValueParent> values = new ArrayList<>();

    public float animationXOffset = 0.0f;

    public Module(String name, ModuleEnum moduleEnum) {
        this.name = name;
        this.moduleEnum = moduleEnum;
    }

    public Module(String name, ModuleEnum moduleEnum, boolean enabled) {
        this.name = name;
        this.moduleEnum = moduleEnum;
        setEnabled(enabled);
    }

    public ModuleEnum getModuleEnum() {
        return this.moduleEnum;
    }

    public List<ValueParent> getValues() {
        return this.values;
    }

    public String getName() {
        return this.name;
    }

    public void onEnabled() {
    }

    public void onDisabled() {
    }

    public void addValue(ValueParent... valueParents) {
        values.addAll(Arrays.asList(valueParents));
    }

    public void setEnabled(boolean b) {
        this.enabled = b;
        if (b) {
            animationXOffset = 100f;
            Gemini.eventManager.register(this);
            onEnabled();
        } else {
            Gemini.eventManager.unregister(this);
            onDisabled();
        }
    }

    public final void toggle() {
        setEnabled(!enabled);
    }
}
