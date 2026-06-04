package geminiclient.gemini.base;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.*;
import geminiclient.gemini.event.events.impl.moveFixEvent.*;
import geminiclient.gemini.modules.impl.combat.killaura.Rotation;
import geminiclient.gemini.modules.impl.movement.MovementFix;
import geminiclient.gemini.utils.MovementUtils;

import java.util.HashMap;
import java.util.Map;

public class RotationManager implements MinecraftInstance {

    public static final int PRIORITY_KILLAURA = 10;
    public static final int PRIORITY_SCAFFOLD = 5;

    public Rotation animationRotation = null;
    public Rotation lastAnimationRotation = null;

    // 整合为一个 Map，避免维护三个独立的 Map 造成混乱
    private final Map<Object, RotationRequest> requests = new HashMap<>();
    private RotationRequest activeRequest;

    public void requestRotation(Object source, float yaw, float pitch, int priority, boolean moveFix) {
        RotationRequest req = requests.get(source);
        if (req != null) {
            req.rotation.setYaw(yaw);
            req.rotation.setPitch(pitch);
            req.priority = priority;
            req.moveFix = moveFix;
        } else {
            requests.put(source, new RotationRequest(new Rotation(yaw, pitch), priority, moveFix));
        }
        updateCurrent();
    }

    public void releaseRotation(Object source) {
        requests.remove(source);
        updateCurrent();
    }

    private void updateCurrent() {
        activeRequest = null;
        int bestPriority = -1;

        for (RotationRequest req : requests.values()) {
            if (req.priority > bestPriority) {
                bestPriority = req.priority;
                activeRequest = req;
            }
        }
    }

    @EventTarget(-1000)
    private void onMotion(MotionEvent event) {
        if (activeRequest != null) {
            float yaw = activeRequest.rotation.getYaw();
            float pitch = activeRequest.rotation.getPitch();
            if (!Float.isNaN(yaw) && !Float.isNaN(pitch)) {
                event.setyRot(yaw);
                event.setxRot(pitch);
            }
        }

        lastAnimationRotation = animationRotation;
        animationRotation = new Rotation(event.getyRot(), event.getxRot());
    }

    public boolean isActive() {
        return activeRequest != null;
    }

    public boolean isSourceControlling(Object source) {
        return activeRequest != null && requests.get(source) == activeRequest;
    }

    public float getYaw() {
        return activeRequest != null ? activeRequest.rotation.getYaw() : (mc.player != null ? mc.player.getYRot() : 0f);
    }

    public float getPitch() {
        return activeRequest != null ? activeRequest.rotation.getPitch() : (mc.player != null ? mc.player.getXRot() : 0f);
    }

    // --- 提取公共逻辑以减少冗余 ---
    private boolean shouldFixMovement() {
        return activeRequest != null && activeRequest.moveFix && mc.player != null && !mc.player.isFallFlying() && isMovementFixEnabled();
    }

    private boolean isMovementFixEnabled() {
        MovementFix mf = Gemini.moduleManager.getModule(MovementFix.class);
        return mf != null && mf.enabled;
    }

    // --- 事件监听 ---
    @EventTarget(100)
    public void onMoveInput(MoveInputEvent event) {
        if (shouldFixMovement()) {
            float targetYaw = activeRequest.rotation.getYaw();
            MovementFix.applyMoveFix(event, targetYaw);
        }
    }

    @EventTarget(100)
    public void onStrafe(StrafeEvent event) {
        if (shouldFixMovement()) {
            event.setYaw(activeRequest.rotation.getYaw());
        }
    }

    @EventTarget(-10)
    public void onJump(JumpEvent event) {
        if (shouldFixMovement()) {
            event.setYaw(activeRequest.rotation.getYaw());
        }
    }

    @EventTarget(-10)
    public void onRaytrace(RayTraceEvent event) {
        if (shouldFixMovement()) {
            event.setYaw(activeRequest.rotation.getYaw());
            event.setPitch(activeRequest.rotation.getPitch());
        }
    }

    @EventTarget(-10)
    public void onFallFlying(FallFlyingEvent event) {
        if (shouldFixMovement()) {
            event.setPitch(activeRequest.rotation.getPitch());
        }
    }

    @EventTarget(-10)
    public void onUseItemRaytrace(UseItemRaytraceEvent event) {
        if (shouldFixMovement()) {
            event.setYaw(activeRequest.rotation.getYaw());
            event.setPitch(activeRequest.rotation.getPitch());
        }
    }

    @EventTarget(-10)
    public void onAttackYaw(AttackYawEvent event) {
        if (isActive() && mc.player != null) {
            event.setYaw(activeRequest.rotation.getYaw());
        }
    }

    @EventTarget(-10)
    public void onRotationAnimation(RotationAnimationEvent event) {
        if (animationRotation != null && lastAnimationRotation != null) {
            event.setYaw(animationRotation.getYaw());
            event.setLastYaw(lastAnimationRotation.getYaw());
            event.setPitch(animationRotation.getPitch());
            event.setLastPitch(lastAnimationRotation.getPitch());
        }
    }

    // 内部数据类封装
    private static class RotationRequest {
        Rotation rotation;
        int priority;
        boolean moveFix;

        RotationRequest(Rotation rotation, int priority, boolean moveFix) {
            this.rotation = rotation;
            this.priority = priority;
            this.moveFix = moveFix;
        }
    }
}