package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.values.impl.FloatRangeValue;

import java.text.DecimalFormat;

/**
 * MD3 dual-thumb range slider for {@link FloatRangeValue}.
 */
public class Md3FloatRangeValueComponent extends Md3RangeSliderComponent {

    private final FloatRangeValue rangeValue;
    private final DecimalFormat format = new DecimalFormat("0.00");

    public Md3FloatRangeValueComponent(FloatRangeValue value, int width, Md3Overlay.Host host) {
        super(value, width, host);
        this.rangeValue = value;
    }

    private float span() {
        return rangeValue.getMax() - rangeValue.getMin();
    }

    @Override
    protected float getMinFraction() {
        return span() == 0 ? 0 : (rangeValue.getMinValue() - rangeValue.getMin()) / span();
    }

    @Override
    protected float getMaxFraction() {
        return span() == 0 ? 0 : (rangeValue.getMaxValue() - rangeValue.getMin()) / span();
    }

    @Override
    protected void setMinFromFraction(float fraction) {
        rangeValue.setMinValue(rangeValue.getMin() + span() * fraction);
    }

    @Override
    protected void setMaxFromFraction(float fraction) {
        rangeValue.setMaxValue(rangeValue.getMin() + span() * fraction);
    }

    @Override
    protected String formatRange() {
        return format.format(rangeValue.getMinValue()) + " - " + format.format(rangeValue.getMaxValue());
    }
}
