package net.minecraft.client.resources.model;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3fc;

import net.neoforged.neoforge.client.extensions.ModelBakerExtension;
import net.neoforged.neoforge.client.extensions.ModelBakerInternerExtension;

@OnlyIn(Dist.CLIENT)
public interface ModelBaker extends ModelBakerExtension {
    ResolvedModel getModel(Identifier location);

    BlockStateModelPart missingBlockModelPart();

    MaterialBaker materials();

    ModelBaker.Interner interner();

    <T> T compute(ModelBaker.SharedOperationKey<T> key);

    interface Interner extends ModelBakerInternerExtension {
        Vector3fc vector(Vector3fc vector);

        BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material);
    }

    @FunctionalInterface
    interface SharedOperationKey<T> {
        T compute(ModelBaker modelBakery);
    }
}
