package net.minecraft.client.data.models.model;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ModelLocationUtils {
    @Deprecated
    public static Identifier decorateBlockModelLocation(String id) {
        // Neo: Use Identifier.parse to support modded paths
        return Identifier.parse(id).withPrefix("block/");
    }

    public static Identifier decorateItemModelLocation(String id) {
        // Neo: Use Identifier.parse to support modded paths
        return Identifier.parse(id).withPrefix("item/");
    }

    public static Identifier getModelLocation(Block block, String suffix) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
        return key.withPath(path -> "block/" + path + suffix);
    }

    public static Identifier getModelLocation(Block block) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
        return key.withPrefix("block/");
    }

    public static Identifier getModelLocation(Item item) {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        return key.withPrefix("item/");
    }

    public static Identifier getModelLocation(Item item, String suffix) {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        return key.withPath(path -> "item/" + path + suffix);
    }
}
