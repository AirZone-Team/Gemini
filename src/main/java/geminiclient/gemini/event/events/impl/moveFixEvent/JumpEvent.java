package geminiclient.gemini.event.events.impl.moveFixEvent;

import geminiclient.gemini.event.impl.Event;

public class JumpEvent implements Event {
    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    private float yaw;
    private boolean jump = false;
    public JumpEvent(float yaw) {
        this.yaw = yaw;
        jump = false;
    }

    public JumpEvent(boolean jump) {
        this.jump = jump;
    }
}
