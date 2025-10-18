package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.Event;

public class StrafeEvent implements Event {
    public float getStrafe() {
        return strafe;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public float getForward() {
        return forward;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    private float forward;
    private float strafe;

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    private float yaw;
    public StrafeEvent(float forward,float strafe) {
        this.forward = forward;
        this.strafe = strafe;
    }

    public StrafeEvent(float yaw) {
        this.yaw = yaw;
    }
}
