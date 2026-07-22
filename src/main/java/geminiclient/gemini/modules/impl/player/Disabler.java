package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;

public class Disabler extends Module {
    private final BoolValue aimModulo360 = new BoolValue("AimModulo360");
    public Disabler() {
        super("Disabler", ModuleEnum.Player);
        addValue(aimModulo360);
    }
}
