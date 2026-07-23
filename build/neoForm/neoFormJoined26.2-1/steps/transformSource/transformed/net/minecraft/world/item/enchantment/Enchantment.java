package net.minecraft.world.item.enchantment;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.effects.DamageImmunity;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;

public record Enchantment(Component description, Enchantment.EnchantmentDefinition definition, HolderSet<Enchantment> exclusiveSet, DataComponentMap effects) {
    public static final int MAX_LEVEL = 255;
    public static final Codec<Enchantment> DIRECT_CODEC = RecordCodecBuilder.create(
        i -> i.group(
                ComponentSerialization.CODEC.fieldOf("description").forGetter(Enchantment::description),
                Enchantment.EnchantmentDefinition.CODEC.forGetter(Enchantment::definition),
                RegistryCodecs.homogeneousList(Registries.ENCHANTMENT).optionalFieldOf("exclusive_set", HolderSet.empty()).forGetter(Enchantment::exclusiveSet),
                EnchantmentEffectComponents.CODEC.optionalFieldOf("effects", DataComponentMap.EMPTY).forGetter(Enchantment::effects)
            )
            .apply(i, Enchantment::new)
    );
    public static final Codec<Holder<Enchantment>> CODEC = RegistryFixedCodec.create(Registries.ENCHANTMENT);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Enchantment>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT);

    public static Enchantment.Cost constantCost(int base) {
        return new Enchantment.Cost(base, 0);
    }

    public static Enchantment.Cost dynamicCost(int base, int perLevel) {
        return new Enchantment.Cost(base, perLevel);
    }

    public static Enchantment.EnchantmentDefinition definition(
        HolderSet<Item> supportedItems,
        HolderSet<Item> primaryItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        EquipmentSlotGroup... slots
    ) {
        return new Enchantment.EnchantmentDefinition(supportedItems, Optional.of(primaryItems), weight, maxLevel, minCost, maxCost, anvilCost, List.of(slots));
    }

    public static Enchantment.EnchantmentDefinition definition(
        HolderSet<Item> supportedItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        EquipmentSlotGroup... slots
    ) {
        return new Enchantment.EnchantmentDefinition(supportedItems, Optional.empty(), weight, maxLevel, minCost, maxCost, anvilCost, List.of(slots));
    }

    public Map<EquipmentSlot, ItemStack> getSlotItems(LivingEntity entity) {
        Map<EquipmentSlot, ItemStack> itemStacks = Maps.newEnumMap(EquipmentSlot.class);

        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (this.matchingSlot(slot)) {
                ItemStack itemStack = entity.getItemBySlot(slot);
                if (!itemStack.isEmpty()) {
                    itemStacks.put(slot, itemStack);
                }
            }
        }

        return itemStacks;
    }

    /**
     * @deprecated Neo: Use {@link ItemStack#supportsEnchantment(Holder)}
     */
    @Deprecated
    public HolderSet<Item> getSupportedItems() {
        return this.definition.supportedItems();
    }

    public boolean matchingSlot(EquipmentSlot slot) {
        return this.definition.slots().stream().anyMatch(group -> group.test(slot));
    }

    /**
     * @deprecated Neo: Use {@link ItemStack#isPrimaryItemFor(Holder)}
     *
     * This method does not respect {@link ItemStack#supportsEnchantment(Holder)} since the {@link Holder} is not available, which makes the result of calling it invalid.
     */
    @Deprecated
    public boolean isPrimaryItem(ItemStack item) {
        return this.isSupportedItem(item) && (this.definition.primaryItems.isEmpty() || item.is(this.definition.primaryItems.get()));
    }

    /**
     * @deprecated Neo: Use {@link ItemStack#supportsEnchantment(Holder)}
     */
    @Deprecated
    public boolean isSupportedItem(ItemStack item) {
        return item.is(this.definition.supportedItems);
    }

    public int getWeight() {
        return this.definition.weight();
    }

    public int getAnvilCost() {
        return this.definition.anvilCost();
    }

    public int getMinLevel() {
        return 1;
    }

    public int getMaxLevel() {
        return this.definition.maxLevel();
    }

    public int getMinCost(int level) {
        return this.definition.minCost().calculate(level);
    }

    public int getMaxCost(int level) {
        return this.definition.maxCost().calculate(level);
    }

    @Override
    public String toString() {
        return "Enchantment " + this.description.getString();
    }

    public static boolean areCompatible(Holder<Enchantment> enchantment, Holder<Enchantment> other) {
        return !enchantment.equals(other) && !enchantment.value().exclusiveSet.contains(other) && !other.value().exclusiveSet.contains(enchantment);
    }

    public static Component getFullname(Holder<Enchantment> enchantment, int level) {
        MutableComponent result = enchantment.value().description.copy();
        if (enchantment.is(EnchantmentTags.CURSE)) {
            result = ComponentUtils.mergeStyles(result, Style.EMPTY.withColor(ChatFormatting.RED));
        } else {
            result = ComponentUtils.mergeStyles(result, Style.EMPTY.withColor(ChatFormatting.GRAY));
        }

        if (level != 1 || enchantment.value().getMaxLevel() != 1) {
            result.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + level));
        }

        return result;
    }

    /**
     * @deprecated Neo: Use {@link ItemStack#supportsEnchantment(Holder)}
     */
    @Deprecated
    public boolean canEnchant(ItemStack itemStack) {
        return this.definition.supportedItems().contains(itemStack.typeHolder());
    }

    public <T> List<T> getEffects(DataComponentType<List<T>> type) {
        return this.effects.getOrDefault(type, List.of());
    }

    public boolean isImmuneToDamage(ServerLevel serverLevel, int enchantmentLevel, Entity victim, DamageSource source) {
        LootContext context = damageContext(serverLevel, enchantmentLevel, victim, source);

        for (ConditionalEffect<DamageImmunity> filteredEffect : this.getEffects(EnchantmentEffectComponents.DAMAGE_IMMUNITY)) {
            if (filteredEffect.matches(context)) {
                return true;
            }
        }

        return false;
    }

    public void modifyDamageProtection(
        ServerLevel serverLevel, int enchantmentLevel, ItemStack item, Entity victim, DamageSource source, MutableFloat protection
    ) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION),
            damageContext(serverLevel, enchantmentLevel, victim, source),
            protection,
            (e, v) -> e.process(enchantmentLevel, victim.getRandom(), v)
        );
    }

    public void modifyDurabilityChange(ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, MutableFloat change) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.ITEM_DAMAGE, serverLevel, enchantmentLevel, itemStack, change);
    }

    public void modifyAmmoCount(ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, MutableFloat change) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.AMMO_USE, serverLevel, enchantmentLevel, itemStack, change);
    }

    public void modifyPiercingCount(ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, MutableFloat count) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.PROJECTILE_PIERCING, serverLevel, enchantmentLevel, itemStack, count);
    }

    public void modifyBlockExperience(ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, MutableFloat count) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.BLOCK_EXPERIENCE, serverLevel, enchantmentLevel, itemStack, count);
    }

    public void modifyMobExperience(ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, Entity killer, MutableFloat experience) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.MOB_EXPERIENCE, serverLevel, enchantmentLevel, itemStack, killer, experience);
    }

    public void modifyDurabilityToRepairFromXp(ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, MutableFloat change) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.REPAIR_WITH_XP, serverLevel, enchantmentLevel, itemStack, change);
    }

    public void modifyTridentReturnToOwnerAcceleration(ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, Entity trident, MutableFloat count) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION, serverLevel, enchantmentLevel, itemStack, trident, count);
    }

    public void modifyTridentSpinAttackStrength(RandomSource random, int enchantmentLevel, MutableFloat strength) {
        this.modifyUnfilteredValue(EnchantmentEffectComponents.TRIDENT_SPIN_ATTACK_STRENGTH, random, enchantmentLevel, strength);
    }

    public void modifyFishingTimeReduction(ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, Entity fisher, MutableFloat timeReduction) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.FISHING_TIME_REDUCTION, serverLevel, enchantmentLevel, itemStack, fisher, timeReduction);
    }

    public void modifyFishingLuckBonus(ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, Entity fisher, MutableFloat luck) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.FISHING_LUCK_BONUS, serverLevel, enchantmentLevel, itemStack, fisher, luck);
    }

    public void modifyDamage(ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, Entity victim, DamageSource damageSource, MutableFloat amount) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.DAMAGE, serverLevel, enchantmentLevel, itemStack, victim, damageSource, amount);
    }

    public void modifyFallBasedDamage(
        ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, Entity victim, DamageSource damageSource, MutableFloat amount
    ) {
        this.modifyDamageFilteredValue(
            EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK, serverLevel, enchantmentLevel, itemStack, victim, damageSource, amount
        );
    }

    public void modifyKnockback(
        ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, Entity victim, DamageSource damageSource, MutableFloat amount
    ) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.KNOCKBACK, serverLevel, enchantmentLevel, itemStack, victim, damageSource, amount);
    }

    public void modifyArmorEffectivness(
        ServerLevel serverLevel, int enchantmentLevel, ItemStack itemStack, Entity victim, DamageSource damageSource, MutableFloat amount
    ) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.ARMOR_EFFECTIVENESS, serverLevel, enchantmentLevel, itemStack, victim, damageSource, amount);
    }

    public void doPostAttack(
        ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, EnchantmentTarget forTarget, Entity victim, DamageSource damageSource
    ) {
        for (TargetedConditionalEffect<EnchantmentEntityEffect> effect : this.getEffects(EnchantmentEffectComponents.POST_ATTACK)) {
            if (forTarget == effect.enchanted()) {
                doPostAttack(effect, serverLevel, enchantmentLevel, item, victim, damageSource);
            }
        }
    }

    public static void doPostAttack(
        TargetedConditionalEffect<EnchantmentEntityEffect> effect,
        ServerLevel serverLevel,
        int enchantmentLevel,
        EnchantedItemInUse item,
        Entity victim,
        DamageSource damageSource
    ) {
        if (effect.matches(damageContext(serverLevel, enchantmentLevel, victim, damageSource))) {
            Entity target = switch (effect.affected()) {
                case ATTACKER -> damageSource.getEntity();
                case DAMAGING_ENTITY -> damageSource.getDirectEntity();
                case VICTIM -> victim;
            };
            if (target != null) {
                effect.effect().apply(serverLevel, enchantmentLevel, item, target, target.position());
            }
        }
    }

    public void doPostPiercingAttack(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity user) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.POST_PIERCING_ATTACK),
            entityContext(serverLevel, enchantmentLevel, user, user.position()),
            e -> e.apply(serverLevel, enchantmentLevel, item, user, user.position())
        );
    }

    public void modifyProjectileCount(ServerLevel serverLevel, int enchantmentLevel, ItemStack weapon, Entity shooter, MutableFloat count) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.PROJECTILE_COUNT, serverLevel, enchantmentLevel, weapon, shooter, count);
    }

    public void modifyProjectileSpread(ServerLevel serverLevel, int enchantmentLevel, ItemStack weapon, Entity shooter, MutableFloat angle) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.PROJECTILE_SPREAD, serverLevel, enchantmentLevel, weapon, shooter, angle);
    }

    public void modifyCrossbowChargeTime(RandomSource random, int enchantmentLevel, MutableFloat time) {
        this.modifyUnfilteredValue(EnchantmentEffectComponents.CROSSBOW_CHARGE_TIME, random, enchantmentLevel, time);
    }

    public void modifyUnfilteredValue(DataComponentType<EnchantmentValueEffect> component, RandomSource random, int enchantmentLevel, MutableFloat value) {
        EnchantmentValueEffect effect = this.effects.get(component);
        if (effect != null) {
            value.setValue(effect.process(enchantmentLevel, random, value.floatValue()));
        }
    }

    public void tick(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.TICK),
            entityContext(serverLevel, enchantmentLevel, entity, entity.position()),
            e -> e.apply(serverLevel, enchantmentLevel, item, entity, entity.position())
        );
    }

    public void onProjectileSpawned(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse weapon, Entity projectile) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED),
            entityContext(serverLevel, enchantmentLevel, projectile, projectile.position()),
            e -> e.apply(serverLevel, enchantmentLevel, weapon, projectile, projectile.position())
        );
    }

    public void onHitBlock(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse weapon, Entity projectile, Vec3 position, BlockState hitBlock) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.HIT_BLOCK),
            blockHitContext(serverLevel, enchantmentLevel, projectile, position, hitBlock),
            e -> e.apply(serverLevel, enchantmentLevel, weapon, projectile, position)
        );
    }

    public void modifyItemFilteredCount(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> effectType,
        ServerLevel serverLevel,
        int enchantmentLevel,
        ItemInstance itemStack,
        MutableFloat value
    ) {
        applyEffects(
            this.getEffects(effectType),
            itemContext(serverLevel, enchantmentLevel, itemStack),
            value,
            (e, v) -> e.process(enchantmentLevel, serverLevel.getRandom(), v)
        );
    }

    public void modifyEntityFilteredValue(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> effectType,
        ServerLevel serverLevel,
        int enchantmentLevel,
        ItemStack itemStack,
        Entity entity,
        MutableFloat value
    ) {
        applyEffects(
            this.getEffects(effectType),
            entityContext(serverLevel, enchantmentLevel, entity, entity.position()),
            value,
            (e, v) -> e.process(enchantmentLevel, entity.getRandom(), v)
        );
    }

    public void modifyDamageFilteredValue(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> effectType,
        ServerLevel serverLevel,
        int enchantmentLevel,
        ItemStack itemStack,
        Entity victim,
        DamageSource damageSource,
        MutableFloat value
    ) {
        applyEffects(
            this.getEffects(effectType),
            damageContext(serverLevel, enchantmentLevel, victim, damageSource),
            value,
            (e, v) -> e.process(enchantmentLevel, victim.getRandom(), v)
        );
    }

    public static LootContext damageContext(ServerLevel serverLevel, int enchantmentLevel, Entity victim, DamageSource source) {
        LootParams params = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.THIS_ENTITY, victim)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, victim.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, source)
            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
            .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
            .create(LootContextParamSets.ENCHANTED_DAMAGE);
        return new LootContext.Builder(params).create(Optional.empty());
    }

    public static LootContext itemContext(ServerLevel serverLevel, int enchantmentLevel, ItemInstance itemStack) {
        LootParams params = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.TOOL, itemStack)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .create(LootContextParamSets.ENCHANTED_ITEM);
        return new LootContext.Builder(params).create(Optional.empty());
    }

    public static LootContext locationContext(ServerLevel serverLevel, int enchantmentLevel, Entity entity, boolean active) {
        LootParams params = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, entity.position())
            .withParameter(LootContextParams.ENCHANTMENT_ACTIVE, active)
            .create(LootContextParamSets.ENCHANTED_LOCATION);
        return new LootContext.Builder(params).create(Optional.empty());
    }

    public static LootContext entityContext(ServerLevel serverLevel, int enchantmentLevel, Entity entity, Vec3 position) {
        LootParams params = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, position)
            .create(LootContextParamSets.ENCHANTED_ENTITY);
        return new LootContext.Builder(params).create(Optional.empty());
    }

    public static LootContext blockHitContext(ServerLevel serverLevel, int enchantmentLevel, Entity entity, Vec3 position, BlockState hitBlock) {
        LootParams params = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, position)
            .withParameter(LootContextParams.BLOCK_STATE, hitBlock)
            .create(LootContextParamSets.HIT_BLOCK);
        return new LootContext.Builder(params).create(Optional.empty());
    }

    public static <T> void applyEffects(List<ConditionalEffect<T>> effects, LootContext filterData, Enchantment.GenericAction<T> action) {
        for (ConditionalEffect<T> conditionalEffect : effects) {
            if (conditionalEffect.matches(filterData)) {
                action.apply(conditionalEffect.effect());
            }
        }
    }

    private static <T> void applyEffects(List<ConditionalEffect<T>> effects, LootContext filterData, MutableFloat value, Enchantment.FloatAction<T> action) {
        applyEffects(effects, filterData, action.asGeneric(value));
    }

    public void runLocationChangedEffects(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, LivingEntity entity) {
        EquipmentSlot slot = item.inSlot();
        if (slot != null) {
            Map<Enchantment, Set<EnchantmentLocationBasedEffect>> activeLocationDependentEffects = entity.activeLocationDependentEnchantments(slot);
            if (!this.matchingSlot(slot)) {
                Set<EnchantmentLocationBasedEffect> activeEffects = activeLocationDependentEffects.remove(this);
                if (activeEffects != null) {
                    activeEffects.forEach(effectx -> effectx.onDeactivated(item, entity, entity.position(), enchantmentLevel));
                }
            } else {
                Set<EnchantmentLocationBasedEffect> activeEffects = activeLocationDependentEffects.get(this);

                for (ConditionalEffect<EnchantmentLocationBasedEffect> filteredEffect : this.getEffects(EnchantmentEffectComponents.LOCATION_CHANGED)) {
                    EnchantmentLocationBasedEffect effect = filteredEffect.effect();
                    boolean wasActive = activeEffects != null && activeEffects.contains(effect);
                    if (filteredEffect.matches(locationContext(serverLevel, enchantmentLevel, entity, wasActive))) {
                        if (!wasActive) {
                            if (activeEffects == null) {
                                activeEffects = new ObjectArraySet<>();
                                activeLocationDependentEffects.put(this, activeEffects);
                            }

                            activeEffects.add(effect);
                        }

                        effect.onChangedBlock(serverLevel, enchantmentLevel, item, entity, entity.position(), !wasActive);
                    } else if (activeEffects != null && activeEffects.remove(effect)) {
                        effect.onDeactivated(item, entity, entity.position(), enchantmentLevel);
                    }
                }

                if (activeEffects != null && activeEffects.isEmpty()) {
                    activeLocationDependentEffects.remove(this);
                }
            }
        }
    }

    public void stopLocationBasedEffects(int enchantmentLevel, EnchantedItemInUse item, LivingEntity entity) {
        EquipmentSlot slot = item.inSlot();
        if (slot != null) {
            Set<EnchantmentLocationBasedEffect> activeEffects = entity.activeLocationDependentEnchantments(slot).remove(this);
            if (activeEffects != null) {
                for (EnchantmentLocationBasedEffect effect : activeEffects) {
                    effect.onDeactivated(item, entity, entity.position(), enchantmentLevel);
                }
            }
        }
    }

    public static Enchantment.Builder enchantment(Enchantment.EnchantmentDefinition definition) {
        return new Enchantment.Builder(definition);
    }

//    TODO: Reimplement. Not sure if we want to patch EnchantmentDefinition or hack this in as an EnchantmentEffectComponent.
//    /**
//     * Is this enchantment allowed to be enchanted on books via Enchantment Table
//     * @return false to disable the vanilla feature
//     */
//    public boolean isAllowedOnBooks() {
//        return true;
//    }

    public static class Builder {
        private final Enchantment.EnchantmentDefinition definition;
        private HolderSet<Enchantment> exclusiveSet = HolderSet.empty();
        private final Map<DataComponentType<?>, List<?>> effectLists = new HashMap<>();
        private final DataComponentMap.Builder effectMapBuilder = DataComponentMap.builder();

        /**
         * Neo: Allow customizing or changing the {@link Component} created by the enchantment builder.
         */
        protected java.util.function.UnaryOperator<MutableComponent> nameFactory = java.util.function.UnaryOperator.identity();

        public Builder(Enchantment.EnchantmentDefinition definition) {
            this.definition = definition;
        }

        public Enchantment.Builder exclusiveWith(HolderSet<Enchantment> set) {
            this.exclusiveSet = set;
            return this;
        }

        public <E> Enchantment.Builder withEffect(DataComponentType<List<ConditionalEffect<E>>> type, E effect, LootItemCondition.Builder condition) {
            this.getEffectsList(type).add(new ConditionalEffect<>(effect, Optional.of(condition.build())));
            return this;
        }

        public <E> Enchantment.Builder withEffect(DataComponentType<List<ConditionalEffect<E>>> type, E effect) {
            this.getEffectsList(type).add(new ConditionalEffect<>(effect, Optional.empty()));
            return this;
        }

        public <E> Enchantment.Builder withEffect(
            DataComponentType<List<TargetedConditionalEffect<E>>> type,
            EnchantmentTarget enchanted,
            EnchantmentTarget affected,
            E effect,
            LootItemCondition.Builder condition
        ) {
            this.getEffectsList(type).add(new TargetedConditionalEffect<>(enchanted, affected, effect, Optional.of(condition.build())));
            return this;
        }

        public <E> Enchantment.Builder withEffect(
            DataComponentType<List<TargetedConditionalEffect<E>>> type, EnchantmentTarget enchanted, EnchantmentTarget affected, E effect
        ) {
            this.getEffectsList(type).add(new TargetedConditionalEffect<>(enchanted, affected, effect, Optional.empty()));
            return this;
        }

        public Enchantment.Builder withEffect(DataComponentType<List<EnchantmentAttributeEffect>> type, EnchantmentAttributeEffect effect) {
            this.getEffectsList(type).add(effect);
            return this;
        }

        public <E> Enchantment.Builder withSpecialEffect(DataComponentType<E> type, E effect) {
            this.effectMapBuilder.set(type, effect);
            return this;
        }

        public Enchantment.Builder withEffect(DataComponentType<Unit> type) {
            this.effectMapBuilder.set(type, Unit.INSTANCE);
            return this;
        }

        /**
         * Allows specifying an operator that can customize the default {@link Component} created by {@link #build(Identifier)}.
         *
         * @return this
         */
        public Enchantment.Builder withCustomName(java.util.function.UnaryOperator<MutableComponent> nameFactory) {
            this.nameFactory = nameFactory;
            return this;
        }

        private <E> List<E> getEffectsList(DataComponentType<List<E>> type) {
            return (List<E>)this.effectLists.computeIfAbsent(type, k -> {
                ArrayList<E> newList = new ArrayList<>();
                this.effectMapBuilder.set(type, newList);
                return newList;
            });
        }

        public Enchantment build(Identifier descriptionKey) {
            return new Enchantment(
                // Neo: permit custom name components instead of a single hardcoded translatable component.
                this.nameFactory.apply(Component.translatable(Util.makeDescriptionId("enchantment", descriptionKey))),
                this.definition,
                this.exclusiveSet,
                this.effectMapBuilder.build()
            );
        }
    }

    public record Cost(int base, int perLevelAboveFirst) {
        public static final Codec<Enchantment.Cost> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    Codec.INT.fieldOf("base").forGetter(Enchantment.Cost::base),
                    Codec.INT.fieldOf("per_level_above_first").forGetter(Enchantment.Cost::perLevelAboveFirst)
                )
                .apply(i, Enchantment.Cost::new)
        );

        public int calculate(int level) {
            return this.base + this.perLevelAboveFirst * (level - 1);
        }
    }

    public record EnchantmentDefinition(
        HolderSet<Item> supportedItems,
        Optional<HolderSet<Item>> primaryItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        List<EquipmentSlotGroup> slots
    ) {
        public static final MapCodec<Enchantment.EnchantmentDefinition> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(
                    RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("supported_items").forGetter(Enchantment.EnchantmentDefinition::supportedItems),
                    RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("primary_items").forGetter(Enchantment.EnchantmentDefinition::primaryItems),
                    ExtraCodecs.intRange(1, 1024).fieldOf("weight").forGetter(Enchantment.EnchantmentDefinition::weight),
                    ExtraCodecs.intRange(1, 255).fieldOf("max_level").forGetter(Enchantment.EnchantmentDefinition::maxLevel),
                    Enchantment.Cost.CODEC.fieldOf("min_cost").forGetter(Enchantment.EnchantmentDefinition::minCost),
                    Enchantment.Cost.CODEC.fieldOf("max_cost").forGetter(Enchantment.EnchantmentDefinition::maxCost),
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("anvil_cost").forGetter(Enchantment.EnchantmentDefinition::anvilCost),
                    EquipmentSlotGroup.CODEC.listOf().fieldOf("slots").forGetter(Enchantment.EnchantmentDefinition::slots)
                )
                .apply(i, Enchantment.EnchantmentDefinition::new)
        );
    }

    @FunctionalInterface
    private interface FloatAction<T> {
        float apply(T effect, float value);

        default Enchantment.GenericAction<T> asGeneric(MutableFloat v) {
            return effect -> v.setValue(this.apply(effect, v.floatValue()));
        }
    }

    @FunctionalInterface
    private interface GenericAction<T> {
        void apply(T effect);
    }
}
