package net.minecraft.client.renderer.entity.state;

import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FishingHookRenderState extends EntityRenderState {
    public Vec3 lineOriginOffset = Vec3.ZERO;
}
