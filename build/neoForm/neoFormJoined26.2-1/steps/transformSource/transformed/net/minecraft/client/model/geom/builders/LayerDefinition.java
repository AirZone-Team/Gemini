package net.minecraft.client.model.geom.builders;

import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LayerDefinition {
    private final MeshDefinition mesh;
    private final MaterialDefinition material;

    private LayerDefinition(MeshDefinition mesh, MaterialDefinition material) {
        this.mesh = mesh;
        this.material = material;
    }

    public LayerDefinition apply(MeshTransformer transformer) {
        return new LayerDefinition(transformer.apply(this.mesh), this.material);
    }

    public ModelPart bakeRoot() {
        return this.mesh.getRoot().bake(this.material.xTexSize, this.material.yTexSize);
    }

    public static LayerDefinition create(MeshDefinition mesh, int xTexSize, int yTexSize) {
        return new LayerDefinition(mesh, new MaterialDefinition(xTexSize, yTexSize));
    }
}
