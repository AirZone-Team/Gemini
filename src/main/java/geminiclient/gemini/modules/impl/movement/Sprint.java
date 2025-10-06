package geminiclient.gemini.modules.impl.movement;

import com.cubk.event.annotations.EventTarget;
import geminiclient.gemini.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.MovementUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.CheckboxValue;

public class Sprint extends Module {
    CheckboxValue checkboxValue = new CheckboxValue("test",new BoolValue[]{
            new BoolValue("fw"),new BoolValue("sb")
    });
    public Sprint() {
        super("Sprint", ModuleEnum.Movement,true);
        addValue(
                checkboxValue
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
