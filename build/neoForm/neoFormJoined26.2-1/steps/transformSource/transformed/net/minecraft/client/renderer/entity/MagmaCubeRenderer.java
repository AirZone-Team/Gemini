package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.MagmaCubeModel;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MagmaCubeRenderer extends AbstractCubeMobRenderer<MagmaCube, SlimeRenderState, MagmaCubeModel> {
    private static final Identifier MAGMACUBE_LOCATION = Identifier.withDefaultNamespace("textures/entity/slime/magmacube.png");

    public MagmaCubeRenderer(EntityRendererProvider.Context context) {
        super(context, new MagmaCubeModel(context.bakeLayer(ModelLayers.MAGMA_CUBE)));
    }

    protected int getBlockLightLevel(MagmaCube entity, BlockPos blockPos) {
        return 15;
    }

    public Identifier getTextureLocation(SlimeRenderState state) {
        return MAGMACUBE_LOCATION;
    }

    public SlimeRenderState createRenderState() {
        return new SlimeRenderState();
    }
}
