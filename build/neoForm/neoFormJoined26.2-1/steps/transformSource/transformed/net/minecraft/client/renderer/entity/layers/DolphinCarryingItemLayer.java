package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.dolphin.DolphinModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.DolphinRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DolphinCarryingItemLayer extends RenderLayer<DolphinRenderState, DolphinModel> {
    public DolphinCarryingItemLayer(RenderLayerParent<DolphinRenderState, DolphinModel> renderer) {
        super(renderer);
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, DolphinRenderState state, float yRot, float xRot) {
        ItemStackRenderState item = state.heldItem;
        if (!item.isEmpty()) {
            poseStack.pushPose();
            float y = 1.0F;
            float z = -1.0F;
            float angleXPercent = Mth.abs(state.xRot) / 60.0F;
            if (state.xRot < 0.0F) {
                poseStack.translate(0.0F, 1.0F - angleXPercent * 0.5F, -1.0F + angleXPercent * 0.5F);
            } else {
                poseStack.translate(0.0F, 1.0F + angleXPercent * 0.8F, -1.0F + angleXPercent * 0.2F);
            }

            item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
    }
}
