package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LivingEntityEmissiveLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
    private final Function<S, Identifier> textureProvider;
    private final LivingEntityEmissiveLayer.AlphaFunction<S> alphaFunction;
    private final M model;
    private final Function<Identifier, RenderType> bufferProvider;
    private final boolean alwaysVisible;

    public LivingEntityEmissiveLayer(
        RenderLayerParent<S, M> renderer,
        Function<S, Identifier> textureProvider,
        LivingEntityEmissiveLayer.AlphaFunction<S> alphaFunction,
        M model,
        Function<Identifier, RenderType> bufferProvider,
        boolean alwaysVisible
    ) {
        super(renderer);
        this.textureProvider = textureProvider;
        this.alphaFunction = alphaFunction;
        this.model = model;
        this.bufferProvider = bufferProvider;
        this.alwaysVisible = alwaysVisible;
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state, float yRot, float xRot) {
        if (!state.isInvisible || this.alwaysVisible) {
            float alpha = this.alphaFunction.apply(state, state.ageInTicks);
            if (!(alpha <= 1.0E-5F)) {
                int color = ARGB.white(alpha);
                RenderType renderType = this.bufferProvider.apply(this.textureProvider.apply(state));
                submitNodeCollector.order(1)
                    .submitModel(
                        this.model,
                        state,
                        poseStack,
                        renderType,
                        lightCoords,
                        LivingEntityRenderer.getOverlayCoords(state, 0.0F),
                        color,
                        null,
                        state.outlineColor,
                        null
                    );
            }
        }
    }

    public interface AlphaFunction<S extends LivingEntityRenderState> {
        float apply(final S state, final float ageInTicks);
    }
}
