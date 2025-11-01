package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;

public class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super("NoJumpDelay", ModuleEnum.Movement);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.getTimeEnum() == TimeEnum.Pre) {
            if (mc.player == null)
                return;
            mc.player.noJumpDelay = 0;
        }
    }
}
