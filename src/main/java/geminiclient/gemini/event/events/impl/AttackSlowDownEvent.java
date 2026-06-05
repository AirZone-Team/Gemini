package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.CancellableEvent;
import net.minecraft.world.entity.Entity;

public class AttackSlowDownEvent extends CancellableEvent {
    private final Entity entity;
    private final float knockbackAmount;

    public AttackSlowDownEvent(Entity entity, float knockbackAmount) {
        this.entity = entity;
        this.knockbackAmount = knockbackAmount;
    }

    public Entity getEntity() {
        return entity;
    }

    public float getKnockbackAmount() {
        return knockbackAmount;
    }
}
