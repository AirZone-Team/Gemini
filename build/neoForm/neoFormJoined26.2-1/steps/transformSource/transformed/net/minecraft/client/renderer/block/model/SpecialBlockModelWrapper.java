package net.minecraft.client.renderer.block.model;

import com.mojang.math.Transformation;
import java.util.Optional;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4fc;

@OnlyIn(Dist.CLIENT)
public class SpecialBlockModelWrapper<T> implements BlockModel {
    private final SpecialModelRenderer<T> specialRenderer;
    private final Matrix4fc transformation;

    public SpecialBlockModelWrapper(SpecialModelRenderer<T> specialRenderer, Matrix4fc transformation) {
        this.specialRenderer = specialRenderer;
        this.transformation = transformation;
    }

    @Override
    public void update(BlockModelRenderState output, BlockState blockState, BlockDisplayContext displayContext, long seed) {
        output.setupSpecialModel(this.specialRenderer, this.transformation);
    }

    public record Unbaked<T>(SpecialModelRenderer.Unbaked<T> model, Optional<Transformation> transformation) implements BlockModel.Unbaked {
        @Override
        public BlockModel bake(BlockModel.BakingContext context, Matrix4fc transformation) {
            SpecialModelRenderer<T> baked = this.model.bake(context);
            if (baked == null) {
                return EmptyBlockModel.INSTANCE;
            }

            Matrix4fc modelTransform = Transformation.compose(transformation, this.transformation);
            return new SpecialBlockModelWrapper<>(baked, modelTransform);
        }
    }
}
