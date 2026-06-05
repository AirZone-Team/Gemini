package geminiclient.gemini.modules.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.itemPhysical.ItemEntityRenderStateExtender;
import geminiclient.gemini.values.impl.FloatValue;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;

public class ItemPhysical extends Module {

    private static final double RANDOM_Y_OFFSET_SCALE = 0.05 / (Math.PI * 2);

    private final FloatValue rotateSpeed = new FloatValue("RotateSpeed", 1.0f, 0.0f, 10.0f);

    public ItemPhysical() {
        super("ItemPhysical", ModuleEnum.Visual);
        addValue(rotateSpeed);
    }

    public float getRotateSpeed() {
        return rotateSpeed.getValue();
    }

    public static boolean submit(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                 RandomSource rand) {
        ItemPhysical module = Gemini.moduleManager.getModule(ItemPhysical.class);
        if (module == null || !module.enabled)
            return false;

        if (state.ageInTicks < 1)
            return false;

        pose.pushPose();

        rand.setSeed(state.seed);
        int j = getModelCount(state.count);
        boolean gui3d = ((ItemEntityRenderStateExtender) state).isBlock();

        pose.mulPose(Axis.XP.rotation((float) Math.PI / 2));
        pose.mulPose(Axis.ZP.rotation(((ItemEntityRenderStateExtender) state).getYRot()));

        if (state.ageInTicks != 0) {
            if (gui3d)
                pose.translate(0, -0.2, -0.08);
            else if (((ItemEntityRenderStateExtender) state).hasAdditionalOffset())
                pose.translate(0, 0.0, -0.14 - state.bobOffset * RANDOM_Y_OFFSET_SCALE);
            else
                pose.translate(0, 0, -0.04 - state.bobOffset * RANDOM_Y_OFFSET_SCALE);

            double height = 0.2;
            if (gui3d)
                pose.translate(0, height, 0);
            pose.mulPose(Axis.YP.rotation(((ItemEntityRenderStateExtender) state).getXRot()));
            if (gui3d)
                pose.translate(0, -height, 0);
        }

        if (!gui3d) {
            float f7 = -0.0F * (j - 1) * 0.5F;
            float f8 = -0.0F * (j - 1) * 0.5F;
            float f9 = -0.09375F * (j - 1) * 0.5F;
            pose.translate(f7, f8, f9);
        }

        for (int k = 0; k < j; ++k) {
            pose.pushPose();
            if (k > 0) {
                if (gui3d) {
                    float f11 = (rand.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float f13 = (rand.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float f10 = (rand.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    pose.translate(f11, f13, f10);
                }
            }

            state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
            if (!gui3d)
                pose.translate(0.0F, 0.0F, 0.09375F);
        }

        pose.popPose();
        return true;
    }

    public static boolean requiresOffset(ItemEntity entity) {
        var blockState = entity.level().getBlockState(entity.blockPosition());
        var belowState = entity.level().getBlockState(entity.blockPosition().below());
        return blockState.is(Blocks.SNOW) || blockState.is(Blocks.SOUL_SAND) || blockState.is(Blocks.MUD)
                || belowState.is(Blocks.SNOW) || belowState.is(Blocks.SOUL_SAND) || belowState.is(Blocks.MUD);
    }

    private static int getModelCount(int count) {
        if (count > 48)
            return 5;
        if (count > 32)
            return 4;
        if (count > 16)
            return 3;
        if (count > 1)
            return 2;
        return 1;
    }
}
