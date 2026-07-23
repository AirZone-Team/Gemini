package net.minecraft.advancements.predicates.entity;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.mojang.serialization.Codec;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.DataComponentMatchers;
import net.minecraft.advancements.predicates.DistancePredicate;
import net.minecraft.advancements.predicates.LocationPredicate;
import net.minecraft.advancements.predicates.MobEffectsPredicate;
import net.minecraft.advancements.predicates.NbtPredicate;
import net.minecraft.advancements.predicates.SlotsPredicate;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class EntityPredicate {
    private static final Codec<Map<Codec<? extends EntitySubPredicate>, EntitySubPredicate>> MAP_CODEC = Codec.dispatchedMap(
        BuiltInRegistries.ENTITY_SUB_PREDICATE_TYPE.byNameCodec(), c -> c
    );
    public static final Codec<EntityPredicate> CODEC = MAP_CODEC.xmap(EntityPredicate::new, p -> p.parts);
    public static final Codec<ContextAwarePredicate> ADVANCEMENT_CODEC = Codec.withAlternative(ContextAwarePredicate.CODEC, CODEC, EntityPredicate::wrap);
    private static final Comparator<Entry<Codec<? extends EntitySubPredicate>, EntitySubPredicate>> PREDICATE_TYPE_ORDER = Entry.comparingByKey(
        Comparator.comparing(
            codec -> {
                if (codec == EntityTypePredicate.CODEC) {
                    return -1;
                } else if (codec == VehiclePredicate.CODEC
                    || codec == PassengerPredicate.CODEC
                    || codec == TargetedEntityPredicate.CODEC
                    || codec == LightningBoltPredicate.CODEC
                    || codec == PlayerPredicate.CODEC) {
                    return 1;
                } else {
                    return codec == EntityNbtPredicate.CODEC ? 2 : 0;
                }
            }
        )
    );
    private final Map<Codec<? extends EntitySubPredicate>, EntitySubPredicate> parts;
    private final EntitySubPredicate combinedPart;

    public EntityPredicate(Map<Codec<? extends EntitySubPredicate>, EntitySubPredicate> parts) {
        this.parts = parts;
        this.combinedPart = combine(parts);
    }

    public static ContextAwarePredicate wrap(EntityPredicate.Builder singlePredicate) {
        return wrap(singlePredicate.build());
    }

    public static Optional<ContextAwarePredicate> wrap(Optional<EntityPredicate> singlePredicate) {
        return singlePredicate.map(EntityPredicate::wrap);
    }

    public static List<ContextAwarePredicate> wrap(EntityPredicate.Builder... predicates) {
        return Stream.of(predicates).map(EntityPredicate::wrap).toList();
    }

    public static ContextAwarePredicate wrap(EntityPredicate singlePredicate) {
        LootItemCondition asCondition = LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS, singlePredicate).build();
        return new ContextAwarePredicate(List.of(asCondition));
    }

    public boolean matches(ServerPlayer player, @Nullable Entity entity) {
        return this.matches(player.level(), player.position(), entity);
    }

    public boolean matches(ServerLevel level, @Nullable Vec3 position, @Nullable Entity entity) {
        return entity == null ? false : this.combinedPart.matches(entity, level, position);
    }

    @Nullable
    public <T extends EntitySubPredicate> T getPartIfExists(Codec<T> type) {
        return (T) parts.get(type);
    }

    public static LootContext createContext(ServerPlayer player, Entity entity) {
        LootParams lootParams = new LootParams.Builder(player.level())
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ORIGIN, player.position())
            .create(LootContextParamSets.ADVANCEMENT_ENTITY);
        return new LootContext.Builder(lootParams).create(Optional.empty());
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof EntityPredicate that && Objects.equals(this.parts, that.parts);
    }

    @Override
    public int hashCode() {
        return this.parts.hashCode();
    }

    @Override
    public String toString() {
        return "EntityPredicate[parts=" + this.parts + "]";
    }

    private static EntitySubPredicate combine(Map<Codec<? extends EntitySubPredicate>, EntitySubPredicate> predicateMap) {
        if (predicateMap.isEmpty()) {
            return EntitySubPredicate.ALWAYS_TRUE;
        }

        if (predicateMap.size() == 1) {
            return Iterables.getOnlyElement(predicateMap.values());
        }

        EntitySubPredicate[] predicates = predicateMap.entrySet().stream().sorted(PREDICATE_TYPE_ORDER).map(Entry::getValue).toArray(EntitySubPredicate[]::new);
        return predicates.length == 2 ? predicates[0].and(predicates[1]) : (entity, level, position) -> {
            for (EntitySubPredicate part : predicates) {
                if (!part.matches(entity, level, position)) {
                    return false;
                }
            }

            return true;
        };
    }

    public static class Builder {
        private final ImmutableMap.Builder<Codec<? extends EntitySubPredicate>, EntitySubPredicate> parts = ImmutableMap.builder();

        public static EntityPredicate.Builder entity() {
            return new EntityPredicate.Builder();
        }

        /**
         * Neo: create a builder that inherits all sub-predicates from the given {@code predicate}, that match the given {@code filter}.
         */
        public static EntityPredicate.Builder from(EntityPredicate predicate, java.util.function.Predicate<Codec<? extends EntitySubPredicate>> filter) {
            var builder = new Builder();
            predicate.parts.forEach((key, value) -> {
                if (filter.test(key)) {
                    builder.parts.put(key, value);
                }
            });
            return builder;
        }

        public <T extends EntitySubPredicate> EntityPredicate.Builder put(Codec<T> key, T predicate) {
            this.parts.put(key, predicate);
            return this;
        }

        public EntityPredicate.Builder of(HolderGetter<EntityType<?>> lookup, EntityType<?> entityType) {
            return this.entityType(EntityTypePredicate.of(lookup, entityType));
        }

        public EntityPredicate.Builder of(HolderGetter<EntityType<?>> lookup, TagKey<EntityType<?>> entityTypeTag) {
            return this.entityType(EntityTypePredicate.of(lookup, entityTypeTag));
        }

        public EntityPredicate.Builder entityType(EntityTypePredicate entityType) {
            return this.put(EntityTypePredicate.CODEC, entityType);
        }

        public EntityPredicate.Builder distance(DistancePredicate distanceToPlayer) {
            return this.put(DistanceToPlayerPredicate.CODEC, new DistanceToPlayerPredicate(distanceToPlayer));
        }

        public EntityPredicate.Builder moving(MovementPredicate movement) {
            return this.put(MovementPredicate.CODEC, movement);
        }

        public EntityPredicate.Builder located(LocationPredicate.Builder location) {
            return this.put(EntityLocationPredicate.CODEC, new EntityLocationPredicate(location.build()));
        }

        public EntityPredicate.Builder steppingOn(LocationPredicate.Builder location) {
            return this.put(SteppingOnPredicate.CODEC, new SteppingOnPredicate(location.build()));
        }

        public EntityPredicate.Builder movementAffectedBy(LocationPredicate.Builder location) {
            return this.put(MovementAffectedByPredicate.CODEC, new MovementAffectedByPredicate(location.build()));
        }

        public EntityPredicate.Builder effects(MobEffectsPredicate.Builder effects) {
            return this.put(EntityEffectsPredicate.CODEC, new EntityEffectsPredicate(effects.build()));
        }

        public EntityPredicate.Builder nbt(NbtPredicate nbt) {
            return this.put(EntityNbtPredicate.CODEC, new EntityNbtPredicate(nbt));
        }

        public EntityPredicate.Builder flags(EntityFlagsPredicate.Builder flags) {
            return this.put(EntityFlagsPredicate.CODEC, flags.build());
        }

        public EntityPredicate.Builder equipment(EntityEquipmentPredicate.Builder equipment) {
            return this.put(EntityEquipmentPredicate.CODEC, equipment.build());
        }

        public EntityPredicate.Builder equipment(EntityEquipmentPredicate equipment) {
            return this.put(EntityEquipmentPredicate.CODEC, equipment);
        }

        public EntityPredicate.Builder periodicTick(int period) {
            return this.put(PeriodicEntityTickPredicate.CODEC, new PeriodicEntityTickPredicate(period));
        }

        public EntityPredicate.Builder vehicle(EntityPredicate.Builder vehicle) {
            return this.put(VehiclePredicate.CODEC, new VehiclePredicate(vehicle.build()));
        }

        public EntityPredicate.Builder passenger(EntityPredicate.Builder passenger) {
            return this.put(PassengerPredicate.CODEC, new PassengerPredicate(passenger.build()));
        }

        public EntityPredicate.Builder targetedEntity(EntityPredicate.Builder targetedEntity) {
            return this.put(TargetedEntityPredicate.CODEC, new TargetedEntityPredicate(targetedEntity.build()));
        }

        public EntityPredicate.Builder team(String team) {
            return this.put(TeamPredicate.CODEC, new TeamPredicate(team));
        }

        public EntityPredicate.Builder slots(SlotsPredicate slots) {
            return this.put(EntitySlotsPredicate.CODEC, new EntitySlotsPredicate(slots));
        }

        public EntityPredicate.Builder components(DataComponentMatchers components) {
            if (!components.exact().isEmpty()) {
                this.components(components.exact());
            }

            if (!components.partial().isEmpty()) {
                this.components(components.partial());
            }

            return this;
        }

        public EntityPredicate.Builder components(DataComponentExactPredicate components) {
            return this.put(EntityExactDataComponentsPredicate.CODEC, new EntityExactDataComponentsPredicate(components));
        }

        public EntityPredicate.Builder components(Map<DataComponentPredicate.Type<?>, DataComponentPredicate> components) {
            return this.put(EntityPartialComponentsPredicate.CODEC, new EntityPartialComponentsPredicate(components));
        }

        public EntityPredicate.Builder lightingBolt(LightningBoltPredicate lightningBolt) {
            return this.put(LightningBoltPredicate.CODEC, lightningBolt);
        }

        public EntityPredicate.Builder player(PlayerPredicate player) {
            return this.put(PlayerPredicate.CODEC, player);
        }

        public EntityPredicate.Builder sheep(SheepPredicate sheep) {
            return this.put(SheepPredicate.CODEC, sheep);
        }

        public EntityPredicate.Builder cubeMob(CubeMobPredicate cubeMob) {
            return this.put(CubeMobPredicate.CODEC, cubeMob);
        }

        public EntityPredicate.Builder raider(RaiderPredicate raider) {
            return this.put(RaiderPredicate.CODEC, raider);
        }

        public EntityPredicate.Builder fishingHook(FishingHookPredicate fishingHook) {
            return this.put(FishingHookPredicate.CODEC, fishingHook);
        }

        public EntityPredicate build() {
            return new EntityPredicate(this.parts.buildOrThrow());
        }
    }
}
