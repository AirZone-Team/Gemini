package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.vex.VexModel;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Vex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VexRenderer extends MobRenderer<Vex, VexRenderState, VexModel> {
    private static final Identifier VEX_LOCATION = Identifier.withDefaultNamespace("textures/entity/illager/vex.png");
    private static final Identifier VEX_CHARGING_LOCATION = Identifier.withDefaultNamespace("textures/entity/illager/vex_charging.png");

    public VexRenderer(EntityRendererProvider.Context context) {
        super(context, new VexModel(context.bakeLayer(ModelLayers.VEX)), 0.3F);
        this.addLayer(new ItemInHandLayer<>(this));
    }

    protected int getBlockLightLevel(Vex entity, BlockPos blockPos) {
        return 15;
    }

    public Identifier getTextureLocation(VexRenderState state) {
        return state.isCharging ? VEX_CHARGING_LOCATION : VEX_LOCATION;
    }

    public VexRenderState createRenderState() {
        return new VexRenderState();
    }

    public void extractRenderState(Vex entity, VexRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelResolver, partialTicks);
        state.isCharging = entity.isCharging();
    }
}
