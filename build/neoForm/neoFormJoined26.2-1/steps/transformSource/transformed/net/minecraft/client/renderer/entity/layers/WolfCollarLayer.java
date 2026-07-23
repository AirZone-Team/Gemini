package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WolfCollarLayer extends RenderLayer<WolfRenderState, WolfModel> {
    private static final Identifier WOLF_COLLAR_LOCATION = Identifier.withDefaultNamespace("textures/entity/wolf/wolf_collar.png");
    private static final Identifier WOLF_BABY_COLLAR_LOCATION = Identifier.withDefaultNamespace("textures/entity/wolf/wolf_collar_baby.png");

    public WolfCollarLayer(RenderLayerParent<WolfRenderState, WolfModel> renderer) {
        super(renderer);
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, WolfRenderState state, float yRot, float xRot) {
        DyeColor collarColor = state.collarColor;
        if (collarColor != null && !state.isInvisible) {
            int color = collarColor.getTextureDiffuseColor();
            Identifier collarLocation = state.isBaby ? WOLF_BABY_COLLAR_LOCATION : WOLF_COLLAR_LOCATION;
            submitNodeCollector.order(1)
                .submitModel(
                    this.getParentModel(),
                    state,
                    poseStack,
                    RenderTypes.entityCutout(collarLocation),
                    lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    color,
                    null,
                    state.outlineColor,
                    null
                );
        }
    }
}
