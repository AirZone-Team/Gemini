package net.minecraft.client.renderer.entity.state;

import net.minecraft.world.entity.animal.equine.Markings;
import net.minecraft.world.entity.animal.equine.Variant;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class HorseRenderState extends EquineRenderState {
    public Variant variant = Variant.WHITE;
    public Markings markings = Markings.NONE;
}
