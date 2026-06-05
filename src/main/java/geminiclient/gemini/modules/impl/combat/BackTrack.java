package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.*;
import geminiclient.gemini.event.events.impl.enums.IOEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.RenderUtils;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class BackTrack extends Module {

    // --- Settings ---
    private final FloatRangeValue range = new FloatRangeValue("Range", 1.0f, 3.0f, 0.0f, 10.0f);
    private final IntRangeValue delay = new IntRangeValue("Delay", 100, 150, 0, 1000);
    private final IntValue nextBacktrackDelay = new IntValue("NextBacktrackDelay", 5, 0, 2000);
    private final IntValue trackingBuffer = new IntValue("TrackingBuffer", 500, 0, 2000);
    private final FloatValue chance = new FloatValue("Chance", 50.0f, 0.0f, 100.0f);
    private final ListValue targetMode = new ListValue("TargetMode", "Attack", new String[]{"Attack", "Range"});
    private final IntValue lastAttackTimeToWork = new IntValue("LastAttackTimeToWork", 1000, 0, 5000);
    private final BoolValue pauseOnHurtTime = new BoolValue("PauseOnHurtTime", false);
    private final IntValue hurtTime = new IntValue("HurtTime", 3, 0, 10, () -> pauseOnHurtTime.enabled);
    private final ListValue esp = new ListValue("ESP", "Box", new String[]{"Box", "Wireframe", "None"});

    // --- State ---
    private final ConcurrentLinkedQueue<PacketEntry> packetQueue = new ConcurrentLinkedQueue<>();
    private Entity target;
    private Vec3 trackedPos = Vec3.ZERO;
    private Vec3 lastTrackedPos = Vec3.ZERO;
    private int currentDelay;
    private int currentChance;
    private boolean shouldPause;
    private boolean isBacktrackingActive;

    private final TimerUtils chronometer = new TimerUtils();
    private final TimerUtils trackingBufferTimer = new TimerUtils();
    private final TimerUtils attackTimer = new TimerUtils();

    public BackTrack() {
        super("BackTrack", ModuleEnum.Combat);
        addValue(range, delay, nextBacktrackDelay, trackingBuffer, chance,
                targetMode, lastAttackTimeToWork, pauseOnHurtTime, hurtTime, esp);
        currentDelay = randomDelay();
        currentChance = randomChance();
    }

    // --- Lifecycle ---

    @Override
    public void onEnabled() {
        clear(false);
        currentDelay = randomDelay();
        currentChance = randomChance();
    }

    @Override
    public void onDisabled() {
        clear(true);
    }

    // --- Target Selection ---

    @EventTarget
    public void onAttack(AttackEvent event) {
        attackTimer.reset();
        currentChance = randomChance();

        if (targetMode.is("Attack")) {
            processTarget(event.entity());
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) {
            clear(true);
            return;
        }

        if (targetMode.is("Range")) {
            Entity nearest = findNearestTarget();
            if (nearest == null) {
                clear(false);
            } else {
                processTarget(nearest);
            }
        }

        // Clear if target became invalid
        if (target != null && (!target.isAlive() || target.isRemoved())) {
            clear(true);
        }

        // If backtracking is active, continue processing
        if (isBacktrackingActive && target != null) {
            if (!shouldBacktrack(target)) {
                clear(true);
                return;
            }
            processQueue();
        }

        // If we have queued packets but shouldn't be cancelling, flush
        if (!packetQueue.isEmpty() && !shouldCancelPackets()) {
            flushAll();
        }

        // Reset delay each time queue drains
        if (packetQueue.isEmpty()) {
            currentDelay = randomDelay();
            isBacktrackingActive = false;
        }
    }

    // --- Packet Handling ---

    @SuppressWarnings({"rawtypes"})
    @EventTarget(value = 1)
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (event.isCancelled()) return;
        if (event.getIoEnum() != IOEnum.In) return;
        if (isVelocityControllingQueue()) return;

        Packet packet = event.getPacket();
        boolean shouldCancel = shouldCancelPackets();
        boolean hasQueued = !packetQueue.isEmpty();

        // Always pass chat/command packets through
        if (packet instanceof ServerboundChatPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ServerboundChatCommandPacket) {
            return;
        }

        // Flush on teleport or disconnect
        if (packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundDisconnectPacket) {
            clear(true);
            return;
        }

        // Pass through own hurt sounds
        if (packet instanceof ClientboundSoundPacket sound
                && sound.getSound().value() == SoundEvents.PLAYER_HURT) {
            return;
        }

        // Flush on own death
        if (packet instanceof ClientboundSetHealthPacket health
                && health.getHealth() <= 0) {
            clear(true);
            return;
        }

        // Track position from movement / teleport packets
        Entity tracked = target;
        if (tracked != null) {
            Vec3 pos = null;

            if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()) {
                Entity entity = move.getEntity(mc.level);
                if (entity != null && entity.getId() == tracked.getId()) {
                    pos = decodeRelativePos(move.getXa(), move.getYa(), move.getZa());
                }
            } else if (packet instanceof ClientboundTeleportEntityPacket tp) {
                if (tp.id() == tracked.getId()) {
                    pos = tp.change().position();
                }
            }

            if (pos != null) {
                lastTrackedPos = trackedPos;
                trackedPos = pos;

                // Smart flush: if new packet position is closer than entity's actual
                // position, flush so we can hit the target
                double newDist = pos.distanceToSqr(mc.player.position());
                double actualDist = tracked.position().distanceToSqr(mc.player.position());
                if (newDist < actualDist) {
                    flushAll();
                    return;
                }
            }
        }

        if (!hasQueued && !shouldCancel) return;

        if (shouldCancel) {
            packetQueue.add(new PacketEntry(packet));
            event.setCancelled(true);
            isBacktrackingActive = true;
        }
    }

    // --- Rendering ---

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (esp.is("None")) return;
        if (!isBacktrackingActive) return;
        Entity entity = target;
        if (entity == null || mc.player == null) return;

        Vec3 pos = getInterpolatedPos(event.partialTick());
        double hw = entity.getBbWidth() / 2.0;
        double h = entity.getBbHeight();
        AABB box = new AABB(pos.x - hw, pos.y, pos.z - hw, pos.x + hw, pos.y + h, pos.z + hw);

        boolean fill = esp.is("Box");
        boolean outline = esp.is("Box") || esp.is("Wireframe");

        if (fill) RenderUtils.drawFilledBox(box, 0x40FFFFFF);
        if (outline) RenderUtils.drawOutlineBox(box, 0xCCFFFFFF);
    }

    @EventTarget
    public void onShutdown(ShutdownEvent event) {
        clear(true);
    }

    // --- Target Processing ---

    private void processTarget(Entity enemy) {
        if (enemy == null || enemy == mc.player) return;

        shouldPause = enemy instanceof LivingEntity living && living.hurtTime >= hurtTime.getValue();

        if (!shouldBacktrack(enemy)) return;

        if (enemy != target) {
            clear(false);
            trackedPos = enemy.position();
            lastTrackedPos = trackedPos;
        }

        target = enemy;
    }

    private Entity findNearestTarget() {
        if (mc.level == null || mc.player == null) return null;
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        float rMin = range.getMinValue();
        float rMax = range.getMaxValue();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (isNotValidTarget(entity)) continue;
            double dist = mc.player.distanceTo(entity);
            if (dist >= rMin && dist <= rMax && dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    // --- Backtrack Logic ---

    private boolean shouldBacktrack(Entity target) {
        if (target == null || mc.player == null) return false;
        if (isNotValidTarget(target)) return false;
        if (mc.player.tickCount <= 10) return false;
        if (!chronometer.hasReached(nextBacktrackDelay.getValue())) return false;

        float rMin = range.getMinValue();
        float rMax = range.getMaxValue();
        double dist = mc.player.distanceTo(target);
        boolean inRange = dist >= rMin && dist <= rMax;

        if (inRange) trackingBufferTimer.reset();

        boolean inBuffer = !trackingBufferTimer.hasReached(trackingBuffer.getValue());
        if (!inRange && !inBuffer) return false;

        if (currentChance >= (int) chance.getValue()) return false;
        if (shouldPause()) return false;
        if (isVelocityBlocked()) return false;
        return !attackTimer.hasReached(lastAttackTimeToWork.getValue());
    }

    private boolean shouldCancelPackets() {
        return target != null && target.isAlive() && shouldBacktrack(target);
    }

    private boolean shouldPause() {
        return pauseOnHurtTime.enabled && shouldPause;
    }

    // --- Packet Queue ---

    private void processQueue() {
        long now = System.currentTimeMillis();
        while (!packetQueue.isEmpty()) {
            PacketEntry entry = packetQueue.peek();
            if (now - entry.timestamp < currentDelay) break;
            packetQueue.poll();
            try {
                if (mc.getConnection() != null) {
                    entry.packet.handle(mc.getConnection());
                }
            } catch (Exception ignored) {
            }
        }
        if (packetQueue.isEmpty()) {
            isBacktrackingActive = false;
        }
    }

    private void flushAll() {
        isBacktrackingActive = false;
        while (!packetQueue.isEmpty()) {
            PacketEntry entry = packetQueue.poll();
            try {
                if (mc.getConnection() != null) {
                    entry.packet.handle(mc.getConnection());
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void clear(boolean handlePackets) {
        if (handlePackets) {
            flushAll();
        } else {
            packetQueue.clear();
        }

        if (target != null) {
            chronometer.reset();
        }

        target = null;
        trackedPos = Vec3.ZERO;
        lastTrackedPos = Vec3.ZERO;
        isBacktrackingActive = false;
        currentDelay = randomDelay();
    }

    // --- Helpers ---

    private boolean isNotValidTarget(Entity entity) {
        if (entity == null || entity == mc.player) return true;
        if (!entity.isAlive()) return true;
        if (entity instanceof LivingEntity living
                && (living.isDeadOrDying() || living.getHealth() <= 0.0f)) return true;
        return !(entity instanceof Player);
    }

    private boolean isVelocityControllingQueue() {
        Velocity vel = Gemini.moduleManager.getModule(Velocity.class);
        return vel != null && vel.enabled && vel.isSuspending();
    }

    private boolean isVelocityBlocked() {
        Velocity vel = Gemini.moduleManager.getModule(Velocity.class);
        return vel != null && vel.enabled && vel.isSuspending();
    }

    private int randomDelay() {
        return ThreadLocalRandom.current().nextInt(delay.getMinValue(), delay.getMaxValue() + 1);
    }

    private int randomChance() {
        return ThreadLocalRandom.current().nextInt(0, 101);
    }

    private Vec3 decodeRelativePos(short xa, short ya, short za) {
        return trackedPos.add(xa / 4096.0, ya / 4096.0, za / 4096.0);
    }

    private Vec3 getInterpolatedPos(float partial) {
        return new Vec3(
                lastTrackedPos.x + (trackedPos.x - lastTrackedPos.x) * partial,
                lastTrackedPos.y + (trackedPos.y - lastTrackedPos.y) * partial,
                lastTrackedPos.z + (trackedPos.z - lastTrackedPos.z) * partial);
    }

    // --- Inner Class ---

    public static final class PacketEntry {
        public final Packet packet;
        public final long timestamp;

        public PacketEntry(Packet packet) {
            this.packet = packet;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
