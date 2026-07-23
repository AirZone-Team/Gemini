package net.minecraft.client.renderer.block.model;

import com.mojang.math.Transformation;
import java.util.Optional;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.properties.conditional.ConditionalBlockModelProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4fc;

@OnlyIn(Dist.CLIENT)
public class ConditionalBlockModel implements BlockModel {
    private final ConditionalBlockModelProperty property;
    private final BlockModel onTrue;
    private final BlockModel onFalse;

    public ConditionalBlockModel(ConditionalBlockModelProperty property, BlockModel onTrue, BlockModel onFalse) {
        this.property = property;
        this.onTrue = onTrue;
        this.onFalse = onFalse;
    }

    @Override
    public void update(BlockModelRenderState output, BlockState blockState, BlockDisplayContext displayContext, long seed) {
        (this.property.get(blockState) ? this.onTrue : this.onFalse).update(output, blockState, displayContext, seed);
    }

    public record Unbaked(
        Optional<Transformation> transformation, ConditionalBlockModelProperty property, BlockModel.Unbaked onTrue, BlockModel.Unbaked onFalse
    ) implements BlockModel.Unbaked {
        @Override
        public BlockModel bake(BlockModel.BakingContext context, Matrix4fc transformation) {
            Matrix4fc childTransform = Transformation.compose(transformation, this.transformation);
            return new ConditionalBlockModel(this.property, this.onTrue.bake(context, childTransform), this.onFalse.bake(context, childTransform));
        }
    }
}
