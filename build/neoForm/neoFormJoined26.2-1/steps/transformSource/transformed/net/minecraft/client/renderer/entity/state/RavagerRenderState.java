package net.minecraft.client.renderer.entity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RavagerRenderState extends LivingEntityRenderState {
    public float stunnedTicksRemaining;
    public float attackTicksRemaining;
    public float roarAnimation;
}
