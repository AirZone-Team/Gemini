package net.minecraft.data.loot;

import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import net.neoforged.neoforge.common.extensions.LootTableSubProviderExtension;

@FunctionalInterface
public interface LootTableSubProvider extends LootTableSubProviderExtension {
    void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output);
}
