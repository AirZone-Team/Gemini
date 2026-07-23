package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.ambient.BatModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ambient.Bat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BatRenderer extends MobRenderer<Bat, BatRenderState, BatModel> {
    private static final Identifier BAT_LOCATION = Identifier.withDefaultNamespace("textures/entity/bat/bat.png");

    public BatRenderer(EntityRendererProvider.Context context) {
        super(context, new BatModel(context.bakeLayer(ModelLayers.BAT)), 0.25F);
    }

    public Identifier getTextureLocation(BatRenderState state) {
        return BAT_LOCATION;
    }

    public BatRenderState createRenderState() {
        return new BatRenderState();
    }

    public void extractRenderState(Bat entity, BatRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isResting = entity.isResting();
        state.flyAnimationState.copyFrom(entity.flyAnimationState);
        state.restAnimationState.copyFrom(entity.restAnimationState);
    }
}
