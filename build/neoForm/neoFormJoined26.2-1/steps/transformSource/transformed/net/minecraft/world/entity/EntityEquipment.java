package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import net.minecraft.world.item.ItemStack;

public class EntityEquipment {
    public static final Codec<EntityEquipment> CODEC = Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.CODEC).xmap(items -> {
        EnumMap<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
        map.putAll((Map<? extends EquipmentSlot, ? extends ItemStack>)items);
        return new EntityEquipment(map);
    }, equipment -> {
        Map<EquipmentSlot, ItemStack> items = new EnumMap<>(equipment.items);
        items.values().removeIf(ItemStack::isEmpty);
        return items;
    });
    private final EnumMap<EquipmentSlot, ItemStack> items;

    private EntityEquipment(EnumMap<EquipmentSlot, ItemStack> items) {
        this.items = items;
    }

    public EntityEquipment() {
        this(new EnumMap<>(EquipmentSlot.class));
    }

    public ItemStack set(EquipmentSlot slot, ItemStack itemStack) {
        return Objects.requireNonNullElse(this.items.put(slot, itemStack), ItemStack.EMPTY);
    }

    public ItemStack get(EquipmentSlot slot) {
        return this.items.getOrDefault(slot, ItemStack.EMPTY);
    }

    public boolean isEmpty() {
        for (ItemStack item : this.items.values()) {
            if (!item.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public void tick(Entity owner) {
        for (Entry<EquipmentSlot, ItemStack> entry : this.items.entrySet()) {
            ItemStack item = entry.getValue();
            if (!item.isEmpty()) {
                item.inventoryTick(owner.level(), owner, entry.getKey());
            }
        }
    }

    public void setAll(EntityEquipment equipment) {
        this.items.clear();
        this.items.putAll(equipment.items);
    }

    public void dropAll(LivingEntity dropper) {
        for (ItemStack item : this.items.values()) {
            dropper.drop(item, true, false);
        }

        this.clear();
    }

    public void clear() {
        this.items.replaceAll((s, v) -> ItemStack.EMPTY);
    }
}
