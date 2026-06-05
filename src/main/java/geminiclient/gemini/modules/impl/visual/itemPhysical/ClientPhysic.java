package geminiclient.gemini.modules.impl.visual.itemPhysical;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.modules.impl.visual.ItemPhysical;
import geminiclient.mixin.access.AccessEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

public class ClientPhysic {

    private static final float BASE_MULTIPLIER = 0.25F;
    private static final Minecraft MC = Minecraft.getInstance();

    public static void calculateRotation(ItemEntity entity, ItemEntityRenderState state) {
        ItemPhysical module = Gemini.moduleManager.getModule(ItemPhysical.class);
        if (module == null || !module.enabled)
            return;

        float rotateBy = MC.getDeltaTracker().getRealtimeDeltaTicks() * BASE_MULTIPLIER * module.getRotateSpeed();
        if (MC.isPaused())
            rotateBy = 0;

        Vec3 motionMultiplier = ((AccessEntity) entity).getStuckSpeedMultiplier();
        if (motionMultiplier != null && motionMultiplier.lengthSqr() > 0)
            rotateBy *= (float) (motionMultiplier.x * 0.2);

        boolean gui3d = ((ItemEntityRenderStateExtender) state).isBlock();

        if (gui3d) {
            if (!entity.onGround()) {
                rotateBy *= 2;
                entity.setXRot(entity.getXRot() + rotateBy);
            }
        } else if (!Double.isNaN(entity.getX()) && !Double.isNaN(entity.getY()) && !Double.isNaN(entity.getZ())) {
            if (entity.onGround()) {
                entity.setXRot(0);
            } else {
                rotateBy *= 2;
                entity.setXRot(entity.getXRot() + rotateBy);
            }
        }
    }
}
