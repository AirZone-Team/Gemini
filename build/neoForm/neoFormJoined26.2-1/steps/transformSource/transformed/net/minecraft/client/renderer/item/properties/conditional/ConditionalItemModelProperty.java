package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.MapCodec;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ConditionalItemModelProperty extends ItemModelPropertyTest {
    MapCodec<? extends ConditionalItemModelProperty> type();
}
