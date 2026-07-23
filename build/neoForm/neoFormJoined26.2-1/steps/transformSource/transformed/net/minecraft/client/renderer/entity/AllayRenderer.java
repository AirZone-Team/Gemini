package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.animal.allay.AllayModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AllayRenderState;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.allay.Allay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AllayRenderer extends MobRenderer<Allay, AllayRenderState, AllayModel> {
    private static final Identifier ALLAY_TEXTURE = Identifier.withDefaultNamespace("textures/entity/allay/allay.png");

    public AllayRenderer(EntityRendererProvider.Context context) {
        super(context, new AllayModel(context.bakeLayer(ModelLayers.ALLAY)), 0.4F);
        this.addLayer(new ItemInHandLayer<>(this));
    }

    public Identifier getTextureLocation(AllayRenderState state) {
        return ALLAY_TEXTURE;
    }

    public AllayRenderState createRenderState() {
        return new AllayRenderState();
    }

    public void extractRenderState(Allay entity, AllayRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelResolver, partialTicks);
        state.isDancing = entity.isDancing();
        state.isSpinning = entity.isSpinning();
        state.spinningProgress = entity.getSpinningProgress(partialTicks);
        state.holdingAnimationProgress = entity.getHoldingItemAnimationProgress(partialTicks);
    }

    protected int getBlockLightLevel(Allay entity, BlockPos blockPos) {
        return 15;
    }
}
