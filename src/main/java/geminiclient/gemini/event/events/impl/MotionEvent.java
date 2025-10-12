package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.CancellableEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import net.minecraft.world.phys.Vec3;

public class MotionEvent extends CancellableEvent {
    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public float getyRot() {
        return yRot;
    }

    public void setyRot(float yRot) {
        this.yRot = yRot;
    }

    public float getxRot() {
        return xRot;
    }

    public void setxRot(float xRot) {
        this.xRot = xRot;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    public boolean isHorizontalCollision() {
        return horizontalCollision;
    }

    public void setHorizontalCollision(boolean horizontalCollision) {
        this.horizontalCollision = horizontalCollision;
    }

    protected double x;
    protected double y;
    protected double z;
    protected float yRot;
    protected float xRot;
    protected boolean onGround;
    protected boolean horizontalCollision;
    protected TimeEnum timeEnum;

    public MotionEvent(Vec3 pos,float yRot,float xRot,boolean onGround,boolean horizontalCollision,TimeEnum timeEnum) {
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.yRot = yRot;
        this.xRot = xRot;
        this.onGround = onGround;
        this.horizontalCollision = horizontalCollision;
        this.timeEnum = timeEnum;
    }

    public MotionEvent(float yRot,float xRot,TimeEnum timeEnum) {
        this.xRot = xRot;
        this.yRot = yRot;
        this.timeEnum = timeEnum;
    }

    public TimeEnum getTimeEnum() {
        return this.timeEnum;
    }
}
