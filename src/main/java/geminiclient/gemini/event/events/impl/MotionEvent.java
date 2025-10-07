package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.CancellableEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import net.minecraft.world.phys.Vec3;

public class MotionEvent extends CancellableEvent {
    protected final double x;
    protected final double y;
    protected final double z;
    protected final float yRot;
    protected final float xRot;
    protected final boolean onGround;
    protected final boolean horizontalCollision;
    protected final TimeEnum timeEnum;

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

    public TimeEnum getTimeEnum() {
        return this.timeEnum;
    }
}
