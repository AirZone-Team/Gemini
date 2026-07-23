package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.animal.squid.SquidModel;
import net.minecraft.client.renderer.entity.state.SquidRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GlowSquidRenderer extends SquidRenderer<GlowSquid> {
    private static final Identifier GLOW_SQUID_LOCATION = Identifier.withDefaultNamespace("textures/entity/squid/glow_squid.png");
    private static final Identifier GLOW_SQUID_BABY_LOCATION = Identifier.withDefaultNamespace("textures/entity/squid/glow_squid_baby.png");

    public GlowSquidRenderer(EntityRendererProvider.Context context, SquidModel model, SquidModel babyModel) {
        super(context, model, babyModel);
    }

    @Override
    public Identifier getTextureLocation(SquidRenderState state) {
        return state.isBaby ? GLOW_SQUID_BABY_LOCATION : GLOW_SQUID_LOCATION;
    }

    protected int getBlockLightLevel(GlowSquid entity, BlockPos blockPos) {
        int glowLightLevel = (int)Mth.clampedLerp(1.0F - entity.getDarkTicksRemaining() / 10.0F, 0.0F, 15.0F);
        return glowLightLevel == 15 ? 15 : Math.max(glowLightLevel, super.getBlockLightLevel(entity, blockPos));
    }
}
