package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.HoglinRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class HoglinRenderer extends AbstractHoglinRenderer<Hoglin> {
    private static final Identifier HOGLIN_LOCATION = Identifier.withDefaultNamespace("textures/entity/hoglin/hoglin.png");
    private static final Identifier BABY_HOGLIN_LOCATION = Identifier.withDefaultNamespace("textures/entity/hoglin/hoglin_baby.png");

    public HoglinRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.HOGLIN, ModelLayers.HOGLIN_BABY, 0.7F);
    }

    public Identifier getTextureLocation(HoglinRenderState state) {
        return state.isBaby ? BABY_HOGLIN_LOCATION : HOGLIN_LOCATION;
    }

    public void extractRenderState(Hoglin entity, HoglinRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isConverting = entity.isConverting();
    }

    protected boolean isShaking(HoglinRenderState state) {
        return super.isShaking(state) || state.isConverting;
    }
}
