package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.apache.commons.lang3.mutable.MutableInt;

public class LootPool implements Validatable {
    public static final Codec<LootPool> CODEC = RecordCodecBuilder.create(
        i -> i.group(
                LootPoolEntries.CODEC.listOf().fieldOf("entries").forGetter(p -> p.entries),
                LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(p -> p.conditions),
                LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter(p -> p.functions),
                NumberProviders.CODEC.fieldOf("rolls").forGetter(p -> p.rolls),
                NumberProviders.CODEC.optionalFieldOf("bonus_rolls", ConstantValue.exactly(0.0F)).forGetter(p -> p.bonusRolls),
                Codec.STRING.optionalFieldOf("name").forGetter(pool -> java.util.Optional.ofNullable(pool.name).filter(name -> !name.startsWith("custom#")))
            )
            .apply(i, LootPool::new)
    );
    private final List<LootPoolEntryContainer> entries;
    private final List<LootItemCondition> conditions;
    private final Predicate<LootContext> compositeCondition;
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    private NumberProvider rolls;
    private NumberProvider bonusRolls;

    private LootPool(
        List<LootPoolEntryContainer> entries,
        List<LootItemCondition> conditions,
        List<LootItemFunction> functions,
        NumberProvider rolls,
        NumberProvider bonusRolls,
        java.util.Optional<String> name
    ) {
        this.entries = entries;
        this.conditions = conditions;
        this.compositeCondition = Util.allOf(conditions);
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
        this.rolls = rolls;
        this.bonusRolls = bonusRolls;
        this.name = name.orElse(null);
    }

    private void addRandomItem(Consumer<ItemStack> result, LootContext context) {
        RandomSource random = context.getRandom();
        List<LootPoolEntry> validEntries = Lists.newArrayList();
        MutableInt totalWeight = new MutableInt();

        for (LootPoolEntryContainer entry : this.entries) {
            entry.expand(context, e -> {
                int weight = e.getWeight(context.getLuck());
                if (weight > 0) {
                    validEntries.add(e);
                    totalWeight.add(weight);
                }
            });
        }

        int entryCount = validEntries.size();
        if (totalWeight.intValue() != 0 && entryCount != 0) {
            if (entryCount == 1) {
                validEntries.get(0).createItemStack(result, context);
            } else {
                int index = random.nextInt(totalWeight.intValue());

                for (LootPoolEntry entry : validEntries) {
                    index -= entry.getWeight(context.getLuck());
                    if (index < 0) {
                        entry.createItemStack(result, context);
                        return;
                    }
                }
            }
        }
    }

    public void addRandomItems(Consumer<ItemStack> result, LootContext context) {
        if (this.compositeCondition.test(context)) {
            Consumer<ItemStack> decoratedConsumer = LootItemFunction.decorate(this.compositeFunction, result, context);
            int count = this.rolls.getInt(context) + Mth.floor(this.bonusRolls.getFloat(context) * context.getLuck());

            for (int i = 0; i < count; i++) {
                this.addRandomItem(decoratedConsumer, context);
            }
        }
    }

    @Override
    public void validate(ValidationContext output) {
        Validatable.validate(output, "conditions", this.conditions);
        Validatable.validate(output, "functions", this.functions);
        Validatable.validate(output, "entries", this.entries);
        Validatable.validate(output, "rolls", this.rolls);
        Validatable.validate(output, "bonus_rolls", this.bonusRolls);
    }

    // Neo: Implement LootPool freezing to prevent manipulation outside of Neo APIs
    private boolean isFrozen = false;

    public void freeze() {
        this.isFrozen = true;
    }

    public boolean isFrozen() {
        return this.isFrozen;
    }

    private void checkFrozen() {
        if (this.isFrozen())
            throw new RuntimeException("Attempted to modify LootPool after being frozen!");
    }

    // Neo: Apply names for LootPools to allow easier targeting specific pools
    @org.jspecify.annotations.Nullable
    private String name;

    @org.jspecify.annotations.Nullable
    public String getName() {
        return this.name;
    }

    void setName(final String name) {
        if (this.name != null) {
            throw new UnsupportedOperationException("Cannot change the name of a pool when it has a name set!");
        }
        this.name = name;
    }

    // Neo: Add getters and settings for changing the rolls for this pool
    public NumberProvider getRolls() {
        return this.rolls;
    }

    public NumberProvider getBonusRolls() {
        return this.bonusRolls;
    }

    public void setRolls (NumberProvider v) {
        checkFrozen();
        this.rolls = v;
    }

    public void setBonusRolls (NumberProvider v) {
        checkFrozen();
        this.bonusRolls = v;
    }

    public static LootPool.Builder lootPool() {
        return new LootPool.Builder();
    }

    public static class Builder implements FunctionUserBuilder<LootPool.Builder>, ConditionUserBuilder<LootPool.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemCondition> conditions = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
        private NumberProvider rolls = ConstantValue.exactly(1.0F);
        private NumberProvider bonusRolls = ConstantValue.exactly(0.0F);
        @org.jspecify.annotations.Nullable
        private String name;

        public LootPool.Builder setRolls(NumberProvider rolls) {
            this.rolls = rolls;
            return this;
        }

        public LootPool.Builder unwrap() {
            return this;
        }

        public LootPool.Builder setBonusRolls(NumberProvider bonusRolls) {
            this.bonusRolls = bonusRolls;
            return this;
        }

        public LootPool.Builder add(LootPoolEntryContainer.Builder<?> entry) {
            this.entries.add(entry.build());
            return this;
        }

        public LootPool.Builder addAll(List<? extends LootPoolSingletonContainer.Builder<?>> entries) {
            for (LootPoolEntryContainer.Builder<?> entry : entries) {
                this.add(entry);
            }

            return this;
        }

        public LootPool.Builder when(LootItemCondition.Builder condition) {
            this.conditions.add(condition.build());
            return this;
        }

        public LootPool.Builder apply(LootItemFunction.Builder function) {
            this.functions.add(function.build());
            return this;
        }

        public LootPool.Builder name(String name) {
            this.name = name;
            return this;
        }

        public LootPool build() {
            return new LootPool(this.entries.build(), this.conditions.build(), this.functions.build(), this.rolls, this.bonusRolls, java.util.Optional.ofNullable(this.name));
        }
    }
}
