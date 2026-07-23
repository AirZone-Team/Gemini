package net.minecraft.client.renderer.entity.state;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class UndeadRenderState extends HumanoidRenderState {
    @Override
    public ItemStack getUseItemStackForArm(HumanoidArm arm) {
        return this.getMainHandItemStack();
    }
}
