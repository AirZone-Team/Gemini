package net.minecraft.client.model.geom.builders;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MaterialDefinition {
    final int xTexSize;
    final int yTexSize;

    public MaterialDefinition(int xTexSize, int yTexSize) {
        this.xTexSize = xTexSize;
        this.yTexSize = yTexSize;
    }
}
