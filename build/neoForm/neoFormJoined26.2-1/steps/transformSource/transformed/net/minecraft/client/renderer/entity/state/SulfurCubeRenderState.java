package net.minecraft.client.renderer.entity.state;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SulfurCubeRenderState extends SlimeRenderState {
    public BlockModelRenderState containedBlock = new BlockModelRenderState();
    public float fuseRemainingTicks;
}
