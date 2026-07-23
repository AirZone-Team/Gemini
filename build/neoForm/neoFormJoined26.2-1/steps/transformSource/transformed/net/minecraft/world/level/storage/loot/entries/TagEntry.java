package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class TagEntry extends LootPoolSingletonContainer {
    public static final MapCodec<TagEntry> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(TagKey.codec(Registries.ITEM).fieldOf("name").forGetter(e -> e.tag), Codec.BOOL.fieldOf("expand").forGetter(e -> e.expand))
            .and(singletonFields(i))
            .apply(i, TagEntry::new)
    );
    private final TagKey<Item> tag;
    private final boolean expand;

    private TagEntry(TagKey<Item> tag, boolean expand, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.tag = tag;
        this.expand = expand;
    }

    @Override
    public MapCodec<TagEntry> codec() {
        return MAP_CODEC;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> output, LootContext context) {
        BuiltInRegistries.ITEM.getTagOrEmpty(this.tag).forEach(item -> output.accept(new ItemStack((Holder<Item>)item)));
    }

    private boolean expandTag(LootContext context, Consumer<LootPoolEntry> output) {
        if (!this.canRun(context)) {
            return false;
        }

        for (final Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(this.tag)) {
            output.accept(new LootPoolSingletonContainer.EntryBase() {
                @Override
                public void createItemStack(Consumer<ItemStack> output, LootContext contextx) {
                    output.accept(new ItemStack(item));
                }
            });
        }

        return true;
    }

    @Override
    public boolean expand(LootContext context, Consumer<LootPoolEntry> output) {
        return this.expand ? this.expandTag(context, output) : super.expand(context, output);
    }

    public static LootPoolSingletonContainer.Builder<?> tagContents(TagKey<Item> tag) {
        return simpleBuilder((weight, quality, conditions, functions) -> new TagEntry(tag, false, weight, quality, conditions, functions));
    }

    public static LootPoolSingletonContainer.Builder<?> expandTag(TagKey<Item> tag) {
        return simpleBuilder((weight, quality, conditions, functions) -> new TagEntry(tag, true, weight, quality, conditions, functions));
    }
}
