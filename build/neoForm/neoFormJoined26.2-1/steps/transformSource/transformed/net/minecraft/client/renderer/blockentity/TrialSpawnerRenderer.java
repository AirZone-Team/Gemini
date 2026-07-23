package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.SpawnerRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerStateData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class TrialSpawnerRenderer implements BlockEntityRenderer<TrialSpawnerBlockEntity, SpawnerRenderState> {
    private final EntityRenderDispatcher entityRenderer;

    public TrialSpawnerRenderer(BlockEntityRendererProvider.Context context) {
        this.entityRenderer = context.entityRenderer();
    }

    public SpawnerRenderState createRenderState() {
        return new SpawnerRenderState();
    }

    public void extractRenderState(
        TrialSpawnerBlockEntity blockEntity,
        SpawnerRenderState state,
        float partialTicks,
        Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        if (blockEntity.getLevel() != null) {
            TrialSpawner spawner = blockEntity.getTrialSpawner();
            TrialSpawnerStateData data = spawner.getStateData();
            Entity displayEntity = data.getOrCreateDisplayEntity(spawner, blockEntity.getLevel(), spawner.getState());
            extractSpawnerData(state, partialTicks, displayEntity, this.entityRenderer, data.getOSpin(), data.getSpin());
        }
    }

    public static void extractSpawnerData(
        SpawnerRenderState state, float partialTicks, @Nullable Entity displayEntity, EntityRenderDispatcher entityRenderer, double oSpin, double spin
    ) {
        if (displayEntity != null) {
            state.displayEntity = entityRenderer.extractEntity(displayEntity, partialTicks);
            state.displayEntity.lightCoords = state.lightCoords;
            state.spin = (float)Mth.lerp(partialTicks, oSpin, spin) * 10.0F;
            state.scale = 0.53125F;
            float maxLength = Math.max(displayEntity.getBbWidth(), displayEntity.getBbHeight());
            if (maxLength > 1.0) {
                state.scale /= maxLength;
            }
        }
    }

    public void submit(SpawnerRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.displayEntity != null) {
            SpawnerRenderer.submitEntityInSpawner(poseStack, submitNodeCollector, state.displayEntity, this.entityRenderer, state.spin, state.scale, camera);
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(TrialSpawnerBlockEntity blockEntity) {
        net.minecraft.core.BlockPos pos = blockEntity.getBlockPos();
        return new net.minecraft.world.phys.AABB(pos.getX() - 1.0, pos.getY() - 1.0, pos.getZ() - 1.0, pos.getX() + 2.0, pos.getY() + 2.0, pos.getZ() + 2.0);
    }
}
