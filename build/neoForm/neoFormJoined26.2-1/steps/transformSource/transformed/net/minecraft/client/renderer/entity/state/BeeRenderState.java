package net.minecraft.client.renderer.entity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BeeRenderState extends LivingEntityRenderState {
    public float rollAmount;
    public boolean hasStinger = true;
    public boolean isOnGround;
    public boolean isAngry;
    public boolean hasNectar;
}
