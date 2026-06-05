package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.AttackSlowDownEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;

public class KeepSprint extends Module {
    public KeepSprint() {
        super("KeepSprint", ModuleEnum.Movement);
    }

    @EventTarget
    public void onAttackSlow(AttackSlowDownEvent event) {
        event.setCancelled(true);
    }
}
