package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.values.impl.IntValue;

/**
 * MD3 slider for {@link IntValue}.
 */
public class Md3IntValueComponent extends Md3SliderComponent {

    private final IntValue intValue;

    public Md3IntValueComponent(IntValue value, int width, Md3Overlay.Host host) {
        super(value, width, host);
        this.intValue = value;
    }

    @Override
    protected float getFraction() {
        float range = intValue.getMax() - intValue.getMin();
        return range == 0 ? 0 : (intValue.getValue() - intValue.getMin()) / range;
    }

    @Override
    protected void setFromFraction(float fraction) {
        intValue.setValue(Math.round(intValue.getMin()
                + (intValue.getMax() - intValue.getMin()) * fraction));
    }

    @Override
    protected String formatValue() {
        return String.valueOf(intValue.getValue());
    }
}
