package geminiclient.gemini.modules.impl.combat.killaura;

public class Rotation {
    private float yaw;
    private float pitch;

    public Rotation(float yaw,float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}