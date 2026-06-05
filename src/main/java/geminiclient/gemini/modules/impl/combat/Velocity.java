package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.LinkedBlockingDeque;

public class Velocity extends Module {

    private final ListValue mode = new ListValue("Mode", "Simple", new String[]{"Simple", "NoXZ"});
    private final IntValue maxSuspendTicks = new IntValue("MaxSuspendTicks", 12, 5, 20, () -> mode.is("NoXZ"));
    private final IntValue attackAmount = new IntValue("AttackAmount", 3, 1, 10, () -> mode.is("NoXZ"));
    private final BoolValue requireSprint = new BoolValue("RequireSprint", true, () -> mode.is("NoXZ"));

    // Suspension State
    private boolean suspending;
    private int suspendTicks;
    private int attacksRemaining;
    private int flagCooldown;

    public boolean isSuspending() {
        return suspending;
    }

    public boolean hasQueuedIncoming() {
        return !packetQueue.isEmpty() || !movePacketQueue.isEmpty();
    }

    // Queues
    private final LinkedBlockingDeque<Packet<?>> packetQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<Packet<?>> movePacketQueue = new LinkedBlockingDeque<>();
    private ClientboundSetEntityMotionPacket knockbackPacket = null;

    // Flags
    private volatile boolean isFlushing = false;
    private boolean shouldFlushMotion = false;

    public Velocity() {
        super("Velocity", ModuleEnum.Combat);
        addValue(mode, maxSuspendTicks, attackAmount, requireSprint);
    }

    @Override
    public void onEnabled() {
        resetSuspension();
    }

    @Override
    public void onDisabled() {
        release();
    }

    @SuppressWarnings({"unused"})
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || isFlushing) return;

        Packet<?> packet = event.getPacket();

        // Server-bound Move Packet Queuing
        if (packet instanceof ServerboundMovePlayerPacket && suspending) {
            movePacketQueue.add(packet);
            event.setCancelled(true);
            return;
        }

        // Flag Detection
        if (packet instanceof ClientboundPlayerPositionPacket) {
            if (suspending) {
                release();
            }
            resetSuspension();
            flagCooldown = 2;
            return;
        }

        if (flagCooldown > 0) return;

        // Inbound Packet Queuing (Excluding allowed packets)
        if (suspending) {
            if (!isAllowedPacket(packet)) {
                packetQueue.add(packet);
                event.setCancelled(true);
            }
            return;
        }

        // Knockback Interception
        if (packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
            if (motionPacket.id() != mc.player.getId()) return;

            if (mode.is("Simple")) {
                event.setCancelled(true);
                return;
            }

            if (isDeadOrInvalid()) return;

            double dx = Math.abs(motionPacket.movement().x);
            double dz = Math.abs(motionPacket.movement().z);
            if (dx < 0.01 && dz < 0.01) return;

            // Target Validation (Optional refinement: Check if target exists before suspending)
            Entity target = getTarget();
            boolean canAttack = isValidTarget(target) && mc.player.isSprinting();

            if (!mc.player.onGround() || !canAttack) {
                if (!requireSprint.enabled || mc.player.isSprinting()) {
                    suspending = true;
                    suspendTicks = 0;
                    knockbackPacket = motionPacket;
                    event.setCancelled(true);
                }
            } else {
                // If on ground and can instantly hit back
                attacksRemaining = attackAmount.getValue();
            }
        }
    }

    @SuppressWarnings({"unused", "rawtypes", "unchecked"})
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mode.is("Simple")) return;

        // 1. Flush Received Packets from previous release cycle
        if (shouldFlushMotion) {
            while (!packetQueue.isEmpty()) {
                Packet packet = packetQueue.poll();
                if (packet == null) continue;
                try {
                    packet.handle(mc.getConnection());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            shouldFlushMotion = false;
        }

        if (flagCooldown > 0) flagCooldown--;

        if (isDeadOrInvalid()) {
            release();
            resetSuspension();
            return;
        }

        // 2. Handle Suspension Timeout / Landing
        if (suspending) {
            suspendTicks++;
            boolean onGround = mc.player.onGround();
            boolean isTimeout = suspendTicks >= maxSuspendTicks.getValue();

            if (onGround || isTimeout) {
                Entity target = getTarget();
                boolean canAttack = isValidTarget(target);
                boolean sprinting = mc.player.isSprinting();

                if (onGround && canAttack && sprinting) {
                    isFlushing = true;
                    attacksRemaining = attackAmount.getValue();

                    sendMovePackets();
                    applyKnockbackPacket();

                    doAttackSequence();
                    scheduleMotionFlush();

                    suspending = false;
                    suspendTicks = 0;
                    isFlushing = false;
                } else {
                    release();
                    if (onGround && mc.player.isSprinting()) {
                        mc.player.setSprinting(false);
                    }
                }
            }
            return;
        }

        // 3. Handle Multi-Attacks Remaining
        if (attacksRemaining > 0 && getTarget() != null) {
            doAttackSequence();
        }
    }

    private void doAttackSequence() {
        Entity target = getTarget();
        if (target == null || !isValidTarget(target)) {
            attacksRemaining = 0;
            return;
        }

        attacksRemaining--;
        boolean sprinting = mc.player.isSprinting();

        if (sprinting) {
            mc.player.setSprinting(false);
        }

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);

        if (sprinting) {
            Vec3 vel = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(vel.x * 0.6, vel.y, vel.z * 0.6);
        }

        if (attacksRemaining <= 0) {
            attacksRemaining = 0;
        }
    }

    private void sendMovePackets() {
        if (mc.getConnection() == null) return;
        while (!movePacketQueue.isEmpty()) {
            Packet<?> packet = movePacketQueue.poll();
            if (packet == null) continue;
            try {
                mc.getConnection().send(packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void applyKnockbackPacket() {
        if (knockbackPacket != null && mc.getConnection() != null) {
            try {
                knockbackPacket.handle(mc.getConnection());
            } catch (Exception e) {
                e.printStackTrace();
            }
            knockbackPacket = null;
        }
    }

    private void scheduleMotionFlush() {
        if (mc.getConnection() != null) {
            shouldFlushMotion = true;
        }
    }

    private void release() {
        isFlushing = true;
        sendMovePackets();
        applyKnockbackPacket();
        scheduleMotionFlush();
        isFlushing = false;

        suspending = false;
        suspendTicks = 0;
        attacksRemaining = 0;
    }

    private void resetSuspension() {
        suspending = false;
        suspendTicks = 0;
        knockbackPacket = null;
        packetQueue.clear();
        movePacketQueue.clear();
        isFlushing = false;
        attacksRemaining = 0;
    }

    private boolean isAllowedPacket(Packet<?> packet) {
        return packet instanceof ClientboundSetEntityMotionPacket ||
                packet instanceof ClientboundSetHealthPacket ||
                packet instanceof ClientboundPlayerPositionPacket ||
                packet instanceof ClientboundSoundPacket ||
                packet instanceof ClientboundPlayerChatPacket ||
                packet instanceof ClientboundPlayerCombatKillPacket ||
                packet instanceof ClientboundContainerClosePacket ||
                packet instanceof ClientboundHurtAnimationPacket ||
                packet instanceof ClientboundSetTitleTextPacket ||
                packet instanceof ClientboundSetPlayerTeamPacket ||
                packet instanceof ClientboundSystemChatPacket ||
                packet instanceof ClientboundDisconnectPacket ||
                (packet instanceof ClientboundAnimatePacket && ((ClientboundAnimatePacket) packet).getId() != mc.player.getId());
    }

    private Entity getTarget() {
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) mc.hitResult).getEntity();
            if (entity != mc.player && entity.isAlive() && !entity.isSpectator()) {
                return entity;
            }
        }
        return null;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity instanceof LivingEntity livingEntity && (livingEntity.isDeadOrDying() || livingEntity.getHealth() <= 0.0f)) return false;

        double maxReach = 3.7;
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        net.minecraft.world.phys.AABB box = entity.getBoundingBox();
        double clampedX = Math.max(box.minX, Math.min(eyePos.x, box.maxX));
        double clampedY = Math.max(box.minY, Math.min(eyePos.y, box.maxY));
        double clampedZ = Math.max(box.minZ, Math.min(eyePos.z, box.maxZ));

        return eyePos.distanceTo(new Vec3(clampedX, clampedY, clampedZ)) <= maxReach;
    }

    private boolean isDeadOrInvalid() {
        return mc.player == null || mc.player.isDeadOrDying() || !mc.player.isAlive()
                || mc.player.getHealth() <= 0 || mc.player.isSpectator();
    }
}