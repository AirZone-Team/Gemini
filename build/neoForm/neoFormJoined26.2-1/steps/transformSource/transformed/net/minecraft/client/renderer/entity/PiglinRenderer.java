package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.monster.piglin.AdultPiglinModel;
import net.minecraft.client.model.monster.piglin.BabyPiglinModel;
import net.minecraft.client.model.monster.piglin.PiglinModel;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.CrossbowItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PiglinRenderer extends HumanoidMobRenderer<AbstractPiglin, PiglinRenderState, PiglinModel> {
    private static final Identifier PIGLIN_LOCATION = Identifier.withDefaultNamespace("textures/entity/piglin/piglin.png");
    private static final Identifier PIGLIN_BABY_LOCATION = Identifier.withDefaultNamespace("textures/entity/piglin/piglin_baby.png");
    private static final Identifier PIGLIN_BRUTE_LOCATION = Identifier.withDefaultNamespace("textures/entity/piglin/piglin_brute.png");
    public static final CustomHeadLayer.Transforms PIGLIN_CUSTOM_HEAD_TRANSFORMS = new CustomHeadLayer.Transforms(0.0F, 0.0F, 1.0019531F);

    public PiglinRenderer(
        EntityRendererProvider.Context context,
        ModelLayerLocation body,
        ModelLayerLocation babyBody,
        ArmorModelSet<ModelLayerLocation> armorSet,
        ArmorModelSet<ModelLayerLocation> babyArmorSet
    ) {
        super(context, new AdultPiglinModel(context.bakeLayer(body)), new BabyPiglinModel(context.bakeLayer(babyBody)), 0.5F, PIGLIN_CUSTOM_HEAD_TRANSFORMS);
        this.addLayer(
            new HumanoidArmorLayer<>(
                this,
                ArmorModelSet.bake(armorSet, context.getModelSet(), AdultPiglinModel::new),
                ArmorModelSet.bake(babyArmorSet, context.getModelSet(), BabyPiglinModel::new),
                context.getEquipmentRenderer()
            )
        );
    }

    public Identifier getTextureLocation(PiglinRenderState state) {
        return state.isBrute ? PIGLIN_BRUTE_LOCATION : (state.isBaby ? PIGLIN_BABY_LOCATION : PIGLIN_LOCATION);
    }

    public PiglinRenderState createRenderState() {
        return new PiglinRenderState();
    }

    public void extractRenderState(AbstractPiglin entity, PiglinRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isBrute = entity.is(EntityTypes.PIGLIN_BRUTE);
        state.armPose = entity.getArmPose();
        state.maxCrossbowChageDuration = CrossbowItem.getChargeDuration(entity.getUseItem(), entity);
        state.isConverting = entity.isConverting();
    }

    protected boolean isShaking(PiglinRenderState state) {
        return super.isShaking(state) || state.isConverting;
    }
}
