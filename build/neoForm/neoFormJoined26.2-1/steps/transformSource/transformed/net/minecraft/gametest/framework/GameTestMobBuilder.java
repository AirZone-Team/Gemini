package net.minecraft.gametest.framework;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public class GameTestMobBuilder<E extends Mob> extends GameTestEntityBuilder<E> {
    private boolean freeWill = true;

    public GameTestMobBuilder(GameTestHelper testHelper, EntityType<E> entityType, Vec3 position) {
        super(testHelper, entityType, position);
    }

    public GameTestMobBuilder<E> withNoFreeWill() {
        this.freeWill = false;
        return this;
    }

    public E spawn() {
        E entity = super.spawn();
        if (!this.freeWill) {
            entity.removeFreeWill();
        }

        return entity;
    }
}
