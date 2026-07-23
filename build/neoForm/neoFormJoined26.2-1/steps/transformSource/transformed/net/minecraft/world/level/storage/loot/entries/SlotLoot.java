package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.slot.SlotSource;
import net.minecraft.world.item.slot.SlotSources;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Validatable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SlotLoot extends LootPoolSingletonContainer {
    public static final MapCodec<SlotLoot> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(SlotSources.CODEC.fieldOf("slot_source").forGetter(t -> t.slotSource)).and(singletonFields(i)).apply(i, SlotLoot::new)
    );
    private final SlotSource slotSource;

    private SlotLoot(SlotSource slotSource, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.slotSource = slotSource;
    }

    @Override
    public MapCodec<SlotLoot> codec() {
        return MAP_CODEC;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> output, LootContext context) {
        this.slotSource.provide(context).itemCopies().filter(stack -> !stack.isEmpty()).forEach(output);
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        Validatable.validate(context, "slot_source", this.slotSource);
    }
}
