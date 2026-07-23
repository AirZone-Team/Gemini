package net.minecraft.client.model.animal.feline;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BabyOcelotModel extends BabyFelineModel<FelineRenderState> {
    public BabyOcelotModel(ModelPart root) {
        super(root);
    }
}
