package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.animal.chicken.AdultChickenModel;
import net.minecraft.client.model.animal.chicken.BabyChickenModel;
import net.minecraft.client.model.animal.chicken.ChickenModel;
import net.minecraft.client.model.animal.chicken.ColdChickenModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ChickenRenderer extends MobRenderer<Chicken, ChickenRenderState, ChickenModel> {
    private final Map<ChickenVariant.ModelType, AdultAndBabyModelPair<ChickenModel>> models;

    public ChickenRenderer(EntityRendererProvider.Context context) {
        super(context, new AdultChickenModel(context.bakeLayer(ModelLayers.CHICKEN)), 0.3F);
        this.models = bakeModels(context);
    }

    private static Map<ChickenVariant.ModelType, AdultAndBabyModelPair<ChickenModel>> bakeModels(EntityRendererProvider.Context context) {
        return Maps.newEnumMap(
            Map.of(
                ChickenVariant.ModelType.NORMAL,
                new AdultAndBabyModelPair<>(
                    new AdultChickenModel(context.bakeLayer(ModelLayers.CHICKEN)), new BabyChickenModel(context.bakeLayer(ModelLayers.CHICKEN_BABY))
                ),
                ChickenVariant.ModelType.COLD,
                new AdultAndBabyModelPair<>(
                    new ColdChickenModel(context.bakeLayer(ModelLayers.COLD_CHICKEN)), new BabyChickenModel(context.bakeLayer(ModelLayers.CHICKEN_BABY))
                )
            )
        );
    }

    public void submit(ChickenRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.variant != null) {
            this.model = this.models.get(state.variant.modelAndTexture().model()).getModel(state.isBaby);
            super.submit(state, poseStack, submitNodeCollector, camera);
        }
    }

    public Identifier getTextureLocation(ChickenRenderState state) {
        return state.variant == null
            ? MissingTextureAtlasSprite.getLocation()
            : (state.isBaby ? state.variant.babyTexture().texturePath() : state.variant.modelAndTexture().asset().texturePath());
    }

    public ChickenRenderState createRenderState() {
        return new ChickenRenderState();
    }

    public void extractRenderState(Chicken entity, ChickenRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.flap = Mth.lerp(partialTicks, entity.oFlap, entity.flap);
        state.flapSpeed = Mth.lerp(partialTicks, entity.oFlapSpeed, entity.flapSpeed);
        state.variant = entity.getVariant().value();
    }
}
