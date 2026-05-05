package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.MovementUtils;
import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint",ModuleEnum.Movement);
    }

    @EventTarget(0)
    public void onMotion(MotionEvent e) {
        if (e.getTimeEnum() == TimeEnum.Pre) {
            mc.options.keySprint.setDown(true);
            mc.options.toggleSprint().set(false);
        }
    }

    @Override
    public void onDisabled() {
        mc.options.keySprint.setDown(false);
    }
}