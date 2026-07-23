package net.minecraft.client.renderer.entity;

import net.minecraft.client.renderer.entity.state.TippableArrowRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TippableArrowRenderer extends ArrowRenderer<Arrow, TippableArrowRenderState> {
    public static final Identifier NORMAL_ARROW_LOCATION = Identifier.withDefaultNamespace("textures/entity/projectiles/arrow.png");
    public static final Identifier TIPPED_ARROW_LOCATION = Identifier.withDefaultNamespace("textures/entity/projectiles/arrow_tipped.png");

    public TippableArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    protected Identifier getTextureLocation(TippableArrowRenderState state) {
        return state.isTipped ? TIPPED_ARROW_LOCATION : NORMAL_ARROW_LOCATION;
    }

    public TippableArrowRenderState createRenderState() {
        return new TippableArrowRenderState();
    }

    public void extractRenderState(Arrow entity, TippableArrowRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isTipped = entity.getColor() > 0;
    }
}
