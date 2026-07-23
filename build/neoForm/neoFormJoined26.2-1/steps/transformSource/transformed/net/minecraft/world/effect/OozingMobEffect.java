package net.minecraft.world.effect;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

class OozingMobEffect extends MobEffect {
    private static final int RADIUS_TO_CHECK_SLIMES = 2;
    public static final int SLIME_SIZE = 2;
    private final ToIntFunction<RandomSource> spawnedCount;

    protected OozingMobEffect(MobEffectCategory category, int color, ToIntFunction<RandomSource> spawnedCount) {
        super(category, color, ParticleTypes.ITEM_SLIME);
        this.spawnedCount = spawnedCount;
    }

    @VisibleForTesting
    protected static int numberOfSlimesToSpawn(int maxEntityCramming, OozingMobEffect.NearbySlimes nearbySlimes, int numberRequested) {
        return maxEntityCramming < 1 ? numberRequested : Mth.clamp(0, maxEntityCramming - nearbySlimes.count(maxEntityCramming), numberRequested);
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity mob, int amplifier, Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED) {
            int requestedSlimesToSpawn = this.spawnedCount.applyAsInt(mob.getRandom());
            int maxEntityCramming = level.getGameRules().get(GameRules.MAX_ENTITY_CRAMMING);
            int numberOfSlimesToSpawn = numberOfSlimesToSpawn(maxEntityCramming, OozingMobEffect.NearbySlimes.closeTo(mob), requestedSlimesToSpawn);

            for (int i = 0; i < numberOfSlimesToSpawn; i++) {
                this.spawnSlimeOffspring(mob.level(), mob.getX(), mob.getY() + 0.5, mob.getZ());
            }
        }
    }

    private void spawnSlimeOffspring(Level level, double x, double y, double z) {
        Slime slime = EntityTypes.SLIME.create(level, EntitySpawnReason.TRIGGERED);
        if (slime != null) {
            slime.setSize(2, true);
            slime.snapTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(slime);
        }
    }

    @FunctionalInterface
    protected interface NearbySlimes {
        int count(final int maxResults);

        private static OozingMobEffect.NearbySlimes closeTo(LivingEntity mob) {
            return maxResults -> {
                List<Slime> slimesNearby = new ArrayList<>();
                mob.level().getEntities(EntityTypes.SLIME, mob.getBoundingBox().inflate(2.0), slime -> slime != mob, slimesNearby, maxResults);
                return slimesNearby.size();
            };
        }
    }
}
