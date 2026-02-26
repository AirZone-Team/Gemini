package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.MovementUtils;

public class Speed extends Module {
    public Speed() {
        super("Speed", ModuleEnum.Movement);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null)
            return;

        if (MovementUtils.moving()) {
            if (mc.player.onGround()) {
                mc.player.jumpFromGround();
            }
            MovementUtils.strafe();
        }
    }
}
