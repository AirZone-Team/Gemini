package geminiclient.gemini.modules.impl.combat.velocity;

import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.impl.Mode;

public class Fall00 extends Mode {
    public Fall00() {
        super("Fall00");
    }
    private boolean start = false;

    @Override
    public void onEnabled() {
        start = false;
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null)
            return;

        if (mc.player.hurtTime > 0) {
            if (mc.player.fallDistance > 0.0 && !start) {
                mc.player.setDeltaMovement(0.0,mc.player.getDeltaMovement().y,0.0);
                start = true;
            }
        }

        if (mc.player.onGround())
            start = false;
    }
}
