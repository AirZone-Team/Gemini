package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.GuardianRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ElderGuardianRenderer extends GuardianRenderer {
    public static final Identifier GUARDIAN_ELDER_LOCATION = Identifier.withDefaultNamespace("textures/entity/guardian/guardian_elder.png");

    public ElderGuardianRenderer(EntityRendererProvider.Context context) {
        super(context, 1.2F, ModelLayers.ELDER_GUARDIAN);
    }

    @Override
    public Identifier getTextureLocation(GuardianRenderState state) {
        return GUARDIAN_ELDER_LOCATION;
    }
}
