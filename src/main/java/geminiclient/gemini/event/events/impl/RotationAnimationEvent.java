package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.Event;
import net.minecraft.world.entity.Entity;

public class RotationAnimationEvent implements Event {
    public static Entity currentEntity;

    private float yaw;
    private float lastYaw;
    private float pitch;
    private float lastPitch;

    public RotationAnimationEvent(float yaw, float lastYaw, float pitch, float lastPitch) {
        this.yaw = yaw;
        this.lastYaw = lastYaw;
        this.pitch = pitch;
        this.lastPitch = lastPitch;
    }

    public float getYaw() { return yaw; }
    public float getLastYaw() { return lastYaw; }
    public float getPitch() { return pitch; }
    public float getLastPitch() { return lastPitch; }

    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setLastYaw(float lastYaw) { this.lastYaw = lastYaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }
    public void setLastPitch(float lastPitch) { this.lastPitch = lastPitch; }
}
