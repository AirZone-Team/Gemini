package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record HasComponent(DataComponentType<?> componentType, boolean ignoreDefault) implements ConditionalItemModelProperty {
    public static final MapCodec<HasComponent> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec().fieldOf("component").forGetter(HasComponent::componentType),
                Codec.BOOL.optionalFieldOf("ignore_default", false).forGetter(HasComponent::ignoreDefault)
            )
            .apply(i, HasComponent::new)
    );

    @Override
    public boolean get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
        return this.ignoreDefault ? itemStack.hasNonDefault(this.componentType) : itemStack.has(this.componentType);
    }

    @Override
    public MapCodec<HasComponent> type() {
        return MAP_CODEC;
    }
}
