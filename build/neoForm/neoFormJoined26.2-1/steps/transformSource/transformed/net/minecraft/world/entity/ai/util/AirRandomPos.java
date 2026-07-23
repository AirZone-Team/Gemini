package net.minecraft.world.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class AirRandomPos {
    public static @Nullable Vec3 getPosTowards(
        PathfinderMob mob, int horizontalDist, int verticalDist, int flyingHeight, Vec3 towardsPos, double maxXzRadiansFromDir
    ) {
        Vec3 dir = towardsPos.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean restrict = GoalUtils.mobRestricted(mob, horizontalDist);
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos pos = AirAndWaterRandomPos.generateRandomPos(mob, horizontalDist, verticalDist, flyingHeight, dir.x, dir.z, maxXzRadiansFromDir, restrict);
            return pos != null && !GoalUtils.isWater(mob, pos) ? pos : null;
        });
    }
}
