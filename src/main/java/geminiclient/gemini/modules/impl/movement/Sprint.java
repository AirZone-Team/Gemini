package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", ModuleEnum.Movement);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void updateEvent(UpdateEvent updateEvent) {
        mc.options.keySprint.setDown(true);
    }
}
