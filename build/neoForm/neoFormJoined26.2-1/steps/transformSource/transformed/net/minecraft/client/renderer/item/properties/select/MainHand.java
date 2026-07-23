package net.minecraft.client.renderer.item.properties.select;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record MainHand() implements SelectItemModelProperty<HumanoidArm> {
    public static final Codec<HumanoidArm> VALUE_CODEC = HumanoidArm.CODEC;
    public static final SelectItemModelProperty.Type<MainHand, HumanoidArm> TYPE = SelectItemModelProperty.Type.create(
        MapCodec.unit(new MainHand()), VALUE_CODEC
    );

    public @Nullable HumanoidArm get(
        ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext
    ) {
        return owner == null ? null : owner.getMainArm();
    }

    @Override
    public SelectItemModelProperty.Type<MainHand, HumanoidArm> type() {
        return TYPE;
    }

    @Override
    public Codec<HumanoidArm> valueCodec() {
        return VALUE_CODEC;
    }
}
