package net.minecraft.client.renderer.entity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TurtleRenderState extends LivingEntityRenderState {
    public boolean isOnLand;
    public boolean isLayingEgg;
    public boolean hasEgg;
}
