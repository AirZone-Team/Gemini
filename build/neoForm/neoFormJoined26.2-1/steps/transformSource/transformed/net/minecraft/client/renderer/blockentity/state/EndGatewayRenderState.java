package net.minecraft.client.renderer.blockentity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EndGatewayRenderState extends EndPortalRenderState {
    public int height;
    public float scale;
    public int color;
    public float animationTime;
}
