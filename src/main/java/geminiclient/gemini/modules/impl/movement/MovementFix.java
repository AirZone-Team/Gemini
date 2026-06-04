package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MoveInputEvent;
import geminiclient.gemini.event.events.impl.StrafeEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.util.Mth;

public class MovementFix extends Module {
    private final ListValue modes = new ListValue("Modes", "Angle", new String[]{
            "Angle",
            "Matrix"
    });

    public MovementFix() {
        super("MovementFix", ModuleEnum.Movement);
        setEnabled(true);
        addValue(modes);
    }

    public String getMode() {
        return modes.get();
    }

    // ========================================================================
    //  Public static API — called by RotationManager.onStrafe
    // ========================================================================

    public static void applyMoveFix(MoveInputEvent event, float yaw) {
        MovementFix mf = Gemini.moduleManager.getModule(MovementFix.class);
        // 增加 !mf.isEnabled() 检查
        if (mf == null || !mf.enabled) return;

        switch (mf.getMode()) {
            case "Matrix" -> applyMoveFixMatrix(event, yaw);
            case "Angle" -> applyMoveFixAngle(event, yaw);
        }
    }

    public static void applyMoveFixAngle(MoveInputEvent event, float yaw) {
        float forward = event.getForward();
        float strafe = event.getStrafe();

        float direction = getDirectionStatic(forward, strafe);

        int angleUnit = 45;
        float angleTolerance = 22.5f;
        float directionFactor = Math.max(Math.abs(forward), Math.abs(strafe));
        double angleDifference = Mth.wrapDegrees(direction - yaw);
        double angleDistance = Math.abs(angleDifference);

        forward = 0.0f;
        strafe = 0.0f;

        if (angleDistance <= (double) ((float) angleUnit + angleTolerance)) {
            forward++;
        } else if (angleDistance >= (double) (180.0F - (float) angleUnit - angleTolerance)) {
            forward--;
        }

        if (angleDifference >= (double) ((float) angleUnit - angleTolerance) && angleDifference <= (double) (180.0F - (float) angleUnit + angleTolerance)) {
            strafe--;
        } else if (angleDifference <= (double) ((float) (-angleUnit) + angleTolerance) && angleDifference >= (double) (-180.0F + (float) angleUnit - angleTolerance)) {
            strafe++;
        }

        forward *= directionFactor;
        strafe *= directionFactor;

        event.setForward(forward);
        event.setStrafe(strafe);
    }

    public static void applyMoveFixMatrix(MoveInputEvent event, float yaw) {
        float forward = event.getForward();
        float strafe = event.getStrafe();

        float originalYaw = mc.player.getYRot();
        float delta = Mth.wrapDegrees(originalYaw - yaw);
        float rad = (float) Math.toRadians(delta);
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);

        float newForward = forward * cos + strafe * sin;
        float newStrafe = strafe * cos - forward * sin;

        event.setForward(Math.round(newForward));
        event.setStrafe(Math.round(newStrafe));
    }

    // ========================================================================
    //  Private helpers (shared core logic)
    // ========================================================================

    private static float getDirectionStatic(float forward, float strafe) {
        float direction = mc.player.getYRot();

        boolean isMovingForward = forward > 0.0f;
        boolean isMovingBack = forward < 0.0f;
        boolean isMovingRight = strafe > 0.0f;
        boolean isMovingLeft = strafe < 0.0f;
        boolean isMovingSideways = isMovingRight || isMovingLeft;
        boolean isMovingStraight = isMovingForward || isMovingBack;

        if (forward != 0.0F || strafe != 0.0F) {
            if (isMovingBack && !isMovingSideways) {
                return direction + 180.0f;
            }
            if (isMovingForward && isMovingLeft) {
                return direction + 45.0f;
            }
            if (isMovingForward && isMovingRight) {
                return direction - 45.0f;
            }
            if (!isMovingStraight && isMovingLeft) {
                return direction + 90.0f;
            }
            if (!isMovingStraight && isMovingRight) {
                return direction - 90.0f;
            }
            if (isMovingBack && isMovingLeft) {
                return direction + 135.0f;
            }
            if (isMovingBack) {
                return direction - 135.0f;
            }
        }

        return direction;
    }

    private void fixMovementAngle(MoveInputEvent event, float yaw) {
        float forward = event.getForward();
        float strafe = event.getStrafe();

        float direction = getDirectionStatic(forward, strafe);

        int angleUnit = 45;
        float angleTolerance = 22.5f;
        float directionFactor = Math.max(Math.abs(forward), Math.abs(strafe));
        double angleDifference = Mth.wrapDegrees(direction - yaw);
        double angleDistance = Math.abs(angleDifference);

        forward = 0.0f;
        strafe = 0.0f;

        if (angleDistance <= (double) ((float) angleUnit + angleTolerance)) {
            forward++;
        } else if (angleDistance >= (double) (180.0F - (float) angleUnit - angleTolerance)) {
            forward--;
        }

        if (angleDifference >= (double) ((float) angleUnit - angleTolerance) && angleDifference <= (double) (180.0F - (float) angleUnit + angleTolerance)) {
            strafe--;
        } else if (angleDifference <= (double) ((float) (-angleUnit) + angleTolerance) && angleDifference >= (double) (-180.0F + (float) angleUnit - angleTolerance)) {
            strafe++;
        }

        forward *= directionFactor;
        strafe *= directionFactor;

        event.setForward(forward);
        event.setStrafe(strafe);
    }

    private void fixMovementMatrix(MoveInputEvent event, float yaw) {
        float forward = event.getForward();
        float strafe = event.getStrafe();

        float originalYaw = mc.player.getYRot();
        float delta = Mth.wrapDegrees(originalYaw - yaw);
        float rad = (float) Math.toRadians(delta);
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);

        float newForward = forward * cos + strafe * sin;
        float newStrafe = strafe * cos - forward * sin;

        event.setForward(Math.round(newForward));
        event.setStrafe(Math.round(newStrafe));
    }

    // ========================================================================
    //  Event handlers
    // ========================================================================

//    @EventTarget
//    public void onMoveInput(MoveInputEvent event) {
//        if (mc.player == null || Gemini.rotationManager.isActive())
//            return;
//
//        float yaw = mc.player.getYRot();
//
//        switch (modes.get()) {
//            case "Matrix" -> fixMovementMatrix(event, yaw);
//            case "Angle" -> fixMovementAngle(event, yaw);
//        }
//    }
}
