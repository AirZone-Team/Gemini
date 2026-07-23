package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractCubeMobRenderer<T extends AbstractCubeMob, S extends SlimeRenderState, M extends EntityModel<? super S>>
    extends MobRenderer<T, S, M> {
    public AbstractCubeMobRenderer(EntityRendererProvider.Context context, M model) {
        super(context, model, 0.25F);
    }

    protected float getShadowRadius(SlimeRenderState state) {
        return state.size * 0.25F;
    }

    protected void scale(S state, PoseStack poseStack) {
        super.scale(state, poseStack);
        this.applySizeAndSquish(state, poseStack);
    }

    protected void downscaleSlightly(PoseStack poseStack) {
        float s = 0.999F;
        poseStack.scale(0.999F, 0.999F, 0.999F);
        poseStack.translate(0.0F, 0.001F, 0.0F);
    }

    protected void applySizeAndSquish(S state, PoseStack poseStack) {
        float size = state.size;
        float ss = state.squish / (size * 0.5F + 1.0F);
        float w = 1.0F / (ss + 1.0F);
        poseStack.scale(w * size, 1.0F / w * size, w * size);
    }

    public void extractRenderState(T entity, S state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.squish = Mth.lerp(partialTicks, entity.oSquish, entity.squish);
        state.size = entity.getSize();
    }
}
