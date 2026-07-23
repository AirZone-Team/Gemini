package net.minecraft.client.renderer.item.properties.numeric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record Damage(boolean normalize) implements RangeSelectItemModelProperty {
    public static final MapCodec<Damage> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(Codec.BOOL.optionalFieldOf("normalize", true).forGetter(Damage::normalize)).apply(i, Damage::new)
    );

    @Override
    public float get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        float damage = itemStack.getDamageValue();
        float maxDamage = itemStack.getMaxDamage();
        return this.normalize ? Mth.clamp(damage / maxDamage, 0.0F, 1.0F) : Mth.clamp(damage, 0.0F, maxDamage);
    }

    @Override
    public MapCodec<Damage> type() {
        return MAP_CODEC;
    }
}
