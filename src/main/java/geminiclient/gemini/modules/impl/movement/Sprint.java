package geminiclient.gemini.modules.impl.movement;

import com.cubk.event.annotations.EventTarget;
import geminiclient.gemini.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.MovementUtils;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", ModuleEnum.Movement,true);
        addValue(
        );
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void updateEvent(UpdateEvent updateEvent) {
//        this.setEnabled(true);
        MovementUtils.moving();
        if (player != null) {
            player.setSprinting(true);
        }
    }
}
