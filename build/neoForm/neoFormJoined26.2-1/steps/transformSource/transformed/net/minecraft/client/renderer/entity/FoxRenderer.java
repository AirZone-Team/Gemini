package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.animal.fox.AdultFoxModel;
import net.minecraft.client.model.animal.fox.BabyFoxModel;
import net.minecraft.client.model.animal.fox.FoxModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.FoxHeldItemLayer;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.fox.Fox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FoxRenderer extends AgeableMobRenderer<Fox, FoxRenderState, FoxModel> {
    private static final Identifier RED_FOX_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox.png");
    private static final Identifier RED_FOX_SLEEP_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox_sleep.png");
    private static final Identifier SNOW_FOX_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox_snow.png");
    private static final Identifier SNOW_FOX_SLEEP_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox_snow_sleep.png");
    private static final Identifier BABY_RED_FOX_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox_baby.png");
    private static final Identifier BABY_RED_FOX_SLEEP_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox_sleep_baby.png");
    private static final Identifier BABY_SNOW_FOX_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox_snow_baby.png");
    private static final Identifier BABY_SNOW_FOX_SLEEP_TEXTURE = Identifier.withDefaultNamespace("textures/entity/fox/fox_snow_sleep_baby.png");
    private static final EnumMap<Fox.Variant, FoxRenderer.FoxTexturesByState> TEXTURES_BY_VARIANT = Maps.newEnumMap(
        Map.of(
            Fox.Variant.RED,
            new FoxRenderer.FoxTexturesByState(
                new FoxRenderer.FoxTexturesByAge(RED_FOX_TEXTURE, BABY_RED_FOX_TEXTURE),
                new FoxRenderer.FoxTexturesByAge(RED_FOX_SLEEP_TEXTURE, BABY_RED_FOX_SLEEP_TEXTURE)
            ),
            Fox.Variant.SNOW,
            new FoxRenderer.FoxTexturesByState(
                new FoxRenderer.FoxTexturesByAge(SNOW_FOX_TEXTURE, BABY_SNOW_FOX_TEXTURE),
                new FoxRenderer.FoxTexturesByAge(SNOW_FOX_SLEEP_TEXTURE, BABY_SNOW_FOX_SLEEP_TEXTURE)
            )
        )
    );

    public FoxRenderer(EntityRendererProvider.Context context) {
        super(context, new AdultFoxModel(context.bakeLayer(ModelLayers.FOX)), new BabyFoxModel(context.bakeLayer(ModelLayers.FOX_BABY)), 0.4F);
        this.addLayer(new FoxHeldItemLayer(this));
    }

    protected void setupRotations(FoxRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        if (state.isPouncing || state.isFaceplanted) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-state.xRot));
        }
    }

    public Identifier getTextureLocation(FoxRenderState state) {
        FoxRenderer.FoxTexturesByState byState = TEXTURES_BY_VARIANT.get(state.variant);
        if (byState == null) {
            return RED_FOX_TEXTURE;
        }

        FoxRenderer.FoxTexturesByAge ageTextures = state.isSleeping ? byState.sleeping() : byState.idle();
        return state.isBaby ? ageTextures.baby() : ageTextures.adult();
    }

    public FoxRenderState createRenderState() {
        return new FoxRenderState();
    }

    public void extractRenderState(Fox entity, FoxRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        HoldingEntityRenderState.extractHoldingEntityRenderState(entity, state, this.itemModelResolver);
        state.headRollAngle = entity.getHeadRollAngle(partialTicks);
        state.isCrouching = entity.isCrouching();
        state.crouchAmount = entity.getCrouchAmount(partialTicks);
        state.isSleeping = entity.isSleeping();
        state.isSitting = entity.isSitting();
        state.isFaceplanted = entity.isFaceplanted();
        state.isPouncing = entity.isPouncing();
        state.variant = entity.getVariant();
    }

    private record FoxTexturesByAge(Identifier adult, Identifier baby) {
    }

    private record FoxTexturesByState(FoxRenderer.FoxTexturesByAge idle, FoxRenderer.FoxTexturesByAge sleeping) {
    }
}
