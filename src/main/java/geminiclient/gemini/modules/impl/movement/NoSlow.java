package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.SlowDownEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;

public class NoSlow extends Module {
    public NoSlow() {
        super("NoSlow", ModuleEnum.Movement);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (mc.player == null)
            return;

        if (mc.player.isUsingItem()) {
            event.setCancelled(true);
        }
    }
}
