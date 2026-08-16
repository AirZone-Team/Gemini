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
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * BackTrack —— 延迟释放目标玩家的位置数据包。
 *
 * <p>激活期间把"被追踪玩家"的运动/传送/转头/速度数据包暂存 Delay(ms)，
 * 客户端因此只会看到该玩家的旧位置残影，残影落在 Range 窗口（默认 1~3 格）
 * 内时攻击判定仍能命中残影（虚假 Reach，或避开敌人走位闪避）；随后积压的
 * 数据包按原始顺序集中释放回客户端，玩家表现为瞬间"回溯"到新位置。</p>
 *
 * <p>线程模型：MixinConnection 在 netty 线程投递入站 PacketEvent，因此
 * onPacket 运行在 netty 线程，而 onUpdate / onRender3D / onDisabled 运行在
 * 渲染线程。所有跨线程共享状态一律通过 ConcurrentLinkedQueue 与 volatile
 * 字段传递，避免旧实现直接读写普通字段造成的竞态（数据包错乱、重复释放）。</p>
 */
public class BackTrack extends Module {

    /** 一个被暂存的数据包；position 为解码出的目标位置（非位置类数据包为 null）。 */
    public record PacketEntry(Packet<?> packet, int entityId, Vec3 position, long timestamp) {
        public PacketEntry(Packet<?> packet, int entityId, Vec3 position) {
            this(packet, entityId, position, System.currentTimeMillis());
        }
    }

    // --- Settings ---

    /** 残影距离窗口：残影（旧位置）落在 [min, max) 之间时激活延迟。 */
    private final FloatRangeValue range = new FloatRangeValue("Range", 1.0f, 3.0f, 0.0f, 10.0f);
    /** 数据包暂存时长（毫秒），到期后积压包集中释放。 */
    private final IntValue delay = new IntValue("Delay", 200, 0, 1000);
    /** 目标获取概率：决定本次交战是否对该敌人启用回溯（降低行为一致性，减少被针对）。 */
    private final FloatValue chance = new FloatValue("Chance", 50.0f, 0.0f, 100.0f);
    /** 本地玩家受到击退（收到自己的运动数据包）时重置并释放积压包。 */
    private final BoolValue resetOnVelocity = new BoolValue("Reset On Velocity", false);
    /** KillAura 切换目标时跟随切换；新目标不在窗口内则停止回溯。 */
    private final BoolValue resetOnTargetSwitch = new BoolValue("Reset On Target Switch", true);
    private final BoolValue render = new BoolValue("Render", true);
    private final ListValue esp = new ListValue("ESP", "Box", new String[]{"Box", "Wireframe", "None"});

    // --- State（netty 线程写入，渲染线程消费；volatile / 并发队列保证可见性） ---

    /** 暂存的数据包（FIFO，按到达顺序释放）；仅 netty 线程入队、渲染线程出队。 */
    private final ConcurrentLinkedQueue<PacketEntry> packetQueue = new ConcurrentLinkedQueue<>();
    /** 当前被追踪玩家的实体 id；-1 表示未追踪。 */
    private volatile int trackedEntityId = -1;
    /** 是否处于回溯（拦截目标数据包）状态。 */
    private volatile boolean isBacktrackingActive;
    /** 解码相对位移所需的基准位置，等价于 vanilla VecDeltaCodec.base。 */
    private volatile Vec3 decodeBase;
    /** 最近一次收到被追踪玩家数据包的时间。 */
    private volatile long lastPacketTime;
    /** 客户端当前可见的旧位置（最近一个已释放回客户端的目标位置数据包）。 */
    private Vec3 appliedPos;
    /** netty 线程请求重置（目标消失 / 自己被传送）。 */
    private volatile boolean resetRequested;
    /** netty 线程请求重置（自己受到击退）。 */
    private volatile boolean velocityResetRequested;
    /** chance 判定失败被临时"拒绝"的玩家，一段时间内不再重复尝试。 */
    private int rejectedEntityId = -1;
    private long rejectedUntil;

    public BackTrack() {
        super("BackTrack", ModuleEnum.Combat);
        addValue(range, delay, chance, resetOnVelocity, resetOnTargetSwitch, render, esp);
    }

    // --- Lifecycle ---

    @Override
    public void onEnabled() {
        resetAll();
    }

    @Override
    public void onDisabled() {
        resetAll();
    }

    // --- Event Handlers ---

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            resetAll();
            return;
        }

        // 消费 netty 线程提出的重置请求（统一在渲染线程释放积压包）
        if (resetRequested || velocityResetRequested) {
            resetAll();
            return;
        }

        if (trackedEntityId == -1) {
            tryAcquireTarget();
            return;
        }

        Entity tracked = mc.level.getEntity(trackedEntityId);
        if (tracked == null || !tracked.isAlive() || tracked.isRemoved()) {
            resetAll();
            return;
        }

        // 跟随 KillAura 的目标切换，避免回溯一个并未在攻击的敌人
        if (resetOnTargetSwitch.enabled) {
            KillAura killAura = Gemini.moduleManager.getModule(KillAura.class);
            if (killAura != null && killAura.enabled) {
                Entity kaTarget = killAura.currentTarget();
                if (kaTarget != null && kaTarget.getId() != tracked.getId()) {
                    if (kaTarget instanceof Player player && isCandidateInBand(player)) {
                        acquire(player);
                    } else {
                        resetAll();
                    }
                    return;
                }
            }
        }

        // 目标长时间不再发送数据包（停下 / 离开可交互范围）→ 释放并结束
        if (System.currentTimeMillis() - lastPacketTime > delay.getValue() + 500L) {
            resetAll();
            return;
        }

        boolean active = isWithinBackTrackBand(tracked);
        isBacktrackingActive = active;

        if (active) {
            processQueue();
        } else {
            flushPackets(); // 离开范围窗口立即释放，避免残影悬空
        }
    }

    @EventTarget(1)
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (event.isCancelled()) return;
        if (event.getIoEnum() != IOEnum.In) return;

        // 单次读取，保证"拦截判定"与"取消判定"使用同一个状态快照，避免误入队
        boolean active = this.isBacktrackingActive;
        if (!active) return;

        if (resetOnVelocity.enabled && event.getPacket() instanceof ClientboundSetEntityMotionPacket motion) {
            if (motion.id() == mc.player.getId()) {
                this.velocityResetRequested = true;
                return;
            }
        }

        int tracked = this.trackedEntityId;
        if (tracked == -1) return;

        Packet<?> packet = event.getPacket();

        // 被追踪玩家被移除 / 自己重登录（被传送）→ 请求重置，积压包由渲染线程释放
        if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
            if (remove.getEntityIds().contains(tracked)) {
                this.resetRequested = true;
                return;
            }
        } else if (packet instanceof ClientboundPlayerPositionPacket) {
            this.resetRequested = true;
            return;
        }

        Vec3 pos;

        if (packet instanceof ClientboundMoveEntityPacket move) {
            Entity entity = move.getEntity(mc.level);
            if (entity != null && entity.getId() == tracked) {
                pos = move.hasPosition() ? decodeMove(move, entity) : null;
                queueTargetPacket(event, packet, tracked, pos, active);
            }
        } else if (packet instanceof ClientboundTeleportEntityPacket tp) {
            if (tp.id() == tracked) {
                queueTargetPacket(event, packet, tracked, decodeTeleport(tp), active);
            }
        } else if (packet instanceof ClientboundRotateHeadPacket head) {
            Entity entity = head.getEntity(mc.level);
            if (entity != null && entity.getId() == tracked) {
                queueTargetPacket(event, packet, tracked, null, active);
            }
        } else if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            if (motion.id() == tracked) {
                queueTargetPacket(event, packet, tracked, null, active);
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.render.enabled) return;
        if (!this.isBacktrackingActive) return;
        if (mc.player == null || mc.level == null) return;

        String espMode = esp.get();
        if (espMode.equals("None")) return;

        Entity tracked = mc.level.getEntity(trackedEntityId);
        if (tracked == null) return;

        boolean fill = espMode.equals("Box");
        boolean outline = espMode.equals("Box") || espMode.equals("Wireframe");

        // 残影（客户端当前显示的旧位置）—— 攻击判定命中的位置
        Vec3 backPos = currentAfterimagePos();
        if (backPos == null) backPos = tracked.position();

        AABB box = buildBox(tracked, backPos);
        if (fill) RenderUtils.drawFilledBox(box, 0x40FFFFFF);
        if (outline) RenderUtils.drawOutlineBox(box, 0xCCFFFFFF);

        // 真实位置（服务器最新位置）淡显，便于观察回溯跨度
        Vec3 realPos = this.decodeBase;
        if (realPos != null && realPos.distanceToSqr(backPos) > 0.05 * 0.05) {
            RenderUtils.drawOutlineBox(buildBox(tracked, realPos), 0x66FF5555);
        }
    }

    @EventTarget
    public void onShutdown(ShutdownEvent event) {
        resetAll();
    }

    // --- Packet Queue ---

    /** 释放暂存时间已到期的数据包（保序，渲染线程调用）。 */
    private void processQueue() {
        long now = System.currentTimeMillis();
        long delayMs = delay.getValue();
        int tracked = trackedEntityId;

        while (!this.packetQueue.isEmpty()) {
            PacketEntry entry = this.packetQueue.peek();
            // 跳过失配目标的残留包，避免阻塞队列
            if (entry.entityId() != tracked) {
                this.packetQueue.poll();
                continue;
            }
            if (now - entry.timestamp() < delayMs) break;
            this.packetQueue.poll();
            dispatch(entry.packet(), entry.position());
        }
    }

    /** 把积压数据包按原顺序一次性全部释放回客户端（切换目标 / 关闭 / 失效时）。 */
    private void flushPackets() {
        while (!this.packetQueue.isEmpty()) {
            PacketEntry entry = this.packetQueue.poll();
            dispatch(entry.packet(), entry.position());
        }
    }

    @SuppressWarnings({"unchecked"})
    private void dispatch(Packet<?> packet, Vec3 position) {
        try {
            if (mc.getConnection() != null) {
                // 直接走 vanilla 的处理入口，等价于 channelRead0 的转发；不会再次触发
                // MixinConnection 的注入点，因此不存在"释放后又重新拦截"的问题。
                ((Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>) packet)
                        .handle(mc.getConnection());
            }
        } catch (Exception ignored) {
        }
        // 记录客户端此刻可见的位置（渲染线程写入，onPacket 不再访问）
        if (position != null) {
            this.appliedPos = position;
        }
    }

    /** netty 线程：入队，并按调用时的激活快照决定是否取消该数据包。 */
    private void queueTargetPacket(PacketEvent event, Packet<?> packet, int entityId, Vec3 pos, boolean cancel) {
        this.packetQueue.add(new PacketEntry(packet, entityId, pos));
        this.lastPacketTime = System.currentTimeMillis();
        if (cancel) {
            event.setCancelled(true);
        }
    }

    /** 清空状态并释放积压数据包（onEnabled/onDisabled/目标失效/离开窗口时调用）。 */
    private void resetAll() {
        this.isBacktrackingActive = false;
        this.trackedEntityId = -1;
        this.decodeBase = null;
        this.appliedPos = null;
        this.lastPacketTime = 0L;
        this.resetRequested = false;
        this.velocityResetRequested = false;
        this.rejectedEntityId = -1;
        this.rejectedUntil = 0L;
        flushPackets();
    }

    // --- 位置解码（与 vanilla VecDeltaCodec / handleMoveEntity 保持一致） ---

    /**
     * 解码相对移动包。vanilla 使用
     * {@code base' = round(base*4096)/4096 + delta/4096}（逐轴，delta 为 0 时保持原值）；
     * 旧实现直接 {@code base + delta/4096} 会造成累计误差，导致残影判定漂移。
     */
    private Vec3 decodeMove(ClientboundMoveEntityPacket move, Entity entity) {
        Vec3 base = this.decodeBase;
        if (base == null) base = entity.position(); // 激活后首个位置包以实体当前位置为基准
        Vec3 pos = new Vec3(
                decodeAxis(base.x, move.getXa()),
                decodeAxis(base.y, move.getYa()),
                decodeAxis(base.z, move.getZa()));
        this.decodeBase = pos;
        return pos;
    }

    private static double decodeAxis(double base, short delta) {
        if (delta == 0) return base;
        return Math.round(base * 4096.0) / 4096.0 + delta / 4096.0;
    }

    /** 解码绝对传送包（含相对坐标，与 handleTeleportEntity 的 calculateAbsolute 一致）。 */
    private Vec3 decodeTeleport(ClientboundTeleportEntityPacket tp) {
        Vec3 base = this.decodeBase;
        Vec3 pos;
        if (tp.relatives().isEmpty()) {
            pos = tp.change().position();
        } else {
            PositionMoveRotation current = new PositionMoveRotation(
                    base != null ? base : Vec3.ZERO, Vec3.ZERO, 0.0f, 0.0f);
            pos = PositionMoveRotation.calculateAbsolute(current, tp.change(), tp.relatives()).position();
        }
        this.decodeBase = pos;
        return pos;
    }

    // --- Target 获取与判定 ---

    /** 选择回溯目标：优先 KillAura 当前目标，否则取范围窗口内最近的玩家。 */
    private void tryAcquireTarget() {
        if (mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();

        KillAura killAura = Gemini.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.enabled) {
            // KillAura 有目标时只考虑该目标，避免回溯无关敌人
            if (killAura.currentTarget() instanceof Player player && isCandidateInBand(player)) {
                if (player.getId() == rejectedEntityId && now < rejectedUntil) return;
                if (rollChance(now)) {
                    acquire(player);
                } else {
                    rejectedEntityId = player.getId();
                    rejectedUntil = now + 5000L;
                }
            }
            return;
        }

        float rMin = range.getMinValue();
        float rMax = range.getMaxValue();
        Vec3 eye = mc.player.getEyePosition();

        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player player : mc.level.players()) {
            if (player == mc.player || player.isRemoved() || !player.isAlive()) continue;
            // 之前 chance 判定失败过的玩家在一段时间内不再重复尝试
            if (player.getId() == rejectedEntityId && now < rejectedUntil) continue;
            double dist = eye.distanceTo(closestPoint(eye, player.getBoundingBox()));
            if (dist >= rMin && dist < rMax + 2.0 && dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        if (best == null) return;

        if (rollChance(now)) {
            acquire(best);
        } else {
            rejectedEntityId = best.getId();
            rejectedUntil = now + 5000L;
        }
    }

    /** 切换 / 开始回溯指定玩家（先释放上一个目标的积压包）。 */
    private void acquire(Player player) {
        flushPackets();
        this.trackedEntityId = player.getId();
        this.decodeBase = null;
        this.appliedPos = null; // 新目标的残影从下一帧重建
        this.lastPacketTime = System.currentTimeMillis();
        this.isBacktrackingActive = false; // 等待下一次 onUpdate 的距离判定再激活
    }

    /** 残影距离是否落在 [rangeMin, rangeMax) 窗口内，决定是否延迟目标数据包。 */
    private boolean isWithinBackTrackBand(Entity tracked) {
        if (mc.player == null) return false;

        Vec3 eye = mc.player.getEyePosition();
        float rMin = range.getMinValue();
        float rMax = range.getMaxValue();

        // 真实距离过远时不值得拦截
        double realDistance = eye.distanceTo(closestPoint(eye, tracked.getBoundingBox()));
        if (realDistance > rMax + 2.0) return false;

        // 残影 = 客户端当前可见的旧位置（队列中最旧的带位置数据包，或当前实时位置）
        Vec3 backPos = currentAfterimagePos();
        if (backPos == null) backPos = tracked.position();
        double backDistance = eye.distanceTo(closestPoint(eye, buildBox(tracked, backPos)));

        return backDistance >= rMin && backDistance < rMax;
    }

    /** 玩家是否位于目标获取窗口内（供跟随 KillAura 目标时使用）。 */
    private boolean isCandidateInBand(Player player) {
        if (mc.player == null || player.isRemoved() || !player.isAlive()) return false;
        Vec3 eye = mc.player.getEyePosition();
        float rMin = range.getMinValue();
        float rMax = range.getMaxValue();
        double dist = eye.distanceTo(closestPoint(eye, player.getBoundingBox()));
        return dist >= rMin && dist < rMax + 2.0;
    }

    /** 队列中最早的带位置数据包所对应的位置（即客户端当前可见的残影）。 */
    private Vec3 currentAfterimagePos() {
        if (this.appliedPos != null) return this.appliedPos;
        for (PacketEntry entry : this.packetQueue) {
            if (entry.entityId() == this.trackedEntityId && entry.position() != null) {
                return entry.position();
            }
        }
        return null;
    }

    private static AABB buildBox(Entity entity, Vec3 pos) {
        double halfWidth = entity.getBbWidth() / 2.0;
        double height = entity.getBbHeight();
        return new AABB(
                pos.x - halfWidth, pos.y, pos.z - halfWidth,
                pos.x + halfWidth, pos.y + height, pos.z + halfWidth);
    }

    private static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
                Math.max(box.minX, Math.min(point.x, box.maxX)),
                Math.max(box.minY, Math.min(point.y, box.maxY)),
                Math.max(box.minZ, Math.min(point.z, box.maxZ)));
    }

    private boolean rollChance(long now) {
        return Math.random() * 100.0 <= this.chance.getValue();
    }

    // --- Public API ---

    /** 是否处于回溯（拦截目标数据包）状态。 */
    public boolean isBacktracking() {
        return this.isBacktrackingActive;
    }

    /** 是否处于回溯状态且队列中有积压数据包。 */
    public boolean isActive() {
        return this.isBacktrackingActive && !this.packetQueue.isEmpty();
    }
}
