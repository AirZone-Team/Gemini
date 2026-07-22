package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.*;
import geminiclient.gemini.event.events.impl.enums.IOEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.RenderUtils;
import geminiclient.gemini.values.impl.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {

    // --- Inner Classes ---

    public static final class PacketEntry {
        public final Packet<?> packet;
        public final long timestamp;

        public PacketEntry(Packet<?> packet) {
            this.packet = packet;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static final class PositionTracker {
        public final Player player;
        public Vec3 currentPos;
        public Vec3 lastPos;

        public PositionTracker(Player player, Vec3 pos) {
            this.player = player;
            this.currentPos = pos;
            this.lastPos = pos;
        }

        public Vec3 decodeRelativePos(short xa, short ya, short za) {
            return this.currentPos.add(xa / 4096.0, ya / 4096.0, za / 4096.0);
        }

        public void updatePos(Vec3 pos) {
            this.lastPos = this.currentPos;
            this.currentPos = pos;
        }

        public void applyPos() {
            this.lastPos = this.currentPos;
        }

        public Vec3 getInterpolatedPos(float partial) {
            return new Vec3(
                    this.lastPos.x + (this.currentPos.x - this.lastPos.x) * partial,
                    this.lastPos.y + (this.currentPos.y - this.lastPos.y) * partial,
                    this.lastPos.z + (this.currentPos.z - this.lastPos.z) * partial);
        }
    }

    // --- Settings ---

    private final FloatRangeValue range = new FloatRangeValue("Range", 1.0f, 3.0f, 0.0f, 10.0f);
    private final IntValue delay = new IntValue("Delay", 200, 0, 1000);
    private final FloatValue chance = new FloatValue("Chance", 50.0f, 0.0f, 100.0f);
    private final BoolValue resetOnVelocity = new BoolValue("Reset On Velocity", false);
    private final BoolValue render = new BoolValue("Render", true);
    private final ListValue esp = new ListValue("ESP", "Box", new String[]{"Box", "Wireframe", "None"});

    // --- State ---

    private final ConcurrentLinkedQueue<PacketEntry> packetQueue = new ConcurrentLinkedQueue<>();
    private PositionTracker positionTracker;
    private boolean isBacktrackingActive;

    public BackTrack() {
        super("BackTrack", ModuleEnum.Combat);
        addValue(range, delay, chance, resetOnVelocity, render, esp);
    }

    // --- Lifecycle ---

    @Override
    public void onEnabled() {
        this.positionTracker = null;
        this.isBacktrackingActive = false;
        this.packetQueue.clear();
    }

    @Override
    public void onDisabled() {
        this.positionTracker = null;
        releasePackets();
    }

    // --- Event Handlers ---

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) {
            this.positionTracker = null;
            releasePackets();
            return;
        }

        if (this.positionTracker == null) {
            releasePackets();
            return;
        }

        if (!this.positionTracker.player.isAlive() || this.positionTracker.player.isRemoved()) {
            this.positionTracker = null;
            releasePackets();
            return;
        }

        if (this.resetOnVelocity.enabled && isVelocitySuspending()) {
            this.positionTracker = null;
            releasePackets();
            return;
        }

        this.positionTracker.applyPos();

        if (this.isBacktrackingActive) {
            checkBacktrackRange(this.positionTracker);
            processQueue();
        }
    }

    @EventTarget
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.dead()) return;

        if (event.entity() instanceof Player player) {
            double chanceVal = this.chance.getValue() / 100.0;
            if (Math.random() <= chanceVal) {
                if (this.resetOnVelocity.enabled && isVelocitySuspending()) return;

                if (this.positionTracker == null) {
                    releasePackets();
                    this.positionTracker = new PositionTracker(player, player.position());
                }
            } else {
                this.positionTracker = null;
                releasePackets();
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @EventTarget(1)
    public void onPacket(PacketEvent event) {
        PositionTracker tracker = this.positionTracker;
        if (tracker == null) return;
        if (mc.player == null || mc.level == null) return;
        if (event.isCancelled()) return;
        if (event.getIoEnum() != IOEnum.In) return;

        Packet packet = event.getPacket();

        // Track position from movement packets
        if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()) {
            Entity entity = move.getEntity(mc.level);
            if (entity != null && entity.getId() == tracker.player.getId()) {
                Vec3 pos = tracker.decodeRelativePos(move.getXa(), move.getYa(), move.getZa());
                tracker.currentPos = pos;
                checkBacktrackRange(tracker);
            }
        } else if (packet instanceof ClientboundTeleportEntityPacket tp) {
            if (tp.id() == tracker.player.getId()) {
                Vec3 pos = tp.change().position();
                tracker.updatePos(pos);
                tracker.currentPos = pos;
                checkBacktrackRange(tracker);
            }
        } else if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
            if (remove.getEntityIds().contains(tracker.player.getId())) {
                this.positionTracker = null;
                releasePackets();
                return;
            }
        } else if (packet instanceof ClientboundPlayerPositionPacket) {
            this.positionTracker = null;
            releasePackets();
            return;
        }

        // Cancel and queue packets when backtracking is active
        if (this.isBacktrackingActive) {
            if (this.resetOnVelocity.enabled && packet instanceof ClientboundSetEntityMotionPacket motion) {
                if (mc.player != null && motion.id() == mc.player.getId()) {
                    this.positionTracker = null;
                    releasePackets();
                    return;
                }
            }

            this.packetQueue.add(new PacketEntry(packet));
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.render.enabled) return;
        if (!this.isBacktrackingActive) return;

        PositionTracker tracker = this.positionTracker;
        if (tracker == null || tracker.player == null) return;
        if (mc.player == null) return;

        Vec3 pos = tracker.getInterpolatedPos(event.partialTick());
        double halfWidth = tracker.player.getBbWidth() / 2.0;
        double height = tracker.player.getBbHeight();

        AABB box = new AABB(
                pos.x - halfWidth, pos.y, pos.z - halfWidth,
                pos.x + halfWidth, pos.y + height, pos.z + halfWidth);

        String espMode = esp.get();
        boolean fill = espMode.equals("Box");
        boolean outline = espMode.equals("Box") || espMode.equals("Wireframe");

        if (fill) RenderUtils.drawFilledBox(box, 0x40FFFFFF);
        if (outline) RenderUtils.drawOutlineBox(box, 0xCCFFFFFF);
    }

    @EventTarget
    public void onShutdown(ShutdownEvent event) {
        this.positionTracker = null;
        releasePackets();
    }

    // --- Packet Queue ---

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void processQueue() {
        long now = System.currentTimeMillis();
        long delayMs = this.delay.getValue();

        while (!this.packetQueue.isEmpty()) {
            PacketEntry entry = this.packetQueue.peek();
            if (now - entry.timestamp < delayMs) break;

            this.packetQueue.poll();
            try {
                if (mc.getConnection() != null) {
                    ((Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>) entry.packet)
                            .handle(mc.getConnection());
                }
            } catch (Exception ignored) {
            }
        }

        if (this.packetQueue.isEmpty()) {
            this.positionTracker = null;
            this.isBacktrackingActive = false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void releasePackets() {
        if (this.isBacktrackingActive) {
            this.isBacktrackingActive = false;
            while (!this.packetQueue.isEmpty()) {
                PacketEntry entry = this.packetQueue.poll();
                try {
                    if (mc.getConnection() != null) {
                        ((Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>) entry.packet)
                                .handle(mc.getConnection());
                    }
                } catch (Exception ignored) {
                }
            }
        }
        this.packetQueue.clear();
    }

    // --- Helpers ---

    private void checkBacktrackRange(PositionTracker tracker) {
        if (tracker == null || mc.player == null) return;

        Vec3 eye = mc.player.getEyePosition();
        double halfWidth = tracker.player.getBbWidth() / 2.0;

        AABB box = new AABB(
                tracker.currentPos.x - halfWidth, tracker.currentPos.y, tracker.currentPos.z - halfWidth,
                tracker.currentPos.x + halfWidth, tracker.currentPos.y + tracker.player.getBbHeight(), tracker.currentPos.z + halfWidth);

        AABB realBox = tracker.player.getBoundingBox();

        Vec3 closest = closestPoint(eye, box);
        Vec3 realClosest = closestPoint(eye, realBox);

        double distance = eye.distanceTo(closest);
        double realDistance = eye.distanceTo(realClosest);

        float rMin = range.getMinValue();
        float rMax = range.getMaxValue();

        if (realDistance <= 3.0
                && distance >= rMin
                && distance < rMax) {
            this.isBacktrackingActive = true;
        } else {
            this.positionTracker = null;
            releasePackets();
        }
    }

    private static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
                Math.max(box.minX, Math.min(point.x, box.maxX)),
                Math.max(box.minY, Math.min(point.y, box.maxY)),
                Math.max(box.minZ, Math.min(point.z, box.maxZ)));
    }

    private boolean isVelocitySuspending() {
        Velocity vel = Gemini.moduleManager.getModule(Velocity.class);
        return vel != null && vel.enabled;
    }

    // --- Public API ---

    public boolean isBacktracking() {
        return this.isBacktrackingActive;
    }

    public boolean isActive() {
        return this.isBacktrackingActive && !this.packetQueue.isEmpty();
    }
}
