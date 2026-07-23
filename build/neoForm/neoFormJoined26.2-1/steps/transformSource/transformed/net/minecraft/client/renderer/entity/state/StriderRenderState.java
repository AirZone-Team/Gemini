package net.minecraft.client.renderer.entity.state;

import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StriderRenderState extends LivingEntityRenderState {
    public ItemStack saddle = ItemStack.EMPTY;
    public boolean isSuffocating;
    public boolean isRidden;
}
