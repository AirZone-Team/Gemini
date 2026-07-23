package net.minecraft.client.renderer.block.model.properties.conditional;

import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ConditionalBlockModelProperty {
    boolean get(BlockState state);
}
