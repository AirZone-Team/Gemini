package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.values.impl.IntRangeValue;

/**
 * MD3 dual-thumb range slider for {@link IntRangeValue}.
 */
public class Md3IntRangeValueComponent extends Md3RangeSliderComponent {

    private final IntRangeValue rangeValue;

    public Md3IntRangeValueComponent(IntRangeValue value, int width, Md3Overlay.Host host) {
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
        rangeValue.setMinValue(Math.round(rangeValue.getMin() + span() * fraction));
    }

    @Override
    protected void setMaxFromFraction(float fraction) {
        rangeValue.setMaxValue(Math.round(rangeValue.getMin() + span() * fraction));
    }

    @Override
    protected String formatRange() {
        return rangeValue.getMinValue() + " - " + rangeValue.getMaxValue();
    }
}
