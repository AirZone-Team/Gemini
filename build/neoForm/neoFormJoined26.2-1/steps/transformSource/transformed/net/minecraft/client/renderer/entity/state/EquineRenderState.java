package net.minecraft.client.renderer.entity.state;

import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EquineRenderState extends LivingEntityRenderState {
    public ItemStack saddle = ItemStack.EMPTY;
    public ItemStack bodyArmorItem = ItemStack.EMPTY;
    public boolean isRidden;
    public boolean animateTail;
    public float eatAnimation;
    public float standAnimation;
    public float feedingAnimation;
}
