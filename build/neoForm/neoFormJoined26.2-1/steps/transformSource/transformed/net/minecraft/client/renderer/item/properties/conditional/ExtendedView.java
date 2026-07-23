package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record ExtendedView() implements ConditionalItemModelProperty {
    public static final MapCodec<ExtendedView> MAP_CODEC = MapCodec.unit(new ExtendedView());

    @Override
    public boolean get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.GUI && Minecraft.getInstance().hasShiftDown();
    }

    @Override
    public MapCodec<ExtendedView> type() {
        return MAP_CODEC;
    }
}
