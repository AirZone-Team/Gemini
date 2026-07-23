package net.minecraft.client.renderer.entity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MinecartTntRenderState extends MinecartRenderState {
    public float fuseRemainingInTicks = -1.0F;
}
