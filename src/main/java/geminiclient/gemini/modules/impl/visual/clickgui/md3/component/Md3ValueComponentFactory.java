package geminiclient.gemini.modules.impl.visual.clickgui.md3.component;

import geminiclient.gemini.values.ValueParent;
import geminiclient.gemini.values.impl.*;

/**
 * Maps {@link ValueParent} types to their MD3 UI components.
 * MD3 counterpart of the classic {@code ValueComponentFactory}.
 */
public final class Md3ValueComponentFactory {

    private Md3ValueComponentFactory() {
    }

    public static Md3ValueComponent create(ValueParent value, int width, Md3Overlay.Host host) {
        if (value instanceof BoolValue boolValue) {
            return new Md3BoolValueComponent(boolValue, width, host);
        } else if (value instanceof FloatValue floatValue) {
            return new Md3FloatValueComponent(floatValue, width, host);
        } else if (value instanceof IntValue intValue) {
            return new Md3IntValueComponent(intValue, width, host);
        } else if (value instanceof ListValue listValue) {
            return new Md3ListValueComponent(listValue, width, host);
        } else if (value instanceof FloatRangeValue floatRangeValue) {
            return new Md3FloatRangeValueComponent(floatRangeValue, width, host);
        } else if (value instanceof IntRangeValue intRangeValue) {
            return new Md3IntRangeValueComponent(intRangeValue, width, host);
        } else if (value instanceof CheckboxValue checkboxValue) {
            return new Md3CheckboxValueComponent(checkboxValue, width, host);
        } else if (value instanceof ColorValue colorValue) {
            return new Md3ColorValueComponent(colorValue, width, host);
        }
        return null;
    }
}
