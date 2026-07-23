package net.minecraft.client.renderer.entity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SquidRenderState extends LivingEntityRenderState {
    public float tentacleAngle;
    public float xBodyRot;
    public float zBodyRot;
}
