package net.minecraft.client.renderer.item.properties.select;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record TrimMaterialProperty() implements SelectItemModelProperty<ResourceKey<TrimMaterial>> {
    public static final Codec<ResourceKey<TrimMaterial>> VALUE_CODEC = ResourceKey.codec(Registries.TRIM_MATERIAL);
    public static final SelectItemModelProperty.Type<TrimMaterialProperty, ResourceKey<TrimMaterial>> TYPE = SelectItemModelProperty.Type.create(
        MapCodec.unit(new TrimMaterialProperty()), VALUE_CODEC
    );

    public @Nullable ResourceKey<TrimMaterial> get(
        ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext
    ) {
        ArmorTrim trim = itemStack.get(DataComponents.TRIM);
        return trim == null ? null : trim.material().unwrapKey().orElse(null);
    }

    @Override
    public SelectItemModelProperty.Type<TrimMaterialProperty, ResourceKey<TrimMaterial>> type() {
        return TYPE;
    }

    @Override
    public Codec<ResourceKey<TrimMaterial>> valueCodec() {
        return VALUE_CODEC;
    }
}
