package net.minecraft.client.renderer.entity.state;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.world.entity.Crackiness;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IronGolemRenderState extends LivingEntityRenderState {
    public float attackTicksRemaining;
    public int offerFlowerTick;
    public final BlockModelRenderState flowerBlock = new BlockModelRenderState();
    public Crackiness.Level crackiness = Crackiness.Level.NONE;
}
