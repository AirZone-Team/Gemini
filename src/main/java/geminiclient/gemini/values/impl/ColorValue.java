package geminiclient.gemini.values.impl;

import geminiclient.gemini.values.ValueParent;

import java.util.function.Supplier;

public class ColorValue extends ValueParent {
    private int color;

    public ColorValue(String name, int defaultColor) {
        super(name);
        this.color = defaultColor;
    }

    public ColorValue(String name, int defaultColor, Supplier<Boolean> visibility) {
        super(name, visibility);
        this.color = defaultColor;
    }

    public int getColor() {
        return color;
    }

    public int getRGB() {
        return color & 0xFFFFFF;
    }

    public int getAlpha() {
        return (color >> 24) & 0xFF;
    }

    public void setColor(int color) {
        this.color = color;
        notifyChange();
    }
}
