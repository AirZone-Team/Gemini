package geminiclient.gemini.utils;

import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.event.events.impl.moveFixEvent.StrafeEvent;

public class MovementUtils implements MinecraftInstance {
    public static boolean moving() {
        if (mc.player == null)
            return false;

        return mc.player.input.getMoveVector().x != 0.0 || mc.player.input.getMoveVector().y != 0.0f;
    }

    private static float direction(float forward, float strafe) {
        float direction = mc.player.getYRot();
        boolean isMovingForward = forward > 0.0F;
        boolean isMovingBack = forward < 0.0F;
        boolean isMovingRight = strafe > 0.0F;
        boolean isMovingLeft = strafe < 0.0F;
        boolean isMovingSideways = isMovingRight || isMovingLeft;
        boolean isMovingStraight = isMovingForward || isMovingBack;
        if (forward != 0.0F || strafe != 0.0F) {
            if (isMovingBack && !isMovingSideways) {
                return direction + 180.0F;
            }

            if (isMovingForward && isMovingLeft) {
                return direction + 45.0F;
            }

            if (isMovingForward && isMovingRight) {
                return direction - 45.0F;
            }

            if (!isMovingStraight && isMovingLeft) {
                return direction + 90.0F;
            }

            if (!isMovingStraight && isMovingRight) {
                return direction - 90.0F;
            }

            if (isMovingBack && isMovingLeft) {
                return direction + 135.0F;
            }

            if (isMovingBack) {
                return direction - 135.0F;
            }
        }

        return direction;
    }

    public static void fixMovement(StrafeEvent event, float yaw) {
        float forward = event.getForward();
        float strafe = event.getStrafe();
        int angleUnit = 45;
        float angleTolerance = 22.5F;
        float directionFactor = Math.max(Math.abs(forward), Math.abs(strafe));
        double angleDifference = (double)MathHelper.wrapDegrees(direction(forward, strafe) - yaw);
        double angleDistance = Math.abs(angleDifference);
        forward = 0.0F;
        strafe = 0.0F;
        if (angleDistance <= (double)((float)angleUnit + angleTolerance)) {
            forward++;
        } else if (angleDistance >= (double)(180.0F - (float)angleUnit - angleTolerance)) {
            forward--;
        }

        if (angleDifference >= (double)((float)angleUnit - angleTolerance) && angleDifference <= (double)(180.0F - (float)angleUnit + angleTolerance)) {
            strafe--;
        } else if (angleDifference <= (double)((float)(-angleUnit) + angleTolerance) && angleDifference >= (double)(-180.0F + (float)angleUnit - angleTolerance)) {
            strafe++;
        }

        forward *= directionFactor;
        strafe *= directionFactor;
        event.setForward(forward);
        event.setStrafe(strafe);
    }
}
