package geminiclient.gemini.utils;

import geminiclient.gemini.base.MinecraftInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.AABB;

public class ReachUtils implements MinecraftInstance {

    public static double getMinDistanceSquaredBetweenEntities(AABB boxA, double expandA,
                                                              AABB boxB, double expandB) {
        return boxA.inflate(expandA).distanceToSqr(boxB.inflate(expandB));
    }

    public static double getEntityReach() {
        if (mc.player == null)
            return 3.0;
        return mc.player.entityInteractionRange();
    }

    public static double getBlockReach() {
        if (mc.player == null)
            return 4.5;
        return mc.player.blockInteractionRange();
    }

    public static AttackRange getAttackRange() {
        if (mc.player == null)
            return null;
        return mc.player.getAttackRangeWith(mc.player.getMainHandItem());
    }

    public static boolean isInReach(Entity entity) {
        return isInReach(entity, 0.0);
    }

    public static boolean isInReach(Entity entity, double buffer) {
        if (mc.player == null || entity == null)
            return false;
        return mc.player.isWithinEntityInteractionRange(entity.getBoundingBox(), buffer);
    }

    public static boolean isInAttackRange(Entity entity) {
        return isInAttackRange(entity, 0.0);
    }

    public static boolean isInAttackRange(Entity entity, double buffer) {
        if (mc.player == null || entity == null)
            return false;
        return mc.player.isWithinAttackRange(mc.player.getMainHandItem(), entity.getBoundingBox(), buffer);
    }

    public static double distanceToSqr(Entity entity) {
        if (mc.player == null || entity == null)
            return Double.MAX_VALUE;
        return entity.getBoundingBox().distanceToSqr(mc.player.getEyePosition());
    }

    public static double distanceTo(Entity entity) {
        return Math.sqrt(distanceToSqr(entity));
    }

    public static boolean isWithinRange(Entity entity, double range) {
        if (mc.player == null || entity == null)
            return false;
        AABB bb = entity.getBoundingBox();
        double dx = Math.max(0.0, Math.max(bb.minX - mc.player.getX(), mc.player.getX() - bb.maxX));
        double dy = Math.max(0.0, Math.max(bb.minY - mc.player.getEyeY(), mc.player.getEyeY() - bb.maxY));
        double dz = Math.max(0.0, Math.max(bb.minZ - mc.player.getZ(), mc.player.getZ() - bb.maxZ));
        return dx * dx + dy * dy + dz * dz < range * range;
    }
}
