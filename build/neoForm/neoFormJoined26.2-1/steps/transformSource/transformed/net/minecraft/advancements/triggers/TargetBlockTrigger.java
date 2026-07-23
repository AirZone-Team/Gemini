package net.minecraft.advancements.triggers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Validatable;
import net.minecraft.world.level.storage.loot.ValidationContextSource;
import net.minecraft.world.phys.Vec3;

public class TargetBlockTrigger extends SimpleCriterionTrigger<TargetBlockTrigger.TriggerInstance> {
    @Override
    public Codec<TargetBlockTrigger.TriggerInstance> codec() {
        return TargetBlockTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Entity projectile, Vec3 hitPosition, int signalStrength) {
        LootContext projectileContext = EntityPredicate.createContext(player, projectile);
        this.trigger(player, t -> t.matches(projectileContext, hitPosition, signalStrength));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, MinMaxBounds.Ints signalStrength, Optional<ContextAwarePredicate> projectile)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TargetBlockTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TargetBlockTrigger.TriggerInstance::player),
                    MinMaxBounds.Ints.CODEC
                        .optionalFieldOf("signal_strength", MinMaxBounds.Ints.ANY)
                        .forGetter(TargetBlockTrigger.TriggerInstance::signalStrength),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("projectile").forGetter(TargetBlockTrigger.TriggerInstance::projectile)
                )
                .apply(i, TargetBlockTrigger.TriggerInstance::new)
        );

        public static Criterion<TargetBlockTrigger.TriggerInstance> targetHit(
            MinMaxBounds.Ints redstoneSignalStrength, Optional<ContextAwarePredicate> projectile
        ) {
            return CriteriaTriggers.TARGET_BLOCK_HIT
                .createCriterion(new TargetBlockTrigger.TriggerInstance(Optional.empty(), redstoneSignalStrength, projectile));
        }

        public boolean matches(LootContext projectile, Vec3 hitPosition, int signalStrength) {
            return !this.signalStrength.matches(signalStrength) ? false : !this.projectile.isPresent() || this.projectile.get().matches(projectile);
        }

        @Override
        public void validate(ValidationContextSource validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            Validatable.validate(validator.entityContext(), "projectile", this.projectile);
        }
    }
}
