package geminiclient.gemini.utils;

import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.event.events.impl.StrafeEvent;
import net.minecraft.world.phys.Vec3;

public class MovementUtils implements MinecraftInstance {
    public static boolean moving() {
        if (mc.player == null)
            return false;

        return mc.player.input.getMoveVector().x != 0.0 || mc.player.input.getMoveVector().y != 0.0f;
    }

    public static double getSpeed() {
        if (mc.player == null)
            return 0;
        Vec3 vec3 = mc.player.getDeltaMovement();
        return Math.sqrt(vec3.x * vec3.x + vec3.z * vec3.z);
    }

    public static void strafe() {
        strafe(getSpeed());
    }

    public static void strafe(double speed) {
        if (mc.player == null)
            return;
        if (!moving()) {
            return;
        }
        Vec3 vec3 = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(-Math.sin(direction()) * speed, vec3.y, Math.cos(direction()) * speed);
    }

    private static double direction() {
        if (mc.player == null)
            return 0;
        float yaw = mc.player.getYRot();
        if (mc.player.input.getMoveVector().y < 0f) yaw += 180f;
        float forward = 1f;
        if (mc.player.input.getMoveVector().y < 0f) forward = -0.5f;
        else if (mc.player.input.getMoveVector().y > 0f) forward = 0.5f;
        if (mc.player.input.getMoveVector().x > 0f) yaw -= 90f * forward;
        if (mc.player.input.getMoveVector().x < 0f) yaw += 90f * forward;
        return Math.toRadians(yaw);
    }

//    public static void fixMovement(StrafeEvent event, float yaw) {
//        float forward = event.getForward();
//        float strafe = event.getStrafe();
//        int angleUnit = 45;
//        float angleTolerance = 22.5F;
//        float directionFactor = Math.max(Math.abs(forward), Math.abs(strafe));
//        double angleDifference = MathHelper.wrapDegrees((float) direction() - yaw);
//        double angleDistance = Math.abs(angleDifference);
//        forward = 0.0F;
//        strafe = 0.0F;
//        if (angleDistance <= (double) ((float) angleUnit + angleTolerance)) {
//            forward++;
//        } else if (angleDistance >= (double) (180.0F - (float) angleUnit - angleTolerance)) {
//            forward--;
//        }
//
//        if (angleDifference >= (double) ((float) angleUnit - angleTolerance) && angleDifference <= (double) (180.0F - (float) angleUnit + angleTolerance)) {
//            strafe--;
//        } else if (angleDifference <= (double) ((float) (-angleUnit) + angleTolerance) && angleDifference >= (double) (-180.0F + (float) angleUnit - angleTolerance)) {
//            strafe++;
//        }
//
//        forward *= directionFactor;
//        strafe *= directionFactor;
//        event.setForward(forward);
//        event.setStrafe(strafe);
//    }

    public static void fixMovement(StrafeEvent event, float yaw, float originalYaw) {
        float forward = event.getForward();
        float strafe = event.getStrafe();

        // 计算视角偏差（真实的第一人称视角 - AimBot锁定的视角）
        float delta = MathHelper.wrapDegrees(originalYaw - yaw);
        float rad = (float) Math.toRadians(delta);
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);

        // 【修正】反转了 sin 的正负号，适配 Minecraft 的 Strafe 轴方向
        float newForward = forward * cos + strafe * sin;
        float newStrafe = strafe * cos - forward * sin;

        // 【进阶优化】将结果四舍五入回标准按键输入值 (-1, 0, 1)
        event.setForward(Math.round(newForward));
        event.setStrafe(Math.round(newStrafe));
    }
}
