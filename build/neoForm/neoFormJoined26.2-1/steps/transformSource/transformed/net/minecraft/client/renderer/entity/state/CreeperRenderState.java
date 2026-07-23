package net.minecraft.client.renderer.entity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CreeperRenderState extends LivingEntityRenderState {
    public float swelling;
    public boolean isPowered;
}
