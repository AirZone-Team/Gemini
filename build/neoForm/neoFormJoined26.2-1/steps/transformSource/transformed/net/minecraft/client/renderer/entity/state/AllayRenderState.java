package net.minecraft.client.renderer.entity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AllayRenderState extends ArmedEntityRenderState {
    public boolean isDancing;
    public boolean isSpinning;
    public float spinningProgress;
    public float holdingAnimationProgress;
}
