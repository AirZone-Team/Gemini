package net.minecraft.client.renderer.entity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SkeletonRenderState extends HumanoidRenderState {
    public boolean isAggressive;
    public boolean isShaking;
    public boolean isHoldingBow;
}
