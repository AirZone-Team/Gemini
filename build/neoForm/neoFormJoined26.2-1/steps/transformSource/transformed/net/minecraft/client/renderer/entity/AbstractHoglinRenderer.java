package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.monster.hoglin.BabyHoglinModel;
import net.minecraft.client.model.monster.hoglin.HoglinModel;
import net.minecraft.client.renderer.entity.state.HoglinRenderState;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.hoglin.HoglinBase;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractHoglinRenderer<T extends Mob & HoglinBase> extends AgeableMobRenderer<T, HoglinRenderState, HoglinModel> {
    public AbstractHoglinRenderer(EntityRendererProvider.Context context, ModelLayerLocation adultLayer, ModelLayerLocation babyLayer, float shadow) {
        super(context, new HoglinModel(context.bakeLayer(adultLayer)), new BabyHoglinModel(context.bakeLayer(babyLayer)), shadow);
    }

    public HoglinRenderState createRenderState() {
        return new HoglinRenderState();
    }

    public void extractRenderState(T entity, HoglinRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.attackAnimationRemainingTicks = entity.getAttackAnimationRemainingTicks();
    }
}
