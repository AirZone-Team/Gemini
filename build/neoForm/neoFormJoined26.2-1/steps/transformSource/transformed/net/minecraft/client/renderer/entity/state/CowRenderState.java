package net.minecraft.client.renderer.entity.state;

import net.minecraft.world.entity.animal.cow.CowVariant;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class CowRenderState extends LivingEntityRenderState {
    public @Nullable CowVariant variant;
}
