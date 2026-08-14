package geminiclient.gemini.modules.impl.combat.killaura;

import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.utils.ReachUtils;
import geminiclient.gemini.utils.TimerUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 目标管理：实体收集/过滤/排序、瞄准点选择与平滑、前向预测 + 人类反应延迟，
 * 以及点击节奏（HumanClickPattern）的驱动。仅依赖 {@link KillAuraSettings}，
 * 每 tick 由模块按固定顺序调用。
 */
public final class TargetManager implements MinecraftInstance {
    private final KillAuraSettings settings;

    private final List<Entity> entities = new CopyOnWriteArrayList<>();
    private Entity curr;
    private final TimerUtils attackTimer = new TimerUtils();
    // [修复] 缓存下一次攻击的延迟时间，防止每次 Update 时随机数跳动
    private long nextAttackDelay = 0;

    // Aim point state
    private Entity aimPointTarget;
    private String aimPointMode;
    private Vec3 currentLocalAim;
    private Vec3 goalLocalAim;
    private int randomAimTicksRemaining;

    // Human reaction delay state
    private Entity reactionTarget;
    private Vec3 lastObservedTargetPosition;
    private Vec3 lastObservedTargetVelocity;
    private Vec3 acceptedWorldAim;
    private Vec3 heldWorldAim;
    private Vec3 catchupWorldAim;
    private float heldYaw, heldPitch;
    private long reactionUntilNanos;
    private int reactionCooldownTicks;
    private int catchupTicksRemaining;
    private boolean reactionHolding;
    private boolean reactionCatchingUp;

    // Human-like click helpers
    private final HumanClickPattern.Config clickPatternConfig = new HumanClickPattern.Config();
    private final HumanClickPattern clickPattern = new HumanClickPattern(clickPatternConfig);
    private Entity clickTarget;

    private static final int REACTION_COOLDOWN_TICKS = 4;
    private static final int MAX_CATCHUP_TICKS = 12;
    private static final double MAX_PREDICTION_VELOCITY = 1.5;

    public TargetManager(KillAuraSettings settings) {
        this.settings = settings;
    }

    // ---------------------------------------------------------------------
    // 对外接口（模块编排用）
    // ---------------------------------------------------------------------

    public Entity current() {
        return curr;
    }

    public List<Entity> all() {
        return entities;
    }

    public Entity clickTarget() {
        return clickTarget;
    }

    /** 是否应该继续工作（没有被 UsingItem / OpeningScreen 打断）。 */
    public boolean canWork() {
        if (mc.player == null)
            return false;
        if (mc.player.isUsingItem() && settings.stop.boolValues[0].enabled)
            return false;
        return mc.gui.screen() == null || !settings.stop.boolValues[1].enabled;
    }

    /**
     * 计算当前 tick 的目标世界坐标瞄准点：本地瞄准点平滑 + 前向预测 + 人类反应延迟。
     * 目标切换时会初始化瞄准点并复位点击节奏。
     */
    public Vec3 computeAim(Entity entity, Vec3 eyePosition, float serverYaw, float serverPitch) {
        boolean targetChanged = entity != aimPointTarget;
        if (targetChanged) {
            initializeAimPoint(entity);
        } else {
            updateAimPointGoal(entity);
        }
        if (!currentLocalAim.equals(goalLocalAim)) {
            currentLocalAim = currentLocalAim.lerp(goalLocalAim, settings.aimPointSmooth.getValue());
        }

        Vec3 liveWorldAim = localAimToWorld(entity, currentLocalAim);
        Vec3 predictedWorldAim = applyForwardPrediction(liveWorldAim);
        return applyReactionDelay(entity, eyePosition, predictedWorldAim,
                serverYaw, serverPitch, targetChanged);
    }

    // 切换目标后重新开始点击节奏（人眼重新锁定新目标需要时间）
    public void onTargetSwitched(Entity target, double distanceToTarget) {
        clickTarget = target;
        attackTimer.reset();
        nextAttackDelay = computeNextClickDelay(distanceToTarget);
    }

    public boolean isClickDue() {
        return attackTimer.getTimeElapsed() >= nextAttackDelay;
    }

    // 攻击后重置下一次的随机延迟
    public void onAttack(double distanceToTarget) {
        attackTimer.reset();
        nextAttackDelay = computeNextClickDelay(distanceToTarget);
    }

    /**
     * 完整复位瞄准/反应/点击状态，并按给定距离预计算第一次点击延迟
     * （用于模块启用时，等效于原 onEnabled 中的初始化）。
     */
    public void reset(double initialDistance) {
        resetState();
        nextAttackDelay = computeNextClickDelay(initialDistance);
    }

    /** 复位瞄准/反应/点击状态，不触碰点击延迟（用于禁用 / 失去目标时的干净收尾）。 */
    public void resetState() {
        clickTarget = null;
        clickPattern.reset();
        resetAimTracking();
    }

    /** 射线是否真的打在目标身上（RayTrace 用）。 */
    public boolean isLookingAt(Entity entity, float serverYaw, float serverPitch) {
        if (mc.player == null || mc.level == null)
            return false;

        Vec3 start = mc.player.getEyePosition();
        Vec3 direction = Vec3.directionFromRotation(serverPitch, serverYaw);
        Vec3 end = start.add(direction.scale(settings.range.getValue()));

        double maxDistanceSquared = start.distanceToSqr(end);
        if (settings.blockRayTrace.enabled) {
            // 开启时由方块命中点截断射线，避免隔墙或透过方块攻击。
            BlockHitResult blockHit = mc.level.clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            maxDistanceSquared = start.distanceToSqr(blockHit.getLocation());
        }

        Entity closestHit = null;
        double closestDistanceSquared = maxDistanceSquared;
        for (Entity candidate : mc.level.entitiesForRendering()) {
            if (candidate == mc.player || !candidate.isPickable())
                continue;

            // 使用原始碰撞箱；只有真实命中实体时才允许攻击。
            Optional<Vec3> hit = candidate.getBoundingBox().clip(start, end);
            if (hit.isEmpty())
                continue;

            double hitDistanceSquared = start.distanceToSqr(hit.get());
            if (hitDistanceSquared < closestDistanceSquared) {
                closestDistanceSquared = hitDistanceSquared;
                closestHit = candidate;
            }
        }

        return closestHit == entity;
    }

    // ---------------------------------------------------------------------
    // 目标收集与过滤
    // ---------------------------------------------------------------------

    public void findTargets() {
        if (mc.level == null || mc.player == null)
            return;

        entities.clear();
        curr = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (isValidTarget(entity) && isInFov(entity) &&
                    ReachUtils.distanceToSqr(entity) < settings.range.getValue() * settings.range.getValue()) {
                entities.add(entity);
            }
        }

        if (entities.isEmpty())
            return;

        switch (settings.pro.get()) {
            case "Distance":
                entities.sort(Comparator.comparingDouble(mc.player::distanceTo));
                break;
            case "Health":
                entities.sort(
                        Comparator.comparingDouble(e -> e instanceof LivingEntity living ? living.getHealth() : 0.0));
                break;
            case "hurtTime":
                entities.sort(
                        Comparator.comparingDouble(e -> e instanceof LivingEntity living ? living.hurtTime : 0.0));
                break;
        }

        curr = entities.getFirst(); // 考虑到兼容性，通常使用 get(0) 替代 Java21 的 getFirst()
    }

    private boolean isValidTarget(Entity entity) {
        if (mc.player == null)
            return false;
        if (entity == null || entity == mc.player)
            return false;
        if (!(entity instanceof LivingEntity))
            return false;
        if (((LivingEntity) entity).hurtTime > settings.hurtTime.getValue())
            return false;
        if (entity instanceof ArmorStand)
            return false;
        if (entity.isAlive() || settings.targets.boolValues[3].enabled) {
            if (entity instanceof Player && settings.targets.boolValues[0].enabled)
                return true;
            if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat)
                    && settings.targets.boolValues[1].enabled)
                return true;
            if (entity instanceof Player && settings.targets.boolValues[4].enabled) {
                if (isTeammate(entity)) {
                    return false;
                }
            }
            return entity instanceof Animal && settings.targets.boolValues[2].enabled;
        }
        return false;
    }

    private boolean isInFov(Entity entity) {
        if (mc.player == null)
            return false;
        if (settings.fov.getValue() >= 360f)
            return true;
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();
        double yawToEntity = Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        double yawDiff = Math.abs(((mc.player.getYRot() - yawToEntity) % 360 + 540) % 360 - 180);
        return yawDiff <= settings.fov.getValue() / 2;
    }

    private boolean isTeammate(@UnknownNullability Entity player) {
        if (mc.player == null)
            return false;
        return player.getTeamColor() == mc.player.getTeamColor();
    }

    // ---------------------------------------------------------------------
    // 瞄准点选择与平滑
    // ---------------------------------------------------------------------

    private void initializeAimPoint(Entity entity) {
        resetAimTracking();
        aimPointTarget = entity;
        aimPointMode = settings.aimMode.get();
        goalLocalAim = selectLocalAimPoint(entity);
        currentLocalAim = goalLocalAim;
        randomAimTicksRemaining = settings.aimMode.is("Random") ? settings.randomAimInterval.getValue() : 0;
    }

    private void updateAimPointGoal(Entity entity) {
        String selectedMode = settings.aimMode.get();
        if (!selectedMode.equals(aimPointMode)) {
            aimPointMode = selectedMode;
            goalLocalAim = selectLocalAimPoint(entity);
            randomAimTicksRemaining = settings.aimMode.is("Random") ? settings.randomAimInterval.getValue() : 0;
            return;
        }
        if (settings.aimMode.is("Random") && --randomAimTicksRemaining <= 0) {
            goalLocalAim = selectLocalAimPoint(entity);
            randomAimTicksRemaining = settings.randomAimInterval.getValue();
        }
    }

    private Vec3 selectLocalAimPoint(Entity entity) {
        return switch (settings.aimMode.get()) {
            case "Chest" -> new Vec3(0.0, 0.65, 0.0);
            case "Body" -> new Vec3(0.0, 0.50, 0.0);
            case "Legs" -> new Vec3(0.0, 0.20, 0.0);
            case "Random" -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                yield new Vec3(
                        random.nextDouble(-0.25, 0.25),
                        random.nextDouble(0.15, 0.90),
                        random.nextDouble(-0.25, 0.25));
            }
            case "Head" -> new Vec3(0.0,
                    net.minecraft.util.Mth.clamp(
                            entity.getEyeHeight() / Math.max(entity.getBbHeight(), 0.001f),
                            0.0f, 1.0f),
                    0.0);
            default -> new Vec3(0.0, 0.50, 0.0);
        };
    }

    private Vec3 localAimToWorld(Entity entity, Vec3 localAim) {
        return new Vec3(
                entity.getX() + localAim.x * entity.getBbWidth(),
                entity.getY() + localAim.y * entity.getBbHeight(),
                entity.getZ() + localAim.z * entity.getBbWidth());
    }

    // ---------------------------------------------------------------------
    // 前向预测 + 人类反应延迟
    // ---------------------------------------------------------------------

    private Vec3 applyForwardPrediction(Vec3 worldAim) {
        if (mc.player == null || settings.prediction.getValue() <= 0f)
            return worldAim;

        Vec3 relativeVelocity = clampVelocity(
                aimPointTarget.getDeltaMovement().subtract(mc.player.getDeltaMovement()));
        Vec3 predictedAim = worldAim.add(relativeVelocity.scale(settings.prediction.getValue()));
        return isFinite(predictedAim) ? predictedAim : worldAim;
    }

    private Vec3 clampVelocity(Vec3 velocity) {
        if (!isFinite(velocity))
            return Vec3.ZERO;
        double length = velocity.length();
        if (length <= MAX_PREDICTION_VELOCITY)
            return velocity;
        return velocity.scale(MAX_PREDICTION_VELOCITY / length);
    }

    private Vec3 applyReactionDelay(Entity entity, Vec3 eyePosition,
                                    Vec3 predictedWorldAim, float serverYaw, float serverPitch,
                                    boolean targetChanged) {
        Vec3 targetPosition = entity.position();
        Vec3 targetVelocity = isFinite(entity.getDeltaMovement())
                ? entity.getDeltaMovement() : Vec3.ZERO;

        if (targetChanged || entity != reactionTarget) {
            reactionTarget = entity;
            lastObservedTargetPosition = targetPosition;
            lastObservedTargetVelocity = targetVelocity;
            acceptedWorldAim = predictedWorldAim;
            if (settings.movementReaction.enabled) {
                startReaction(eyePosition, createHeldAim(eyePosition, predictedWorldAim, serverYaw, serverPitch));
            }
        } else {
            boolean repositioned = detectReposition(targetPosition);
            lastObservedTargetPosition = targetPosition;
            lastObservedTargetVelocity = targetVelocity;
            if (reactionCooldownTicks > 0)
                reactionCooldownTicks--;
            if (settings.movementReaction.enabled && repositioned && !reactionHolding
                    && !reactionCatchingUp && reactionCooldownTicks <= 0) {
                startReaction(eyePosition, acceptedWorldAim != null ? acceptedWorldAim : predictedWorldAim);
            }
        }

        if (!settings.movementReaction.enabled) {
            clearReactionState();
            reactionTarget = null;
            lastObservedTargetPosition = null;
            lastObservedTargetVelocity = null;
            acceptedWorldAim = predictedWorldAim;
            return predictedWorldAim;
        }

        if (reactionHolding) {
            if (System.nanoTime() < reactionUntilNanos)
                return aimFromRotation(eyePosition, heldYaw, heldPitch,
                        eyePosition.distanceTo(predictedWorldAim));
            reactionHolding = false;
            reactionCatchingUp = true;
            catchupWorldAim = aimFromRotation(eyePosition, heldYaw, heldPitch,
                    eyePosition.distanceTo(predictedWorldAim));
            catchupTicksRemaining = MAX_CATCHUP_TICKS;
            reactionCooldownTicks = REACTION_COOLDOWN_TICKS;
        }

        if (reactionCatchingUp) {
            double catchupFactor = Math.max(settings.aimPointSmooth.getValue(), 0.25f);
            catchupWorldAim = catchupWorldAim.lerp(predictedWorldAim, catchupFactor);
            catchupTicksRemaining--;
            if (catchupTicksRemaining <= 0 || catchupWorldAim.distanceToSqr(predictedWorldAim) < 0.0004) {
                reactionCatchingUp = false;
                catchupWorldAim = predictedWorldAim;
            }
            acceptedWorldAim = catchupWorldAim;
            return catchupWorldAim;
        }

        acceptedWorldAim = predictedWorldAim;
        return predictedWorldAim;
    }

    private boolean detectReposition(Vec3 targetPosition) {
        if (lastObservedTargetPosition == null || lastObservedTargetVelocity == null
                || reactionHolding)
            return false;
        Vec3 residual = targetPosition.subtract(
                lastObservedTargetPosition.add(lastObservedTargetVelocity));
        double horizontalResidual = Math.sqrt(
                residual.x * residual.x + residual.z * residual.z);
        double threshold = settings.repositionThreshold.getValue();
        return horizontalResidual > threshold || Math.abs(residual.y) > threshold * 1.5;
    }

    private void startReaction(Vec3 eyePosition, Vec3 holdPoint) {
        int minDelay = settings.reactionDelay.getMinValue();
        int maxDelay = settings.reactionDelay.getMaxValue();
        int delayMillis = minDelay == maxDelay
                ? minDelay
                : ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);
        heldWorldAim = holdPoint;
        Vec3 holdDelta = holdPoint.subtract(eyePosition);
        double horizontalDistance = Math.sqrt(
                holdDelta.x * holdDelta.x + holdDelta.z * holdDelta.z);
        heldYaw = (float) (Math.atan2(holdDelta.z, holdDelta.x) * 180 / Math.PI) - 90f;
        heldPitch = (float) -Math.toDegrees(Math.atan2(holdDelta.y, horizontalDistance));
        reactionUntilNanos = System.nanoTime() + delayMillis * 1_000_000L;
        reactionHolding = delayMillis > 0;
        reactionCatchingUp = false;
        catchupWorldAim = holdPoint;
        catchupTicksRemaining = 0;
    }

    private Vec3 createHeldAim(Vec3 eyePosition, Vec3 liveAim, float serverYaw, float serverPitch) {
        return aimFromRotation(eyePosition, serverYaw, serverPitch,
                Math.max(eyePosition.distanceTo(liveAim), 0.1));
    }

    private Vec3 aimFromRotation(Vec3 eyePosition, float yaw, float pitch, double distance) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        Vec3 look = new Vec3(
                -Math.sin(yawRadians) * Math.cos(pitchRadians),
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * Math.cos(pitchRadians));
        return eyePosition.add(look.scale(Math.max(distance, 0.1)));
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    // ---------------------------------------------------------------------
    // 点击节奏
    // ---------------------------------------------------------------------

    /**
     * 把模块设置填入 {@link HumanClickPattern.Config} 并计算下一次点击延迟（毫秒）。
     */
    private long computeNextClickDelay(double distanceToTarget) {
        if (!settings.noCoolDown.enabled)
            return 0;

        HumanClickPattern.Config cfg = clickPatternConfig;
        cfg.minCps = settings.cps.getMinValue();
        cfg.maxCps = settings.cps.getMaxValue();
        cfg.variance = settings.clickVariance.getValue();
        cfg.doubleClickChance = settings.doubleClickChance.getValue();
        double gap = settings.doubleClickGap.getValue();
        cfg.doubleClickGapMin = gap * 0.55;
        cfg.doubleClickGapMax = gap * 1.45;
        cfg.pauseEveryMin = settings.pauseEvery.getMinValue();
        cfg.pauseEveryMax = settings.pauseEvery.getMaxValue();
        cfg.pauseMin = settings.pauseLength.getMinValue();
        cfg.pauseMax = settings.pauseLength.getMaxValue();
        cfg.adrenaline = settings.adrenalineBoost.getValue();

        double rangeVal = Math.max(settings.range.getValue(), 0.001f);
        double adrenalineRangeVal = settings.adrenalineRange.getValue();
        return clickPattern.nextDelay(distanceToTarget,
                rangeVal * adrenalineRangeVal,
                rangeVal * (adrenalineRangeVal + 0.35));
    }

    // ---------------------------------------------------------------------
    // 状态复位
    // ---------------------------------------------------------------------

    private void resetAimTracking() {
        aimPointTarget = null;
        aimPointMode = null;
        currentLocalAim = null;
        goalLocalAim = null;
        randomAimTicksRemaining = 0;
        reactionTarget = null;
        lastObservedTargetPosition = null;
        lastObservedTargetVelocity = null;
        acceptedWorldAim = null;
        clearReactionState();
    }

    private void clearReactionState() {
        heldWorldAim = null;
        catchupWorldAim = null;
        heldYaw = 0f;
        heldPitch = 0f;
        reactionUntilNanos = 0L;
        reactionCooldownTicks = 0;
        catchupTicksRemaining = 0;
        reactionHolding = false;
        reactionCatchingUp = false;
    }
}
