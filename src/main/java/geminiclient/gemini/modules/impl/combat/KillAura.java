package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.RotationManager;
import geminiclient.gemini.utils.MathHelper;
import geminiclient.gemini.utils.ReachUtils;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class KillAura extends Module {
    private final BoolValue noCoolDown = new BoolValue("NoCoolDown", false);
    private final IntRangeValue cps = new IntRangeValue("CPS", 10, 18, 1, 20, () -> noCoolDown.enabled);
    private final IntValue hurtTime = new IntValue("HurtTime", 10, 1, 10);
    private final FloatValue range = new FloatValue("Range", 3.0f, 1.0f, 6.0f);
    private final FloatValue fov = new FloatValue("FOV", 180f, 30f, 360f);
    private final FloatValue rotationSpeed = new FloatValue("RotationSpeed", 180f, 30f, 360f);
    private final ListValue pro = new ListValue("Priority", "Distance", new String[]{
            "Distance", "Health", "hurtTime"
    });
    private final CheckboxValue targets = new CheckboxValue("Targets", new BoolValue[]{
            new BoolValue("AttackPlayers", true), new BoolValue("AttackMobs", true),
            new BoolValue("AttackAnimals", false), new BoolValue("AttackDead")
    });
    private final CheckboxValue stop = new CheckboxValue("StopWorking", new BoolValue[]{
            new BoolValue("UsingItem"),
            new BoolValue("OpeningScreen")
    });
    private final ListValue rotationMode = new ListValue("RotationMode", "Linear", new String[]{
            "Linear", "Exponential", "Smooth", "Adaptive"
    });
    private final BoolValue silentRotate = new BoolValue("SilentRotate", true);
    private final BoolValue requireAim = new BoolValue("RequireAim", false);
    private final ListValue aimMode = new ListValue("AimMode", "Head", new String[]{
            "Head", "Chest", "Body", "Legs", "Random"
    });

    private final List<Entity> entities = new CopyOnWriteArrayList<>();
    private Entity curr;
    private final TimerUtils attackTimer = new TimerUtils();

    // [修复] 缓存下一次攻击的延迟时间，防止每次 Update 时随机数跳动
    private long nextAttackDelay = 0;

    float serverYaw, serverPitch;

    // Smooth mode state
    private float smoothStartYaw, smoothStartPitch, smoothProgress;
    private Entity smoothTarget;

    public KillAura() {
        super("KillAura", ModuleEnum.Combat);
        addValue(noCoolDown, cps, range, fov, hurtTime, pro,
                targets, stop, rotationSpeed, rotationMode,
                silentRotate, requireAim, aimMode);
    }

    @Override
    public void onEnabled() {
        smoothTarget = null;
        smoothProgress = 0f;
        nextAttackDelay = getRandomDelay(); // 初始化第一次延迟
        if (mc.player != null) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
        }
    }

    @Override
    public void onDisabled() {
        Gemini.rotationManager.releaseRotation(this);
        smoothTarget = null;
    }

    @SuppressWarnings("unused")
    @EventTarget(5)
    public void onMotion(MotionEvent event) {
        if (curr == null || mc.player == null || !stopWorkingMethod())
            return;
        if (event.getTimeEnum() == TimeEnum.Pre) {
            if (!silentRotate.enabled) {
                mc.player.setYRot(serverYaw);
                mc.player.setXRot(serverPitch);
            }
        }
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.gameMode == null)
            return;

        findTargets();

        if (curr != null && stopWorkingMethod()) {
            updateTargetAngles(curr);
            Gemini.rotationManager.requestRotation(this, serverYaw, serverPitch,
                    RotationManager.PRIORITY_KILLAURA, true);

            if (requireAim.enabled && !isLookingAtTarget(curr))
                return;

            if (noCoolDown.enabled) {
                // [修复] 使用固定的 nextAttackDelay 进行比较，而不是一直生成新随机数
                if (attackTimer.getTimeElapsed() >= nextAttackDelay) {
                    mc.gameMode.attack(mc.player, curr);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    attackTimer.reset();
                    nextAttackDelay = getRandomDelay(); // 攻击后重置下一次的随机延迟
                }
            } else {
                // 原版冷却模式
                if (mc.player.getAttackStrengthScale(0.5f) >= 1.0f) {
                    mc.gameMode.attack(mc.player, curr);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }
        } else {
            Gemini.rotationManager.releaseRotation(this);
        }
    }

    private long getRandomDelay() {
        if (!noCoolDown.enabled)
            return 0;

        int minCps = cps.getMinValue();
        int maxCps = cps.getMaxValue();
        int cpsVal = minCps + (int) (Math.random() * (maxCps - minCps + 1));

        return (long) (1000.0 / Math.max(1, cpsVal));
    }

    private void findTargets() {
        if (mc.level == null || mc.player == null)
            return;

        entities.clear();
        curr = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            // 注意：这里假设你的 ReachUtils 返回的是实际距离的平方，如果是返回单边距离，请将 range.getValue() * range.getValue() 改回 range.getValue()
            if (isValidTarget(entity) && isInFov(entity) &&
                    ReachUtils.getMinDistanceBetweenEntities(mc.player.getBoundingBox(), 0.1, mc.player.getX(), mc.player.getY(), mc.player.getZ(), entity.getBoundingBox(), 0.0, entity.getX(), entity.getY(), entity.getZ()) < range.getValue() * range.getValue()) {
                entities.add(entity);
            }
        }

        if (entities.isEmpty())
            return;

        switch (pro.get()) {
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

    private boolean stopWorkingMethod() {
        if (mc.player == null)
            return false;
        if (mc.player.isUsingItem() && stop.boolValues[0].enabled)
            return false;
        return mc.screen == null || !stop.boolValues[1].enabled;
    }

    private boolean isValidTarget(Entity entity) {
        if (mc.player == null)
            return false;
        if (entity == null || entity == mc.player)
            return false;
        if (!(entity instanceof LivingEntity))
            return false;
        if (((LivingEntity) entity).hurtTime > hurtTime.getValue())
            return false;
        if (entity instanceof ArmorStand)
            return false;
        if (entity.isAlive() || targets.boolValues[3].enabled) {
            if (entity instanceof Player && targets.boolValues[0].enabled)
                return true;
            if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat)
                    && targets.boolValues[1].enabled)
                return true;
            return entity instanceof Animal && targets.boolValues[2].enabled;
        }
        return false;
    }

    private boolean isInFov(Entity entity) {
        if (mc.player == null)
            return false;
        if (fov.getValue() >= 360f)
            return true;
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();
        double yawToEntity = Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        double yawDiff = Math.abs(((mc.player.getYRot() - yawToEntity) % 360 + 540) % 360 - 180);
        return yawDiff <= fov.getValue() / 2;
    }

    private void updateTargetAngles(Entity entity) {
        if (mc.player == null)
            return;

        double targetX = entity.getX();
        double targetZ = entity.getZ();
        double targetY;

        switch (aimMode.get()) {
            case "Chest":
                targetY = entity.getY() + entity.getBbHeight() * 0.65;
                break;
            case "Body":
                targetY = entity.getY() + entity.getBbHeight() * 0.5;
                break;
            case "Legs":
                targetY = entity.getY() + entity.getBbHeight() * 0.2;
                break;
            case "Random":
                targetY = entity.getY() + entity.getBbHeight() * (0.1 + Math.random() * 0.85);
                double halfWidth = entity.getBbWidth() / 2.0;
                targetX += (Math.random() - 0.5) * halfWidth;
                targetZ += (Math.random() - 0.5) * halfWidth;
                break;
            case "Head":
            default:
                targetY = entity.getY() + entity.getEyeHeight();
        }

        final double xSize = targetX - mc.player.getX();
        final double ySize = targetY - (mc.player.getY() + mc.player.getEyeHeight());
        final double zSize = targetZ - mc.player.getZ();
        final double theta = MathHelper.sqrt_double(xSize * xSize + zSize * zSize);

        final float targetYaw = (float) (Math.atan2(zSize, xSize) * 180 / Math.PI) - 90;
        final float targetPitch = (float) (-(Math.atan2(ySize, theta) * 180 / Math.PI));

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - currentPitch);

        float finalYaw = currentYaw + yawDiff;
        float finalPitch = net.minecraft.util.Mth.clamp(currentPitch + pitchDiff, -90.0f, 90.0f);

        applyRotation(finalYaw, finalPitch, entity);
    }

    private void applyRotation(float targetYaw, float targetPitch, Entity target) {
        float rotYawDiff = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float rotPitchDiff = targetPitch - serverPitch;
        float speed = rotationSpeed.getValue();

        switch (rotationMode.get()) {
            case "Linear": {
                serverYaw += Math.copySign(Math.min(Math.abs(rotYawDiff), speed), rotYawDiff);
                serverPitch += Math.copySign(Math.min(Math.abs(rotPitchDiff), speed), rotPitchDiff);
                break;
            }
            case "Exponential": {
                float factor = Math.min(speed / 180f, 0.99f);
                serverYaw += rotYawDiff * factor;
                serverPitch += rotPitchDiff * factor;
                break;
            }
            case "Smooth": {
                if (target != smoothTarget) {
                    smoothTarget = target;
                    smoothStartYaw = serverYaw;
                    smoothStartPitch = serverPitch;
                    smoothProgress = 0f;
                }
                float totalYawDiff = MathHelper.wrapAngleTo180_float(targetYaw - smoothStartYaw);
                float totalPitchDiff = targetPitch - smoothStartPitch;
                float totalDist = Math.abs(totalYawDiff) + Math.abs(totalPitchDiff);
                if (totalDist > 0.5f) {
                    float duration = Math.max(totalDist / speed, 3f);
                    smoothProgress += 1f / duration;
                    if (smoothProgress >= 1f) smoothProgress = 1f;
                    float t = smoothProgress;
                    float eased = t * t * (3f - 2f * t);
                    serverYaw = smoothStartYaw + totalYawDiff * eased;
                    serverPitch = smoothStartPitch + totalPitchDiff * eased;
                }
                break;
            }
            case "Adaptive": {
                float yawDist = Math.abs(rotYawDiff);
                float pitchDist = Math.abs(rotPitchDiff);
                float yawStep = speed * (0.5f + yawDist / 90f);
                float pitchStep = speed * (0.5f + pitchDist / 90f);
                serverYaw += Math.copySign(Math.min(yawDist, yawStep), rotYawDiff);
                serverPitch += Math.copySign(Math.min(pitchDist, pitchStep), rotPitchDiff);
                break;
            }
        }

        // [修复] 强制限制最终的 serverPitch 避免平滑计算越界翻转引发的反作弊拦截
        serverPitch = net.minecraft.util.Mth.clamp(serverPitch, -90f, 90f);
    }

    private boolean isLookingAtTarget(Entity entity) {
        if (mc.player == null)
            return false;

        float yaw = serverYaw;
        float pitch = serverPitch;

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double dx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dy = -Math.sin(pitchRad);
        double dz = Math.cos(yawRad) * Math.cos(pitchRad);

        // [修复] 增加一个极小值避免除以0导致的 NaN 或不可预测的 Infinity
        if (dx == 0) dx = 0.0001;
        if (dy == 0) dy = 0.0001;
        if (dz == 0) dz = 0.0001;

        double eyeX = mc.player.getX();
        double eyeY = mc.player.getY() + mc.player.getEyeHeight();
        double eyeZ = mc.player.getZ();

        AABB bb = entity.getBoundingBox().inflate(0.1);

        double tMin = 0.0;
        double tMax = range.getValue();

        // X slab
        double t1 = (bb.minX - eyeX) / dx;
        double t2 = (bb.maxX - eyeX) / dx;
        if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
        tMin = Math.max(tMin, t1);
        tMax = Math.min(tMax, t2);
        if (tMin > tMax) return false;

        // Y slab
        t1 = (bb.minY - eyeY) / dy;
        t2 = (bb.maxY - eyeY) / dy;
        if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
        tMin = Math.max(tMin, t1);
        tMax = Math.min(tMax, t2);
        if (tMin > tMax) return false;

        // Z slab
        t1 = (bb.minZ - eyeZ) / dz;
        t2 = (bb.maxZ - eyeZ) / dz;
        if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
        tMin = Math.max(tMin, t1);
        tMax = Math.min(tMax, t2);

        return tMin <= tMax;
    }
}