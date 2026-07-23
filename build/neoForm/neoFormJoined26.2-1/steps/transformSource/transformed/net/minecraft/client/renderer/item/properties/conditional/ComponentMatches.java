package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record ComponentMatches(DataComponentPredicate.Single<?> predicate) implements ConditionalItemModelProperty {
    public static final MapCodec<ComponentMatches> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(DataComponentPredicate.singleCodec("predicate").forGetter(ComponentMatches::predicate)).apply(i, ComponentMatches::new)
    );

    @Override
    public boolean get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
        return this.predicate.predicate().matches(itemStack);
    }

    @Override
    public MapCodec<ComponentMatches> type() {
        return MAP_CODEC;
    }
}
