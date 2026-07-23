package net.minecraft.world.entity;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public interface NeutralMob {
    String TAG_ANGER_END_TIME = "anger_end_time";
    String TAG_ANGRY_AT = "angry_at";
    long NO_ANGER_END_TIME = -1L;

    long getPersistentAngerEndTime();

    default void setTimeToRemainAngry(long remainingTime) {
        this.setPersistentAngerEndTime(this.level().getGameTime() + remainingTime);
    }

    void setPersistentAngerEndTime(long endTime);

    @Nullable EntityReference<LivingEntity> getPersistentAngerTarget();

    void setPersistentAngerTarget(final @Nullable EntityReference<LivingEntity> persistentAngerTarget);

    void startPersistentAngerTimer();

    Level level();

    default void addPersistentAngerSaveData(ValueOutput output) {
        output.putLong("anger_end_time", this.getPersistentAngerEndTime());
        output.storeNullable("angry_at", EntityReference.codec(), this.getPersistentAngerTarget());
    }

    default void readPersistentAngerSaveData(Level level, ValueInput input) {
        Optional<Long> endTime = input.getLong("anger_end_time");
        if (endTime.isPresent()) {
            this.setPersistentAngerEndTime(endTime.get());
        } else {
            Optional<Integer> angerTime = input.getInt("AngerTime");
            if (angerTime.isPresent()) {
                this.setTimeToRemainAngry(angerTime.get().intValue());
            } else {
                this.setPersistentAngerEndTime(-1L);
            }
        }

        if (level instanceof ServerLevel) {
            this.setPersistentAngerTarget(EntityReference.read(input, "angry_at"));
            this.setTarget(EntityReference.getLivingEntity(this.getPersistentAngerTarget(), level));
        }
    }

    default void updatePersistentAnger(ServerLevel level, boolean stayAngryIfTargetPresent) {
        LivingEntity previousTarget = this.getTargetUnchecked();
        EntityReference<LivingEntity> persistentAngerTarget = this.getPersistentAngerTarget();
        if (previousTarget != null
            && previousTarget.isDeadOrDying()
            && persistentAngerTarget != null
            && persistentAngerTarget.matches(previousTarget)
            && previousTarget instanceof Mob) {
            this.stopBeingAngry();
        } else {
            LivingEntity target = this.getTarget();
            if (target != null) {
                boolean newTarget = persistentAngerTarget == null || !persistentAngerTarget.matches(target);
                if (newTarget) {
                    this.setPersistentAngerTarget(EntityReference.of(target));
                }

                if (newTarget || stayAngryIfTargetPresent) {
                    this.startPersistentAngerTimer();
                }
            }

            if (persistentAngerTarget != null && !this.isAngry() && (target == null || !isValidPlayerTarget(target) || !stayAngryIfTargetPresent)) {
                this.stopBeingAngry();
            }

            if (EntityReference.getLivingEntity(persistentAngerTarget, level) instanceof Player player
                && (player.isCreative() || player.isSpectator() || level.getDifficulty() == Difficulty.PEACEFUL)) {
                this.stopBeingAngry();
            }
        }
    }

    private static boolean isValidPlayerTarget(LivingEntity target) {
        return target instanceof Player player && !player.isCreative() && !player.isSpectator() && player.level().getDifficulty() != Difficulty.PEACEFUL;
    }

    default boolean isAngryAt(LivingEntity entity, ServerLevel level) {
        if (!this.canAttack(entity)) {
            return false;
        }

        if (isValidPlayerTarget(entity) && this.isAngryAtAllPlayers(level)) {
            return true;
        }

        EntityReference<LivingEntity> persistentAngerTarget = this.getPersistentAngerTarget();
        return persistentAngerTarget != null && persistentAngerTarget.matches(entity);
    }

    default boolean isAngryAtAllPlayers(ServerLevel level) {
        return level.getGameRules().get(GameRules.UNIVERSAL_ANGER) && this.isAngry() && this.getPersistentAngerTarget() == null;
    }

    default boolean isAngry() {
        long endTime = this.getPersistentAngerEndTime();
        if (endTime > 0L) {
            long remaining = endTime - this.level().getGameTime();
            return remaining > 0L;
        } else {
            return false;
        }
    }

    default void playerDied(ServerLevel level, Player player) {
        if (level.getGameRules().get(GameRules.FORGIVE_DEAD_PLAYERS)) {
            EntityReference<LivingEntity> persistentAngerTarget = this.getPersistentAngerTarget();
            if (persistentAngerTarget != null && persistentAngerTarget.matches(player)) {
                this.stopBeingAngry();
            }
        }
    }

    default void forgetCurrentTargetAndRefreshUniversalAnger() {
        this.stopBeingAngry();
        this.startPersistentAngerTimer();
    }

    default void stopBeingAngry() {
        this.setLastHurtByMob(null);
        this.setPersistentAngerTarget(null);
        this.setTarget(null);
        this.setPersistentAngerEndTime(-1L);
    }

    @Nullable LivingEntity getLastHurtByMob();

    void setLastHurtByMob(final @Nullable LivingEntity hurtBy);

    void setTarget(final @Nullable LivingEntity target);

    boolean canAttack(final LivingEntity target);

    @Nullable LivingEntity getTarget();

    @Nullable LivingEntity getTargetUnchecked();
}
