package net.minecraft.client.renderer.block.model;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4fc;

@OnlyIn(Dist.CLIENT)
public class EmptyBlockModel implements BlockModel {
    public static final BlockModel INSTANCE = new EmptyBlockModel();

    @Override
    public void update(BlockModelRenderState output, BlockState blockState, BlockDisplayContext displayContext, long seed) {
    }

    public record Unbaked() implements BlockModel.Unbaked {
        @Override
        public BlockModel bake(BlockModel.BakingContext context, Matrix4fc transformation) {
            return EmptyBlockModel.INSTANCE;
        }
    }
}
