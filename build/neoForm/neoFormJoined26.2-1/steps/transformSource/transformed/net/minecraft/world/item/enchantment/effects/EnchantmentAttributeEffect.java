package net.minecraft.world.item.enchantment.effects;

import com.google.common.collect.HashMultimap;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;

public record EnchantmentAttributeEffect(Identifier id, Holder<Attribute> attribute, LevelBasedValue amount, AttributeModifier.Operation operation)
    implements EnchantmentLocationBasedEffect {
    public static final MapCodec<EnchantmentAttributeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(EnchantmentAttributeEffect::id),
                Attribute.CODEC.fieldOf("attribute").forGetter(EnchantmentAttributeEffect::attribute),
                LevelBasedValue.CODEC.fieldOf("amount").forGetter(EnchantmentAttributeEffect::amount),
                AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(EnchantmentAttributeEffect::operation)
            )
            .apply(i, EnchantmentAttributeEffect::new)
    );

    private Identifier idForSlot(StringRepresentable slot) {
        return this.id.withSuffix("/" + slot.getSerializedName());
    }

    public AttributeModifier getModifier(int level, StringRepresentable slot) {
        return new AttributeModifier(this.idForSlot(slot), this.amount().calculate(level), this.operation());
    }

    @Override
    public void onChangedBlock(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position, boolean becameActive) {
        if (becameActive && entity instanceof LivingEntity living) {
            living.getAttributes().addTransientAttributeModifiers(this.makeAttributeMap(enchantmentLevel, item.inSlot()));
        }
    }

    @Override
    public void onDeactivated(EnchantedItemInUse item, Entity entity, Vec3 position, int level) {
        if (entity instanceof LivingEntity living) {
            living.getAttributes().removeAttributeModifiers(this.makeAttributeMap(level, item.inSlot()));
        }
    }

    private HashMultimap<Holder<Attribute>, AttributeModifier> makeAttributeMap(int enchantmentLevel, EquipmentSlot slot) {
        HashMultimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        map.put(this.attribute, this.getModifier(enchantmentLevel, slot));
        return map;
    }

    @Override
    public MapCodec<EnchantmentAttributeEffect> codec() {
        return MAP_CODEC;
    }
}
