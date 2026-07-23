package net.minecraft.client.renderer.entity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FelineRenderState extends LivingEntityRenderState {
    public boolean isCrouching;
    public boolean isSprinting;
    public boolean isSitting;
    public float lieDownAmount;
    public float lieDownAmountTail;
    public float relaxStateOneAmount;
}
