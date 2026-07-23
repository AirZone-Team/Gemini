package net.minecraft.client.model.monster.zombie;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ZombieModel<S extends ZombieRenderState> extends AbstractZombieModel<S> {
    public ZombieModel(ModelPart root) {
        super(root);
    }
}
