package net.minecraft.client.renderer.blockentity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ConduitRenderState extends BlockEntityRenderState {
    public float animTime;
    public boolean isActive;
    public float activeRotation;
    public int animationPhase;
    public boolean isHunting;
}
