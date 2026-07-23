package net.minecraft.client.renderer.entity.state;

import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ParrotRenderState extends LivingEntityRenderState {
    public Parrot.Variant variant = Parrot.Variant.RED_BLUE;
    public float flapAngle;
    public ParrotModel.Pose pose = ParrotModel.Pose.FLYING;
}
