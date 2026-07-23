package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EvokerRenderer<T extends SpellcasterIllager> extends IllagerRenderer<T, EvokerRenderState> {
    private static final Identifier EVOKER_ILLAGER = Identifier.withDefaultNamespace("textures/entity/illager/evoker.png");

    public EvokerRenderer(EntityRendererProvider.Context context) {
        super(context, new IllagerModel<>(context.bakeLayer(ModelLayers.EVOKER)), 0.5F);
        this.addLayer(new ItemInHandLayer<EvokerRenderState, IllagerModel<EvokerRenderState>>(this) {
            public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, EvokerRenderState state, float yRot, float xRot) {
                if (state.isCastingSpell) {
                    super.submit(poseStack, submitNodeCollector, lightCoords, state, yRot, xRot);
                }
            }
        });
    }

    public Identifier getTextureLocation(EvokerRenderState state) {
        return EVOKER_ILLAGER;
    }

    public EvokerRenderState createRenderState() {
        return new EvokerRenderState();
    }

    public void extractRenderState(T entity, EvokerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isCastingSpell = entity.isCastingSpell();
    }
}
