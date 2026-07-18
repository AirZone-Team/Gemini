package geminiclient.gemini.modules.impl.visual.clickgui.component;

import geminiclient.gemini.values.ValueParent;
import geminiclient.gemini.values.impl.*;

/**
 * Maps {@link ValueParent} types to their classic-mode UI components.
 * New value types only need to be registered here (and in the MD3 factory).
 */
public final class ValueComponentFactory {

    private ValueComponentFactory() {
    }

    /**
     * Create the classic component for the given value.
     *
     * @return the component, or {@code null} if the value type is unsupported
     */
    public static ValueComponent create(ValueParent value, int x, int y, int width, int height) {
        if (value instanceof BoolValue boolValue) {
            return new BoolValueComponent(boolValue, x, y, width, height);
        } else if (value instanceof FloatValue floatValue) {
            return new FloatValueComponent(floatValue, x, y, width, height);
        } else if (value instanceof IntValue intValue) {
            return new IntValueComponent(intValue, x, y, width, height);
        } else if (value instanceof ListValue listValue) {
            return new ListValueComponent(listValue, x, y, width, height);
        } else if (value instanceof FloatRangeValue floatRangeValue) {
            return new FloatRangeValueComponent(floatRangeValue, x, y, width, height);
        } else if (value instanceof IntRangeValue intRangeValue) {
            return new IntRangeValueComponent(intRangeValue, x, y, width, height);
        } else if (value instanceof CheckboxValue checkboxValue) {
            return new CheckboxValueComponent(checkboxValue, x, y, width, height);
        } else if (value instanceof ColorValue colorValue) {
            return new ColorValueComponent(colorValue, x, y, width, height);
        }
        return null;
    }
}
