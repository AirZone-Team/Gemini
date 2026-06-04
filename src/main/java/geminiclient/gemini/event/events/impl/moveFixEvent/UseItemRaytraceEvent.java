package geminiclient.gemini.event.events.impl.moveFixEvent;

import geminiclient.gemini.event.impl.Event;

public class UseItemRaytraceEvent implements Event {

    private float yaw;
    private float pitch;

    public UseItemRaytraceEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

}