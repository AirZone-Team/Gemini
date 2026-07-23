package net.minecraft.client.renderer.entity.state;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ThrownTridentRenderState extends EntityRenderState {
    public float xRot;
    public float yRot;
    public boolean isFoil;
}
