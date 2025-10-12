package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.SlowDownEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.FloatValue;

public class NoSlow extends Module {
    private final FloatValue factor = new FloatValue("Factor",0.98f,0.2f,0.98f);
    public NoSlow() {
        super("NoSlow", ModuleEnum.Movement);
        addValue(factor);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (mc.player == null)
            return;

        if (mc.player.isUsingItem()) {
            event.setFactor(factor.getValue());
        }
    }
}
