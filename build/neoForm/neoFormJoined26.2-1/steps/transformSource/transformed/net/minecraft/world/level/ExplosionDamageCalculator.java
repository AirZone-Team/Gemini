package net.minecraft.world.level;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class ExplosionDamageCalculator {
    public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState block, FluidState fluid) {
        return block.isAir() && fluid.isEmpty()
            ? Optional.empty()
            : Optional.of(Math.max(block.getExplosionResistance(level, pos, explosion), fluid.getExplosionResistance(level, pos, explosion)));
    }

    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
        return true;
    }

    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
        return true;
    }

    public float getKnockbackMultiplier(Entity entity) {
        return 1.0F;
    }

    public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
        float doubleRadius = explosion.radius() * 2.0F;
        Vec3 center = explosion.center();
        double dist = Math.sqrt(entity.distanceToSqr(center)) / doubleRadius;
        double pow = (1.0 - dist) * exposure;
        return (float)((pow * pow + pow) / 2.0 * 7.0 * doubleRadius + 1.0);
    }
}
