package net.minecraft.client.renderer.block.dispatch;

import java.util.List;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.client.extensions.BlockStateModelPartExtension;

@OnlyIn(Dist.CLIENT)
public interface BlockStateModelPart extends BlockStateModelPartExtension {
    List<BakedQuad> getQuads(@Nullable Direction direction);

    /// @deprecated Neo: Use [#ambientOcclusion()] instead.
    @Deprecated
    boolean useAmbientOcclusion();

    Material.Baked particleMaterial();

    @BakedQuad.MaterialFlags int materialFlags();

    interface Unbaked extends ResolvableModel {
        BlockStateModelPart bake(ModelBaker modelBakery);
    }
}
