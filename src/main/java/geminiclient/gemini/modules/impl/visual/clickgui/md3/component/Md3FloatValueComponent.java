package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.values.impl.FloatValue;

import java.text.DecimalFormat;

/**
 * MD3 slider for {@link FloatValue}.
 */
public class Md3FloatValueComponent extends Md3SliderComponent {

    private final FloatValue floatValue;
    private final DecimalFormat format = new DecimalFormat("0.00");

    public Md3FloatValueComponent(FloatValue value, int width, Md3Overlay.Host host) {
        super(value, width, host);
        this.floatValue = value;
    }

    @Override
    protected float getFraction() {
        float range = floatValue.getMax() - floatValue.getMin();
        return range == 0 ? 0 : (floatValue.getValue() - floatValue.getMin()) / range;
    }

    @Override
    protected void setFromFraction(float fraction) {
        floatValue.setValue(floatValue.getMin()
                + (floatValue.getMax() - floatValue.getMin()) * fraction);
    }

    @Override
    protected String formatValue() {
        return format.format(floatValue.getValue());
    }
}
