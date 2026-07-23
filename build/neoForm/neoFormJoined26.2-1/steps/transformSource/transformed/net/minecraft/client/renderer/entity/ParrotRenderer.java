package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ParrotRenderer extends MobRenderer<Parrot, ParrotRenderState, ParrotModel> {
    private static final Identifier RED_BLUE = Identifier.withDefaultNamespace("textures/entity/parrot/parrot_red_blue.png");
    private static final Identifier BLUE = Identifier.withDefaultNamespace("textures/entity/parrot/parrot_blue.png");
    private static final Identifier GREEN = Identifier.withDefaultNamespace("textures/entity/parrot/parrot_green.png");
    private static final Identifier YELLOW_BLUE = Identifier.withDefaultNamespace("textures/entity/parrot/parrot_yellow_blue.png");
    private static final Identifier GREY = Identifier.withDefaultNamespace("textures/entity/parrot/parrot_grey.png");

    public ParrotRenderer(EntityRendererProvider.Context context) {
        super(context, new ParrotModel(context.bakeLayer(ModelLayers.PARROT)), 0.3F);
    }

    public Identifier getTextureLocation(ParrotRenderState state) {
        return getVariantTexture(state.variant);
    }

    public ParrotRenderState createRenderState() {
        return new ParrotRenderState();
    }

    public void extractRenderState(Parrot entity, ParrotRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.variant = entity.getVariant();
        float flap = Mth.lerp(partialTicks, entity.oFlap, entity.flap);
        float flapSpeed = Mth.lerp(partialTicks, entity.oFlapSpeed, entity.flapSpeed);
        state.flapAngle = (Mth.sin(flap) + 1.0F) * flapSpeed;
        state.pose = ParrotModel.getPose(entity);
    }

    public static Identifier getVariantTexture(Parrot.Variant variant) {
        return switch (variant) {
            case RED_BLUE -> RED_BLUE;
            case BLUE -> BLUE;
            case GREEN -> GREEN;
            case YELLOW_BLUE -> YELLOW_BLUE;
            case GRAY -> GREY;
        };
    }
}
