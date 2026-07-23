package net.minecraft.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class NoRenderParticleGroup extends ParticleGroup<NoRenderParticle> {
    private static final ParticleGroupRenderState EMPTY_RENDER_STATE = (ignored, camera) -> {};

    public NoRenderParticleGroup(ParticleEngine engine) {
        super(engine);
    }

    @Override
    public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTickTime) {
        return EMPTY_RENDER_STATE;
    }
}
