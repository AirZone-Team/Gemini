package geminiclient.gemini.modules.impl.combat.velocity;

import geminiclient.gemini.modules.impl.Mode;
import net.minecraft.world.entity.Entity;

public class Heypixel extends Mode {
    public Heypixel() {
        super("Heypixel");
    }

    private int attacks = 0;
    private double speed = 0;
    private Entity target = null;
    private boolean jumpReset = false;
    private boolean onlyReset = false;

    @Override
    public void onEnabled() {
        speed = 0.0;
        attacks = 0;
        jumpReset = false;
        target = null;
        onlyReset = false;
    }
}
