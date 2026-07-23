package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record IsCarried() implements ConditionalItemModelProperty {
    public static final MapCodec<IsCarried> MAP_CODEC = MapCodec.unit(new IsCarried());

    @Override
    public boolean get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
        return owner instanceof LocalPlayer player && player.containerMenu.getCarried() == itemStack;
    }

    @Override
    public MapCodec<IsCarried> type() {
        return MAP_CODEC;
    }
}
