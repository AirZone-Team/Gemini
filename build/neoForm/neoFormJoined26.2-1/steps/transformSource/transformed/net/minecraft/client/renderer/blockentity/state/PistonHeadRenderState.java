package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class PistonHeadRenderState extends BlockEntityRenderState {
    public @Nullable MovingBlockRenderState block;
    public @Nullable MovingBlockRenderState base;
    public float xOffset;
    public float yOffset;
    public float zOffset;
}
