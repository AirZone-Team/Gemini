package geminiclient.gemini.modules.impl.combat.killaura;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.StrafeEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.JumpEvent;
import geminiclient.gemini.utils.MathHelper;
import geminiclient.gemini.utils.MovementUtils;

public class Rotation implements MinecraftInstance {
    private float yaw;
    private float pitch;
    private boolean isActive;
    private boolean moveFix = true;

    // Smooth head→view transition on deactivate
    private boolean transitioning;
    private boolean transitionRegistered; // whether we're registered as event listener
    private float transitionFromHeadYaw;
    private float transitionFromBodyYaw;
    private long transitionStartTime;
    private static final long TRANSITION_MS = 250;

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    // ---- yaw / pitch ----

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    // ---- moveFix ----

    public void setMoveFix(boolean moveFix) {
        this.moveFix = moveFix;
    }

    public boolean isMoveFix() {
        return moveFix;
    }

    // ---- isActive ----

    public boolean isActive() {
        return isActive;
    }

    /**
     * Enable: sync rotation angles to the player's current view.
     * Disable: smoothly restore head/body yaw back to the player's actual view
     *          instead of snapping the view to the aimbot head.
     */
    public void setActive(boolean active) {
        if (this.isActive == active)
            return;
        this.isActive = active;

        if (mc.player == null)
            return;

        if (active) {
            // onEnabled behaviour — seed rotation from current camera
            this.yaw = mc.player.getYRot();
            this.pitch = net.minecraft.util.Mth.clamp(mc.player.getXRot(), -90.0f, 90.0f);
            if (transitionRegistered) {
                Gemini.eventManager.unregister(this);
                transitionRegistered = false;
            }
            transitioning = false;
        } else {
            // onDisabled behaviour — animate head back to view
            startSmoothTransition();
        }
    }

    // ---- smooth transition ----

    public boolean isTransitioning() {
        return transitioning;
    }

    private void startSmoothTransition() {
        if (mc.player == null)
            return;
        transitioning = true;
        transitionStartTime = System.currentTimeMillis();
        transitionFromHeadYaw = mc.player.yHeadRot;
        transitionFromBodyYaw = mc.player.yBodyRot;

        if (!transitionRegistered) {
            Gemini.eventManager.register(this);
            transitionRegistered = true;
        }
    }

    private void finishTransition() {
        transitioning = false;
        if (mc.player != null) {
            mc.player.yHeadRot = mc.player.getYRot();
            mc.player.yBodyRot = mc.player.getYRot();
        }
        if (transitionRegistered) {
            Gemini.eventManager.unregister(this);
            transitionRegistered = false;
        }
    }

    /**
     * Called each client tick during the transition.
     * Interpolates yHeadRot/yBodyRot from the aimbot angle back to the player's view.
     */
    @SuppressWarnings("unused")
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!transitioning)
            return;

        if (mc.player == null) {
            finishTransition();
            return;
        }

        float elapsed = System.currentTimeMillis() - transitionStartTime;
        float t = Math.min(1.0f, elapsed / TRANSITION_MS);
        // ease-out cubic
        float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);

        float targetYaw = mc.player.getYRot();
        float headDiff = MathHelper.wrapAngleTo180_float(targetYaw - transitionFromHeadYaw);
        float bodyDiff = MathHelper.wrapAngleTo180_float(targetYaw - transitionFromBodyYaw);

        mc.player.yHeadRot = transitionFromHeadYaw + headDiff * eased;
        mc.player.yBodyRot = transitionFromBodyYaw + bodyDiff * eased;

        if (t >= 1.0f) {
            finishTransition();
        }
    }

    // ---- movement fix handlers ----

    public void handleStrafe(StrafeEvent event) {
        if (!moveFix || !isActive || mc.player == null)
            return;

        float targetYaw = this.yaw;
        float realYaw = mc.player.getYRot();

        float rawForward = mc.player.input.keyPresses.forward() == mc.player.input.keyPresses.backward()
                ? 0.0F
                : (mc.player.input.keyPresses.forward() ? 1.0F : -1.0F);
        float rawStrafe = mc.player.input.keyPresses.left() == mc.player.input.keyPresses.right()
                ? 0.0F
                : (mc.player.input.keyPresses.left() ? 1.0F : -1.0F);

        event.setForward(rawForward);
        event.setStrafe(rawStrafe);
        MovementUtils.fixMovement(event, targetYaw, realYaw);
        event.setYaw(targetYaw);
    }

    public void handleJump(JumpEvent event) {
        if (moveFix && isActive) {
            event.setYaw(this.yaw);
        }
    }
}
